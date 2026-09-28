<?php

/**
 * =====================================================================================
 *  Console routes + SCHEDULER
 * =====================================================================================
 *  Laravel 12 mein app/Console/Kernel.php nahi hota — scheduling ab yahan hoti hai.
 *  (BUILD_PLAN "Kernel mein schedule karo" isi jagah ko keh raha hai, sirf naam badla hai.)
 *
 *  Server pe ek hi cron entry chahiye (deploy/cron.d/jalrakshak — flock ke saath, taaki
 *  schedule:run khud bhi kabhi dhed na lage):
 *      * * * * * www-data flock -n /tmp/jr-schedule.lock php /var/www/jalrakshak/backend/artisan schedule:run
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
 * runInBackground() JAAN-BUJH KE NAHI: foreground mein chalne se cron ka `flock` + `timeout 25m`
 * (deploy/cron.d/jalrakshak) poore run ko dhakta hai. Background mein risk:compute un dono
 * ke bahar nikal jaata. Schedule mein aur koi task nahi, to block hona koi nuksaan nahi.
 */
Schedule::command('risk:compute --mode=live')
    ->everyThirtyMinutes()
    // 25 = lock ki expiry (minute). Default 24 GHANTE hai: agar koi run OOM-kill ho jaaye
    // (1GB droplet pe asli khatra) to lock chhoot jaata aur agle 24 ghante ek bhi run
    // nahi hota — live map chup-chaap purana. 25 min baad lock apne aap mit jaata hai,
    // aur Open-Meteo ka timeout (3 x 20s) usse kahin chhota hai, to overlap phir bhi nahi.
    ->withoutOverlapping(25);
