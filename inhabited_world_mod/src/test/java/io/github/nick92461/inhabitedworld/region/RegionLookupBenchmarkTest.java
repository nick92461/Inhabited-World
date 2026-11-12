package io.github.nick92461.inhabitedworld.region;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.nick92461.inhabitedworld.spawn.SpawnPolicy;
import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;

import net.minecraft.world.entity.EntitySpawnReason;

/*
  The cost of one region lookup and one spawn decision (canon 06 section 17.6: governance must be fast, with minimal
  allocation). Measures, after a JIT warm-up, the average time per call for positions in the wilderness, inside the
  town, and spread over the whole map, for the v0.1 layout and for a large world with many settlements; and checks
  that a million decisions allocate nothing.

  Timing assertions are deliberately loose (builds run at idle priority on two cores, user rule): the numbers are
  written to build/reports/region-lookup-benchmark.txt, and only a pathological cost fails the build.
 */
class RegionLookupBenchmarkTest {
    private static final int POSITIONS = 1 << 16;
    private static final int CALLS = 5_000_000;

    private static volatile Object sink;

    private record Positions(int[] x, int[] y, int[] z) {
        static Positions random(Random random, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            int[] x = new int[POSITIONS];
            int[] y = new int[POSITIONS];
            int[] z = new int[POSITIONS];
            for (int k = 0; k < POSITIONS; k++) {
                x[k] = minX + random.nextInt(maxX - minX + 1);
                y[k] = minY + random.nextInt(maxY - minY + 1);
                z[k] = minZ + random.nextInt(maxZ - minZ + 1);
            }
            return new Positions(x, y, z);
        }
    }

    @Test
    void lookupAndDecisionCostPerCall() throws IOException {
        Random random = new Random(20261112L);
        RegionIndex v01 = RegionIndex.of(RegionIndexTest.WILDERNESS, List.of(RegionIndexTest.TOWN, RegionIndexTest.MINE));
        RegionIndex large = RegionIndex.of(RegionIndexTest.WILDERNESS, RegionIndexTest.manyRegions(random, 300, 10240));

        RegionVolume town = RegionIndexTest.TOWN.volume();
        Positions wilderness = Positions.random(random, -512, 0, -64, 319, 0, 511);
        Positions inTown = Positions.random(random, town.minX(), town.maxX(), 40, 120, town.minZ(), town.maxZ());
        Positions map = Positions.random(random, -512, 511, -64, 319, -512, 511);
        Positions largeMap = Positions.random(random, -5120, 5119, -64, 319, -5120, 5119);

        List<String> report = new ArrayList<>();
        report.add("Region lookup and spawn decision cost (" + CALLS + " calls each, after warm-up; " + System.getProperty("java.vm.name") + " "
                + Runtime.version() + ", " + Runtime.getRuntime().availableProcessors() + " processors visible)");
        double worst = 0;
        worst = Math.max(worst, measure(report, "v0.1 lookup, wilderness chunks (fast path)", () -> lookups(v01, wilderness)));
        worst = Math.max(worst, measure(report, "v0.1 lookup, inside the town", () -> lookups(v01, inTown)));
        worst = Math.max(worst, measure(report, "v0.1 lookup, anywhere on the map", () -> lookups(v01, map)));
        worst = Math.max(worst, measure(report, "large world (" + large.regions().size() + " regions, " + large.gridCells() + " chunk columns) lookup",
                () -> lookups(large, largeMap)));
        worst = Math.max(worst, measure(report, "v0.1 full decision (lookup + policy), anywhere on the map", () -> decisions(v01, map)));
        worst = Math.max(worst, measure(report, "v0.1 full decision, inside the town (refused)", () -> decisions(v01, inTown)));

        long allocated = allocatedBy(() -> decisions(v01, map));
        report.add(String.format(Locale.ROOT, "bytes allocated by %d full decisions: %d", CALLS, allocated));

        Path file = Path.of("build", "reports", "region-lookup-benchmark.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, report, StandardCharsets.UTF_8);
        report.forEach(System.out::println);

        // A sane lookup is tens of nanoseconds; a microsecond would mean something is badly wrong (a scan, a map, boxing).
        assertTrue(worst < 1000, "a region lookup or decision costs " + worst + " ns: " + report);
        // Allowance for the measurement itself; a real allocation per call would be tens of megabytes.
        assertTrue(allocated < 64 * 1024, "spawn decisions must not allocate, but " + CALLS + " allocated " + allocated + " bytes");
    }

    private static long lookups(RegionIndex index, Positions p) {
        long hits = 0;
        int mask = POSITIONS - 1;
        for (int k = 0; k < CALLS; k++) {
            int i = k & mask;
            if (!index.regionAt(p.x()[i], p.y()[i], p.z()[i]).isWilderness()) {
                hits++;
            }
        }
        return hits;
    }

    private static long decisions(RegionIndex index, Positions p) {
        long refused = 0;
        int mask = POSITIONS - 1;
        for (int k = 0; k < CALLS; k++) {
            int i = k & mask;
            SpawnProfile profile = index.regionAt(p.x()[i], p.y()[i], p.z()[i]).profile();
            if (!SpawnPolicy.decide(true, false, true, EntitySpawnReason.NATURAL, profile).allowed()) {
                refused++;
            }
        }
        return refused;
    }

    private interface Run {
        long run();
    }

    private static double measure(List<String> report, String name, Run run) {
        for (int warm = 0; warm < 3; warm++) {
            sink = run.run();
        }
        double best = Double.MAX_VALUE;
        long result = 0;
        for (int round = 0; round < 5; round++) {
            long start = System.nanoTime();
            result = run.run();
            best = Math.min(best, (System.nanoTime() - start) / (double) CALLS);
        }
        sink = result;
        report.add(String.format(Locale.ROOT, "%-75s %6.1f ns per call (best of 5; %d of %d matched)", name, best, result, CALLS));
        return best;
    }

    private static long allocatedBy(Run run) {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        sink = run.run();
        long before = threads.getThreadAllocatedBytes(thread);
        sink = run.run();
        return threads.getThreadAllocatedBytes(thread) - before;
    }
}
