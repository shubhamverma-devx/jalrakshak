<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * Village — JalRakshak ki core unit. Poora risk, alert aur relief isi ke around ghoomta hai.
 *
 * Kyun village-level: govt SMS poore district ko ek jaisa alert bhejta hai. Humara differentiator
 * hi ye hai ki 30 gaon mein se sirf 4 RED hain to sirf un 4 ko alert jaaye.
 *
 * $timestamps = false: schema mein villages ke created_at/updated_at nahi (static seed data hai).
 */
class Village extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'name',
        'district',
        'lat',
        'lng',
        'elevation_m',
        'population',
        'river_station_id',
    ];

    protected function casts(): array
    {
        return [
            'lat' => 'float',
            'lng' => 'float',
            'elevation_m' => 'integer',
            'population' => 'integer',
        ];
    }

    /** Nearest river gauge — RiskEngine ka river input yahan se. */
    public function riverStation(): BelongsTo
    {
        return $this->belongsTo(RiverStation::class);
    }

    /** Saari barish readings (live + replay dono). */
    public function rainfall(): HasMany
    {
        return $this->hasMany(Rainfall::class);
    }

    /** RiskEngine ke computed snapshots (history). */
    public function riskScores(): HasMany
    {
        return $this->hasMany(RiskScore::class);
    }

    /** Is gaon ko bheje gaye alerts (app ka feed). */
    public function alerts(): HasMany
    {
        return $this->hasMany(Alert::class);
    }

    /** Is gaon se aayi SOS requests. */
    public function reliefRequests(): HasMany
    {
        return $this->hasMany(ReliefRequest::class);
    }

    /** Is gaon ke paas ke relief camps. */
    public function shelters(): HasMany
    {
        return $this->hasMany(Shelter::class);
    }
}
