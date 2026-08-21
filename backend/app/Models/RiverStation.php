<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * RiverStation — ek CWC-style river gauge site.
 *
 * Kyun ye model matter karta hai: RiskEngine ka sabse decisive input yahin se aata hai.
 * `current_level_m` ko `warning_level_m` / `danger_level_m` se compare karke level nikalta hai.
 *
 * $timestamps = false: schema (BUILD_PLAN section 7) mein sirf `updated_at` hai, `created_at` nahi.
 * Isliye Laravel ka default timestamp pair band, aur UPDATED_AT manually handle karte hain.
 */
class RiverStation extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'name',
        'warning_level_m',
        'danger_level_m',
        'current_level_m',
        'updated_at',
    ];

    protected function casts(): array
    {
        return [
            'warning_level_m' => 'float',
            'danger_level_m' => 'float',
            'current_level_m' => 'float',
            'updated_at' => 'datetime',
        ];
    }

    /** Is station se jude saare gaon. */
    public function villages(): HasMany
    {
        return $this->hasMany(Village::class);
    }

    /**
     * Kya is station ke paas usable government thresholds hain?
     *
     * Kyun: kuch seeded stations ke warning/danger verified nahi (DATA_NOTES.md) — wo NULL hain.
     * RiskEngine aise stations pe river rule SKIP karta hai (galat number dikhane se behtar hai
     * honestly bolna "river data nahi hai"), aur sirf rainfall + elevation pe chalta hai.
     *
     * Output: bool
     */
    public function hasThresholds(): bool
    {
        return $this->warning_level_m !== null && $this->danger_level_m !== null;
    }
}
