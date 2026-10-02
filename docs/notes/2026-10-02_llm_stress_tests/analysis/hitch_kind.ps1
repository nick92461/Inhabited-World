$Tf = [datetime]::Parse('2026-10-02 14:25:15.992').AddSeconds(-6.0)
$Ts = [datetime]::Parse('2026-10-02 14:25:06.1')
$off = ($Tf - $Ts).TotalSeconds
$ft = [double[]](Get-Content "C:\Users\Nick\AppData\Local\AMD\CN\20261002-143535.FrameTime" | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' })
$o = Import-Clixml "C:\llm\stress_results\parsed2.xml"
$nb = 6000
# bit 1 = player-facing call in flight, bit 2 = background call in flight
$flag = New-Object byte[] $nb
foreach ($c in $o) {
    $s = $c.t - $c.lat / 1000
    $bit = if ($c.kind -eq 'BACKGROUND') { 2 } else { 1 }
    for ($b = [int][math]::Floor($s * 10); $b -lt [int][math]::Ceiling($c.t * 10); $b++) { if ($b -ge 0 -and $b -lt $nb) { $flag[$b] = $flag[$b] -bor $bit } }
}
$hit = New-Object int[] $nb
$cum = $off
foreach ($x in $ft) { $cum += $x / 1e6; if ($x / 1000 -gt 33.4) { $b = [int][math]::Floor($cum * 10); if ($b -ge 0 -and $b -lt $nb) { $hit[$b]++ } } }
$ranges = @(@(45, 150), @(330, 600))
$names = @('idle (no call)', 'player-facing only', 'background only', 'both at once')
$secs = @(0.0, 0.0, 0.0, 0.0); $cnt = @(0, 0, 0, 0)
foreach ($r in $ranges) { for ($b = [int]($r[0] * 10); $b -lt [int]($r[1] * 10); $b++) { $k = $flag[$b]; $secs[$k] += 0.1; $cnt[$k] += $hit[$b] } }
"player-active windows only (building), frames > 33.4 ms:"
for ($k = 0; $k -lt 4; $k++) { "{0,-20} {1,5:N0}s  {2,4} hitches  = {3,6:N1}/min" -f $names[$k], $secs[$k], $cnt[$k], $(if ($secs[$k] -gt 0) { 60 * $cnt[$k] / $secs[$k] } else { 0 }) }
"`nsame for the away window (150-330 s):"
$secs = @(0.0, 0.0, 0.0, 0.0); $cnt = @(0, 0, 0, 0)
for ($b = 1500; $b -lt 3300; $b++) { $k = $flag[$b]; $secs[$k] += 0.1; $cnt[$k] += $hit[$b] }
for ($k = 0; $k -lt 4; $k++) { "{0,-20} {1,5:N0}s  {2,4} hitches  = {3,6:N1}/min" -f $names[$k], $secs[$k], $cnt[$k], $(if ($secs[$k] -gt 0) { 60 * $cnt[$k] / $secs[$k] } else { 0 }) }
