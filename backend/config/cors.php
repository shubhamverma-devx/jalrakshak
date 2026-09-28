<?php

/**
 * =====================================================================================
 *  CORS — kaunse browser origins API bula sakte hain
 * =====================================================================================
 *  Pehle ye file thi hi nahi => Laravel default `*` (koi bhi site API bula sakti thi).
 *  Ab sirf hamara dashboard:
 *
 *    CORS_ALLOWED_ORIGINS        comma-separated exact origins
 *                                (Railway: https://<project>.vercel.app)
 *    CORS_ALLOWED_ORIGIN_PATTERN optional regex — Vercel ke preview deploy URL har push pe
 *                                badalte hain (https://<project>-<hash>-<team>.vercel.app)
 *
 *  Default local dev (vite :5173) hai, taaki naye developer ko kuch set na karna pade.
 *
 *  NOTE: Android app pe CORS lagta hi nahi — wo browser nahi hai. Ye sirf dashboard ke
 *  liye hai. Auth abhi bhi nahi hai (D8/D17) — CORS security nahi, bas browser ki seema.
 * =====================================================================================
 */

$origins = array_values(array_filter(array_map(
    'trim',
    explode(',', env('CORS_ALLOWED_ORIGINS', 'http://localhost:5173,http://127.0.0.1:5173')),
)));

return [

    'paths' => ['api/*'],

    'allowed_methods' => ['GET', 'POST', 'PATCH', 'OPTIONS'],

    'allowed_origins' => $origins,

    'allowed_origins_patterns' => array_filter([env('CORS_ALLOWED_ORIGIN_PATTERN')]),

    'allowed_headers' => ['Content-Type', 'Accept'],

    'exposed_headers' => [],

    // Preflight (OPTIONS) ka jawaab browser 1 din yaad rakhe — har PATCH/POST pe do request nahi.
    'max_age' => 86400,

    'supports_credentials' => false,

];
