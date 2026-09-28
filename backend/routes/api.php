<?php

/**
 * =====================================================================================
 *  JalRakshak — Public JSON API
 * =====================================================================================
 *
 *  Laravel yahan PURE API backend hai (decision D3). Do client isko consume karte hain:
 *    - Officer dashboard : React + Leaflet  (alag deploy)
 *    - Citizen app       : Kotlin + Compose (Android)
 *
 *  SAB ROUTES PUBLIC HAIN — koi auth nahi. Ye jaan-bujh ke hai: BUILD_PLAN section 2 mein
 *  auth/security explicitly future scope hai (SIH prototype, 4 din). Deployment mein
 *  officer routes (POST /alert, GET /relief) ke peeche login lagega. Ye bhoolna nahi.
 *
 *  COMMON QUERY PARAMS (read routes pe):
 *    ?mode=live    -> Open-Meteo se abhi ki asli barish (default)
 *    ?mode=replay  -> Assam June-2022 flood ka seeded data
 *    ?day=N        -> replay ka din (0 = 15 June 2022 ... 11 = 26 June 2022)
 * =====================================================================================
 */

use App\Http\Controllers\Api\AlertController;
use App\Http\Controllers\Api\DeviceTokenController;
use App\Http\Controllers\Api\ReliefController;
use App\Http\Controllers\Api\SarController;
use App\Http\Controllers\Api\ShelterController;
use App\Http\Controllers\Api\VillageController;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Route;

// --- Risk map (dashboard ka main data + app ka home) --------------------------------
Route::get('/villages', [VillageController::class, 'index']);      // sab gaon + risk + summary
Route::get('/village/{id}', [VillageController::class, 'show']);   // ek gaon ka poora detail
Route::get('/village/{id}/forecast', [VillageController::class, 'forecast']);  // B2 — +24h/+48h forecast

// --- Shelters (app offline cache karti hai) -----------------------------------------
Route::get('/shelters', [ShelterController::class, 'index']);

// --- Relief / SOS (two-way) ---------------------------------------------------------
Route::post('/relief', [ReliefController::class, 'store']);        // citizen bhejta hai
Route::get('/relief', [ReliefController::class, 'index']);         // officer dekhta hai
Route::patch('/relief/{id}', [ReliefController::class, 'update']);  // officer status badalta hai

// --- Push registration (citizen app har launch pe call karti hai) -------------------
Route::post('/register-token', [DeviceTokenController::class, 'store']);

// --- Alerts (targeted, Hindi+English) -----------------------------------------------
Route::post('/alert', [AlertController::class, 'store']);          // officer bhejta hai (FCM Day 3)
Route::get('/alerts', [AlertController::class, 'index']);          // history / app ka feed

// --- B1: SAR flood detection (trained U-Net) ----------------------------------------
// NOTE: ye TRAINED MODEL hai — baaki risk endpoints rule-based RiskEngine se aate hain.
// Do alag cheezein hain, isliye routes bhi alag rakhe hain.
Route::get('/sar/scenes', [SarController::class, 'scenes']);
Route::post('/sar/detect', [SarController::class, 'detect']);

/**
 * GET /api/health — uptime monitor ke liye chhota status check.
 *
 * KYA CHECK KARTA HAI (sab sasta, <10ms):
 *   - PHP + php-fpm zinda hai (response aaya = zinda)
 *   - MySQL: `select 1` — sabse common failure (1GB droplet pe OOM-kill) yahi pakadta hai
 *   - Scheduler: `risk:compute` ne aakhri baar kab live map likha (cache timestamp)
 *
 * OUTPUT: 200 {status:"ok", ...} | 503 {status:"degraded", ...} agar DB nahi mila
 *
 * KYUN 503 (200 nahi) jab DB gaya: free uptime monitor (UptimeRobot/Better Stack) sirf
 * status code dekhta hai. Dashboard DB ke bina bhi bundled replay se chal jaata hai — par
 * aapko email aana chahiye ki backend ka aadha hissa gira hua hai.
 * Scheduler ka stale hona sirf `checks` mein dikhta hai, 503 nahi deta — Open-Meteo ka
 * ek ghante down rehna site down hona nahi hai.
 *
 * Koi Open-Meteo/external call yahan NAHI — health check khud kisi bahar ki API pe nahi
 * atakna chahiye. (Laravel ka /up alag hai — wo framework ka hai, ye humara.)
 */
Route::get('/health', function () {
    $checks = [];

    try {
        DB::select('select 1');
        $checks['database'] = 'ok';
    } catch (\Throwable $e) {
        $checks['database'] = 'down';
    }

    // risk:compute live map cache mein generated_at likhta hai. 40 min TTL hai, to na mile
    // ka matlab scheduler ne 40+ min se kuch nahi likha (ya Open-Meteo gaya).
    $live = Cache::get('risk:map:live');
    $checks['live_risk_generated_at'] = $live['generated_at'] ?? null;
    $checks['scheduler'] = $live ? 'ok' : 'stale';

    $ok = $checks['database'] === 'ok';

    return response()->json([
        'app' => 'JalRakshak API',
        'status' => $ok ? 'ok' : 'degraded',
        'checks' => $checks,
        'time' => now()->toIso8601String(),
    ], $ok ? 200 : 503);
});
