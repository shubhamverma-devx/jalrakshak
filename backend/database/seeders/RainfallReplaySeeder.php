<?php

namespace Database\Seeders;

use App\Models\Rainfall;
use App\Models\Village;
use App\Support\CsvReader;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;

/**
 * RainfallReplaySeeder — data/assam_2022_replay.csv se June-2022 Assam flood ka replay load karta hai.
 *
 * CSV columns: village_name, river_station_name, date, rainfall_mm, source
 * Range: 2022-06-15 se 2022-06-26 (12 din), 30 gaon = 360 readings.
 *
 * KYUN REPLAY CHAHIYE (decision D6):
 *  Demo ke din Open-Meteo se saamanya barish hi aayegi — poora map GREEN. Judge ko flood
 *  dikhana hai to koi high-intensity scenario chahiye. 2022 ka Assam flood real tha, isliye
 *  wahi window replay karte hain — date-slider ghumao, paani chadhta hai, gaon RED hote hain.
 *
 *  IMANDAARI (data/DATA_NOTES.md): ye numbers "realistic-shaped" hain, official IMD readings
 *  NAHI. Judge ko exactly yehi bolna hai — "2022 Assam flood period ka representative replay,
 *  real feeds deployment mein plug honge." Kabhi mat bolna "ye official govt data hai."
 *
 *  river_station_name column DB mein nahi jaata — station ka rishta pehle hi
 *  villages.river_station_id mein set ho chuka hai (VillageSeeder). Wahi single source of truth.
 */
class RainfallReplaySeeder extends Seeder
{
    public function run(): void
    {
        $rows = CsvReader::read(CsvReader::dataPath('assam_2022_replay.csv'));

        // Village naam -> id map, ek hi baar. 360 rows pe 360 query karna bewakoofi hai.
        $villageIds = Village::pluck('id', 'name')
            ->mapWithKeys(fn ($id, $name) => [mb_strtolower(trim($name)) => $id])
            ->all();

        $records = [];
        $skipped = [];

        foreach ($rows as $row) {
            $key = mb_strtolower(trim((string) ($row['village_name'] ?? '')));

            if (! isset($villageIds[$key])) {
                // Aisa gaon jo villages.csv mein hai hi nahi — chhod do, par report karo.
                $skipped[] = $row['village_name'] ?? '(khaali)';

                continue;
            }

            $records[] = [
                'village_id' => $villageIds[$key],
                'rainfall_mm' => (float) ($row['rainfall_mm'] ?? 0),
                // CSV ka source column bharosa nahi — ye seeder BY DEFINITION replay data daalta hai.
                'source' => Rainfall::SOURCE_REPLAY,
                // Date ko din ki shuruaat pe fix karte hain (00:00), taaki replay day lookup exact match ho.
                'recorded_at' => $row['date'].' 00:00:00',
            ];
        }

        // upsert() = ek hi query mein sab. 360 alag INSERT droplet (1GB) pe faltu load hai.
        // Unique key (village_id, source, recorded_at) pe conflict hone par rainfall_mm update ho jaayega
        // => seeder dobara chalane pe duplicate nahi, sirf refresh.
        foreach (array_chunk($records, 200) as $chunk) {
            DB::table('rainfall')->upsert(
                $chunk,
                ['village_id', 'source', 'recorded_at'], // conflict target
                ['rainfall_mm']                          // conflict pe kya update ho
            );
        }

        $this->command?->info('  rainfall (replay) seeded: '.Rainfall::source(Rainfall::SOURCE_REPLAY)->count().' readings');

        if ($skipped !== []) {
            $this->command?->warn('  replay rows skip hui (village match nahi hua): '.implode(', ', array_unique($skipped)));
        }
    }
}
