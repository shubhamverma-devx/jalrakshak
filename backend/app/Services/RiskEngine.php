<?php

namespace App\Services;

/**
 * =====================================================================================
 *  RiskEngine — JalRakshak ka dimaag (BUILD_PLAN.md section 8)
 * =====================================================================================
 *
 *  YE KYA HAI: Rule-based flood EARLY-WARNING engine.
 *
 *  YE KYA NAHI HAI: Machine learning nahi. Forecast model nahi. Hum "kal flood aayega"
 *  nahi bolte. Hum bolte hain: "abhi ke rainfall + river level government ke warning/danger
 *  nishaan cross kar rahe hain, isliye ye gaon RED hai." Bas.
 *
 *  KYUN RULE-BASED (decision D4, docs/decisions.md):
 *   - Reliable ML flood model ke liye saalon ka labelled data chahiye — 4 din mein nahi banta.
 *   - Rule-based EXPLAINABLE hai. Officer/judge poochhe "ye RED kyun hai?" to hum exact
 *     number dikha sakte hain: "level 85.4 m, danger mark 85.14 m — cross ho gaya."
 *   - Disaster management mein explainability accuracy jitni hi zaroori hai. Black box pe
 *     koi collector gaon khaali nahi karwaayega.
 *
 *  SAARI RISK LOGIC SIRF IS FILE MEIN (CLAUDE.md convention). Controller, command, seeder —
 *  koi bhi apna threshold ya if-else nahi likhta. Kuch badalna ho to sirf yahan badlega.
 *
 *  INPUT (per village):
 *    R = rainfall (mm)          — Open-Meteo live ya 2022 replay
 *    L = river level (m)        — station ka current level, warning/danger ke against
 *    E = elevation (m)          — neechi zameen pe paani pehle
 *
 *  OUTPUT (per village):
 *    level (green/yellow/red) + score (0-100) + reason (hi/en) + "paani ~X ghante mein"
 *
 *  ---------------------------------------------------------------------------------
 *  SCRIPT RULE (Day 3 mein theek kiya):
 *    Citizen ko DIKHNE WALA Hindi text DEVANAGARI mein hai (saari `_hi` strings) —
 *    kyunki wahi approved design hai (mockup_v2.html ka drawer bhi Devanagari dikhata
 *    hai) aur citizen app ki poori UI Devanagari mein hai.
 *
 *    Code ke COMMENTS aur docs Roman Hindi mein hain — wo developer ke liye hain,
 *    user ke liye nahi. Dono alag cheezein hain.
 *
 *    Pehle `_hi` strings bhi Roman mein thi. Isse Android app ki risk card pe
 *    Devanagari label ("आपका गाँव") ke saath Roman reason ("Nadi ka level...") dikh
 *    raha tha — ek hi card pe do script. Emulator pe test karte waqt saaf pakda gaya.
 *  ---------------------------------------------------------------------------------
 * =====================================================================================
 */
final class RiskEngine
{
    // ---------------------------------------------------------------------------------
    //  THRESHOLDS — sab constants, sab documented. Koi magic number code mein nahi.
    // ---------------------------------------------------------------------------------

    /**
     * Rainfall categories — IMD (India Meteorological Department) ke official daily slabs (mm/24h).
     * Kyun IMD: apni marzi ke number nahi banaye; jo India ka mausam vibhag use karta hai wahi.
     *   64.5 - 115.5  => Heavy
     *   115.6 - 204.4 => Very heavy
     *   204.5+        => Extremely heavy
     */
    public const RAIN_HEAVY_MM = 64.5;

    public const RAIN_VERY_HEAVY_MM = 115.6;

    public const RAIN_EXTREMELY_HEAVY_MM = 204.5;

