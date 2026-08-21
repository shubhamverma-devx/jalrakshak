<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * ReliefRequest — citizen app ke SOS button se banti hai.
 *
 * Kyun important: yahi "two-way" hai. Govt ka SMS ek-taraffa hai — citizen wapas kuch nahi bol sakta.
 * Yahan phasa hua aadmi apni exact GPS location + message bhej sakta hai, officer dashboard pe
 * turant dikhta hai aur status track hota hai.
 */
class ReliefRequest extends Model
{
    public $timestamps = false;

    public const STATUS_NEW = 'new';

    public const STATUS_INPROGRESS = 'inprogress';

    public const STATUS_DONE = 'done';

    /** Valid statuses — controller validation isi list se karta hai (ek jagah rakha). */
    public const STATUSES = [self::STATUS_NEW, self::STATUS_INPROGRESS, self::STATUS_DONE];

    protected $fillable = [
        'village_id',
        'lat',
        'lng',
        'message',
        'status',
        'created_at',
    ];

    protected function casts(): array
    {
        return [
            'lat' => 'float',
            'lng' => 'float',
            'created_at' => 'datetime',
        ];
    }

    public function village(): BelongsTo
    {
        return $this->belongsTo(Village::class);
    }
}
