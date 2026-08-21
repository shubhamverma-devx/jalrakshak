<?php

/**
 * Migration: device_tokens
 *
 * Kya: Har citizen phone ka FCM registration token, aur wo kis gaon ke liye register hua.
 *
 * KYUN YE TABLE (BUILD_PLAN section 7 ki 7 tables ke ALAWA):
 *   Section 7 ka schema Day 1 ke data-model ke liye tha (villages/risk/alerts/relief).
 *   Push bhejne ke liye ek cheez chahiye jo wahan nahi thi — "kis phone ko bhejein".
 *   FCM ko ya to token chahiye ya topic. Bina is table ke targeted push possible hi nahi,
 *   aur targeted alert poore product ka core feature hai (section 2 mein LOCKED "BUILD").
 *   Isliye ye scope creep nahi — ye FCM feature ka zaroori hissa hai.
 *
 * KYUN TOKENS, TOPICS NAHI:
 *   FCM topics (har gaon ka ek topic) aasan lagta hai, par phir server ko pata hi nahi
 *   chalta ki kitne device subscribe hain — officer ko "9,800 citizens notified" jaisa
 *   number nahi dikha sakte, aur purane/dead device clean bhi nahi hote.
 *   Token table se dono milte hain: exact count, aur FCM jab "token invalid" bole to
 *   hum row delete kar dete hain (FcmService dekho).
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('device_tokens', function (Blueprint $table) {
            $table->id();

            // Ye phone kis gaon ka alert sunna chahta hai.
            // Citizen app mein gaon badla ja sakta hai, isliye ye update hota rehta hai.
            $table->foreignId('village_id')->constrained('villages')->cascadeOnDelete();

            // FCM registration token. Lamba hota hai (~160+ chars), isliye string(255) nahi —
            // par unique index chahiye, aur MySQL utf8mb4 mein index limit 3072 bytes hai
            // (768 chars). 255 kaafi hai aur index-safe hai.
            $table->string('token', 255)->unique();

            // Abhi sirf 'android' aayega. Column isliye rakha ki iOS/web baad mein jude to
            // migration na likhni pade — aur FcmService platform-wise payload bhej sake.
            $table->string('platform', 20)->default('android');

            $table->timestamps();

            // "Is gaon ke saare token do" — push bhejte waqt ki ek hi query.
            $table->index('village_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('device_tokens');
    }
};