    /**
     * "Low elevation" ka cut-off (meters).
     * Kyun 80: Brahmaputra ka floodplain belt ~20-100 m ke beech baithta hai. 80 m se neeche
     * wale gaon ko flood-prone maana jaata hai. Ye tuning knob hai — badle to sirf yahan.
     */
    public const LOW_ELEVATION_M = 80;

    /** Risk levels — teen hi, kyunki map pe 🔴🟡🟢 se zyada kuch samajh nahi aata. */
    public const LEVEL_GREEN = 'green';

    public const LEVEL_YELLOW = 'yellow';

    public const LEVEL_RED = 'red';

    /**
     * "Paani ~X ghante mein" ka upper cap.
     * Kyun: 72 ghante se aage ka estimate rise-rate se nikaalna bakwaas hoga (barish badal jaati hai).
     * Usse zyada nikle to hum honestly `null` bolte hain — "abhi koi ETA nahi".
     */
    public const MAX_ETA_HOURS = 72;

    // ---------------------------------------------------------------------------------
    //  MAIN ENTRY POINT
    // ---------------------------------------------------------------------------------

    /**
     * assess() — ek gaon ka poora risk assessment.
     *
     * KYA KARTA HAI: teen input (R, L, E) leta hai, BUILD_PLAN section 8 ke rules chalata hai,
     *                aur ek complete, explainable result deta hai.
     *
     * INPUT (associative array — jo missing ho wo null/0 default):
     *   rainfall_mm       float  R — is reading window ki barish
     *   rainfall_3day_mm  float  pichhle 3 din ka total (soil already saturated hai ya nahi)
     *   elevation_m       int    E — gaon ki height
     *   river_level_m     ?float L — station ka abhi ka level (null = data nahi)
     *   warning_level_m   ?float govt warning mark (null = is station ka verified nahi)
     *   danger_level_m    ?float govt danger mark
     *   rise_rate_m_per_hr ?float paani kitni tezi se badh raha (ETA ke liye)
     *
     * OUTPUT (array): level, score, reason_hi, reason_en, hours_to_danger,
     *                 water_eta_hi/en, advice_hi/en, factors[]
     *
     * KYUN ek hi array in/out: controller, artisan command aur seeder — teeno isi ko call karte
     * hain. Ek hi shape hone se kahin bhi logic duplicate nahi hoti.
     */
    public function assess(array $input): array
    {
        // --- Inputs normalize karo (missing keys se crash na ho) ---------------------
        $rain = (float) ($input['rainfall_mm'] ?? 0.0);
        $rain3day = (float) ($input['rainfall_3day_mm'] ?? $rain);
        $elevation = (int) ($input['elevation_m'] ?? 999);
        $level_m = isset($input['river_level_m']) ? (float) $input['river_level_m'] : null;
        $warning_m = isset($input['warning_level_m']) ? (float) $input['warning_level_m'] : null;
        $danger_m = isset($input['danger_level_m']) ? (float) $input['danger_level_m'] : null;
        $riseRate = isset($input['rise_rate_m_per_hr']) ? (float) $input['rise_rate_m_per_hr'] : null;

        // River rule tabhi chalega jab teeno numbers ho. Warna honestly skip.
        $hasRiverData = $level_m !== null && $warning_m !== null && $danger_m !== null;

        $isLowElevation = $elevation <= self::LOW_ELEVATION_M;

        // --- Rules chalao -----------------------------------------------------------
        $decision = $this->decideLevel($rain, $elevation, $level_m, $warning_m, $danger_m, $hasRiverData, $isLowElevation);

        // --- "Paani ~X ghante mein" -------------------------------------------------
        $hours = $hasRiverData
            ? $this->hoursToDanger($level_m, $danger_m, $riseRate)
            : null;

        return [
            'level' => $decision['level'],
            'score' => $this->score($rain, $rain3day, $elevation, $level_m, $warning_m, $danger_m, $hasRiverData),

            // Kyun ye level aaya — dashboard pe seedha dikhta hai. Ye hi hamari explainability hai.
            'reason_hi' => $decision['reason_hi'],
            'reason_en' => $decision['reason_en'],

            'hours_to_danger' => $hours,
            'water_eta_hi' => $this->etaTextHi($hours, $decision['level'], $hasRiverData),
            'water_eta_en' => $this->etaTextEn($hours, $decision['level'], $hasRiverData),

            // "Kya karo" — citizen app ka sabse kaam ka hissa.
            'advice_hi' => $this->adviceHi($decision['level']),
            'advice_en' => $this->adviceEn($decision['level']),

            // Raw numbers — taaki koi bhi hisaab khud verify kar sake (transparency).
            'factors' => [
                'rainfall_mm' => round($rain, 1),
                'rainfall_3day_mm' => round($rain3day, 1),
                'rainfall_category' => $this->rainCategory($rain),
                'elevation_m' => $elevation,
                'low_elevation' => $isLowElevation,
                'river_level_m' => $level_m !== null ? round($level_m, 2) : null,
                'warning_level_m' => $warning_m,
                'danger_level_m' => $danger_m,
                'river_data' => $hasRiverData, // false => river rule skip hua (data nahi tha)
                'rise_rate_m_per_hr' => $riseRate !== null ? round($riseRate, 4) : null,
            ],
        ];
    }

