<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Services\SarDetectionService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * SarController — dashboard ka "Satellite" tab isse baat karta hai.
 *
 * Ye B1 (trained U-Net) ka HTTP chehra hai. Do endpoint:
 *   GET  /api/sar/scenes  -> kaun se sample chips available hain + model provenance
 *   POST /api/sar/detect  -> ek chip pe model chalao, polygons + area wapas
 *
 * KYUN alag controller (VillageController mein nahi ghusaya): risk map rule-based hai,
 * ye trained model hai. Inhe alag rakhne se code mein bhi wahi seema dikhti hai jo
 * hum judge ke saamne bolte hain — "ye wala ML hai, wo wala rules".
 */
class SarController extends Controller
{
    public function __construct(private readonly SarDetectionService $sar) {}

    /**
     * GET /api/sar/scenes
     * OUTPUT: { scenes: [...], model: {...} }
     *
     * Model info yahin bhej dete hain taaki UI provenance label pehle se dikha sake —
     * detection chalane se pehle hi user ko pata ho ki model kis cheez pe trained hai.
     */
    public function scenes(): JsonResponse
    {
        return response()->json([
            'scenes' => $this->sar->scenes(),
            'model' => $this->sar->modelInfo(),
        ]);
    }

    /**
     * POST /api/sar/detect  { scene: "India_591317" }
     *
     * OUTPUT: 200 + { scene, detection, geojson, nearest_villages, model }
     *         422 agar scene galat/available nahi
     *         503 agar model hi na chal paya (python missing, timeout)
     *
     * KYUN 503 (500 nahi): model na chalna server ka "abhi ye service uplabdh nahi" hai,
     * code ka crash nahi. Dashboard isse alag tarah dikhata hai.
     */
    public function detect(Request $request): JsonResponse
    {
        $data = $request->validate([
            'scene' => ['required', 'string', 'max:64'],
        ]);

        $result = $this->sar->detect($data['scene']);

        if (! ($result['ok'] ?? false)) {
            $msg = $result['error'] ?? 'Detection fail hui.';
            // "nahi hai / galat" = client ki galti (422); baaki = service down (503)
            $isClient = str_contains($msg, 'nahi hai') || str_contains($msg, 'Galat');

            return response()->json(['message' => $msg], $isClient ? 422 : 503);
        }

        return response()->json($result);
    }
}
