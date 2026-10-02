$FrameFile = "C:\Users\Nick\AppData\Local\AMD\CN\20261002-143535.FrameTime"
$Tf = [datetime]::Parse('2026-10-02 14:25:15.992').AddSeconds(-6.0)
$Ts = [datetime]::Parse('2026-10-02 14:25:06.1')
$off = ($Tf - $Ts).TotalSeconds
$ft = [double[]](Get-Content $FrameFile | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' })
$o = Import-Clixml "C:\llm\stress_results\parsed2.xml"
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
# per 15 s bin: frames, hitches>33.4ms, hitches>50ms, mean frame ms, share of time model active
$bin = 15
$nbins = 40
$fr = New-Object 'int[]' $nbins; $h33 = New-Object 'int[]' $nbins; $h50 = New-Object 'int[]' $nbins; $sumMs = New-Object 'double[]' $nbins
$cum = $off
foreach ($x in $ft) {
    $cum += $x / 1e6
    $k = [int][math]::Floor($cum / $bin)
    if ($k -lt 0 -or $k -ge $nbins) { continue }
    $fr[$k]++; $sumMs[$k] += $x / 1000
    if ($x / 1000 -gt 33.4) { $h33[$k]++ }
    if ($x / 1000 -gt 50) { $h50[$k]++ }
}
$g = Import-Csv "C:\llm\stress_results\gpu_20261002_142358.csv" | ForEach-Object {
    [pscustomobject]@{ sec = ([datetime]::Parse('2026-10-02 ' + $_.time) - $Ts).TotalSeconds; cpu = [double]$_.cpu_pct; mc = [double]$_.minecraft_mb; jr = [double]$_.java_ram_mb }
}
$hw = Import-Csv "C:\Users\Nick\AppData\Local\AMD\CN\Hardware.20261002-143535.CSV" | Where-Object { $_.'TIME STAMP' -ne 'N/A' } | ForEach-Object {
    [pscustomobject]@{ sec = ([datetime]::Parse($_.'TIME STAMP') - $Ts).TotalSeconds; gpu = [double]$_.'GPU UTIL'; cpu = [double]$_.'CPU UTIL' }
}
"bin(s)   frames  avgFPS  >33ms  >50ms  model-active  gpu%  cpu%   (15-second bins from loop start)"
for ($k = 0; $k -lt $nbins; $k++) {
    $b0 = $k * $bin * 10; $b1 = [math]::Min($nb, ($k + 1) * $bin * 10)
    $act = 0; for ($b = $b0; $b -lt $b1; $b++) { if ($state[$b] -ne 0) { $act++ } }
    $a = $k * $bin; $z = ($k + 1) * $bin
    $hr = @($hw | Where-Object { $_.sec -ge $a -and $_.sec -lt $z })
    $gm = if ($hr.Count) { ($hr | Measure-Object gpu -Average).Average } else { 0 }
    $cm = if ($hr.Count) { ($hr | Measure-Object cpu -Average).Average } else { 0 }
    $fps = if ($sumMs[$k] -gt 0) { 1000 * $fr[$k] / $sumMs[$k] } else { 0 }
    "{0,3}-{1,3}  {2,6}  {3,6:N0}  {4,5}  {5,5}  {6,6:N0}%      {7,4:N0}  {8,4:N0}" -f $a, $z, $fr[$k], $fps, $h33[$k], $h50[$k], (100 * $act / ($b1 - $b0)), $gm, $cm
}