    // ---------------------------------------------------------------------------------
    //  RULES — BUILD_PLAN section 8 ka exact translation
    // ---------------------------------------------------------------------------------

    /**
     * decideLevel() — green/yellow/red ka faisla.
     *
     * BUILD_PLAN section 8 ka pseudo-code, jaisa ka taisa:
     *
     *   if   L >= danger             -> RED
     *   elif L >= warning            -> YELLOW  (-> RED agar R bahut zyada + low elevation)
     *   elif R > heavy AND low E     -> YELLOW
     *   else                         -> GREEN
     *
     * INPUT: rainfall, elevation, river level + thresholds, flags
     * OUTPUT: ['level' => ..., 'reason_hi' => ..., 'reason_en' => ...]
     *
     * KYUN ye order: sabse pehle river, kyunki flood ka sabse seedha proof paani ka level hai.
     * Barish ek leading indicator hai (aage kya hoga), river level ek confirmed fact hai (abhi kya hai).
     * Confirmed fact hamesha jeetega.
     */
    private function decideLevel(
        float $rain,
        int $elevation,
        ?float $level_m,
        ?float $warning_m,
        ?float $danger_m,
        bool $hasRiverData,
        bool $isLowElevation,
    ): array {
        // ---- RULE 1: danger mark cross -> RED ----------------------------------------
        // Government ka danger mark cross = definition ke hisaab se flooding. Koi debate nahi.
        if ($hasRiverData && $level_m >= $danger_m) {
            $over = round($level_m - $danger_m, 2);

            return [
                'level' => self::LEVEL_RED,
                'reason_hi' => "नदी का पानी ख़तरे के निशान ({$danger_m} m) से {$over} m ऊपर है।",
                'reason_en' => "River is {$over} m above the danger mark ({$danger_m} m).",
            ];
        }

        // ---- RULE 2: warning mark cross -> YELLOW, ya RED agar aur factors bhi bure ----
        if ($hasRiverData && $level_m >= $warning_m) {
            // Escalation: paani warning pe hai + barish "very heavy" + gaon neecha
            // => paani danger tak pahunchne mein ghante lagenge, mahine nahi. RED bolo abhi.
            // Kyun teeno saath: sirf ek factor pe RED bolna false alarm banata hai, aur
            // false alarm ke baad log agli baar alert ignore karte hain — wahi asli khatra hai.
            if ($rain >= self::RAIN_VERY_HEAVY_MM && $isLowElevation) {
                return [
                    'level' => self::LEVEL_RED,
                    'reason_hi' => "पानी चेतावनी निशान ({$warning_m} m) पर है, ऊपर से बहुत तेज़ बारिश ({$rain} mm) और गाँव नीचा ({$elevation} m)।",
                    'reason_en' => "River at warning mark ({$warning_m} m) with very heavy rain ({$rain} mm) and low elevation ({$elevation} m).",
                ];
            }

            return [
                'level' => self::LEVEL_YELLOW,
                'reason_hi' => "नदी चेतावनी निशान ({$warning_m} m) तक पहुँच गई है।",
                'reason_en' => "River has reached the warning mark ({$warning_m} m).",
            ];
        }

        // ---- RULE 3: bhaari barish + neechi zameen -> YELLOW ---------------------------
        // River abhi theek hai, par IMD "heavy" barish + low elevation = local waterlogging
        // aur river ka rise aane wala hai. Ye hamara "hours pehle" wala early-warning hai.
        if ($rain > self::RAIN_HEAVY_MM && $isLowElevation) {
            return [
                'level' => self::LEVEL_YELLOW,
                'reason_hi' => "भारी बारिश ({$rain} mm) और गाँव नीची ज़मीन पर ({$elevation} m)।",
                'reason_en' => "Heavy rainfall ({$rain} mm) over low-lying land ({$elevation} m).",
            ];
        }

        // ---- RULE 4: kuch nahi mila -> GREEN ------------------------------------------
        // Honesty: agar river data hi nahi tha to reason mein wo bhi likho.
        if (! $hasRiverData) {
            return [
                'level' => self::LEVEL_GREEN,
                'reason_hi' => 'फ़िलहाल ख़तरा नहीं। (इस स्टेशन का नदी-स्तर डेटा उपलब्ध नहीं — सिर्फ़ बारिश और ऊँचाई देखी गई।)',
                'reason_en' => 'No immediate risk. (No river-level data for this station — assessed on rainfall and elevation only.)',
            ];
        }

        return [
            'level' => self::LEVEL_GREEN,
            'reason_hi' => "नदी का स्तर ({$level_m} m) चेतावनी निशान ({$warning_m} m) से नीचे है और बारिश सामान्य है।",
            'reason_en' => "River level ({$level_m} m) is below the warning mark ({$warning_m} m) and rainfall is normal.",
        ];
    }

