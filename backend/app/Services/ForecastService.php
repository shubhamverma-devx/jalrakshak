<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;
use Symfony\Component\Process\Process;

/**
 * =====================================================================================
 *  ForecastService — B2 (village flood forecast) ko Laravel se jodta hai
 * =====================================================================================
 *
 *  KYA: `ml/forecast.py` chalata hai aur ek gaon ka +24h / +48h risk level laata hai.
 *
 *  KYUN PYTHON SUBPROCESS: wahi wajah jo SarDetectionService mein likhi hai — model
 *  PyTorch ka hai, aur droplet pe 1GB RAM mein ek aur long-running service uthana bhaari
 *  padta. Forecast tabhi chalti hai jab officer kisi gaon ka drawer kholta hai.
 *
 *  ============ IMANDAARI — YE SABSE ZAROORI HISSA HAI ============
 *  Ye model apne test set pe ek TRIVIAL baseline se behtar NAHI hai:
 *
 *    +24h macro-F1 0.6091  vs  "aaj wala hi level kal bhi" ka 0.6004   (+0.009, itna kam
 *                                                                       ki jeet nahi kehte)
 *    +48h macro-F1 0.4827  vs  0.5113                                  (PEECHE)
 *    RED recall  : +24h 0.244 (11/45) · +48h 0.089 (4/45)
 *
 *  NWP-family atmospheric features (dabaav, nami, baadal) bhi try kiye gaye — unse test par
 *  koi sudhaar NAHI hua (-0.017 / -0.020). ml/FORECAST_README.md mein poori wajah hai.
 *
 *  Isliye har response ke saath `model` block jaata hai jisme YE NUMBERS hote hain, aur
 *  UI unhe dikhata hai. Ye "hamara AI flood predict karta hai" wala feature NAHI hai —
 *  ye ek ishaara hai ki kis gaon par nazar rakhni chahiye.
 *
 *  Numbers `ml/outputs/forecast_metrics.json` se PADHE jaate hain, hardcode nahi hain —
 *  model dobara train ho to UI ka number apne aap sahi ho jaaye.
 * =====================================================================================
 */
final class ForecastService
{
    /** forecast.py Open-Meteo call karta hai (~35 din ka data) + model — 45s kaafi hai. */
    private const TIMEOUT = 45;

    /**
     * Forecast 3 ghante cache hota hai.
     * KYUN 3 ghante: Open-Meteo ka daily data din mein kuch hi baar update hota hai, aur
     * har drawer khulne pe subprocess uthana droplet pe bekaar bojh hai. 3 ghante mein
     * data itna purana nahi hota ki 24-48 ghante ke forecast pe farq pade.
     */
    private const CACHE_TTL = 10800;

    /** Model info kam badalta hai — din bhar cache. */
    private const MODEL_CACHE_TTL = 86400;

    public function mlPath(string $sub = ''): string
    {
        $base = rtrim(config('services.ml.path'), '/');

        return $sub === '' ? $base : $base.'/'.ltrim($sub, '/');
    }

    /**
     * forecast() — ek gaon ka +24h / +48h.
     *
     * INPUT : village id (hamare DB ka id — ml/data/forecast/villages.json wahi order rakhta hai)
     * OUTPUT: ['ok' => true, 'forecast' => [...], 'model' => [...]]
     *         ya ['ok' => false, 'error' => '...']
     */
    public function forecast(int $villageId): array
    {
        $key = "forecast:v{$villageId}:".now()->format('Y-m-d-H');

        $result = Cache::remember($key, self::CACHE_TTL, fn () => $this->runForecast($villageId));

        // Fail hua to cache mat rakho — warna ek temporary network dikkat 3 ghante chipki rahegi.
        if (! ($result['ok'] ?? false)) {
            Cache::forget($key);
        }

        return $result;
    }

