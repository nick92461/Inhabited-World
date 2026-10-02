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
$g = Import-Csv "C:\llm\stress_results\gpu_20261002_142358.csv" | ForEach-Object {
    $t = [datetime]::ParseExact($_.time, 'HH:mm:ss', $null)
    $d = [datetime]::Parse('2026-10-02 ' + $_.time)
    [pscustomobject]@{ clock = $_.time; sec = ($d - $Ts).TotalSeconds; pin = [double]$_.pages_in_per_s; ram = [double]$_.ram_free_mb; lr = [double]$_.llama_ram_mb; jr = [double]$_.java_ram_mb; cpu = [double]$_.cpu_pct }
} | Where-Object { $_.sec -ge 4 -and $_.sec -lt 600 }
"samples in test window: $($g.Count)"
$names = @('idle', 'prompt processing', 'token generation')
foreach ($st in 0, 1, 2) {
    $r = @($g | Where-Object { $state[[int][math]::Floor($_.sec * 10)] -eq $st })
    if ($r.Count) { "{0,-18} n={1,3}  pages-in/s avg {2,7:N0}  max {3,7:N0} | samples with >5,000 pages/s: {4}" -f $names[$st], $r.Count, ($r | Measure-Object pin -Average).Average, ($r | Measure-Object pin -Maximum).Maximum, @($r | Where-Object { $_.pin -gt 5000 }).Count }
}
"`n--- the 12 biggest paging spikes (hard page faults) with model state at that moment ---"
$g | Sort-Object pin -Descending | Select-Object -First 12 | ForEach-Object { "{0}  t={1,5:N0}s  pages-in/s {2,7:N0}  ram free {3,5:N0} MB  llama RAM {4,5:N0}  java RAM {5,5:N0}  state: {6}" -f $_.clock, $_.sec, $_.pin, $_.ram, $_.lr, $_.jr, $names[$state[[int][math]::Floor($_.sec * 10)]] }
"`n--- RAM over time (every ~30 s) ---"
$g | Where-Object { [int]$_.sec % 30 -lt 2 } | ForEach-Object { "{0}  free {1,5:N0} MB  llama {2,5:N0}  java {3,5:N0}  pages-in/s {4,6:N0}" -f $_.clock, $_.ram, $_.lr, $_.jr, $_.pin } | Select-Object -First 24
