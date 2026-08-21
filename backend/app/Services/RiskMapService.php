<?php

namespace App\Services;

use App\Models\Rainfall;
use App\Models\RiskScore;
use App\Models\Village;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;

/**
 * =====================================================================================
 *  RiskMapService — RiskEngine ke liye data ikattha karta hai, aur result cache karta hai
 * =====================================================================================
 *
 *  KYA KARTA HAI: RiskEngine ko chalane ke liye jo inputs chahiye (barish, river level,
 *  elevation) wo DB/Open-Meteo se laata hai, engine ko deta hai, aur output cache karta hai.
 *
 *  YAHAN KOI RISK LOGIC NAHI HAI. Ek bhi threshold, ek bhi if-else jo level decide kare —
 *  nahi. Wo sab sirf RiskEngine.php mein (CLAUDE.md convention). Ye file sirf "plumbing" hai:
 *  data uthao -> engine ko do -> result sambhalo.
 *
 *  DO MODE:
 *    live   -> Open-Meteo se abhi ki asli barish
 *    replay -> Assam June-2022 ka seeded data, day index (0..11) ke hisaab se
 *  Dono mein ENGINE WAHI hai — sirf data feed badalti hai. Yehi baat demo mein bolni hai.
 *
 *  CACHING KYUN (BUILD_PLAN section 6 - scale-ready):
 *   Hazaar citizen ek saath app kholte hain to hazaar baar 30 gaon ka risk compute karna
 *   pagalpan hai. Ek baar compute karke cache mein rakho, sab wahi cached response paate hain.
 *   Laravel FILE cache use kar rahe hain, Redis nahi — droplet pe sirf 1GB RAM hai aur do site
 *   already chal rahi hain (decision D7).
 * =====================================================================================
 */
final class RiskMapService
{
    public const MODE_LIVE = 'live';

    public const MODE_REPLAY = 'replay';

    /**
     * Live risk map ka cache TTL (seconds).
     * KYUN 900 (15 min): scheduler har 30 min chalta hai. Aadha interval rakha taaki scheduler
     * ke turant baad wali request ko fresh data mile, aur phir bhi DB pe load na aaye.
     */
    private const CACHE_TTL_LIVE = 900;

    /**
     * Replay cache TTL — 24 ghante.
     * KYUN itna lamba: 2022 ka data ab kabhi badlega nahi. Ek baar compute, poore demo ke liye.
     * Demo ke waqt slider ghumane pe har day instantly aata hai — koi lag nahi.
     */
    private const CACHE_TTL_REPLAY = 86400;

    public function __construct(
        private readonly RiskEngine $engine,
        private readonly OpenMeteoService $openMeteo,
    ) {}

    // ---------------------------------------------------------------------------------
    //  PUBLIC API
    // ---------------------------------------------------------------------------------

    /**
     * map() — saare gaon ka risk (dashboard ka main map, app ka village list).
     *
     * INPUT : mode ('live'|'replay'), day (replay ke liye 0-based index, null = aakhri din)
     * OUTPUT: ['mode', 'day', 'date', 'generated_at', 'villages' => [...], 'summary' => [...]]
     *
     * KYUN summary bhi saath: dashboard ko "kitne RED, kitne YELLOW, kitni aabadi affected"
     * chahiye hi hota hai. Frontend se dobara loop karwane se behtar ek hi response mein de do.
     */
    public function map(string $mode, ?int $day = null): array
    {
        $mode = $this->normaliseMode($mode);
        $day = $mode === self::MODE_REPLAY ? $this->resolveReplayDay($day) : null;

        $key = $mode === self::MODE_REPLAY ? "risk:map:replay:{$day}" : 'risk:map:live';
        $ttl = $mode === self::MODE_REPLAY ? self::CACHE_TTL_REPLAY : self::CACHE_TTL_LIVE;

        return Cache::remember($key, $ttl, fn () => $this->build($mode, $day));
    }

