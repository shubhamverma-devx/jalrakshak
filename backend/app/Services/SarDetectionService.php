<?php

namespace App\Services;

use App\Models\Village;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Symfony\Component\Process\Exception\ProcessTimedOutException;
use Symfony\Component\Process\Process;

/**
 * =====================================================================================
 *  SarDetectionService — B1 (trained SAR model) ko Laravel se jodta hai
 * =====================================================================================
 *
 *  KYA: bundled Sentinel-1 chip pe `ml/predict.py` chalata hai, aur dashboard ko
 *       water polygons (GeoJSON) + doobe hue area (sq km) + nazdeeki gaon deta hai.
 *
 *  KYUN PYTHON SUBPROCESS (PHP mein model nahi chalate):
 *   Model PyTorch ka hai. PHP mein use chalane ka koi sensible tareeka nahi. Do options
 *   the — (a) alag Python service (FastAPI) khada karna, (b) seedha script call karna.
 *   (b) chuna: droplet pe sirf 1GB RAM hai aur ek aur long-running service uthana bhaari
 *   padta. Detection kabhi-kabhi hi chalti hai (officer button dabaye tab), har request
 *   pe nahi — to subprocess bilkul theek hai.
 *   Scale karna ho to (a) mein badalna aasan hai: sirf ye class badlegi.
 *
 *  IMANDAARI (CLAUDE.md honesty rules):
 *   - Ye **detection** hai, forecast NAHI. Ye batata hai is image mein paani KAHAN tha,
 *     "kal kahan hoga" nahi. Response mein ye baat explicitly jaati hai.
 *   - Model ke metrics response ke saath jaate hain (jis threshold pe chala, ussi ke),
 *     taaki UI honest number dikhaye — headline number nahi.
 *   - "Nearest villages" bolte hain, "affected villages" NAHI. Chip 5x5 km ka hai aur
 *     hamare gaon 7-33 km door hain — unhe "affected" bolna jhooth hota.
 * =====================================================================================
 */
final class SarDetectionService
{
    /**
     * Operational threshold.
     * KYUN 0.3, jabki best IoU 0.5 pe hai: flood warning mein RECALL zyada matter karta hai —
     * paani chhoot jaana, false alarm se zyada khatarnak hai. 0.3 pe recall 0.771 -> 0.816
     * jaata hai aur IoU sirf 0.6489 -> 0.6452 girta hai. Ye trade-off jaan-bujh ke liya hai.
     */
    public const THRESHOLD = 0.3;

    /** Inference ~3-6 sec leti hai CPU pe. 60s cap taaki koi request hamesha ke liye na latke. */
    private const TIMEOUT = 60;

    /**
     * Detection deterministic hai (same chip + same threshold = same nateeja),
     * isliye hamesha ke liye cache. Demo mein doosri baar instant dikhta hai.
     */
    private const CACHE_TTL = 86400;

    public function mlPath(string $sub = ''): string
    {
        $base = rtrim(config('services.ml.path'), '/');

        return $sub === '' ? $base : $base.'/'.ltrim($sub, '/');
    }

    /**
     * scenes() — bundled sample chips ki list.
     *
     * OUTPUT: [['id','name','file','available'], ...]
     * KYUN bundled: poora Sen1Floods11 708 MB hai. Demo ke liye 4 chips repo mein hain
     * (ml/samples/), to dashboard bina dataset download kiye chalta hai.
     */
    public function scenes(): array
    {
        $meta = [
            'India_591317' => ['label' => 'Brahmaputra floodplain — Tezpur', 'gt_water_pct' => 46.3],
            'India_747992' => ['label' => 'Kopili basin — Hojai', 'gt_water_pct' => 44.8],
            'India_1018327' => ['label' => 'Brahmaputra meander — Golaghat', 'gt_water_pct' => 14.0],
            'India_79637' => ['label' => 'Low-water scene — Kaziranga side', 'gt_water_pct' => 2.0],
        ];

        $out = [];
        foreach ($meta as $id => $m) {
            $file = $this->mlPath("samples/{$id}.tif");
            $out[] = [
                'id' => $id,
                'label' => $m['label'],
                // Ground truth % — UI isse "model ne kya kaha" ke saath dikha sakta hai.
                // Ye chips TEST split se hain, training mein kabhi use nahi hue.
                'ground_truth_water_pct' => $m['gt_water_pct'],
                'available' => is_readable($file),
            ];
        }

        return $out;
    }

