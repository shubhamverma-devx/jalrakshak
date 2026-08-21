<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Alert — officer ne ek gaon ko bheja hua message (Hindi + English).
 *
 * Kyun dono bhasha store: citizen app mein hi/en toggle hai. Runtime translation slow + unreliable
 * hota, aur flood ke waqt galat translation khatarnaak. Isliye bhejte waqt hi dono likh dete hain.
 *
 * Day 3: is row se FCM push payload banega (topic: village_<id>). Abhi sirf DB record.
 */
class Alert extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'village_id',
        'message_hi',
        'message_en',
        'sent_by',
        'sent_at',
    ];

    protected function casts(): array
    {
        return [
            'sent_at' => 'datetime',
        ];
    }

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }
}
