<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * DeviceToken — ek phone ka FCM token + wo kis gaon ke alert sunta hai.
 *
 * Ek phone ka token badalta rehta hai (app reinstall, data clear, FCM ka apna refresh).
 * Isliye app har launch pe token backend ko bhejti hai aur hum `token` pe upsert karte hain —
 * duplicate nahi bante, aur gaon badla ho to wahi row update ho jaati hai.
 */
class DeviceToken extends Model
{
    protected $fillable = [
        'village_id',
        'token',
        'platform',
    ];

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }
}
