Add-Type -TypeDefinition @'
using System;
public static class Perm2 {
  public static double[] Eval(int[] hit, byte[] state, byte target, int shift) {
    int n = hit.Length; long hT = 0, hI = 0; int nT = 0, nI = 0;
    for (int i = 0; i < n; i++) {
      byte s = state[(i + shift) % n];
      bool isT = (target == 255) ? (s != 0) : (s == target);
      if (isT) { hT += hit[i]; nT++; } else if (s == 0) { hI += hit[i]; nI++; }
    }
    double rT = nT > 0 ? hT / (double)nT : 0.0;
    double rI = nI > 0 ? hI / (double)nI : 0.0;
    return new double[] { rT, rI, nT, nI };
  }
  public static double[] Run(int[] hit, byte[] state, byte target, int shifts, int seed) {
    int n = hit.Length; Random rnd = new Random(seed);
    double[] obs = Eval(hit, state, target, 0);
    double obsRatio = obs[1] > 0 ? obs[0] / obs[1] : 0.0;
    int ge = 0; double[] ratios = new double[shifts];
    for (int k = 0; k < shifts; k++) {
      int sh = rnd.Next(20, n - 20);
      double[] e = Eval(hit, state, target, sh);
      double r = e[1] > 0 ? e[0] / e[1] : 0.0;
      ratios[k] = r; if (r >= obsRatio) ge++;
    }
    Array.Sort(ratios);
    return new double[] { obs[0], obs[1], (ge + 1.0) / (shifts + 1.0), ratios[shifts / 2], obs[2], obs[3] };
  }
}
'@
$Tf = [datetime]::Parse('2026-10-02 14:25:15.992').AddSeconds(-6.0)
$Ts = [datetime]::Parse('2026-10-02 14:25:06.1')
$off = ($Tf - $Ts).TotalSeconds
$ft = [double[]](Get-Content "C:\Users\Nick\AppData\Local\AMD\CN\20261002-143535.FrameTime" | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' })
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
$away0 = 150; $away1 = 330
function Window($name, $ranges) {
    foreach ($th in 33.4, 50) {
        $hit = New-Object int[] $nb
        $cum = $off
        foreach ($x in $ft) { $cum += $x / 1e6; if ($x / 1000 -gt $th) { $b = [int][math]::Floor($cum * 10); if ($b -ge 0 -and $b -lt $nb) { $hit[$b]++ } } }
        $hh = New-Object System.Collections.ArrayList; $ss = New-Object System.Collections.ArrayList
        foreach ($r in $ranges) { for ($b = [int]($r[0] * 10); $b -lt [int]($r[1] * 10); $b++) { [void]$hh.Add($hit[$b]); [void]$ss.Add($state[$b]) } }
        $hArr = [int[]]$hh.ToArray([int]); $sArr = [byte[]]$ss.ToArray([byte])
        $secs = $hArr.Count / 10.0
        $tot = ($hArr | Measure-Object -Sum).Sum
        $act = @($sArr | Where-Object { $_ -ne 0 }).Count / $sArr.Count
        "{0}  hitch>{1}ms: {2} hitches in {3:N0}s ({4:N1}/min), model active {5:N0}% of the time" -f $name, $th, $tot, $secs, (60 * $tot / $secs), (100 * $act)
        foreach ($t in @(@('  generating vs idle', 2), @('  any model activity vs idle', 255))) {
            $res = [Perm2]::Run($hArr, $sArr, [byte]$t[1], 1500, 5)
            $ratio = if ($res[1] -gt 0) { $res[0] / $res[1] } else { 0 }
            "{0,-30} {1,6:N1}/min vs idle {2,6:N1}/min  ratio {3,4:N2}x  p={4:N3}" -f $t[0], ($res[0] * 600), ($res[1] * 600), $ratio, $res[2]
        }
    }
}
"=== AWAY window (~$away0-$away1 s, inferred; user stepped away, game left running) ==="
Window 'AWAY  ' @(, @($away0, $away1))
"`n=== PLAYER-ACTIVE windows (45-$away0 s and $away1-600 s; user building) ==="
Window 'ACTIVE' @(@(45, $away0), @($away1, 600))
