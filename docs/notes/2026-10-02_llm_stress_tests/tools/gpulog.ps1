# gpulog.ps1 - samples GPU memory (per process), system RAM, paging and CPU every few seconds. Run in its own window.
param(
    [int]$IntervalSec = 2,
    [int]$Seconds = 0,
    [string]$Out = ("C:\llm\stress_results\gpu_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv")
)
New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
"time,gpu_total_mb,llama_mb,minecraft_mb,other_mb,ram_free_mb,cpu_pct,pages_in_per_s,pagefile_pct,llama_ram_mb,java_ram_mb,idea_ram_mb,browser_ram_mb,top_others" | Set-Content $Out
Write-Host "Logging to $Out  (Ctrl+C to stop)"
$paths = @(
    '\GPU Adapter Memory(*)\Dedicated Usage',
    '\GPU Process Memory(*)\Dedicated Usage',
    '\Memory\Available MBytes',
    '\Processor(_Total)\% Processor Time',
    '\Memory\Pages Input/sec',
    '\Paging File(_Total)\% Usage'
)
function WS([string[]]$names) {
    $sum = (@(Get-Process -Name $names -ErrorAction SilentlyContinue) | Measure-Object WorkingSet64 -Sum).Sum
    return [math]::Round($sum / 1MB)
}
$start = Get-Date
while ($true) {
    $cs = (Get-Counter -Counter $paths -ErrorAction SilentlyContinue).CounterSamples
    $adapter = $cs | Where-Object { $_.Path -like '*gpu adapter memory*' } | Sort-Object CookedValue -Descending | Select-Object -First 1
    $by = @{}
    foreach ($s in ($cs | Where-Object { $_.Path -like '*gpu process memory*' })) {
        if ($s.InstanceName -match 'pid_(\d+)_') {
            $p = Get-Process -Id ([int]$Matches[1]) -ErrorAction SilentlyContinue
            $name = if ($p) { $p.ProcessName } else { "pid" + $Matches[1] }
            if (-not $by.ContainsKey($name)) { $by[$name] = 0.0 }
            $by[$name] += $s.CookedValue
        }
    }
    $llama = 0.0; $mc = 0.0
    if ($by.ContainsKey('llama-server')) { $llama = $by['llama-server'] }
    if ($by.ContainsKey('java')) { $mc = $by['java'] }
    $others = $by.GetEnumerator() | Where-Object { $_.Key -ne 'llama-server' -and $_.Key -ne 'java' -and $_.Value -gt 30MB } | Sort-Object Value -Descending
    $otherSum = ($others | Measure-Object Value -Sum).Sum
    $top = (($others | Select-Object -First 5 | ForEach-Object { "{0}={1}" -f $_.Key, [math]::Round($_.Value / 1MB) }) -join ' ')
    $ram = ($cs | Where-Object { $_.Path -like '*available mbytes*' }).CookedValue
    $cpu = ($cs | Where-Object { $_.Path -like '*processor time*' }).CookedValue
    $pin = ($cs | Where-Object { $_.Path -like '*pages input/sec*' }).CookedValue
    $pgf = ($cs | Where-Object { $_.Path -like '*paging file*' }).CookedValue
    $line = "{0},{1},{2},{3},{4},{5},{6},{7},{8},{9},{10},{11},{12},{13}" -f (Get-Date -Format "HH:mm:ss"), [math]::Round($adapter.CookedValue / 1MB), [math]::Round($llama / 1MB), [math]::Round($mc / 1MB), [math]::Round($otherSum / 1MB), [math]::Round($ram), [math]::Round($cpu), [math]::Round($pin), [math]::Round($pgf), (WS 'llama-server'), (WS 'java'), (WS 'idea64'), (WS 'firefox', 'chrome', 'msedge'), $top
    Add-Content $Out $line
    Write-Host $line
    if ($Seconds -gt 0 -and ((Get-Date) - $start).TotalSeconds -ge $Seconds) { break }
    Start-Sleep -Seconds $IntervalSec
}
