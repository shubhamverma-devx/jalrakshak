<?php

/**
 * Migration: shelters
 *
 * Kya: Relief camp / safe building — naam, jagah, capacity.
 * Kyun: Alert ke saath "kahan jao" batana hi asli value hai. Sirf "khatra hai" bolna SMS bhi karta hai.
 *       Ye list citizen app mein OFFLINE cache hoti hai (Room), taaki network jaane pe bhi dikhe.
 *
 * village_id = ye shelter kis gaon ke sabse paas hai (nearest-shelter lookup ka shortcut).
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('shelters', function (Blueprint $table) {
            $table->id();

            $table->string('name');
            $table->decimal('lat', 10, 7);
            $table->decimal('lng', 10, 7);

            // Kitne log samaa sakte hain — officer ko capacity planning ke liye.
            $table->unsignedInteger('capacity');

            $table->foreignId('village_id')
                ->nullable()
                ->constrained('villages')
                ->nullOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('shelters');
    }
};
