<?php
/**
 * risk_grid.php — ASLI RiskEngine ko 4,320 combinations pe chala ke JSON mein dump karta hai.
 * verify_against_php.py isi output se Python port ko compare karta hai.
 * Akela mat chalao — `python verify_against_php.py` ise khud chalata hai.
 */

$REPO = dirname(dirname(__DIR__));
require $REPO . '/backend/vendor/autoload.php';
$app = require_once $REPO . '/backend/bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

$engine = new App\Services\RiskEngine();
$out = [];
$rains  = [0, 10, 40, 64.4, 64.5, 64.6, 100, 115.5, 115.6, 150, 204.5, 300];
$cum3s  = [0, 50, 150, 191, 192, 193, 250, 351, 352, 353, 500, 900];
$elevs  = [22, 60, 79, 80, 95, 186];
$pairs  = [[84.50, 85.14], [20.00, 21.83], [104.50, 105.70], [null, null], [34.00, 35.00]];

foreach ($rains as $r) foreach ($cum3s as $c) foreach ($elevs as $e) foreach ($pairs as $p) {
    [$w, $d] = $p;
    $lvl = $engine->estimateRiverLevel($w, $d, $c);
    $res = $engine->assess([
        'rainfall_mm' => $r, 'rainfall_3day_mm' => $c, 'elevation_m' => $e,
        'river_level_m' => $lvl, 'warning_level_m' => $w, 'danger_level_m' => $d,
    ]);
    $out[] = ['rain'=>$r,'cum3'=>$c,'elev'=>$e,'warn'=>$w,'danger'=>$d,
              'level_m'=>$lvl,'level'=>$res['level']];
}
file_put_contents($argv[1] ?? '/tmp/php_grid.json', json_encode($out));
echo count($out), " cases written\n";