    /**
     * village() — ek gaon ka poora detail (app ka home screen, dashboard ka side panel).
     *
     * INPUT : village id, mode, day
     * OUTPUT: village + risk + station + shelters + recent alerts, ya null agar gaon nahi mila
     *
     * KYUN map() ko reuse karte hain: same numbers dikhne chahiye. Agar detail alag se compute
     * karta to map pe RED aur detail pe YELLOW dikh sakta tha — flood mein ye galti bhaari hai.
     */
    public function village(int $villageId, string $mode, ?int $day = null): ?array
    {
        $map = $this->map($mode, $day);

        $row = collect($map['villages'])->firstWhere('id', $villageId);
        if ($row === null) {
            return null;
        }

        $village = Village::with(['riverStation', 'shelters'])->find($villageId);
        if ($village === null) {
            return null;
        }

        return [
            'mode' => $map['mode'],
            'day' => $map['day'],
            'date' => $map['date'],
            'generated_at' => $map['generated_at'],
            'village' => $row,

            // Nadi ki poori tasveer — officer ko exact number chahiye hote hain.
            'river_station' => $village->riverStation ? [
                'id' => $village->riverStation->id,
                'name' => $village->riverStation->name,
                'warning_level_m' => $village->riverStation->warning_level_m,
                'danger_level_m' => $village->riverStation->danger_level_m,
                'has_thresholds' => $village->riverStation->hasThresholds(),
            ] : null,

            // "Kahan jao" — citizen app isko offline cache karti hai.
            'shelters' => $village->shelters->map(fn ($s) => [
                'id' => $s->id,
                'name' => $s->name,
                'lat' => $s->lat,
                'lng' => $s->lng,
                'capacity' => $s->capacity,
            ])->values(),

            // Alerts feed (aakhri 10) — app ka "purane alerts" tab.
            'recent_alerts' => $village->alerts()
                ->orderByDesc('sent_at')
                ->limit(10)
                ->get()
                ->map(fn ($a) => [
                    'id' => $a->id,
                    'message_hi' => $a->message_hi,
                    'message_en' => $a->message_en,
                    'sent_by' => $a->sent_by,
                    'sent_at' => $a->sent_at?->toIso8601String(),
                ])->values(),

            // Ye gaon abhi kitni SOS requests bhej chuka — officer ke liye triage signal.
            'open_relief_requests' => $village->reliefRequests()
                ->where('status', '!=', 'done')
                ->count(),
        ];
    }

    /**
     * persist() — compute kiya hua risk risk_scores table mein likhta hai.
     *
     * INPUT : map() ka output
     * OUTPUT: kitni rows likhi (int)
     *
     * KYUN store karte hain jab cache already hai: cache temporary hai (file cache, clear ho
     * sakta hai). risk_scores permanent history hai — "20 June subah 6 baje ye gaon RED hua tha"
     * ka record. Ye audit trail disaster management mein zaroori hai, aur dashboard ka
     * timeline graph isi se banega.
     *
     * Sirf `php artisan risk:compute` isko call karta hai — normal API request DB mein nahi likhti
     * (stateless read path, BUILD_PLAN section 6).
     */
    public function persist(array $map): int
    {
        $computedAt = Carbon::parse($map['generated_at']);

        $rows = collect($map['villages'])->map(fn ($v) => [
            'village_id' => $v['id'],
            'score' => $v['risk']['score'],
            'level' => $v['risk']['level'],
            'computed_at' => $computedAt,
        ])->all();

        foreach (array_chunk($rows, 200) as $chunk) {
            DB::table('risk_scores')->insert($chunk);
        }

        return count($rows);
    }

    /**
     * latestStoredLevels() — DB se har gaon ka aakhri saved risk level.
     * INPUT : kuch nahi | OUTPUT: [village_id => ['level','score','computed_at']]
     * KYUN: agar Open-Meteo down ho (live mode fail), to bhi app ko kuch dikhana hai.
     *       Purana data "koi data nahi" se behtar hai — bas timestamp ke saath, taaki
     *       user ko pata rahe ki ye kitna purana hai.
     */
    public function latestStoredLevels(): array
    {
        // Har village ka sabse naya computed_at nikaalo, phir usi row ko utha lo.
        $latest = RiskScore::query()
            ->select('village_id', DB::raw('MAX(computed_at) as max_at'))
            ->groupBy('village_id');

        return RiskScore::query()
            ->joinSub($latest, 'l', fn ($join) => $join
                ->on('risk_scores.village_id', '=', 'l.village_id')
                ->on('risk_scores.computed_at', '=', 'l.max_at'))
            ->get()
            ->keyBy('village_id')
            ->map(fn ($r) => [
                'level' => $r->level,
                'score' => $r->score,
                'computed_at' => $r->computed_at?->toIso8601String(),
            ])
            ->all();
    }

    /**
     * replayDays() — replay mein kitne din hain aur kaunsi date.
     * OUTPUT: ['2022-06-15', '2022-06-16', ...] (sorted)
     * KYUN: dashboard ka date-slider isi list se banta hai — hardcode karne se agar seed data
     *       badla to slider tootega.
     */
    public function replayDays(): array
    {
        return Cache::remember('risk:replay:days', self::CACHE_TTL_REPLAY, fn () => Rainfall::query()
            ->source(Rainfall::SOURCE_REPLAY)
            ->select('recorded_at')
            ->distinct()
            ->orderBy('recorded_at')
            ->pluck('recorded_at')
            ->map(fn ($d) => Carbon::parse($d)->toDateString())
            ->values()
            ->all());
    }

