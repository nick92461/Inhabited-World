param(
    [string]$FrameFile,
    [string]$FirstFpsRow,
    [double]$FrameLead,
    [string]$LoopStart,
    [string]$ParsedXml,
    [double]$Threshold = 33.4
)
$ft = [double[]](Get-Content $FrameFile | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' })
$Tf = [datetime]::Parse($FirstFpsRow).AddSeconds(-$FrameLead)
$Ts = [datetime]::Parse($LoopStart)
$off = ($Tf - $Ts).TotalSeconds
$o = Import-Clixml $ParsedXml
$nb = 6000
$state = New-Object byte[] $nb
$genStarts = New-Object System.Collections.ArrayList
foreach ($c in $o) {
    $s = $c.t - $c.lat / 1000
    $pe = [math]::Min($c.t, $s + $c.pms / 1000)
    [void]$genStarts.Add(@{ t = $pe; end = $c.t })
    for ($b = [int][math]::Floor($s * 10); $b -lt [int][math]::Ceiling($c.t * 10); $b++) {
        if ($b -lt 0 -or $b -ge $nb) { continue }
        $mid = ($b + 0.5) / 10
        if ($mid -lt $pe) { $state[$b] = 1 } elseif ($state[$b] -eq 0) { $state[$b] = 2 }
    }
}
$hits = New-Object System.Collections.ArrayList
$cum = $off
foreach ($x in $ft) { $cum += $x / 1e6; if ($x / 1000 -gt $Threshold) { [void]$hits.Add($cum) } }
"threshold: frames > $Threshold ms; total hitches in test window: " + @($hits | Where-Object { $_ -ge ($off + 0.1) -and $_ -lt 600 }).Count

"`n--- within each phase: hitches per minute by model state ---"
foreach ($ph in @(@('steady1', [math]::Max(4, $off + 0.1), 210), @('burst', 210, 390), @('steady2', 390, 600))) {
    $b0 = [int]($ph[1] * 10); $b1 = [int]($ph[2] * 10)
    $line = "{0,-8}" -f $ph[0]
    foreach ($st in 0, 1, 2) {
        $names = @('idle', 'prompt', 'generating')
        $secs = 0.0; for ($b = $b0; $b -lt $b1; $b++) { if ($state[$b] -eq $st) { $secs += 0.1 } }
        $n = @($hits | Where-Object { $_ -ge $ph[1] -and $_ -lt $ph[2] -and $state[[int][math]::Floor($_ * 10)] -eq $st }).Count
        $rate = if ($secs -gt 0) { 60 * $n / $secs } else { 0 }
        $line += "  {0}: {1,3} in {2,5:N0}s = {3,5:N1}/min |" -f $names[$st], $n, $secs, $rate
    }
    $line
}

"`n--- aligned on the moment token generation starts (only calls with >=4 s idle before them) ---"
$starts = @($genStarts | Sort-Object { $_.t })
$edges = -6, -4, -2, 0, 2, 4, 6, 8
$counts = New-Object 'double[]' ($edges.Count - 1)
$calls = 0
foreach ($g in $starts) {
    $prevEnd = @($starts | Where-Object { $_.end -lt ($g.t - 0.0) -and $_.t -ne $g.t } | ForEach-Object { $_.end } | Measure-Object -Maximum).Maximum
    if ($g.t -lt 8 -or $g.t -gt 590) { continue }
    $calls++
    for ($i = 0; $i -lt $edges.Count - 1; $i++) {
        $a = $g.t + $edges[$i]; $b = $g.t + $edges[$i + 1]
        $counts[$i] += @($hits | Where-Object { $_ -ge $a -and $_ -lt $b }).Count
    }
}
"calls used: $calls"
for ($i = 0; $i -lt $edges.Count - 1; $i++) {
    "{0,3}..{1,2} s from generation start: {2,5:N2} hitches per call-window ({3,5:N1}/min)" -f $edges[$i], $edges[$i + 1], ($counts[$i] / $calls), (60 * $counts[$i] / ($calls * 2))
}
