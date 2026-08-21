<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\RiskMapService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * VillageController — risk map ke read endpoints.
 *
 * Ye endpoints dono client use karte hain:
 *   Officer dashboard (React+Leaflet) -> /api/villages  (poora map + summary)
 *   Citizen app (Kotlin)              -> /api/village/{id} (apna gaon)
 *
 * Controller mein KOI risk logic nahi — sirf request padho, service ko do, JSON wapas.
 * (CLAUDE.md: risk logic sirf RiskEngine.php mein.)
 */
class VillageController extends Controller
{
    public function __construct(private readonly RiskMapService $riskMap) {}

    /**
     * GET /api/villages — saare gaon, abhi ke risk ke saath.
     *
     * QUERY PARAMS:
     *   ?mode=live|replay   (default live)
     *   ?day=N              (sirf replay mein — 0-based din index; chhoda to aakhri din)
     *   ?district=Jorhat    (optional filter — district drill-down ke liye)
     *   ?level=red          (optional filter — "sirf RED gaon dikhao")
     *
     * OUTPUT: mode/date/summary + villages[] (har ek mein risk, reason, advice, ETA)
     *
     * KYUN filter server pe: 30 gaon pe frontend filter bhi chalta, par jab ye 3000 gaon ka
     * hoga to poora payload bhejna galat hoga. Abhi se sahi shape rakhi hai.
     */
    public function index(Request $request): JsonResponse
    {
        $map = $this->riskMap->map(
            $request->query('mode', RiskMapService::MODE_LIVE),
            $request->filled('day') ? (int) $request->query('day') : null,
        );

        $villages = collect($map['villages']);

        if ($request->filled('district')) {
            $district = mb_strtolower($request->query('district'));
            $villages = $villages->filter(fn ($v) => mb_strtolower($v['district']) === $district);
        }

        if ($request->filled('level')) {
            $level = mb_strtolower($request->query('level'));
            $villages = $villages->filter(fn ($v) => $v['risk']['level'] === $level);
        }

        return response()->json([
            ...$map,
            'villages' => $villages->values(),

            // Dashboard ka date-slider isi list se banta hai.
            'replay_days' => $this->riskMap->replayDays(),
        ]);
    }

    /**
     * GET /api/village/{id} — ek gaon ka poora detail.
     *
     * QUERY PARAMS: ?mode= aur ?day= (index jaisa hi)
     * OUTPUT: village + risk + river station + shelters + recent alerts + open SOS count
     *         404 agar gaon exist nahi karta
     *
     * KYUN sab kuch ek response mein: citizen app kharab network pe chalti hai. Ek call mein
     * poori screen ka data mil jaaye to 4 alag call ka round-trip bachta hai — aur wahi
     * ek response Room (SQLite) mein offline cache ho jaata hai.
     */
    public function show(Request $request, int $id): JsonResponse
    {
        $detail = $this->riskMap->village(
            $id,
            $request->query('mode', RiskMapService::MODE_LIVE),
            $request->filled('day') ? (int) $request->query('day') : null,
        );

        if ($detail === null) {
            return response()->json(['message' => 'Village nahi mila.'], 404);
        }

        return response()->json($detail);
    }
}
