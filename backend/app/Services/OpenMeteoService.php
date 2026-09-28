<?php

namespace App\Services;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * =====================================================================================
 *  OpenMeteoService — LIVE rainfall (asli data, abhi ka)
 * =====================================================================================
 *
 *  KYA: Open-Meteo se kisi bhi lat/lng ka current + hourly + daily rainfall laata hai.
 *
 *  KYUN OPEN-METEO (decision D5, docs/decisions.md):
 *   - Bilkul free, NO API KEY — judge ke saamne live demo mein signup screen nahi aayegi.
 *   - Global coverage, ECMWF/GFS models — Assam ke har gaon ka data mil jaata hai.
 *   - IMD/CWC ka clean public API hai hi nahi; scraping 4 din mein reliably nahi banti.
 *     (IMD/CWC official feeds = future scope, deployment mein yahin plug honge.)
 *
 *  Endpoint: https://api.open-meteo.com/v1/forecast
 *
 *  DEMO LINE: "Live mode mein ye barish abhi, is waqt, Open-Meteo se aa rahi hai —
 *  judge ke saamne fetch ho rahi hai. Koi mock nahi."
 * =====================================================================================
 */
final class OpenMeteoService
{
    /** Open-Meteo ka free forecast endpoint (no key). */
    private const ENDPOINT = 'https://api.open-meteo.com/v1/forecast';

    /**
     * Cache TTL (seconds).
     * KYUN 1800 (30 min): scheduler bhi har 30 min chalta hai (BUILD_PLAN section 6), to isse
     * jaldi fetch karne ka koi fayda nahi. Free API pe polite rehna + droplet (1GB) bachana.
     */
    private const CACHE_TTL = 1800;

    /**
     * KYUN 3 past days: RiskEngine ka river-level proxy 3 din ke cumulative rainfall pe chalta hai
     * (nadi mein paani aane mein ek-do din lagta hai). Isliye aaj + pichhle 3 din chahiye.
     */
    private const PAST_DAYS = 3;

    /** Aaj + kal ka forecast — "aage kya aa raha hai" dikhane ke liye. */
    private const FORECAST_DAYS = 2;

    /**
     * fetchForVillages() — ek hi HTTP call mein SAARE gaon ka rainfall.
     *
     * KYA: Open-Meteo comma-separated coordinates support karta hai
     *      (latitude=26.81,26.57,...&longitude=94.32,93.17,...) aur ek JSON array wapas deta hai,
     *      har coordinate ka ek object, usi order mein.
     *
     * KYUN BATCH: 30 gaon = 30 alag HTTP call = ~30 second + free API pe rate-limit ka risk.
     *      Ek batched call ~1 second mein ho jaati hai. Droplet 1GB RAM hai — har request pe
     *      30 curl handles kholna theek nahi. Ye pure scale-readiness ka hissa hai.
     *
     * INPUT : array of ['id' => int, 'lat' => float, 'lng' => float]
     * OUTPUT: [village_id => ['today_mm', 'current_mm', 'rain_3day_mm', 'yesterday_3day_mm',
     *                         'daily' => [date => mm], 'fetched_at']]
     *         API fail hone par KHAALI array (caller ko decide karne do — crash nahi).
     *
     * NOTE: fail hone pe hum exception nahi phenkte. Flood system mein aadha data poore
     * blackout se behtar hai — jinke paas data nahi unka risk "river data nahi" ke saath
     * honestly report hota hai.
     */
    public function fetchForVillages(array $villages): array
    {
        if ($villages === []) {
            return [];
        }

        // Cache key = coordinates ka fingerprint. Village list badle to key apne aap badal jaayegi.
        $cacheKey = 'openmeteo:batch:'.md5(json_encode(array_map(
            fn ($v) => [round((float) $v['lat'], 4), round((float) $v['lng'], 4)],
            $villages
        )));

        return Cache::remember($cacheKey, self::CACHE_TTL, function () use ($villages) {
            $lats = implode(',', array_map(fn ($v) => round((float) $v['lat'], 4), $villages));
            $lngs = implode(',', array_map(fn ($v) => round((float) $v['lng'], 4), $villages));

            // Scheduler (CLI) ke paas time hai — lamba timeout + retry. Web request mein ek
            // visitor ka php-fpm worker Open-Meteo ke intezaar mein nahi baandhna, isliye
            // chhota timeout aur ek hi koshish. Fail = data_ok:false, UI saaf batata hai.
            $cli = app()->runningInConsole();

            try {
                $response = Http::connectTimeout(5)
                    ->timeout($cli ? 20 : 6)
                    ->retry($cli ? 3 : 1, 500, throw: false) // CLI: network hichki pe 2 aur koshish
                    ->get(self::ENDPOINT, [
                        'latitude' => $lats,
                        'longitude' => $lngs,
                        'current' => 'precipitation',       // abhi is ghante kitni barish
                        'hourly' => 'precipitation',       // ghante-ghante ka detail
                        'daily' => 'precipitation_sum',   // din ka total — RiskEngine ka R
                        'past_days' => self::PAST_DAYS,       // pichhle 3 din (cumulative ke liye)
                        'forecast_days' => self::FORECAST_DAYS,
                        'timezone' => 'Asia/Kolkata',        // Assam local time mein dates
                    ]);

                if ($response->failed()) {
                    Log::warning('Open-Meteo failed', ['status' => $response->status()]);

                    return [];
                }

                $json = $response->json();
            } catch (\Throwable $e) {
                // Internet gaya / DNS fail / timeout — system chalta rahe, bas log ho jaaye.
                Log::warning('Open-Meteo unreachable: '.$e->getMessage());

                return [];
            }

            // Ek coordinate maango to object aata hai, kai maango to array. Dono handle karo.
            $points = array_is_list($json ?? []) ? $json : [$json];

            $out = [];
            foreach ($villages as $i => $village) {
                if (! isset($points[$i])) {
                    continue; // API ne kam points diye — us gaon ko chhod do, baaki chalte rahen
                }
                $out[(int) $village['id']] = $this->normalisePoint($points[$i]);
            }

            return $out;
        });
    }

