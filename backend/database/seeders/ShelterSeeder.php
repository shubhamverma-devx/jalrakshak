<?php

namespace Database\Seeders;

use App\Models\Shelter;
use App\Models\Village;
use App\Support\CsvReader;
use Illuminate\Database\Seeder;

/**
 * ShelterSeeder — data/shelters.csv se relief camps load karta hai.
 *
 * CSV columns: name, lat, lng, capacity, near_village
 *
 * `near_village` -> villages.id ki linking naam se hoti hai (wahi pattern jo VillageSeeder mein).
 * Agar gaon na mile to village_id NULL rehta hai — shelter phir bhi map pe dikhega,
 * bas kisi gaon ka "nearest shelter" nahi banega. Shelter chhupa dena galat hoga; flood mein
 * ek extra camp ki jaankari bhi kaam ki hai.
 *
 * IMANDAARI (DATA_NOTES.md): ye asli towns ke paas ki representative camp locations hain.
 * Real deployment mein district administration ki actual shelter list aayegi.
 */
class ShelterSeeder extends Seeder
{
    public function run(): void
    {
        $rows = CsvReader::read(CsvReader::dataPath('shelters.csv'));

        $villageIds = Village::pluck('id', 'name')
            ->mapWithKeys(fn ($id, $name) => [mb_strtolower(trim($name)) => $id])
            ->all();

        foreach ($rows as $row) {
            $key = mb_strtolower(trim((string) ($row['near_village'] ?? '')));

            Shelter::updateOrCreate(
                ['name' => $row['name']], // camp ka naam unique maana — re-seed pe duplicate nahi
                [
                    'lat' => (float) $row['lat'],
                    'lng' => (float) $row['lng'],
                    'capacity' => (int) $row['capacity'],
                    'village_id' => $villageIds[$key] ?? null,
                ]
            );
        }

        $this->command?->info('  shelters seeded: '.Shelter::count());
    }
}
