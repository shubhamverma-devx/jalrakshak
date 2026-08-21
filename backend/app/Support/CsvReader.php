<?php

namespace App\Support;

use RuntimeException;

/**
 * CsvReader — chhota sa CSV helper, sirf seeders ke liye.
 *
 * KYA: repo ke /data folder se CSV padhta hai aur har row ko associative array banata hai
 *      (header row = keys).
 *
 * KYUN apna helper, koi package nahi:
 *   - Sirf 4 chhoti CSV padhni hain. league/csv jaisa package extra dependency + composer weight.
 *   - Droplet 1GB hai — jitna kam vendor code, utna behtar.
 *   - PHP ka fgetcsv() line-by-line padhta hai, poori file RAM mein nahi aati.
 */
final class CsvReader
{
    /**
     * read() — CSV file ko rows ke array mein badalta hai.
     *
     * INPUT : absolute file path
     * OUTPUT: array of associative arrays, jaise
     *         [['name' => 'Nimatighat', 'district' => 'Jorhat', ...], ...]
     *
     * KYUN generator nahi, seedha array: files chhoti hain (max ~360 rows), aur seeder ko
     * poora data ek saath chahiye hota hai (chunk insert ke liye).
     *
     * Kyu kya handle karta hai:
     *   - UTF-8 BOM (Excel se export ki hui files mein aata hai aur pehla header tod deta hai)
     *   - Khaali lines (skip)
     *   - Aakhri column mein bina quote ka comma (dekho mergeOverflow ka comment)
     *   - Header se KAM columns wali row (skip) — aadhi row se galat data banega
     */
    public static function read(string $path): array
    {
        if (! is_readable($path)) {
            throw new RuntimeException("CSV nahi mili ya padhi nahi ja sakti: {$path}");
        }

        $handle = fopen($path, 'r');
        if ($handle === false) {
            throw new RuntimeException("CSV khul nahi payi: {$path}");
        }

        $header = null;
        $rows = [];

        while (($line = fgetcsv($handle, 0, ',', '"', '\\')) !== false) {
            // Bilkul khaali line — fgetcsv [null] deta hai.
            if ($line === [null] || $line === []) {
                continue;
            }

            if ($header === null) {
                // Pehli line = header. BOM hataao warna pehla key "\u{FEFF}name" ban jaayega.
                $line[0] = preg_replace('/^\x{FEFF}/u', '', (string) $line[0]);
                $header = array_map(fn ($h) => trim((string) $h), $line);

                continue;
            }

            // Header se ZYADA columns => aakhri free-text field mein bina quote ka comma tha.
            // Extra tukde wapas jod do (mergeOverflow ka comment padho).
            if (count($line) > count($header)) {
                $line = self::mergeOverflow($line, count($header));
            }

            // Header se KAM columns => row sach mein corrupt hai. Chhod do (poora seed mat giraao).
            if (count($line) !== count($header)) {
                continue;
            }

            $rows[] = array_combine($header, array_map(
                fn ($v) => is_string($v) ? trim($v) : $v,
                $line
            ));
        }

        fclose($handle);

        return $rows;
    }

    /**
     * mergeOverflow() — zyada tukdon ko wapas aakhri column mein jod deta hai.
     *
     * INPUT : parsed row, header ki column count
     * OUTPUT: theek ki hui row (bilkul $expected columns)
     *
     * KYUN YE CHAHIYE (asli bug jo mila):
     *   data/river_stations.csv ki pehli data row aisi hai —
     *     Neamatighat,84.50,85.14,Brahmaputra - Jorhat (real-ish CWC style values, VERIFY before final)
     *   Aakhri `notes` field mein comma hai par wo quotes mein nahi hai. fgetcsv usko do
     *   columns samajhta hai => row ke 5 columns, header ke 4 => pehle ye row SKIP ho rahi thi.
     *
     *   Nateeja bahut bura tha: Neamatighat (Brahmaputra ka Jorhat gauge) load hi nahi hua,
     *   aur Nimatighat + Majuli — replay demo ke do main gaon — bina warning/danger threshold
     *   ke reh gaye. Yaani demo mein wo kabhi RED hote hi nahi.
     *
     * KYUN AAKHRI COLUMN MEIN JODNA SAFE HAI:
     *   Hamari saari CSV mein free-text column (notes) hamesha aakhri hai. Numeric aur ID
     *   columns usse pehle aate hain, unmein comma aa hi nahi sakta. Isliye extra tukda
     *   hamesha notes ka hi hissa hota hai.
     *
     * NOTE: CSV ko chhua nahi hai — /data repo ka shared source hai (dashboard/app bhi wahi
     * padhenge). Galat-shape data ko reader jhel le, ye zyada tikau hai.
     */
    private static function mergeOverflow(array $line, int $expected): array
    {
        $head = array_slice($line, 0, $expected - 1);
        $tail = array_slice($line, $expected - 1);

        $head[] = implode(',', $tail);

        return $head;
    }

    /**
     * dataPath() — repo ke /data folder ka path.
     *
     * INPUT : file ka naam, jaise 'assam_villages.csv'
     * OUTPUT: absolute path
     *
     * KYUN yahan: CSV backend/ ke andar nahi, repo root ke /data mein hain (BUILD_PLAN structure).
     * Wo shared data hai — dashboard/app bhi wahi reference karenge. Har seeder mein
     * '../../data' likhne se behtar hai ek jagah define karna.
     */
    public static function dataPath(string $filename): string
    {
        return dirname(base_path()).DIRECTORY_SEPARATOR.'data'.DIRECTORY_SEPARATOR.$filename;
    }
}
