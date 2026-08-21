<?php

/**
 * Migration: rainfall
 *
 * Kya: Per-village barish reading (mm). RiskEngine ka input "R".
 * Kyun: Do source ek hi table mein —
 *       source='live'   => Open-Meteo se aaya real-time/forecast data (scheduler har 30 min)
 *       source='replay' => Assam June-2022 flood replay (seed se), demo ke dramatic scenario ke liye
 *       Ek hi table isliye ki RiskEngine ko farq nahi padta data kahan se aaya — wahi rules chalte hain.
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('rainfall', function (Blueprint $table) {
            $table->id();

            $table->foreignId('village_id')->constrained('villages')->cascadeOnDelete();

            // Us reading-window ki barish (mm). Replay = daily total, live = daily total (Open-Meteo).
            $table->decimal('rainfall_mm', 8, 2);

            // 'live' ya 'replay' — API ke ?mode= se seedha match hota hai.
            $table->enum('source', ['live', 'replay']);

            // Reading kis waqt ki hai (replay ke liye 2022 ki date).
            $table->timestamp('recorded_at');

            // Same village + same source + same timestamp do baar na ghuse => seeder/scheduler idempotent.
            $table->unique(['village_id', 'source', 'recorded_at'], 'rainfall_unique_reading');

            // "Is village ka is mode ka latest data" query ka main index.
            $table->index(['source', 'recorded_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('rainfall');
    }
};
