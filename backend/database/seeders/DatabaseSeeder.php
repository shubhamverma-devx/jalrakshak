<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;

/**
 * DatabaseSeeder — saara Assam demo data ek command mein: `php artisan db:seed`
 *
 * ORDER MAAYNE RAKHTA HAI (foreign keys ki wajah se):
 *   1. RiverStationSeeder    — villages.river_station_id inhi pe point karta hai
 *   2. VillageSeeder         — baaki sab villages pe point karte hain
 *   3. RainfallReplaySeeder  — village_id chahiye
 *   4. ShelterSeeder         — village_id chahiye
 *
 * Saare seeders updateOrCreate/upsert use karte hain => dobara chalao to duplicate nahi banega,
 * data bas refresh ho jaayega. Development mein baar-baar `migrate:fresh` karna nahi padta.
 *
 * NOTE: risk_scores yahan seed NAHI hota. Wo RiskEngine ka output hai —
 * `php artisan risk:compute` se banta hai. Seeded risk fake risk hota, aur poore project ka
 * point hi yahi hai ki risk asli rules se nikle.
 */
class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        $this->command?->info('JalRakshak seed — Assam demo data (representative, DATA_NOTES.md dekho)');

        $this->call([
            RiverStationSeeder::class,
            VillageSeeder::class,
            RainfallReplaySeeder::class,
            ShelterSeeder::class,
        ]);

        $this->command?->info('Seed poora. Ab chalao: php artisan risk:compute --mode=replay --day=5');
    }
}
