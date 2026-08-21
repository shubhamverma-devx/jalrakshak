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
use App\Http\Controllers\Api\ShelterController;
use App\Http\Controllers\Api\VillageController;
use Illuminate\Support\Facades\Route;

// --- Risk map (dashboard ka main data + app ka home) --------------------------------
Route::get('/villages', [VillageController::class, 'index']);      // sab gaon + risk + summary
Route::get('/village/{id}', [VillageController::class, 'show']);   // ek gaon ka poora detail

// --- Shelters (app offline cache karti hai) -----------------------------------------
Route::get('/shelters', [ShelterController::class, 'index']);

// --- Relief / SOS (two-way) ---------------------------------------------------------
Route::post('/relief', [ReliefController::class, 'store']);        // citizen bhejta hai
Route::get('/relief', [ReliefController::class, 'index']);         // officer dekhta hai

// --- Push registration (citizen app har launch pe call karti hai) -------------------
Route::post('/register-token', [DeviceTokenController::class, 'store']);

// --- Alerts (targeted, Hindi+English) -----------------------------------------------
Route::post('/alert', [AlertController::class, 'store']);          // officer bhejta hai (FCM Day 3)
Route::get('/alerts', [AlertController::class, 'index']);          // history / app ka feed

/**
 * GET /api/health — chhota sa status check.
 * KYUN: droplet deploy ke baad "API zinda hai?" ek curl mein pata chal jaaye. Demo se pehle
 * ye pehla command chalta hai. (Laravel ka /up alag hai — wo framework ka hai, ye humara.)
 */
Route::get('/health', fn () => response()->json([
    'app' => 'JalRakshak API',
    'status' => 'ok',
    'time' => now()->toIso8601String(),
]));
