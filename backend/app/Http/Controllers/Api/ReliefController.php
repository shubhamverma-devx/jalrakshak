<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\ReliefRequest;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * ReliefController — citizen ka SOS (two-way communication).
 *
 * KYUN YE FEATURE MATTER KARTA HAI: govt ka flood SMS ek-taraffa hai — citizen wapas kuch
 * nahi bol sakta. Yahan chhat pe phasa aadmi apni exact GPS location bhej sakta hai aur wo
 * turant officer dashboard pe dikhti hai. Yahi "SMS se aage" ka sabse strong demo point hai.
 */
class ReliefController extends Controller
{
    /**
     * POST /api/relief — citizen app se nayi SOS request.
     *
     * BODY (JSON):
     *   village_id  int    required  kis gaon se
     *   lat, lng    float  required  bhejne wale ki EXACT location (phone GPS)
     *   message     string required  "chhat pe phase hain", "khaana nahi hai"
     *
     * OUTPUT: 201 + bani hui request
     *
     * KYUN lat/lng alag se, village ke centre se nahi: rescue boat ko gaon nahi, aadmi
     * chahiye. Gaon 5 km ka ho sakta hai — centre pe jaana bekaar hai.
     *
     * KYUN koi auth nahi: scope LOCKED (BUILD_PLAN section 2) — auth/security future scope hai.
     * Asli deployment mein phone-number OTP lagega taaki spam SOS na aayen. Ye jaan-bujh ke
     * chhoda gaya hai, bhoole nahi hain.
     */
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'village_id' => ['required', 'integer', 'exists:villages,id'],
            'lat' => ['required', 'numeric', 'between:-90,90'],
            'lng' => ['required', 'numeric', 'between:-180,180'],
            'message' => ['required', 'string', 'max:500'],
        ]);

        $relief = ReliefRequest::create([
            ...$data,
            'status' => ReliefRequest::STATUS_NEW,
            'created_at' => now(),
        ]);

        $relief->load('village:id,name,district');

        return response()->json([
            'message' => 'Madad ki request bhej di gayi. Team ko soochit kar diya gaya hai.',
            'relief' => $this->format($relief),
        ], 201);
    }

    /**
     * GET /api/relief — officer dashboard ki relief table.
     *
     * QUERY PARAMS:
     *   ?status=new|inprogress|done   (optional filter)
     *   ?village_id=5                 (optional filter)
     *
     * OUTPUT: counts (status-wise) + requests[] (naye pehle)
     *
     * KYUN naye pehle (orderByDesc): flood mein sabse nayi SOS sabse urgent hoti hai.
     * Counts saath isliye ki officer ko header pe "12 new, 4 in progress" turant dikhe.
     */
    public function index(Request $request): JsonResponse
    {
        $query = ReliefRequest::with('village:id,name,district');

        if ($request->filled('status')) {
            $status = $request->query('status');
            // Galat status pe khaali list dena chup-chaap bug hai — saaf error do.
            if (! in_array($status, ReliefRequest::STATUSES, true)) {
                return response()->json([
                    'message' => 'Galat status. Valid: '.implode(', ', ReliefRequest::STATUSES),
                ], 422);
            }
            $query->where('status', $status);
        }

        if ($request->filled('village_id')) {
            $query->where('village_id', (int) $request->query('village_id'));
        }

        $requests = $query->orderByDesc('created_at')->limit(500)->get();

        return response()->json([
            'counts' => [
                'new' => ReliefRequest::where('status', ReliefRequest::STATUS_NEW)->count(),
                'inprogress' => ReliefRequest::where('status', ReliefRequest::STATUS_INPROGRESS)->count(),
                'done' => ReliefRequest::where('status', ReliefRequest::STATUS_DONE)->count(),
            ],
            'count' => $requests->count(),
            'requests' => $requests->map(fn ($r) => $this->format($r))->values(),
        ]);
    }

    /**
     * format() — ek relief request ka JSON shape.
     * INPUT: ReliefRequest | OUTPUT: array
     * KYUN alag method: store() aur index() dono same shape bhejein, warna dashboard ko
     * do alag parser likhne padenge.
     */
    private function format(ReliefRequest $r): array
    {
        return [
            'id' => $r->id,
            'village' => $r->village ? [
                'id' => $r->village->id,
                'name' => $r->village->name,
                'district' => $r->village->district,
            ] : null,
            'lat' => $r->lat,
            'lng' => $r->lng,
            'message' => $r->message,
            'status' => $r->status,
            'created_at' => $r->created_at?->toIso8601String(),
        ];
    }
}
