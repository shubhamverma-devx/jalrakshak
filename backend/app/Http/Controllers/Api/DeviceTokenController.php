<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\DeviceToken;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * DeviceTokenController — citizen app apna FCM token yahan register karti hai.
 *
 * KYUN ZAROORI: FCM ko push bhejne ke liye device ka token chahiye. App launch pe
 * Firebase se token milta hai, par backend ko nahi pata hota — app ko khud bhejna padta hai.
 * Iske bina officer ka alert kahin nahi jaayega.
 */
class DeviceTokenController extends Controller
{
    /**
     * POST /api/register-token — token register/update karo.
     *
     * BODY (JSON):
     *   token       string required  FCM registration token
     *   village_id  int    required  ye phone kis gaon ke alert sunna chahta hai
     *   platform    string optional  'android' (default)
     *
     * OUTPUT: 200 + { registered: true, village }
     *
     * KYUN updateOrCreate ON TOKEN (create nahi):
     *   App HAR launch pe ye call karti hai (token kabhi bhi refresh ho sakta hai —
     *   reinstall, data clear, ya FCM khud rotate kare). Agar plain create karte to
     *   ek hi phone ki 50 rows ban jaatin aur usko 50 push jaate.
     *   Token unique hai, isliye wahi row update hoti hai — aur agar user ne gaon badla
     *   hai to village_id bhi apne aap sahi ho jaata hai. Purana gaon unsubscribe karne
     *   ki alag call ki zaroorat hi nahi.
     *
     * KYUN koi auth nahi: scope LOCKED (BUILD_PLAN section 2). Deployment mein ye endpoint
     * rate-limit + app-attestation ke peeche jaayega, warna koi bhi faltu token bhar sakta hai.
     */
    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'token' => ['required', 'string', 'max:255'],
            'village_id' => ['required', 'integer', 'exists:villages,id'],
            'platform' => ['nullable', 'string', 'max:20'],
        ]);

        $device = DeviceToken::updateOrCreate(
            ['token' => $data['token']],
            [
                'village_id' => $data['village_id'],
                'platform' => $data['platform'] ?? 'android',
            ],
        );

        $device->load('village:id,name,district');

        return response()->json([
            'registered' => true,
            'village' => $device->village ? [
                'id' => $device->village->id,
                'name' => $device->village->name,
                'district' => $device->village->district,
            ] : null,
            // Officer ko dashboard pe "kitne phone is gaon mein" dikhane ke liye kaam aata hai.
            'devices_in_village' => DeviceToken::where('village_id', $device->village_id)->count(),
        ]);
    }
}