    // ---------------------------------------------------------------------------------
    //  SCORE — sirf ranking/shading ke liye, faisla level hi karta hai
    // ---------------------------------------------------------------------------------

    /**
     * score() — 0 se 100 ka composite number.
     *
     * KYUN ZAROORAT: dashboard pe 8 gaon RED ho sakte hain. Officer ke paas 2 hi boat hain.
     * Kaunse gaon pe pehle jaaye? Score se sorting hoti hai — "sabse zyada RED" upar.
     *
     * KYUN SCORE SE LEVEL NAHI NIKAALTE: agar hum bolte "score > 70 = RED", to RED ka matlab
     * ek arbitrary number ban jaata. Abhi RED ka matlab hai "government danger mark cross ho gaya" —
     * jo defend ho sakta hai. Score sirf ek ranking helper hai, faisla rules ka hai.
     *
     * Weights (total 100):
     *   River  0-60  — sabse bhaari, kyunki confirmed fact hai
     *   Rain   0-30  — leading indicator
     *   Elev   0-10  — static modifier
     *
     * INPUT: rainfall, 3-day rainfall, elevation, river level + thresholds
     * OUTPUT: int 0-100
     */
    private function score(
        float $rain,
        float $rain3day,
        int $elevation,
        ?float $level_m,
        ?float $warning_m,
        ?float $danger_m,
        bool $hasRiverData,
    ): int {
        $score = 0.0;

        // ---- River component (0-60) ------------------------------------------------
        if ($hasRiverData) {
            $span = max($danger_m - $warning_m, 0.01); // 0 se divide na ho

            if ($level_m >= $danger_m) {
                // Danger cross — 50 base, aur jitna upar utna zyada (max 60).
                $score += min(60.0, 50.0 + (($level_m - $danger_m) / $span) * 10.0);
            } elseif ($level_m >= $warning_m) {
                // Warning aur danger ke beech — 30 se 50 tak linear.
                $score += 30.0 + (($level_m - $warning_m) / $span) * 20.0;
            } else {
                // Warning se neeche — kitna paas hai uske hisaab se 0 se 30.
                $gap = max($warning_m - $level_m, 0.0);
                $score += max(0.0, 30.0 - ($gap / $span) * 15.0);
            }
        } else {
            // River data nahi — us 60 point ke hisse ko neutral rakho (na darao, na chhupao).
            // 3-day barish se ek halka proxy: soil saturated ho to risk to hai hi.
            $score += min(25.0, ($rain3day / 300.0) * 25.0);
        }

        // ---- Rainfall component (0-30) ----------------------------------------------
        // IMD "extremely heavy" (204.5 mm) pe poore 30 point. Uske upar cap.
        $score += min(30.0, ($rain / self::RAIN_EXTREMELY_HEAVY_MM) * 30.0);

        // ---- Elevation component (0-10) ---------------------------------------------
        // 20 m ya neeche => poore 10. 120 m ya upar => 0. Beech mein linear.
        $score += max(0.0, min(10.0, (120 - $elevation) / 10.0));

        return (int) round(max(0.0, min(100.0, $score)));
    }