    /**
     * fetchForPoint() — ek hi lat/lng ka rainfall (single village detail screen ke liye).
     * INPUT : lat, lng | OUTPUT: normalised array (upar wala shape) ya null
     * KYUN: batch call sirf poore map ke liye hai; ek gaon ka detail kholne pe 30 gaon
     *       ka data fetch karna faltu hai.
     */
    public function fetchForPoint(float $lat, float $lng): ?array
    {
        $result = $this->fetchForVillages([['id' => 0, 'lat' => $lat, 'lng' => $lng]]);

        return $result[0] ?? null;
    }

    /**
     * normalisePoint() — Open-Meteo ka raw JSON humare simple shape mein badalta hai.
     *
     * KYA NIKAALTE HAIN:
     *   today_mm          aaj ka total (RiskEngine ka input R)
     *   current_mm        is ghante ki barish (dashboard pe "abhi" dikhane ke liye)
     *   rain_3day_mm      aaj + pichhle 2 din = 3 din ka total (river-level proxy ka input)
     *   yesterday_3day_mm kal tak ka 3-din total (rise rate nikalne ke liye — kitna badha)
     *   daily             date => mm ka poora map (chart ke liye)
     *
     * KYUN alag function: API ka shape kabhi badla to sirf yahan fix karna padega.
     * Baaki poore system ko humara apna shape hi dikhta hai.
     */
    private function normalisePoint(array $point): array
    {
        $dates = $point['daily']['time'] ?? [];
        $sums = $point['daily']['precipitation_sum'] ?? [];

        // date => mm map banao (null ko 0 maano — Open-Meteo missing values null bhejta hai)
        $daily = [];
        foreach ($dates as $i => $date) {
            $daily[$date] = (float) ($sums[$i] ?? 0.0);
        }

        $values = array_values($daily);

        // past_days=3 + forecast_days=2 => index 3 hi "aaj" hai (0,1,2 = pichhle teen din).
        $todayIndex = self::PAST_DAYS;
        $today = $values[$todayIndex] ?? 0.0;

        // 3-din cumulative = aaj + pichhle 2 din. Nadi ka paani aaj ki barish se nahi,
        // catchment mein jama hue paani se badhta hai.
        $rain3day = 0.0;
        for ($i = $todayIndex - 2; $i <= $todayIndex; $i++) {
            $rain3day += $values[$i] ?? 0.0;
        }

        // Wahi hisaab ek din peeche — isse pata chalta hai paani badh raha hai ya ghat raha.
        $yesterday3day = 0.0;
        for ($i = $todayIndex - 3; $i <= $todayIndex - 1; $i++) {
            $yesterday3day += $values[$i] ?? 0.0;
        }

        return [
            'today_mm' => round($today, 2),
            'current_mm' => round((float) ($point['current']['precipitation'] ?? 0.0), 2),
            'rain_3day_mm' => round($rain3day, 2),
            'yesterday_3day_mm' => round($yesterday3day, 2),
            'daily' => $daily,
            'fetched_at' => now()->toIso8601String(),
        ];
    }
}
