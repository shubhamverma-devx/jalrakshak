<?php

/**
 * Migration: villages
 *
 * Kya: JalRakshak ki sabse choti unit — ek gaon. Poora product isi granularity pe kaam karta hai.
 * Kyun: Govt SMS poore zile ko ek jaisa alert bhejta hai. Humara USP = VILLAGE-level targeting,
 *       isliye lat/lng + elevation + population har gaon ka alag store hota hai.
 *
 * elevation_m: RiskEngine ka teesra input (E). Neechi zameen = paani pehle pahunchta hai.
 * population : alert priority + "kitne log affected" dashboard number ke liye.
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('villages', function (Blueprint $table) {
            $table->id();

            $table->string('name');
            $table->string('district')->index(); // dashboard district-summary isi pe group karta hai

            // Map pin. decimal(10,7) => ~1 cm precision, Leaflet ke liye kaafi.
            $table->decimal('lat', 10, 7);
            $table->decimal('lng', 10, 7);

            // Sea level se height (m) — seed data representative hai (DATA_NOTES.md).
            $table->unsignedSmallInteger('elevation_m');

            // Approx aabadi — affected-population estimate ke liye.
            $table->unsignedInteger('population');

            // Nearest river gauge. NULLABLE taaki bina station wala gaon bhi chal sake.
            $table->foreignId('river_station_id')
                ->nullable()
                ->constrained('river_stations')
                ->nullOnDelete();

            // Ek district mein do same-naam gaon na ghusein (seeder idempotent rahe).
            $table->unique(['name', 'district']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('villages');
    }
};
