"""
risk_rules.py — RiskEngine.php ka Python port (SIRF label banane ke liye).

============================ YE FILE KYUN HAI ============================
B2 ka target label "asli flood hua ya nahi" NAHI hai — wo data hamare paas hai hi
nahi. Target ye hai: "HAMARA RiskEngine us din kya bolta". Model hamari apni risk
definition ko aage ke waqt mein le jaana seekh raha hai.

Isliye ye port PHP ke saath BILKUL match hona chahiye. Ek chhota sa farq bhi matlab
ye ki model kuch aur seekh raha hai aur dashboard kuch aur dikha raha hai.
`verify_against_php.py` dono ko 20,000 combinations pe chala ke compare karta hai.

SOURCE OF TRUTH: backend/app/Services/RiskEngine.php — ye uski copy hai, uska
replacement nahi. PHP badle to ye bhi badlo aur verify dobara chalao.

============================ EK ZAROORI BAAT ============================
River level ASLI GAUGE READING NAHI hai (decision D9). Wo 3-din ke cumulative
rainfall se DERIVE hota hai. Iska seedha matlab:

    label = f(rainfall)  — poori tarah, bina kisi hidden hydrology ke

Matlab B2 asal mein "aage ki BARISH ka anumaan lagao, phir rule laga do" hai.
Ye FORECAST_README.md mein saaf likha hai — chhupane wali baat nahi hai.
"""

from decimal import Decimal, ROUND_HALF_UP


def php_round(x, places=2):
    """
    PHP ka round() — Python ke round() se ALAG hai, aur ye farq yahan maayne rakhta hai.

    Python "banker's rounding" karta hai (0.5 ko NAZDEEKI EVEN pe le jaata hai):
        round(35.925, 2) -> 35.92
    PHP aadhe ko HAMESHA zero se door le jaata hai:
        round(35.925, 2) -> 35.93

    Ek paisa bhar ka farq lagta hai, par river level ko warning/danger mark se COMPARE
    kiya jaata hai. 35.92 vs 35.93 ka matlab ho sakta hai "danger cross hua ya nahi" —
    yaani poora label badal jaata hai. Pehli verification run mein isi wajah se 4,320
    mein se 253 label PHP se alag nikle the.
    """
    return float(Decimal(repr(x)).quantize(Decimal("1e-%d" % places), rounding=ROUND_HALF_UP))


# --- IMD ke official thresholds (RiskEngine.php se hu-ba-hu) ---------------------
RAIN_HEAVY_MM = 64.5
RAIN_VERY_HEAVY_MM = 115.6
RAIN_EXTREMELY_HEAVY_MM = 204.5
LOW_ELEVATION_M = 80

GREEN, YELLOW, RED = "green", "yellow", "red"
LEVELS = [GREEN, YELLOW, RED]          # index 0,1,2 — model ka class order
LEVEL_IDX = {l: i for i, l in enumerate(LEVELS)}


def estimate_river_level(warning_m, danger_m, cum3_mm):
    """
    RiskEngine::estimateRiverLevel() ka port — river level ka DERIVED proxy.

    INPUT : warning_m, danger_m (None ho sakte hain), cum3_mm (3 din ka total, aaj samet)
    OUTPUT: level in metres, ya None agar thresholds hi nahi

    Ye asli gauge feed NAHI hai (D9). Deployment mein CWC/state-FMIS ka asli feed lagega.
    """
    if warning_m is None or danger_m is None:
        return None

    span = danger_m - warning_m
    if span <= 0:
        span = 1.0

    base = warning_m - (1.2 * span)      # sookhe mausam ka baseline
    rise = span * (cum3_mm / 160.0)      # 160 mm cum3 => 1 span ka rise
    level = base + rise
    level = min(level, danger_m + (2.5 * span))   # proxy hai, cap laga do
    return php_round(level, 2)                    # PHP jaisa rounding — upar dekho, ye zaroori hai


def decide_level(rain_mm, elevation_m, level_m, warning_m, danger_m):
    """
    RiskEngine::decideLevel() ka port. OUTPUT: 'green' | 'yellow' | 'red'

    Rules (order maayne rakhta hai):
      1. river >= danger                                  -> RED
      2. river >= warning  (+ very heavy rain + low elev) -> RED, warna YELLOW
      3. rain > heavy AND low elevation                   -> YELLOW
      4. baaki sab                                        -> GREEN
    """
    has_river = level_m is not None and warning_m is not None and danger_m is not None
    # PHP: `$elevation <= self::LOW_ELEVATION_M` — INCLUSIVE hai.
    # 80 m wala gaon bhi "neecha" ginta hai. `<` likhne se 4,320 mein se 253 label
    # PHP se alag nikle the.
    is_low = elevation_m <= LOW_ELEVATION_M

    if has_river and level_m >= danger_m:
        return RED

    if has_river and level_m >= warning_m:
        if rain_mm >= RAIN_VERY_HEAVY_MM and is_low:
            return RED
        return YELLOW

    if rain_mm > RAIN_HEAVY_MM and is_low:
        return YELLOW

    return GREEN


def assess(rain_mm, cum3_mm, elevation_m, warning_m, danger_m):
    """
    Ek din, ek gaon -> risk level. Yehi function label banata hai.

    INPUT : aaj ki barish, 3-din ka total (aaj samet), gaon ki elevation, thresholds
    OUTPUT: (level, level_m)  — level_m None ho sakta hai
    """
    level_m = estimate_river_level(warning_m, danger_m, cum3_mm)
    return decide_level(rain_mm, elevation_m, level_m, warning_m, danger_m), level_m


def rain_category(rain_mm):
    """IMD ki barish ki shreni — README/UI mein dikhane ke liye."""
    if rain_mm >= RAIN_EXTREMELY_HEAVY_MM:
        return "extremely_heavy"
    if rain_mm >= RAIN_VERY_HEAVY_MM:
        return "very_heavy"
    if rain_mm >= RAIN_HEAVY_MM:
        return "heavy"
    return "normal"


# --- Rule ko ulta karke dekho: cum3 ka wo number jahan level badalta hai -----------
# Bina rounding ke, algebra se:
#   river >= warning  <=>  warning - 1.2*span + span*cum3/160 >= warning  <=>  cum3 >= 192
#   river >= danger   <=>  ...                >= warning + span          <=>  cum3 >= 352
#
# Ye do number poore B2 ki jaan hain: label kisi chhupi hui hydrology se nahi, SIRF
# 3-din ke cumulative rainfall se banta hai. Matlab B2 ka asli kaam hai "aage ki barish
# ka anumaan lagao" — FORECAST_README.md mein ye saaf likha hai.
#
# NOTE: level 2 decimal pe round hota hai, isliye asli boundary in numbers se baal-baraabar
# idhar-udhar ho sakti hai (span chhota ho to thoda zyada). Ye numbers samajhne ke liye
# hain — LABEL hamesha assess() se banta hai, in constants se nahi.
CUM3_WARNING_MM = 192.0
CUM3_DANGER_MM = 352.0
