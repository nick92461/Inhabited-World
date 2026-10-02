$o = Import-Clixml "C:\llm\stress_results\parsed2.xml"
$Ts = [datetime]::Parse('2026-10-02 14:25:06.1')
$nb = 6000
$state = New-Object byte[] $nb
foreach ($c in $o) {
    $s = $c.t - $c.lat / 1000
    $pe = [math]::Min($c.t, $s + $c.pms / 1000)
    for ($b = [int][math]::Floor($s * 10); $b -lt [int][math]::Ceiling($c.t * 10); $b++) {
        if ($b -lt 0 -or $b -ge $nb) { continue }
        $mid = ($b + 0.5) / 10
        if ($mid -lt $pe) { $state[$b] = 1 } elseif ($state[$b] -eq 0) { $state[$b] = 2 }
    }
}
$fps = Import-Csv "C:\Users\Nick\AppData\Local\AMD\CN\FPS.Latency.20261002-143534.CSV" | Where-Object { $_.FPS -ne 'N/A' -and $_.FPS -ne '' } | ForEach-Object {
    [pscustomobject]@{ sec = ([datetime]::Parse($_.'TIME STAMP') - $Ts).TotalSeconds; fps = [double]$_.FPS }
} | Where-Object { $_.sec -ge 10 -and $_.sec -lt 600 }
"--- AMD FPS samples (vsync 120 Hz) by model state, test window only ---"
$names = @('idle', 'prompt processing', 'token generation')
foreach ($st in 0, 1, 2) {
    $r = @($fps | Where-Object { $state[[int][math]::Floor($_.sec * 10)] -eq $st })
    $v = @($r | ForEach-Object { $_.fps } | Sort-Object)
    "{0,-18} n={1,4}  avg {2,4:N0}  p5 {3,4:N0}  p1 {4,4:N0}  min {5,4:N0} | below 90: {6,5:N1}%  below 60: {7,5:N1}%  below 45: {8,5:N1}%" -f $names[$st], $v.Count, ($v | Measure-Object -Average).Average, $v[[int]($v.Count * 0.05)], $v[[int]($v.Count * 0.01)], $v[0], (100 * @($v | Where-Object { $_ -lt 90 }).Count / $v.Count), (100 * @($v | Where-Object { $_ -lt 60 }).Count / $v.Count), (100 * @($v | Where-Object { $_ -lt 45 }).Count / $v.Count)
}
"`n--- GPU/RAM log (run 2) ---"
$g = Import-Csv "C:\llm\stress_results\gpu_20261002_142358.csv" | ForEach-Object {
    [pscustomobject]@{ t = [datetime]::ParseExact($_.time, 'HH:mm:ss', $null); gpu = [double]$_.gpu_total_mb; llama = [double]$_.llama_mb; mc = [double]$_.minecraft_mb; other = [double]$_.other_mb; ram = [double]$_.ram_free_mb; cpu = [double]$_.cpu_pct; pin = [double]$_.pages_in_per_s; pgf = [double]$_.pagefile_pct; lr = [double]$_.llama_ram_mb; jr = [double]$_.java_ram_mb; ir = [double]$_.idea_ram_mb; br = [double]$_.browser_ram_mb; top = $_.top_others }
}
function A($rows, $p) { $v = @($rows | ForEach-Object { $_.$p }); [pscustomobject]@{ avg = ($v | Measure-Object -Average).Average; max = ($v | Measure-Object -Maximum).Maximum; min = ($v | Measure-Object -Minimum).Minimum } }
foreach ($sg in @(@('before test', '14:23:58', '14:25:05'), @('test', '14:25:06', '14:35:09'))) {
    $a = [datetime]::ParseExact($sg[1], 'HH:mm:ss', $null); $b = [datetime]::ParseExact($sg[2], 'HH:mm:ss', $null)
    $r = @($g | Where-Object { $_.t -ge $a -and $_.t -le $b })
    $gp = A $r 'gpu'; $ll = A $r 'llama'; $mc = A $r 'mc'; $ot = A $r 'other'; $rm = A $r 'ram'; $pi = A $r 'pin'; $pf = A $r 'pgf'; $lr = A $r 'lr'; $jr = A $r 'jr'; $ir = A $r 'ir'; $br = A $r 'br'
    "{0,-11} n={1,3} | VRAM total avg {2,5:N0} max {3,5:N0} | llama {4,5:N0} | minecraft {5,5:N0} | other {6,5:N0} | RAM free min {7,6:N0} | pages-in/s max {8,5:N0} | pagefile max {9,3:N0}% | RAM used: llama {10,5:N0} java {11,5:N0} idea {12,5:N0} browser {13,5:N0}" -f $sg[0], $r.Count, $gp.avg, $gp.max, $ll.max, $mc.max, $ot.avg, $rm.min, $pi.max, $pf.max, $lr.max, $jr.max, $ir.max, $br.max
}
"--- top GPU consumers other than llama/java (mid-test sample) ---"
($g | Where-Object { $_.t -ge [datetime]::ParseExact('14:30:00', 'HH:mm:ss', $null) } | Select-Object -First 1).top
"--- VRAM trajectory ---"
$g | Where-Object { $_.t.Second -lt 3 -and $_.t.Minute % 2 -eq 0 } | ForEach-Object { "{0:HH:mm:ss}  total {1,5:N0}  llama {2,5:N0}  mc {3,5:N0}  other {4,5:N0}  ram free {5,6:N0}  cpu {6,3:N0}%" -f $_.t, $_.gpu, $_.llama, $_.mc, $_.other, $_.ram, $_.cpu }
