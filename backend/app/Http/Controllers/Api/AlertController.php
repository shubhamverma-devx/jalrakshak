<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Alert;
use App\Models\Village;
use App\Services\FcmService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * AlertController — officer ka targeted alert.
 *
 * YEHI PRODUCT KA DIL HAI. Govt ka system poore district ko ek jaisa SMS bhejta hai.
 * Yahan officer ek GAON chunta hai aur sirf usi ke logon ko alert jaata hai — Hindi aur
 * English dono mein, "kya karo" ke saath.
 *
 * DAY 3 (ho gaya): ye endpoint ab alert save karke us gaon ke SAARE registered phones ko
 * asli FCM push bhejta hai (app/Services/FcmService.php). Emulator pe notification bajti hai.
 */
class AlertController extends Controller
{
    /**
     * FcmService constructor se inject hota hai (Laravel container khud deta hai).
     * KYUN inject, `new FcmService()` nahi: usko Firebase ka Messaging client chahiye jo
     * credential file se banta hai — wo wiring kreait/laravel-firebase karta hai.
     */
    public function __construct(private readonly FcmService $fcm) {}

    /**
     * POST /api/alert — ek gaon ko alert bhejo.
     *
     * BODY (JSON):
     *   village_id  int    required
     *   message_hi  string required  Hindi text (app ka default)
     *   message_en  string required  English text (app ka toggle)
     *   sent_by     string required  officer ka naam (auth nahi hai — scope LOCKED)
     *
     * OUTPUT: 201 + alert record
     *
     * KYUN dono bhasha client se: flood mein galat auto-translation jaan le sakti hai
     * ("evacuate" ka ulta matlab). Officer khud dono likhta hai, ya dashboard ready-made
     * template deta hai. Machine translation runtime pe = no.
     */
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'village_id' => ['required', 'integer', 'exists:villages,id'],
            'message_hi' => ['required', 'string', 'max:500'],
            'message_en' => ['required', 'string', 'max:500'],
            'sent_by' => ['required', 'string', 'max:100'],
        ]);

        $alert = Alert::create([
            ...$data,
            'sent_at' => now(),
        ]);

        $village = Village::find($data['village_id']);

        // --------------------------------------------------------------------------
        //  ASLI FCM PUSH — poore product ka sabse important moment.
        //
        //  ORDER MAAYNE RAKHTA HAI: alert PEHLE DB mein save hua (upar), push BAAD mein.
        //  Kyun: FCM down ho to bhi alert ka record rehna chahiye — app khulte hi citizen
        //  use /api/alerts se dekh lega. Ulta karte (pehle push, phir save) to FCM fail
        //  hone pe alert kahin bhi na hota.
        //
        //  setRelation() se village pehle hi jod dete hain taaki FcmService dobara
        //  DB query na kare (notification ke title mein gaon ka naam chahiye hota hai).
        //
        //  FcmService kabhi exception nahi phenkta — har error pakad ke result mein
        //  honestly wapas deta hai. Isliye yahan try/catch ki zaroorat nahi.
        // --------------------------------------------------------------------------
        $alert->setRelation('village', $village);
        $push = $this->fcm->sendForAlert($alert);

        return response()->json([
            'message' => "Alert {$village?->name} ko bhej diya gaya.",
            'alert' => $this->format($alert, $village),
            // Push ka ASLI nateeja — dashboard isko dikhata hai.
            // KYUN poora detail (sirf true/false nahi): officer ko farq pata hona chahiye
            // "gaon mein kisi ne app install hi nahi ki" (devices: 0) aur "bhejne ki koshish
            // fail hui" (error) mein. Dono mein alert nahi pahuncha, par kaaran — aur officer
            // ka agla kadam — bilkul alag hai.
            'push' => [
                'sent' => $push['sent'] > 0,
                'devices' => $push['devices'],
                'success' => $push['sent'],
                'failed' => $push['failed'],
                'error' => $push['error'],
            ],
        ], 201);
    }

    /**
     * GET /api/alerts — bheje gaye alerts (dashboard history + app ka feed).
     *
     * QUERY PARAMS:
     *   ?village_id=5  (optional — sirf ek gaon ke, app isi ka use karti hai)
     *   ?limit=50      (default 50, max 200)
     *
     * OUTPUT: alerts[] (naye pehle)
     *
     * KYUN ye route BUILD_PLAN ki list mein nahi tha par bana diya: alert bhejne ke baad
     * "gaya ya nahi" verify karne ka koi tareeka hi nahi tha, aur citizen app ka "purane alerts"
     * tab (section 2 mein locked feature hai) isi pe chalega. Naya feature nahi — wahi
     * alerts table ka read.
     */
    public function index(Request $request): JsonResponse
    {
        $limit = min(max((int) $request->query('limit', 50), 1), 200);

        $query = Alert::with('village:id,name,district');

        if ($request->filled('village_id')) {
            $query->where('village_id', (int) $request->query('village_id'));
        }

        $alerts = $query->orderByDesc('sent_at')->limit($limit)->get();

        return response()->json([
            'count' => $alerts->count(),
            'alerts' => $alerts->map(fn ($a) => $this->format($a, $a->village))->values(),
        ]);
    }

    /**
     * format() — alert ka JSON shape (store + index dono ke liye ek hi).
     * INPUT: Alert, Village|null | OUTPUT: array
     */
    private function format(Alert $alert, ?Village $village): array
    {
        return [
            'id' => $alert->id,
            'village' => $village ? [
                'id' => $village->id,
                'name' => $village->name,
                'district' => $village->district,
            ] : null,
            'message_hi' => $alert->message_hi,
            'message_en' => $alert->message_en,
            'sent_by' => $alert->sent_by,
            'sent_at' => $alert->sent_at?->toIso8601String(),
        ];
    }
}
