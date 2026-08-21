<?php

/**
 * Migration: alerts
 *
 * Kya: Officer ne kis gaon ko kya alert bheja — uska record.
 * Kyun: Ye "SMS se aage" wala core feature hai — targeted (village_id) + do bhasha (hi/en).
 *       Message Hindi aur English dono store hote hain kyunki citizen app mein language toggle hai;
 *       runtime pe translate karna unreliable + slow, isliye officer ke bhejte waqt hi dono rakhte hain.
 *
 * Day 3: yahi row FCM push ka payload banega. Abhi sirf DB mein store (FCM wiring Day 3).
 */

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('alerts', function (Blueprint $table) {
            $table->id();

            $table->foreignId('village_id')->constrained('villages')->cascadeOnDelete();

            $table->text('message_hi'); // Hindi text — app default
            $table->text('message_en'); // English text — app toggle

            // Kis officer ne bheja. Abhi auth nahi hai (scope LOCKED) isliye plain string naam.
            $table->string('sent_by');

            $table->timestamp('sent_at');

            // App ka "alerts feed" — village ke latest alerts pehle.
            $table->index(['village_id', 'sent_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('alerts');
    }
};