    /**
     * modelInfo() — provenance. UI ismein se honesty label banata hai.
     *
     * OUTPUT: dataset, chips, architecture, aur JIS THRESHOLD PE CHAL RAHE HAIN uske metrics
     *
     * KYUN metrics.json se padhte hain (hardcode nahi): model dobara train hua to yahan ke
     * number chup-chaap purane reh jaate — aur wahi galat number UI pe dikhta. File se
     * padhne se ye kabhi stale nahi ho sakta.
     */
    public function modelInfo(): array
    {
        return Cache::remember('sar:model_info', self::CACHE_TTL, function () {
            $path = $this->mlPath('outputs/metrics.json');
            $m = is_readable($path) ? json_decode(file_get_contents($path), true) : null;

            // Jis threshold pe hum chala rahe hain, USI ka metric dhoondo — 0.5 wala
            // headline number nahi. UI pe wahi dikhna chahiye jo actually lag raha hai.
            $atThreshold = null;
            foreach ($m['test_threshold_sweep'] ?? [] as $row) {
                if (abs(($row['threshold'] ?? -1) - self::THRESHOLD) < 1e-6) {
                    $atThreshold = $row;
                    break;
                }
            }

            return [
                'name' => 'U-Net (ResNet34) — SAR flood segmentation',
                'dataset' => 'Sen1Floods11 (CC-BY 4.0), hand-labeled subset',
                'trained_chips' => array_sum($m['splits'] ?? []) ?: null,
                'train_split' => $m['splits']['train'] ?? null,
                'test_split' => $m['splits']['test'] ?? null,
                'threshold' => self::THRESHOLD,
                'metrics_at_threshold' => $atThreshold ? [
                    'iou' => $atThreshold['iou'],
                    'f1' => $atThreshold['f1'],
                    'precision' => $atThreshold['precision'],
                    'recall' => $atThreshold['recall'],
                ] : null,
                'india_region_iou' => 0.7044, // per_region_test.json — Assam ke chips
                // Ye do line UI mein jaani chahiye. Ye model ki seema hai, marketing nahi.
                'is_forecast' => false,
                'disclaimer' => 'Detection, not forecast — ye batata hai is image mein paani KAHAN tha. '
                    .'Aage kya hoga, ye ye model nahi jaanta.',
            ];
        });
    }

    /**
     * detect() — ek scene pe model chalao.
     *
     * INPUT : scene id ('India_591317')
     * OUTPUT: ['ok'=>true, 'scene', 'detection'=>[...], 'geojson'=>[...], 'nearest_villages'=>[...]]
     *         ya ['ok'=>false, 'error'=>'...']
     *
     * KYUN exception nahi phenkte: demo ke beech mein 500 error se bura kuch nahi. UI ko
     * saaf error message dikhana behtar hai taaki pata chale kya karna hai.
     */
    public function detect(string $sceneId): array
    {
        // Path traversal se bachao — scene id seedha filename banta hai.
        if (! preg_match('/^[A-Za-z0-9_\-]+$/', $sceneId)) {
            return ['ok' => false, 'error' => 'Galat scene id.'];
        }

        $known = collect($this->scenes())->pluck('id')->all();
        if (! in_array($sceneId, $known, true)) {
            return ['ok' => false, 'error' => "Scene '{$sceneId}' bundled samples mein nahi hai."];
        }

        $cacheKey = "sar:detect:{$sceneId}:".self::THRESHOLD;

        $result = Cache::remember($cacheKey, self::CACHE_TTL, fn () => $this->runPredict($sceneId));

        // Fail hua to cache mat rakho — warna ek temporary dikkat 24 ghante chipki rahegi.
        if (! ($result['ok'] ?? false)) {
            Cache::forget($cacheKey);
        }

        return $result;
    }

