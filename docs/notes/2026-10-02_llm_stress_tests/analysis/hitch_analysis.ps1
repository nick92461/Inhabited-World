param(
    [string]$FrameFile,
    [string]$FirstFpsRow,      # timestamp of the first FPS.Latency row with values
    [double]$FrameLead,        # seconds the frame file starts BEFORE that first row
    [string]$LoopStart,        # stress loop start time
    [string]$ParsedXml,
    [int]$Shifts = 1500
)
Add-Type -TypeDefinition @'
using System;
public static class Perm {
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
    return new double[] { obs[0], obs[1], (ge + 1.0) / (shifts + 1.0), ratios[shifts / 2] };
  }
}
'@

$ft = [double[]](Get-Content $FrameFile | Select-Object -Skip 1 | Where-Object { $_ -match '^\d+$' })
$Tf = [datetime]::Parse($FirstFpsRow).AddSeconds(-$FrameLead)
$Ts = [datetime]::Parse($LoopStart)
$off = ($Tf - $Ts).TotalSeconds
$o = Import-Clixml $ParsedXml
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
$first = [int][math]::Ceiling($off * 10) + 1
$tot = @(0, 0, 0)
for ($b = $first; $b -lt $nb; $b++) { $tot[$state[$b]]++ }
$span = $nb - $first
"frame data covers {0:N1}s..600s of the test. time share: idle {1:N0}%  prompt-processing {2:N0}%  generating {3:N0}%" -f ($first / 10), (100 * $tot[0] / $span), (100 * $tot[1] / $span), (100 * $tot[2] / $span)
foreach ($th in 25, 33.4, 50) {
    $hit = New-Object int[] $nb
    $cum = $off
    $n = 0
    foreach ($x in $ft) {
        $cum += $x / 1e6
        if ($x / 1000 -gt $th) {
            $b = [int][math]::Floor($cum * 10)
            if ($b -ge $first -and $b -lt $nb) { $hit[$b]++; $n++ }
        }
    }
    $hh = [int[]]($hit[$first..($nb - 1)])
    $ss = [byte[]]($state[$first..($nb - 1)])
    "`n=== hitch = frame longer than $th ms   (total in window: $n) ==="
    foreach ($t in @(@('any model activity', 255), @('prompt processing', 1), @('token generation', 2))) {
        $r = [Perm]::Run($hh, $ss, [byte]$t[1], $Shifts, 11)
        $ratio = if ($r[1] -gt 0) { $r[0] / $r[1] } else { 0 }
        "{0,-20} hitches/min {1,6:N1} vs idle {2,6:N1}   ratio {3,4:N2}x   p={4:N3}   (shuffled-schedule median ratio {5:N2}x)" -f $t[0], ($r[0] * 600), ($r[1] * 600), $ratio, $r[2], $r[3]
    }
}