    /**
     * flush() — saara risk cache saaf.
     * KYUN: seed refresh ya threshold change ke baad purana cached risk galat ho jaata hai.
     *       `risk:compute` iske baad naya compute karke cache dobara bharta hai.
     */
    public function flush(): void
    {
        Cache::forget('risk:map:live');
        Cache::forget('risk:replay:days');

        foreach (array_keys($this->replayDays()) as $i) {
            Cache::forget("risk:map:replay:{$i}");
        }
    }

    // ---------------------------------------------------------------------------------
    //  ANDAR KA KAAM
    // ---------------------------------------------------------------------------------

    /**
     * build() — asli compute (cache miss pe hi chalta hai).
     *
     * KADAM:
     *   1. Saare gaon + unke river station laao (eager load — N+1 query se bachne ke liye)
     *   2. Mode ke hisaab se rainfall laao (replay = DB, live = Open-Meteo)
     *   3. Har gaon ke liye RiskEngine ko inputs do
     *   4. Result + summary banao
     *
     * INPUT : mode, day | OUTPUT: poora risk map array
     */
    private function build(string $mode, ?int $day): array
    {
        $villages = Village::with('riverStation')->orderBy('name')->get();

        $rainfall = $mode === self::MODE_REPLAY
            ? $this->replayRainfall($day)
            : $this->liveRainfall($villages);

        $date = $mode === self::MODE_REPLAY
            ? ($this->replayDays()[$day] ?? null)
            : now()->toDateString();

        $rows = [];

        foreach ($villages as $village) {
            $station = $village->riverStation;

            // Is gaon ka rainfall. Data na mile to sab 0 — engine phir bhi safe chalta hai.
            $r = $rainfall[$village->id] ?? ['today_mm' => 0.0, 'rain_3day_mm' => 0.0, 'yesterday_3day_mm' => 0.0];

            // River level "derive" hota hai barish se — asli gauge reading nahi.
            // Ye derivation bhi RiskEngine ke andar hai (logic ek hi jagah), yahan sirf call.
            $levelNow = $this->engine->estimateRiverLevel(
                $station?->warning_level_m,
                $station?->danger_level_m,
                (float) $r['rain_3day_mm'],
            );

            // Ek din pehle ka level — isse pata chalta hai paani badh raha hai ya ghat raha.
            $levelBefore = $this->engine->estimateRiverLevel(
                $station?->warning_level_m,
                $station?->danger_level_m,
                (float) $r['yesterday_3day_mm'],
            );

            $risk = $this->engine->assess([
                'rainfall_mm' => (float) $r['today_mm'],
                'rainfall_3day_mm' => (float) $r['rain_3day_mm'],
                'elevation_m' => $village->elevation_m,
                'river_level_m' => $levelNow,
                'warning_level_m' => $station?->warning_level_m,
                'danger_level_m' => $station?->danger_level_m,
                'rise_rate_m_per_hr' => $this->engine->riseRate($levelNow, $levelBefore, 24.0),
            ]);

            $rows[] = [
                'id' => $village->id,
                'name' => $village->name,
                'district' => $village->district,
                'lat' => $village->lat,
                'lng' => $village->lng,
                'elevation_m' => $village->elevation_m,
                'population' => $village->population,
                'risk' => $risk,
            ];
        }

        return [
            'mode' => $mode,
            'day' => $day,
            'date' => $date,
            'generated_at' => now()->toIso8601String(),

            // Live mode mein Open-Meteo down ho sakta hai — frontend ko saaf batao.
            'data_ok' => $mode === self::MODE_REPLAY || $rainfall !== [],

            'summary' => $this->summarise($rows),
            'villages' => $rows,
        ];
    }

