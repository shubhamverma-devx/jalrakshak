<?php

namespace App\Console\Commands;

use App\Models\Rainfall;
use App\Models\RiverStation;
use App\Models\Village;
use App\Services\OpenMeteoService;
use App\Services\RiskEngine;
use App\Services\RiskMapService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

/**
 * =====================================================================================
 *  php artisan risk:compute — saare gaon ka risk dobara compute karo
 * =====================================================================================
 *
 *  KYA KARTA HAI:
 *    1. (live mode) Open-Meteo se abhi ki asli barish laata hai aur `rainfall` table mein
 *       source='live' ke saath store karta hai
 *    2. Har station ka current_level_m update karta hai (RiskEngine ka derived proxy)
 *    3. RiskEngine se har gaon ka risk nikaalta hai
 *    4. Result `risk_scores` mein likhta hai (permanent history)
 *    5. Risk map ka cache refresh karta hai — taaki agli API request instant ho
 *
 *  KYUN COMMAND, HAR REQUEST PE NAHI:
 *    Open-Meteo call + 30 gaon ka compute har API hit pe karna droplet (1GB RAM, 2 aur
 *    site) ko mar dega, aur free API pe rate-limit lag jaayega. Scheduler har 30 min ye
 *    chalata hai, aur API sirf cache padhti hai. Yahi BUILD_PLAN section 6 ka
 *    "scale-ready: API stateless + risk map cached" hai.
 *
 *  SCHEDULE: routes/console.php mein har 30 min (Laravel 12 mein Kernel.php nahi hota —
 *  scheduling ab routes/console.php mein hoti hai).
 *
 *  MANUAL USE:
 *    php artisan risk:compute                        # live (Open-Meteo)
 *    php artisan risk:compute --mode=replay --day=5  # 2022 replay ka 6th din
 *    php artisan risk:compute --mode=replay --all    # replay ke saare din (demo cache warm)
 * =====================================================================================
 */
class ComputeRisk extends Command
{
    protected $signature = 'risk:compute
                            {--mode=live : live (Open-Meteo) ya replay (Assam 2022 seed data)}
                            {--day= : replay ka din index (0 = pehla din). Chhoda to aakhri din}
                            {--all : replay ke saare din compute karo (demo se pehle cache warm karne ke liye)}
                            {--no-store : sirf cache refresh karo, risk_scores mein mat likho}';

    protected $description = 'RiskEngine se saare gaon ka flood early-warning risk recompute karta hai';

    public function __construct(
        private readonly RiskMapService $riskMap,
        private readonly RiskEngine $engine,
        private readonly OpenMeteoService $openMeteo,
    ) {
        parent::__construct();
    }

    /**
     * handle() — command ka main flow.
     * OUTPUT: exit code (0 = theek, 1 = kuch gadbad)
     */
    public function handle(): int
    {
        $mode = $this->option('mode') === RiskMapService::MODE_REPLAY
            ? RiskMapService::MODE_REPLAY
            : RiskMapService::MODE_LIVE;

        if (Village::count() === 0) {
            $this->error('Koi village nahi mila. Pehle chalao: php artisan db:seed');

            return self::FAILURE;
        }

        // Purana cached risk hata do — warna naya compute karke bhi API purana hi dega.
        $this->riskMap->flush();

        // Live mode: pehle asli barish laao aur DB mein store karo.
        if ($mode === RiskMapService::MODE_LIVE) {
            $this->storeLiveRainfall();
        }

        // Replay --all: demo se pehle saare 12 din ka cache bhar do, taaki slider lag na kare.
        $days = ($mode === RiskMapService::MODE_REPLAY && $this->option('all'))
            ? array_keys($this->riskMap->replayDays())
            : [$this->option('day') !== null ? (int) $this->option('day') : null];

        foreach ($days as $day) {
            $map = $this->riskMap->map($mode, $day);

            // Station ka current_level_m update — dashboard "abhi level kitna hai" dikhata hai.
            $this->updateStationLevels($map);

            if (! $this->option('no-store')) {
                $written = $this->riskMap->persist($map);
                $this->line("  risk_scores mein {$written} rows likhi.");
            }

            $s = $map['summary'];
            $this->info(sprintf(
                '[%s%s] %s — RED: %d, YELLOW: %d, GREEN: %d | affected aabadi: %s%s',
                $mode,
                $day !== null ? " day {$day}" : '',
                $map['date'] ?? '-',
                $s['by_level']['red'],
                $s['by_level']['yellow'],
                $s['by_level']['green'],
                number_format($s['affected_population']),
                $map['data_ok'] ? '' : '  (!! rainfall data nahi mila)',
            ));
        }

        return self::SUCCESS;
    }