    // ---------------------------------------------------------------------------------
    //  "PAANI ~X GHANTE MEIN"
    // ---------------------------------------------------------------------------------

    /**
     * hoursToDanger() — paani danger mark tak pahunchne mein kitne ghante.
     *
     * KYA: simple straight-line extrapolation. Paani abhi jis rate se badh raha hai (m/ghanta),
     *      usi rate se badha to danger mark kab cross hoga.
     *
     *      ghante = (danger_mark - abhi_ka_level) / rise_rate
     *
     * INPUT: current level (m), danger mark (m), rise rate (m/ghanta)
     * OUTPUT: int ghante | 0 (paani aa chuka) | null (badh hi nahi raha / bahut door)
     *
     * KYUN ITNA SIMPLE: ye "prediction" nahi hai — ye seedha arithmetic hai jo hum officer ko
     * dikha sakte hain. Isiliye humesha "~" (approx) ke saath bolte hain aur 72 ghante se aage
     * ka estimate dete hi nahi (utni door tak barish ka pattern badal jaata hai).
     *
     * IMANDAARI: rise rate barish se derive hota hai, real gauge telemetry se nahi.
     * Deployment mein CWC ka actual station feed lagega.
     */
    private function hoursToDanger(float $level_m, float $danger_m, ?float $riseRate): ?int
    {
        // Paani pehle hi danger pe/upar hai — intezaar khatam, ab bacho.
        if ($level_m >= $danger_m) {
            return 0;
        }

        // Paani badh hi nahi raha (ya ghat raha hai) => koi ETA nahi. Jhoota number mat do.
        if ($riseRate === null || $riseRate <= 0.0) {
            return null;
        }

        $hours = ($danger_m - $level_m) / $riseRate;

        // Itni door ka estimate bharosemand nahi — honestly null.
        if ($hours > self::MAX_ETA_HOURS) {
            return null;
        }

        return (int) max(1, round($hours));
    }

