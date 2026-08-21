<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Shelter — relief camp / safe building.
 *
 * Kyun: "khatra hai" bolna aadha kaam hai; "kahan jao" bolna poora kaam hai. Yahi SMS se aage hai.
 * Citizen app ye list Room (SQLite) mein cache karti hai — network gaya to bhi shelter dikhta rahe.
 */
class Shelter extends Model
{
    public $timestamps = false;

    protected $fillable = [
        'name',
        'lat',
        'lng',
        'capacity',
        'village_id',
    ];

    protected function casts(): array
    {
        return [
            'lat' => 'float',
            'lng' => 'float',
            'capacity' => 'integer',
        ];
    }

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }
}
