<?php

/**
 * Migration: river_stations
 *
 * Kya: CWC-style river gauge stations. Har station pe do government thresholds hote hain —
 *      warning level aur danger level (meters mein, gauge datum ke hisaab se).
 * Kyun: RiskEngine ka sabse strong signal yahi hai — "river level >= danger mark" matlab RED.
 *       Village khud apna level nahi rakhta, wo apne nearest station se level lete hain
 *       (villages.river_station_id), isliye ye table villages se PEHLE banta hai.
 *
 * Note (honesty — data/DATA_NOTES.md): seed ke kuch warning/danger values approximate/
 * placeholder hain. Jin stations ka threshold pata hi nahi, unke columns NULL rehte hain —
 * RiskEngine tab river rule skip karke sirf rainfall+elevation pe chalta hai (jhooth nahi bolta).
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('river_stations', function (Blueprint $table) {
            $table->id();

            // Station ka naam (CWC gauge site). Unique — seeder isi naam se village/rainfall link karta hai.
            $table->string('name')->unique();

            // Government thresholds (m). NULLABLE — kyunki har station ka verified threshold nahi hai.
            $table->decimal('warning_level_m', 8, 2)->nullable();
            $table->decimal('danger_level_m', 8, 2)->nullable();

            // Abhi ka water level (m). risk:compute command isko update karta hai.
            $table->decimal('current_level_m', 8, 2)->nullable();

            // Level kab update hua — "data kitna purana hai" dikhane ke liye.
            $table->timestamp('updated_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('river_stations');
    }
};