    /**
     * etaTextHi() / etaTextEn() — hours ko insaan ke padhne layak line banate hain.
     * INPUT: ghante (int|null), level, river data hai ya nahi
     * OUTPUT: string
     * KYUN: citizen app aur dashboard dono yahi string dikhate hain — wording ek hi jagah,
     *       taaki app aur dashboard kabhi alag baat na bolein.
     */
    private function etaTextHi(?int $hours, string $level, bool $hasRiverData): string
    {
        if (! $hasRiverData) {
            return 'नदी का डेटा उपलब्ध नहीं — समय का अनुमान नहीं।';
        }
        if ($hours === 0) {
            return 'पानी ख़तरे के निशान से ऊपर है — अभी सुरक्षित जगह जाएँ।';
        }
        if ($hours === null) {
            return $level === self::LEVEL_GREEN
                ? 'पानी अभी नहीं बढ़ रहा।'
                : 'पानी बढ़ रहा है, पर समय का अनुमान अभी नहीं।';
        }

        return "पानी ~{$hours} घंटे में ख़तरे के निशान तक पहुँच सकता है।";
    }

    private function etaTextEn(?int $hours, string $level, bool $hasRiverData): string
    {
        if (! $hasRiverData) {
            return 'No river data available — cannot estimate timing.';
        }
        if ($hours === 0) {
            return 'Water is already above the danger mark — move to safety now.';
        }
        if ($hours === null) {
            return $level === self::LEVEL_GREEN
                ? 'Water is not rising right now.'
                : 'Water is rising, but no reliable time estimate yet.';
        }

        return "Water may reach the danger mark in ~{$hours} hours.";
    }

    // ---------------------------------------------------------------------------------
    //  "KYA KARO" — advice per level
    // ---------------------------------------------------------------------------------

    /**
     * adviceHi() / adviceEn() — har level ka action.
     * INPUT: level | OUTPUT: string
     * KYUN: govt SMS sirf "warning" bolta hai. Asli farq "ab kya karo" batane se padta hai.
     *       Ye text engine mein isliye hai taaki app, dashboard aur push notification —
     *       teeno ek hi wording bhejein.
     */
    private function adviceHi(string $level): string
    {
        return match ($level) {
            self::LEVEL_RED => 'तुरंत नज़दीकी शरण स्थल जाएँ। ज़रूरी काग़ज़, दवाई और पीने का पानी साथ लें। बहते पानी में न चलें।',
            self::LEVEL_YELLOW => 'तैयार रहें। ज़रूरी सामान एक बैग में रखें, मवेशी ऊँची जगह बाँधें, चेतावनियों पर नज़र रखें।',
            default => 'फ़िलहाल ख़तरा नहीं। मौसम अपडेट देखते रहें।',
        };
    }

    private function adviceEn(string $level): string
    {
        return match ($level) {
            self::LEVEL_RED => 'Move to the nearest shelter now. Carry documents, medicines and drinking water. Do not walk through moving water.',
            self::LEVEL_YELLOW => 'Stay prepared. Pack essentials, move livestock to higher ground, and keep watching alerts.',
            default => 'No immediate risk. Keep an eye on weather updates.',
        };
    }

    /**
     * rainCategory() — mm ko IMD ke naam mein badalta hai.
     * INPUT: rainfall mm | OUTPUT: 'normal'|'heavy'|'very_heavy'|'extremely_heavy'
     * KYUN: dashboard pe "180 mm" se zyada "very heavy (IMD)" samajh aata hai, aur ye batata hai
     *       ki hamare threshold apne nahi, IMD ke hain.
     */
    public function rainCategory(float $rain): string
    {
        return match (true) {
            $rain >= self::RAIN_EXTREMELY_HEAVY_MM => 'extremely_heavy',
            $rain >= self::RAIN_VERY_HEAVY_MM => 'very_heavy',
            $rain >= self::RAIN_HEAVY_MM => 'heavy',
            default => 'normal',
        };
    }

    // ---------------------------------------------------------------------------------
    //  RIVER LEVEL PROXY  (imandaari se: ye derived hai, gauge se nahi aaya)
    // ---------------------------------------------------------------------------------

