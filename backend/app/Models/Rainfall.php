<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Rainfall — ek village ki ek reading (mm).
 *
 * Do source, ek table:
 *   live   → Open-Meteo se scheduler ne uthaya (asli, aaj ka)
 *   replay → Assam June-2022 flood ka seeded data (demo scenario)
 *
 * Kyun ek hi table: RiskEngine ko source se koi farq nahi padta — wahi rules dono pe chalte hain.
 * Isse demo mein hum sach bol paate hain: "same engine, sirf data feed badla".
 */
class Rainfall extends Model
{
    /** Table ka naam plural-singular same hai, isliye explicit. */
    protected $table = 'rainfall';

    public $timestamps = false;

    public const SOURCE_LIVE = 'live';

    public const SOURCE_REPLAY = 'replay';

    protected $fillable = [
        'village_id',
        'rainfall_mm',
        'source',
        'recorded_at',
    ];

    protected function casts(): array
    {
        return [
            'rainfall_mm' => 'float',
            'recorded_at' => 'datetime',
        ];
    }

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }

    /**
     * Scope: sirf ek source ka data.
     * Input: 'live' ya 'replay' | Output: query builder
     * Kyun: har API request `?mode=` se aata hai, wahi seedha yahan pass hota hai.
     */
    public function scopeSource(Builder $q, string $source): Builder
    {
        return $q->where('source', $source);
    }
}
