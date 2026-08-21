<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Shelter;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * ShelterController — relief camps ki list.
 *
 * KYUN alag endpoint (jabki village detail mein bhi shelters aate hain):
 * citizen app pehli baar khulte hi POORI shelter list download karke Room mein rakh leti hai.
 * Isse baad mein network jaane par bhi "kahan jao" dikhta rehta hai — offline support ka base.
 */
class ShelterController extends Controller
{
    /**
     * GET /api/shelters — saare shelters (ya kisi gaon ke paas wale).
     *
     * QUERY PARAMS:
     *   ?village_id=5   (optional — sirf us gaon se jude camps)
     *   ?lat=&lng=      (optional — in coordinates se distance ke hisaab se sort)
     *
     * OUTPUT: shelters[] (id, name, lat, lng, capacity, village, distance_km?)
     *
     * KYUN distance sort: baadh mein aadmi ko "koi bhi camp" nahi, "sabse paas ka camp" chahiye.
     */
    public function index(Request $request): JsonResponse
    {
        $query = Shelter::with('village:id,name,district');

        if ($request->filled('village_id')) {
            $query->where('village_id', (int) $request->query('village_id'));
        }

        $shelters = $query->orderBy('name')->get()->map(fn ($s) => [
            'id' => $s->id,
            'name' => $s->name,
            'lat' => $s->lat,
            'lng' => $s->lng,
            'capacity' => $s->capacity,
            'village' => $s->village ? [
                'id' => $s->village->id,
                'name' => $s->village->name,
                'district' => $s->village->district,
            ] : null,
        ]);

        // Agar user ne apni location bheji hai to distance jod ke nazdeek wale upar kar do.
        if ($request->filled('lat') && $request->filled('lng')) {
            $lat = (float) $request->query('lat');
            $lng = (float) $request->query('lng');

            $shelters = $shelters
                ->map(function ($s) use ($lat, $lng) {
                    $s['distance_km'] = $this->haversineKm($lat, $lng, $s['lat'], $s['lng']);

                    return $s;
                })
                ->sortBy('distance_km');
        }

        return response()->json([
            'count' => $shelters->count(),
            'shelters' => $shelters->values(),
        ]);
    }

    /**
     * haversineKm() — do lat/lng ke beech ki seedhi doori (km).
     *
     * INPUT : lat1, lng1, lat2, lng2 | OUTPUT: km (1 decimal)
     * KYUN haversine, road-routing nahi: routing API paise/key maangti hai (scope LOCKED).
     * Seedhi doori "sabse paas ka camp" chunne ke liye kaafi hai. Actual road route =
     * future scope; app abhi bas map pe pin dikhati hai.
     */
    private function haversineKm(float $lat1, float $lng1, float $lat2, float $lng2): float
    {
        $earthRadiusKm = 6371;

        $dLat = deg2rad($lat2 - $lat1);
        $dLng = deg2rad($lng2 - $lng1);

        $a = sin($dLat / 2) ** 2
            + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLng / 2) ** 2;

        return round($earthRadiusKm * 2 * asin(min(1.0, sqrt($a))), 1);
    }
}
