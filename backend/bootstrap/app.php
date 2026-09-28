<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        // JalRakshak = pure JSON API (decision D3). Saare endpoints /api prefix ke peeche.
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Railway ka edge proxy HTTPS khatam karke andar HTTP bhejta hai. Proxy pe bharosa
        // na karein to Laravel khud ko http:// samajhta hai (galat URLs, galat client IP).
        // Container ke aage sirf Railway ka proxy hai, isliye '*' safe hai.
        $middleware->trustProxies(at: '*');

        // API stateless hai (BUILD_PLAN section 6) — koi session/CSRF nahi, isliye default
        // api middleware group hi kaafi hai. Sanctum jaan-bujh ke install nahi kiya:
        // auth future scope hai (section 2), aur droplet 1GB pe har extra package ka weight hai.
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // API-only app hai — error bhi hamesha JSON mein jaana chahiye, HTML page kabhi nahi.
        // Kotlin app aur React dashboard dono JSON hi parse karte hain; HTML aaya to crash.
        $exceptions->shouldRenderJsonWhen(fn () => true);
    })->create();