    /** runForecast() — asli subprocess call. */
    private function runForecast(int $villageId): array
    {
        $python = config('services.ml.python');
        $script = $this->mlPath('forecast.py');
        $weights = $this->mlPath('models/forecast_lstm.pt');

        foreach ([[$python, 'Python'], [$script, 'forecast.py'], [$weights, 'Model weights']] as [$p, $what]) {
            if (! is_readable($p)) {
                Log::warning("Forecast: {$what} nahi mila: {$p}");

                return ['ok' => false, 'error' => "{$what} nahi mila. ml/FORECAST_README.md dekho."];
            }
        }

        $process = new Process([$python, $script, (string) $villageId, '--json']);
        $process->setTimeout(self::TIMEOUT);

        try {
            $process->run();
        } catch (\Throwable $e) {
            Log::warning('Forecast process fail', ['error' => $e->getMessage()]);

            return ['ok' => false, 'error' => 'Forecast chal nahi paayi.'];
        }

        if (! $process->isSuccessful()) {
            Log::warning('Forecast script fail', [
                'village' => $villageId,
                'stderr' => substr($process->getErrorOutput(), 0, 500),
            ]);

            return ['ok' => false, 'error' => 'Forecast script fail hui.'];
        }

        $data = json_decode(trim($process->getOutput()), true);
        if (! is_array($data) || ! isset($data['horizons'])) {
            return ['ok' => false, 'error' => 'Forecast ka output samajh nahi aaya.'];
        }

        return ['ok' => true, 'forecast' => $data, 'model' => $this->modelInfo()];
    }

    /**
     * modelInfo() — provenance + ASLI accuracy. UI isse honesty label banata hai.
     *
     * KYUN baseline bhi bhejte hain: sirf "macro-F1 0.5953" likhna adhoora sach hai.
     * Officer ko ye dikhna chahiye ki wahi kaam bina kisi model ke bhi lagbhag utna hi
     * achha ho jaata hai. Wahi poora sach hai.
     */
    public function modelInfo(): array
    {
        return Cache::remember('forecast:model_info', self::MODEL_CACHE_TTL, function () {
            $path = $this->mlPath('outputs/forecast_metrics.json');
            $m = is_readable($path) ? json_decode(file_get_contents($path), true) : null;

            $h = fn (string $k) => $m['test'][$k] ?? null;
            $b = fn (string $k) => $m['baselines_test'][$k] ?? null;

            return [
                'name' => 'LSTM rainfall forecast -> RiskEngine',
                'dataset' => 'ERA5 rainfall + MERRA-2 atmospheric reanalysis, 2000-2025',
                // Reanalysis hai, operational NWP forecast NAHI — ye farq UI tak jaana chahiye.
                'features' => $m['features']['sequence'] ?? null,
                'nwp_features' => $m['features']['nwp'] ?? null,
                'villages' => $m['data']['villages'] ?? null,
                'samples' => $m['data']['samples'] ?? null,
                'test_period' => '2022-2025 (held out)',
                'metrics' => [
                    'h24' => [
                        'macro_f1' => $h('lstm_24h')['macro_f1'] ?? null,
                        'red_recall' => $h('lstm_24h')['recall'][2] ?? null,
                        'red_support' => $h('lstm_24h')['support'][2] ?? null,
                    ],
                    'h48' => [
                        'macro_f1' => $h('lstm_48h')['macro_f1'] ?? null,
                        'red_recall' => $h('lstm_48h')['recall'][2] ?? null,
                        'red_support' => $h('lstm_48h')['support'][2] ?? null,
                    ],
                ],
                // Baseline SAATH mein jaata hai — bina iske model ka number bada dikhta hai
                // jabki wo hai nahi.
                'baseline' => [
                    'name' => 'persistence — "aaj wala hi level kal bhi"',
                    'h24_macro_f1' => $b('persistence_level_24h')['macro_f1'] ?? null,
                    'h48_macro_f1' => $b('persistence_level_48h')['macro_f1'] ?? null,
                ],
                'is_forecast' => true,
                // Ye do line UI tak jaani chahiye. Ye model ki seema hai, marketing nahi.
                'disclaimer' => 'Forecast hai, observation nahi. Target hamara apna RiskEngine hai '
                    .'(aage ke din par lagaya hua), asli flood record nahi.',
                'accuracy_warning' => 'Ye model abhi ek trivial baseline ke barabar hai. '
                    .'RED din pakadne ki recall +24h par 0.24 aur +48h par 0.09 hai — '
                    .'iske bharose evacuation ka faisla mat lena.',
            ];
        });
    }
}
