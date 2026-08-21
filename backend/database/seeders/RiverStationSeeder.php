<?php

namespace Database\Seeders;

use App\Models\RiverStation;
use App\Support\CsvReader;
use Illuminate\Database\Seeder;

/**
 * RiverStationSeeder — data/river_stations.csv se CWC-style gauge stations load karta hai.
 *
 * KYUN SABSE PEHLE CHALTA HAI: villages.river_station_id ka foreign key inhi rows pe point
 * karta hai. Stations pehle na hon to village linking fail ho jaayegi.
 *
 * CSV columns: name, warning_level_m, danger_level_m, notes
 * NOTE: `notes` column DB mein nahi jaata — BUILD_PLAN section 7 ke schema mein wo column
 * hai hi nahi (schema exactly follow kar rahe hain). Notes ki jaankari data/DATA_NOTES.md
 * mein rehti hai, jahan uski jagah hai.
 */
class RiverStationSeeder extends Seeder
{
    public function run(): void
    {
        $rows = CsvReader::read(CsvReader::dataPath('river_stations.csv'));

        foreach ($rows as $row) {
            $name = $row['name'] ?? null;
            if ($name === null || $name === '') {
                continue;
            }

            // updateOrCreate => seeder ko dobara chalane pe duplicate nahi banega (idempotent).
            RiverStation::updateOrCreate(
                ['name' => $name],
                [
                    // Blank cell ko 0.00 nahi, NULL maano. 0 ka matlab hota "sea level pe danger mark"
                    // jo bakwaas hai; NULL ka matlab hai "pata nahi" — aur RiskEngine NULL ko
                    // sahi tarah handle karta hai (river rule skip kar deta hai).
                    'warning_level_m' => $this->decimalOrNull($row['warning_level_m'] ?? null),
                    'danger_level_m' => $this->decimalOrNull($row['danger_level_m'] ?? null),
                    // current_level_m yahan set nahi karte — wo `php artisan risk:compute` bharta hai.
                ]
            );
        }

        $this->command?->info('  river_stations seeded: '.RiverStation::count());
    }

    /**
     * decimalOrNull() — CSV cell ko float ya null banata hai.
     * INPUT: string|null | OUTPUT: float|null
     * KYUN: khaali cell aur "0" ka matlab bilkul alag hai (upar dekho).
     */
    private function decimalOrNull(?string $value): ?float
    {
        $value = trim((string) $value);

        return ($value === '' || ! is_numeric($value)) ? null : (float) $value;
    }
}
