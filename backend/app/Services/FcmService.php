<?php

namespace App\Services;

use App\Models\Alert;
use App\Models\DeviceToken;
use Illuminate\Support\Facades\Log;
use Kreait\Firebase\Contract\Messaging;
use Kreait\Firebase\Exception\FirebaseException;
use Kreait\Firebase\Messaging\AndroidConfig;
use Kreait\Firebase\Messaging\CloudMessage;
use Kreait\Firebase\Messaging\Notification;

/**
 * =====================================================================================
 *  FcmService — officer ka alert citizen ke phone tak (real push notification)
 * =====================================================================================
 *
 *  KYA: ek Alert leta hai, us gaon ke saare registered device dhoondhta hai, aur
 *       Firebase Cloud Messaging se sabko notification bhejta hai.
 *
 *  KYUN YE PRODUCT KA SABSE ZAROORI HISSA HAI:
 *   Poora JalRakshak isi ek moment pe tika hai — officer dashboard pe button dabata hai,
 *   aur gaon ke logon ke phone BAJTE hain. Isse pehle sab kuch (risk engine, map, charts)
 *   sirf officer ki screen tak tha. Ye wo pul hai jo warning ko aam aadmi tak le jaata hai.
 *
 *  CREDENTIAL: storage/app/firebase/firebase-admin.json (service account private key).
 *  Ye file GITIGNORED hai aur kabhi commit nahi hoti. Path .env ke FIREBASE_CREDENTIALS
 *  se aata hai (config/firebase.php isko padhta hai).
 *
 *  IMPORTANT — ye service KABHI exception nahi phenkta:
 *   Agar FCM down ho ya credential galat ho, to alert phir bhi DB mein save hona chahiye.
 *   Flood ke waqt "push fail hua isliye alert record hi nahi bana" sabse bura outcome hai.
 *   Isliye har error yahan pakda jaata hai, log hota hai, aur result array mein honestly
 *   wapas jaata hai — dashboard use dikhata hai (`push.sent: false`), chhupata nahi.
 * =====================================================================================
 */
final class FcmService
{
    /**
     * FCM ek request mein max 500 token leta hai (sendMulticast ki limit).
     * Abhi 30 gaon hain to itne token honge hi nahi, par code sahi hona chahiye —
     * asli deployment mein ek gaon ke hazaron device ho sakte hain.
     */
    private const BATCH = 500;

    public function __construct(private readonly Messaging $messaging) {}

    /**
     * sendForAlert() — ek alert ko us gaon ke saare phones tak pahuchao.
     *
     * INPUT : Alert model (village_id + message_hi + message_en already set)
     * OUTPUT: ['sent' => int, 'failed' => int, 'devices' => int, 'error' => ?string]
     *
     * KYUN dono bhasha bhejte hain (title/body Hindi, data mein dono):
     *   Notification ka VISIBLE text Hindi mein hai — kyunki gaon mein wahi padha jaata hai
     *   aur BUILD_PLAN mein Hindi default hai. Par `data` payload mein English bhi jaata hai,
     *   taaki app khulne pe user ke chune hue language mein dikha sake. Ek hi push se dono.
     */
    public function sendForAlert(Alert $alert): array
    {
        $tokens = DeviceToken::where('village_id', $alert->village_id)
            ->pluck('token')
            ->all();

        // Koi device register hi nahi — ye error nahi hai. Gaon mein kisi ne app install
        // hi nahi ki. Officer ko ye saaf dikhna chahiye (0 devices), warna wo samjhega
        // ki alert pahunch gaya.
        if ($tokens === []) {
            return ['sent' => 0, 'failed' => 0, 'devices' => 0, 'error' => null];
        }

        $village = $alert->village;

        // Notification ka dikhne wala hissa.
        $notification = Notification::create(
            'JalRakshak · '.($village?->name ?? 'Alert'),
            $alert->message_hi,
        );

        /**
         * Android-specific settings.
         *  priority HIGH  — flood alert doze mode mein bhi turant aana chahiye, baad mein nahi.
         *  sound default  — phone bajna chahiye; chup notification ka koi matlab nahi.
         *  channel_id     — Android 8+ pe channel zaroori hai. App mein bhi yehi id banayi
         *                   hai (JalRakshakMessagingService dekho) — dono match hone chahiye,
         *                   warna notification dikhega hi nahi.
         */
        $androidConfig = AndroidConfig::fromArray([
            'priority' => 'high',
            'notification' => [
                'sound' => 'default',
                'channel_id' => 'jalrakshak_alerts',
            ],
        ]);

        /**
         * data payload — app isko padh ke sahi screen kholti hai (tap pe alert khule).
         * Saari values STRING honi chahiye — FCM data mein number/bool allowed nahi.
         */
        $data = [
            'alert_id' => (string) $alert->id,
            'village_id' => (string) $alert->village_id,
            'village_name' => (string) ($village?->name ?? ''),
            'message_hi' => $alert->message_hi,
            'message_en' => $alert->message_en,
            'sent_by' => $alert->sent_by,
            'sent_at' => $alert->sent_at?->toIso8601String() ?? '',
        ];

        $sent = 0;
        $failed = 0;
        $invalid = [];

        foreach (array_chunk($tokens, self::BATCH) as $chunk) {
            try {
                $message = CloudMessage::new()
                    ->withNotification($notification)
                    ->withAndroidConfig($androidConfig)
                    ->withData($data);

                $report = $this->messaging->sendMulticast($message, $chunk);

                $sent += $report->successes()->count();
                $failed += $report->failures()->count();

                // FCM batata hai ki kaunse token ab valid nahi (app uninstall ho gayi,
                // ya token refresh ho gaya). Unhe collect karke baad mein delete karte hain.
                foreach ($report->invalidTokens() as $t) {
                    $invalid[] = $t;
                }
                foreach ($report->unknownTokens() as $t) {
                    $invalid[] = $t;
                }
            } catch (FirebaseException|\Throwable $e) {
                // Poora batch fail — credential galat, network down, ya FCM outage.
                $failed += count($chunk);
                Log::error('FCM send fail: '.$e->getMessage(), ['alert_id' => $alert->id]);

                return [
                    'sent' => $sent,
                    'failed' => $failed,
                    'devices' => count($tokens),
                    'error' => $e->getMessage(),
                ];
            }
        }

        // Mare hue token hatao — warna table bhar jaayegi aur har push mein faltu
        // failures aayenge (aur officer ko galat "failed" count dikhega).
        if ($invalid !== []) {
            DeviceToken::whereIn('token', $invalid)->delete();
            Log::info('FCM: '.count($invalid).' dead tokens hataye gaye.');
        }

        return ['sent' => $sent, 'failed' => $failed, 'devices' => count($tokens), 'error' => null];
    }
}
