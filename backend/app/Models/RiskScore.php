<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * RiskScore — RiskEngine ka saved output (ek village, ek waqt).
 *
 * Kyun store karte hain jab risk on-the-fly bhi ban sakta hai:
 *  1. History/audit — "20 June ko subah hi RED ho gaya tha" dikhana judge ke saamne strong hai.
 *  2. Scheduler (har 30 min) isko bharta hai, taaki API request pe heavy kaam na ho.
 *
 * NOTE: yahan koi logic nahi — sirf result. Rules sirf app/Services/RiskEngine.php mein.
 */
class RiskScore extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'village_id',
        'score',
        'level',
        'computed_at',
    ];

    protected function casts(): array
    {
        return [
            'score' => 'integer',
            'computed_at' => 'datetime',
        ];
    }

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }
}