    /**
     * estimateRiverLevel() — barish se river level ka approximate proxy nikalta hai.
     *
     *  ####  YE SAAF SAAF DERIVED VALUE HAI, ASLI GAUGE READING NAHI.  ####
     *
     *  KYUN ISKI ZAROORAT PADI: hamare paas free public source se per-station live river level
     *  nahi hai (CWC ka clean public API nahi — decision D5). Rainfall milti hai (Open-Meteo),
     *  aur 2022 replay bhi rainfall hi hai. Bina level ke RiskEngine ka sabse important rule
     *  (danger mark cross) kabhi fire hi nahi karega.
     *
     *  KAISE: 3 din ka cumulative rainfall catchment ka proxy hai — nadi mein paani aane mein
     *  ek-do din lagta hai, isliye aaj ki barish se zyada "pichhle 3 din" matter karta hai.
     *
     *      span  = danger - warning                      (station ka apna operating band)
     *      level = warning - 1.2*span + span * (cum3 / 160)
     *
     *  Har station ke apne warning/danger se scale hota hai, isliye 84 m wale Neamatighat aur
     *  20 m wale Silchar dono pe sahi behave karta hai.
     *
     *  Sanity check (Assam 2022 replay, Nimatighat):
     *      15-18 June  cum3 13-134 mm  -> warning se neeche  -> GREEN
     *      19 June     cum3 ~315 mm    -> warning aur danger ke beech -> YELLOW
     *      20-22 June  cum3 ~480-575mm -> danger ke upar     -> RED
     *      23 June     cum3 ~321 mm    -> wapas YELLOW
     *      24 June se  cum3 ~135 mm    -> GREEN
     *  Yaani asli flood wave jaisa curve — chadhta hai, peak karta hai, utarta hai.
     *
     *  INPUT : warning mark (m), danger mark (m), pichhle 3 din ki total barish (mm)
     *  OUTPUT: estimated level (m), ya null agar station ke thresholds hi nahi
     *
     *  DEPLOYMENT MEIN: ye function poora hat jaayega, jagah lega CWC/state-FMIS ka asli
     *  gauge feed. Judge ko yahi bolna hai — "derived proxy for the demo, real feed plugs in here."
     */
    public function estimateRiverLevel(?float $warning_m, ?float $danger_m, float $cumulative3DayRainMm): ?float
    {
        // Thresholds hi nahi to koi proxy nahi — jhoota number banane se behtar null.
        if ($warning_m === null || $danger_m === null) {
            return null;
        }

        $span = $danger_m - $warning_m;
        if ($span <= 0) {
            $span = 1.0; // kharab seed data se bachao
        }

        // Sookhe mausam ka baseline — warning se comfortably neeche.
        $base = $warning_m - (1.2 * $span);

        // Barish se rise. 160 mm cum3 => 1 span ka rise (upar sanity check dekho).
        $rise = $span * ($cumulative3DayRainMm / 160.0);

        $level = $base + $rise;

        // Upar se cap: proxy hai, isko ridiculous height tak mat jaane do.
        $level = min($level, $danger_m + (2.5 * $span));

        return round($level, 2);
    }

    /**
     * riseRate() — paani kitni tezi se badh raha (m/ghanta).
     *
     * KYA: kal ke estimated level aur aaj ke estimated level ka farq, 24 ghante mein baanta.
     * INPUT : aaj ka level (m), pichhle din ka level (m), kitne ghante ka gap (default 24)
     * OUTPUT: m/ghanta (0 se kam nahi — ghatta paani ETA ke liye bekaar hai)
     * KYUN: "paani ~X ghante mein" ka denominator yahi hai. Alag function isliye ki
     *       live mode (Open-Meteo hourly) aur replay mode (daily) dono isko reuse karte hain.
     */
    public function riseRate(?float $levelNow, ?float $levelBefore, float $hoursBetween = 24.0): ?float
    {
        if ($levelNow === null || $levelBefore === null || $hoursBetween <= 0) {
            return null;
        }

        $rate = ($levelNow - $levelBefore) / $hoursBetween;

        return $rate > 0 ? round($rate, 4) : 0.0;
    }
}