    /**
     * storeLiveRainfall() — Open-Meteo se aaj ki barish laakar `rainfall` table mein daalta hai.
     *
     * INPUT : kuch nahi (saare villages leta hai) | OUTPUT: void
     *
     * KYUN DB MEIN STORE KARTE HAIN JAB CACHE MEIN BHI HAI:
     *   Cache 15 min ka hai aur mit sakta hai. Rainfall history permanent honi chahiye —
     *   "kal is gaon mein kitni barish hui thi" ka record. Aage chal ke dashboard ka
     *   rainfall trend graph isi table se banega.
     *
     * upsert => ek hi din ka data do baar nahi ghusega (scheduler har 30 min chalta hai,
     * aur daily total din bhar update hota rehta hai — wahi row refresh ho jaati hai).
     */
    private function storeLiveRainfall(): void
    {
        $villages = Village::all(['id', 'lat', 'lng']);

        $data = $this->openMeteo->fetchForVillages(
            $villages->map(fn ($v) => ['id' => $v->id, 'lat' => $v->lat, 'lng' => $v->lng])->all()
        );

        if ($data === []) {
            // Internet/API down — command fail nahi karta. Purana data + honest flag se kaam chalega.
            $this->warn('  Open-Meteo se data nahi mila. Purana cached/stored data hi use hoga.');

            return;
        }

        $rows = [];
        foreach ($data as $villageId => $point) {
            foreach ($point['daily'] as $date => $mm) {
                $rows[] = [
                    'village_id' => $villageId,
                    'rainfall_mm' => $mm,
                    'source' => Rainfall::SOURCE_LIVE,
                    'recorded_at' => $date.' 00:00:00',
                ];
            }
        }

        foreach (array_chunk($rows, 300) as $chunk) {
            DB::table('rainfall')->upsert($chunk, ['village_id', 'source', 'recorded_at'], ['rainfall_mm']);
        }

        $this->line('  Open-Meteo se '.count($data).' gaon ki live barish store hui.');
    }

    /**
     * updateStationLevels() — har station ka current_level_m aur updated_at set karta hai.
     *
     * INPUT : risk map (jismein har gaon ke factors mein river_level_m hai)
     * OUTPUT: void
     *
     * KYUN: ek station se kai gaon jude ho sakte hain (jaise Neamatighat se Nimatighat aur
     * Majuli dono). Level station ka property hai, gaon ka nahi — isliye pehle station ke
     * saare gaon ka max level lete hain (sabse conservative = sabse safe), phir wahi store.
     *
     * IMANDAARI: ye level RiskEngine::estimateRiverLevel() ka DERIVED proxy hai, asli CWC
     * gauge reading nahi. Deployment mein yahan asli telemetry aayegi. (DATA_NOTES.md)
     */
    private function updateStationLevels(array $map): void
    {
        // village_id -> station_id
        $stationOf = Village::whereNotNull('river_station_id')
            ->pluck('river_station_id', 'id')
            ->all();

        $levels = [];
        foreach ($map['villages'] as $v) {
            $stationId = $stationOf[$v['id']] ?? null;
            $level = $v['risk']['factors']['river_level_m'] ?? null;

            if ($stationId === null || $level === null) {
                continue;
            }

            // Ek station ke kai gaon => sabse ooncha level lo (safety pehle).
            $levels[$stationId] = max($levels[$stationId] ?? $level, $level);
        }

        foreach ($levels as $stationId => $level) {
            RiverStation::where('id', $stationId)->update([
                'current_level_m' => $level,
                'updated_at' => now(),
            ]);
        }

        $this->line('  '.count($levels).' river stations ka level update hua.');
    }
}
