<?php

/**
 * Migration: relief_requests
 *
 * Kya: Citizen app ke SOS button se aane wali madad ki request.
 * Kyun: Yehi "two-way" hai — govt SMS ek-taraffa hai, JalRakshak mein citizen wapas bol sakta hai.
 *       lat/lng alag se store hote hain (village ke centre se nahi) kyunki phasa hua aadmi
 *       gaon ke centre pe nahi, kahin bhi ho sakta hai — rescue team ko exact point chahiye.
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('relief_requests', function (Blueprint $table) {
            $table->id();

            $table->foreignId('village_id')->constrained('villages')->cascadeOnDelete();

            // SOS bhejne wale ki exact jagah (phone GPS se).
            $table->decimal('lat', 10, 7);
            $table->decimal('lng', 10, 7);

            $table->text('message'); // "khaana nahi hai", "chhat pe phase hain" etc.

            // Officer dashboard isko new -> inprogress -> done karta hai.
            $table->enum('status', ['new', 'inprogress', 'done'])->default('new');

            $table->timestamp('created_at');

            // Officer table: pehle naye/pending, phir purane.
            $table->index(['status', 'created_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('relief_requests');
    }
};
