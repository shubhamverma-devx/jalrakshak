<?php

/**
 * =====================================================================================
 *  Console routes + SCHEDULER
 * =====================================================================================
 *  Laravel 12 mein app/Console/Kernel.php nahi hota — scheduling ab yahan hoti hai.
 *  (BUILD_PLAN "Kernel mein schedule karo" isi jagah ko keh raha hai, sirf naam badla hai.)
 *
 *  Server pe ek hi cron entry chahiye:
 *      * * * * * cd /var/www/jalrakshak/backend && php artisan schedule:run >> /dev/null 2>&1
 * =====================================================================================
 */

use Illuminate\Support\Facades\Schedule;

/**
 * Har 30 minute: Open-Meteo se nayi barish laao, risk dobara compute karo, cache refresh karo.
 *
 * KYUN 30 MIN (BUILD_PLAN section 6 + 13):
 *   - Droplet pe sirf 1GB RAM hai aur do site pehle se chal rahi hain. Har 5 min chalane ka
 *     matlab hai din mein 288 baar 30-gaon ka compute — faltu load.
 *   - Rainfall data hi itni tezi se nahi badalta. Flood ghanton mein banta hai, minton mein nahi.
 *   - 30 min ke andar warning->danger jump ho jaaye aisa bahut kam hota hai, aur us case ke
 *     liye officer manually `risk:compute` chala sakta hai ya dashboard refresh kar sakta hai.
 *
 * withoutOverlapping(): agar Open-Meteo slow ho aur ek run 30 min se zyada le le, to doosra
 * run uske upar nahi chalega — warna dono ek saath DB likhenge aur droplet ki RAM khatam.
 *
 * runInBackground(): scheduler ka main process free rehta hai.
 */
Schedule::command('risk:compute --mode=live')
    ->everyThirtyMinutes()
    ->withoutOverlapping()
    ->runInBackground();