    /**
     * runPredict() — asli subprocess call.
     * INPUT : scene id | OUTPUT: detect() jaisa array
     */
    private function runPredict(string $sceneId): array
    {
        $python = config('services.ml.python');
        $script = $this->mlPath('predict.py');
        $chip = $this->mlPath("samples/{$sceneId}.tif");
        $geojson = storage_path("app/sar/{$sceneId}.geojson");

        foreach ([[$python, 'Python'], [$script, 'predict.py'], [$chip, 'Sample chip']] as [$p, $what]) {
            if (! is_readable($p)) {
                Log::warning("SAR: {$what} nahi mila: {$p}");

                return ['ok' => false, 'error' => "{$what} nahi mila ({$p}). ml/ setup dekho — README."];
            }
        }

        @mkdir(dirname($geojson), 0775, true);

        $process = new Process([
            $python, $script, $chip,
            '--threshold', (string) self::THRESHOLD,
            '--out-geojson', $geojson,
            '--json',
        ]);
        $process->setTimeout(self::TIMEOUT);

        try {
            $process->run();
        } catch (ProcessTimedOutException $e) {
            return ['ok' => false, 'error' => 'Model timeout ho gaya ('.self::TIMEOUT.'s).'];
        }

        if (! $process->isSuccessful()) {
            Log::error('SAR predict fail: '.$process->getErrorOutput());

            return ['ok' => false, 'error' => 'Model chal nahi paya. Backend log dekho.'];
        }

        $out = json_decode($process->getOutput(), true);
        if (! is_array($out)) {
            return ['ok' => false, 'error' => 'Model ne galat output diya.'];
        }

        $polygons = is_readable($geojson) ? json_decode(file_get_contents($geojson), true) : null;

        return [
            'ok' => true,
            'scene' => $sceneId,
            'detection' => [
                'flooded_area_sq_km' => $out['flooded_area_sq_km'] ?? 0,
                'water_fraction' => $out['water_fraction'] ?? 0,
                'water_pixels' => $out['water_pixels'] ?? 0,
                'total_pixels' => $out['total_pixels'] ?? 0,
                'mean_confidence' => $out['mean_water_confidence'] ?? 0,
                'threshold' => $out['threshold'] ?? self::THRESHOLD,
                'bounds' => $out['bounds'] ?? null,
                'centre' => $out['centre'] ?? null,
                'scene_area_sq_km' => round(($out['total_pixels'] ?? 0) * ($out['pixel_area_km2'] ?? 0), 2),
            ],
            'geojson' => $polygons,
            'nearest_villages' => $this->nearestVillages($out['centre'] ?? null),
            'model' => $this->modelInfo(),
        ];
    }

    /**
     * nearestVillages() — scene ke centre se sabse paas ke gaon (haversine).
     *
     * INPUT : [lat, lng] | OUTPUT: [['id','name','district','distance_km','level'], ...]
     *
     * ####  IMPORTANT: ye "AFFECTED villages" NAHI hain.  ####
     * Chip sirf ~5x5 km ka hai aur hamare seeded gaon 7-33 km door hain — ek bhi gaon
     * ka centroid chip ke andar nahi aata. Unhe "affected" bolna seedha jhooth hota.
     * Isliye naam "nearest" hai aur DOORI hamesha saath dikhti hai. UI mein bhi wahi.
     *
     * Asli deployment mein SAR footprint poore district ka hoga, tab overlap asli hoga —
     * aur tab "affected" bolna sach bhi hoga.
     */
    private function nearestVillages(?array $centre, int $limit = 4): array
    {
        if (! $centre || count($centre) < 2) {
            return [];
        }
        [$lat, $lng] = $centre;

        return Village::query()
            ->select('id', 'name', 'district', 'lat', 'lng', 'population')
            ->get()
            ->map(function ($v) use ($lat, $lng) {
                $r = 6371;
                $dLat = deg2rad($v->lat - $lat);
                $dLng = deg2rad($v->lng - $lng);
                $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat)) * cos(deg2rad($v->lat)) * sin($dLng / 2) ** 2;

                return [
                    'id' => $v->id,
                    'name' => $v->name,
                    'district' => $v->district,
                    'population' => $v->population,
                    'distance_km' => round($r * 2 * asin(min(1.0, sqrt($a))), 1),
                ];
            })
            ->sortBy('distance_km')
            ->take($limit)
            ->values()
            ->all();
    }
}
