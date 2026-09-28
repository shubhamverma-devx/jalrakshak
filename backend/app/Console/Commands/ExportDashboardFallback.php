<?php

namespace App\Console\Commands;

use App\Models\Village;
use App\Services\ForecastService;
use App\Services\RiskMapService;
use App\Services\SarDetectionService;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\File;

/**
 * =====================================================================================
 *  php artisan dashboard:export-fallback — dashboard ke liye static JSON bake karo
 * =====================================================================================
 *
 *  KYA KARTA HAI: jo data API se aata hai uska ek frozen copy `dashboard/public/fallback/`
 *  mein likhta hai. Vite `public/` ko jaisa-ka-taisa `dist/` mein copy karta hai, to ye
 *  files build ka hissa ban jaati hain aur nginx unhe seedha serve karta hai — na PHP, na
 *  MySQL, na koi bahar ki API.
 *
 *    replay.json          -> 12 din ka poora replay (har din ka /api/villages?mode=replay jaisa)
 *    village_static.json  -> har gaon ke shelters + river station (drawer ke liye)
 *    models.json          -> B1 + B2 ke metrics (metrics.json / forecast_metrics.json se)
 *    sar/scenes.json      -> 4 demo scenes + SAR model provenance
 *    sar/{scene}.json     -> us scene pe predict.py ka ASLI output (polygons + area)
 *
 *  KYUN (deploy hardening): judge portal se link hafton baad kholega. Droplet 1GB ka hai
 *  aur us par MySQL kabhi bhi OOM ho sakta hai. Tab bhi pehli screen blank nahi honi
 *  chahiye. Replay 2022 ka data waise bhi badalta nahi — uska static copy utna hi sach hai.
 *
 *  SAR KYUN YAHAN SE: droplet par PyTorch load hi nahi hota (~400 MB+ RSS, 1GB box). Isliye
 *  predict.py YAHIN (laptop pe) chaar scenes pe chalta hai aur nateeja JSON mein jaata hai.
 *  predict.py repo mein hi rehta hai — pipeline asli hai, bas server pe nahi chalti.
 *  Dashboard ye baat screen pe likhta hai (SatellitePanel), chhupata nahi.
 *
 *  KAB CHALAO: seed data, RiskEngine rules, ya model badle to. Phir `npm run build`.
 *      php artisan risk:compute --mode=replay --all
 *      php artisan dashboard:export-fallback
 * =====================================================================================
 */
class ExportDashboardFallback extends Command
{
    protected $signature = 'dashboard:export-fallback
                            {--out= : output folder (default: ../dashboard/public/fallback)}
                            {--skip-sar : predict.py mat chalao (sirf replay + metrics)}';

    protected $description = 'Replay data, SAR results aur model metrics dashboard ke static fallback JSON mein likhta hai';

    public function handle(RiskMapService $riskMap, SarDetectionService $sar, ForecastService $forecast): int
    {
        $out = rtrim($this->option('out') ?: base_path('../dashboard/public/fallback'), '/');
        File::ensureDirectoryExists($out.'/sar');

        $generatedAt = now()->toIso8601String();

        // --- 1. Replay: saare din, bilkul wahi shape jo /api/villages deta hai -------------
        // KYUN wahi shape: frontend ka koi component ye na jaane ki data API se aaya ya file
        // se. Do shape hote to har chart mein do raaste banane padte.
        $days = $riskMap->replayDays();
        $snapshots = [];
        foreach (array_keys($days) as $day) {
            $map = $riskMap->map(RiskMapService::MODE_REPLAY, $day);
            $snapshots[] = [...$map, 'replay_days' => $days];
        }
        $this->writeJson("{$out}/replay.json", [
            'generated_at' => $generatedAt,
            'days' => $days,
            'snapshots' => $snapshots,
        ]);
        $this->info('replay.json — '.count($snapshots).' din');

        // --- 2. Drawer ka static hissa (shelters + river station) -----------------------
        // Risk drawer ko snapshot se milta hai; API se sirf shelters aate the. Alerts/SOS
        // count jaan-bujh ke NAHI bake kiye — wo live operational data hai, purana copy
        // dikhana jhooth hoga.
        $static = [];
        foreach (Village::with(['riverStation', 'shelters'])->get() as $v) {
            $static[$v->id] = [
                'river_station' => $v->riverStation ? [
                    'id' => $v->riverStation->id,
                    'name' => $v->riverStation->name,
                    'warning_level_m' => $v->riverStation->warning_level_m,
                    'danger_level_m' => $v->riverStation->danger_level_m,
                    'has_thresholds' => $v->riverStation->hasThresholds(),
                ] : null,
                'shelters' => $v->shelters->map(fn ($s) => [
                    'id' => $s->id,
                    'name' => $s->name,
                    'lat' => $s->lat,
                    'lng' => $s->lng,
                    'capacity' => $s->capacity,
                ])->values(),
            ];
        }
        $this->writeJson("{$out}/village_static.json", $static);
        $this->info('village_static.json — '.count($static).' gaon');

        // --- 3. Model metrics (file se padhe hue, hardcode nahi) ------------------------
        $this->writeJson("{$out}/models.json", [
            'generated_at' => $generatedAt,
            'sar' => $sar->modelInfo(),
            'forecast' => $forecast->modelInfo(),
        ]);
        $this->info('models.json');

        // --- 4. SAR: har scene pe predict.py ------------------------------------------
        $scenes = $sar->scenes();
        $this->writeJson("{$out}/sar/scenes.json", [
            'generated_at' => $generatedAt,
            'precomputed' => true,
            'scenes' => $scenes,
            'model' => $sar->modelInfo(),
        ]);

        if ($this->option('skip-sar')) {
            $this->warn('SAR skip kiya (--skip-sar). Purani sar/*.json jaisi thi waisi hai.');

            return self::SUCCESS;
        }

        $failed = 0;
        foreach ($scenes as $scene) {
            $this->line("  predict.py {$scene['id']} ...");
            $result = $sar->detect($scene['id']);

            if (! ($result['ok'] ?? false)) {
                $this->error("  {$scene['id']}: ".($result['error'] ?? 'fail'));
                $failed++;

                continue;
            }

            unset($result['ok']);
            $this->writeJson("{$out}/sar/{$scene['id']}.json", [
                ...$result,
                'precomputed_at' => $generatedAt,
            ]);
            $this->info("  {$scene['id']} — {$result['detection']['flooded_area_sq_km']} sq km");
        }

        // Ek bhi scene fail hua to exit code 1 — build script ko pata chale ki bake adhoora hai.
        return $failed === 0 ? self::SUCCESS : self::FAILURE;
    }

    /** JSON likho — unicode/slashes escape nahi (Devanagari reason_hi padhne layak rahe). */
    private function writeJson(string $path, mixed $data): void
    {
        File::put($path, json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION));
    }
}