    /**
     * replayRainfall() — replay ke ek din ka barish data (aur pichhle dinon ka cumulative).
     *
     * INPUT : day index (0-based)
     * OUTPUT: [village_id => ['today_mm', 'rain_3day_mm', 'yesterday_3day_mm']]
     *
     * KYUN 3-din ka cumulative bhi: nadi aaj ki barish se nahi chadhti — catchment mein pichhle
     * dinon ka jama paani laata hai. RiskEngine ka river-level proxy isi pe chalta hai.
     *
     * Ek hi query mein 4 din ka data uthate hain (aaj + 3 pichhle), phir PHP mein jodte hain.
     * 30 gaon x 4 din = 120 rows — memory mein kuch bhi nahi.
     */
    private function replayRainfall(int $day): array
    {
        $days = $this->replayDays();

        // Aaj se 3 din peeche tak ki dates (jitni available hon).
        $window = [];
        for ($i = $day - 3; $i <= $day; $i++) {
            if ($i >= 0 && isset($days[$i])) {
                $window[$i] = $days[$i];
            }
        }

        if ($window === []) {
            return [];
        }

        $readings = Rainfall::query()
            ->source(Rainfall::SOURCE_REPLAY)
            ->whereIn(DB::raw('DATE(recorded_at)'), array_values($window))
            ->get(['village_id', 'rainfall_mm', 'recorded_at']);

        // [village_id][date] => mm
        $byVillage = [];
        foreach ($readings as $reading) {
            $byVillage[$reading->village_id][Carbon::parse($reading->recorded_at)->toDateString()]
                = (float) $reading->rainfall_mm;
        }

        $out = [];
        foreach ($byVillage as $villageId => $byDate) {
            $mm = fn (int $index) => $byDate[$days[$index] ?? ''] ?? 0.0;

            $out[$villageId] = [
                'today_mm' => $mm($day),

                // aaj + pichhle 2 din
                'rain_3day_mm' => $mm($day) + $mm($day - 1) + $mm($day - 2),

                // kal tak ka 3-din total (rise rate ke liye)
                'yesterday_3day_mm' => $mm($day - 1) + $mm($day - 2) + $mm($day - 3),
            ];
        }

        return $out;
    }

    /**
     * liveRainfall() — Open-Meteo se abhi ki asli barish (saare gaon, ek batched call).
     *
     * INPUT : villages collection
     * OUTPUT: [village_id => ['today_mm', 'rain_3day_mm', 'yesterday_3day_mm']]
     *         KHAALI array agar API fail ho — build() isko `data_ok: false` mein badal deta hai.
     */
    private function liveRainfall($villages): array
    {
        $points = $villages->map(fn ($v) => [
            'id' => $v->id,
            'lat' => $v->lat,
            'lng' => $v->lng,
        ])->values()->all();

        $fetched = $this->openMeteo->fetchForVillages($points);

        $out = [];
        foreach ($fetched as $villageId => $data) {
            $out[$villageId] = [
                'today_mm' => $data['today_mm'],
                'rain_3day_mm' => $data['rain_3day_mm'],
                'yesterday_3day_mm' => $data['yesterday_3day_mm'],
            ];
        }

        return $out;
    }

    /**
     * summarise() — district-wise + overall ginti.
     * INPUT : village rows | OUTPUT: counts + affected population
     * KYUN: officer ka pehla sawaal hi "kitne gaon khatre mein, kitne log" hota hai.
     *       Ye number dashboard ke header pe seedha lagta hai.
     */
    private function summarise(array $rows): array
    {
        $counts = ['red' => 0, 'yellow' => 0, 'green' => 0];
        $population = ['red' => 0, 'yellow' => 0, 'green' => 0];
        $districts = [];

        foreach ($rows as $row) {
            $level = $row['risk']['level'];

            $counts[$level]++;
            $population[$level] += $row['population'];

            $districts[$row['district']] ??= ['red' => 0, 'yellow' => 0, 'green' => 0];
            $districts[$row['district']][$level]++;
        }

        ksort($districts);

        return [
            'total_villages' => count($rows),
            'by_level' => $counts,
            'population_by_level' => $population,
            // "Affected" = red + yellow. Green wale log affected nahi hain, unhe ginna
            // number ko bada dikhaane ki cheating hogi.
            'affected_population' => $population['red'] + $population['yellow'],
            'by_district' => $districts,
        ];
    }

    /**
     * normaliseMode() — user ka ?mode= saaf karta hai.
     * INPUT : koi bhi string | OUTPUT: 'live' ya 'replay'
     * KYUN: galat/khaali mode pe crash nahi hona chahiye. Default 'live' — kyunki asli
     *       system live hi chalta hai; replay sirf demo ka tool hai.
     */
    private function normaliseMode(?string $mode): string
    {
        return strtolower(trim((string) $mode)) === self::MODE_REPLAY
            ? self::MODE_REPLAY
            : self::MODE_LIVE;
    }

    /**
     * resolveReplayDay() — ?day= ko valid index mein badalta hai.
     * INPUT : day (null ya koi int) | OUTPUT: 0 se (total-1) ke beech ka index
     * KYUN: null => aakhri din (flood ka baad wala din), taaki bina parameter ke bhi
     *       kuch sensible mile. Range se bahar ka number clamp ho jaata hai, error nahi.
     */
    private function resolveReplayDay(?int $day): int
    {
        $days = $this->replayDays();
        $last = max(count($days) - 1, 0);

        if ($day === null) {
            return $last;
        }

        return max(0, min($day, $last));
    }
}
