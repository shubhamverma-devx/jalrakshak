<?php

namespace Database\Seeders;

use App\Models\RiverStation;
use App\Models\Village;
use App\Support\CsvReader;
use Illuminate\Database\Seeder;

/**
 * VillageSeeder — data/assam_villages.csv se ~30 Assam gaon load karta hai.
 *
 * CSV columns: name, district, lat, lng, elevation_m, population, river_station_name
 *
 * NAAM SE LINKING (ye function ka asli kaam hai):
 *  CSV mein station ka ID nahi, NAAM hai ("Neamatighat"). Kyun? Kyunki CSV insaan ne banayi hai
 *  aur insaan ID nahi likhta. To yahan naam -> id ka map banate hain.
 *
 *  DIKKAT JO MILI: assam_villages.csv kuch aise station naam reference karti hai jo
 *  river_stations.csv mein hain hi nahi (Bongaigaon, Kokrajhar, Nalbari, Baksa, Chirang,
 *  Darrang, Udalguri, Biswanath, Sonari, Hailakandi, Karimganj).
 *
 *  HUMNE KYA KIYA: un stations ko NULL warning/danger ke saath bana dete hain.
 *  KYUN aise:
 *   - Naam ke basis pe galat station se jodna (fuzzy match) khatarnaak hai — galat danger mark
 *     matlab galat alert.
 *   - Apni marzi ka threshold bana dena data fabrication hai — DATA_NOTES.md saaf mana karta hai.
 *   - NULL thresholds ke saath RiskEngine river rule SKIP karta hai aur sirf rainfall +
 *     elevation pe chalta hai, aur API response mein `river_data: false` bhejta hai.
 *     Yaani system honestly bolta hai "is station ka data nahi", jhooth nahi bolta.
 */
class VillageSeeder extends Seeder
{
    public function run(): void
    {
        $rows = CsvReader::read(CsvReader::dataPath('assam_villages.csv'));

        // Naam -> id ka lookup, memory mein. Har row pe DB query karne se bachte hain.
        // Key lowercase, taaki "neamatighat" aur "Neamatighat" ek hi maane jaayen.
        $stationIds = RiverStation::pluck('id', 'name')
            ->mapWithKeys(fn ($id, $name) => [mb_strtolower(trim($name)) => $id])
            ->all();

        $autoCreated = [];

        foreach ($rows as $row) {
            $stationName = trim((string) ($row['river_station_name'] ?? ''));
            $stationId = null;

            if ($stationName !== '') {
                $key = mb_strtolower($stationName);

                if (! isset($stationIds[$key])) {
                    // Station CSV mein nahi tha — placeholder banao, thresholds NULL (upar wali wajah).
                    $station = RiverStation::create(['name' => $stationName]);
                    $stationIds[$key] = $station->id;
                    $autoCreated[] = $stationName;
                }

                $stationId = $stationIds[$key];
            }

            Village::updateOrCreate(
                // name + district = natural key (migration mein unique bhi hai) => re-seed safe.
                ['name' => $row['name'], 'district' => $row['district']],
                [
                    'lat' => (float) $row['lat'],
                    'lng' => (float) $row['lng'],
                    'elevation_m' => (int) $row['elevation_m'],
                    'population' => (int) $row['population'],
                    'river_station_id' => $stationId,
                ]
            );
        }

        $this->command?->info('  villages seeded: '.Village::count());

        if ($autoCreated !== []) {
            // Chupchaap mat karo — build ke waqt saaf dikhna chahiye ki kis station ka threshold missing hai.
            $this->command?->warn(
                '  '.count($autoCreated).' station(s) ka threshold data nahi mila, '.
                'NULL warning/danger ke saath bane (RiskEngine inpe river rule skip karega): '.
                implode(', ', array_unique($autoCreated))
            );
        }
    }
}
