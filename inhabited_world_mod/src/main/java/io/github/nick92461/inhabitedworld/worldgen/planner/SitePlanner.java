package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

/*
  The deterministic world planner (canon 02 section 6): from the world seed and the generator's terrain, before any
  chunk exists, it chooses where every required site goes. No AI is involved (02 section 6, 01 section 2.2).

  1. Candidates. Every chunk-aligned position whose semantic region fits inside the playable area (minus the edge
     margin) is a candidate for a site. Each is measured on the terrain lattice (SiteEvaluator) and kept if its
     terrain meets the site's limits, it keeps the spawn clearance, and (for a waterfront site) one of its sides
     faces open sea.
  2. Choice. Candidates are ranked by score (flat, dry, low ground). The seed picks the order among the near-best
     (within POOL_SLACK of the best, at most POOL_SIZE), so two seeds with similar terrain still get different
     places; the rest follow by score. Positions and the relationship between sites therefore vary by seed
     (02 section 6), and the same seed always gives the same plan.
  3. Joint placement. Sites are placed in request order; each later site must not overlap earlier ones and must
     keep the spacing rules. If a later site has no candidate left, the earlier site moves to its next candidate at
     least RETRY_SPACING blocks from the ones that failed, so retries spread over the map (bounded backtracking,
     MAX_TRIES per site).
  4. Fallback. If no plan exists, the rules are relaxed step by step, and the step used is recorded:
       0 strict          every rule as asked
       1 looser terrain  more water, relief, height range and mountain allowed
       2 looser layout   PREFERRED waterfronts dropped, wilderness gap halved, distance cap and spawn clearance
                         eased, terrain loosened once more
       3 last resort     only "inside the map, not overlapping": the best-scoring ground wins
     Step 3 always succeeds when the sites fit in the map at all, so the town and the POI always exist (02 section
     6); a request that can't fit even then is a configuration error.

  Changing anything here or in SiteEvaluator changes which sites a seed gets: raise VERSION. Existing worlds keep
  the plan they were made with (02 section 7).
 */
public final class SitePlanner {
    public static final Identifier ALGORITHM = InhabitedWorld.id("site_planner");
    public static final int VERSION = 1;

    public static final int STRICT = 0;
    public static final int LOOSER_TERRAIN = 1;
    public static final int LOOSER_LAYOUT = 2;
    public static final int LAST_RESORT = 3;

    /** Seeded choice among candidates scoring within this much of the best. */
    static final double POOL_SLACK = 8.0;
    static final int POOL_SIZE = 32;
    /** Candidates tried per site before the site before it moves on. */
    static final int MAX_TRIES = 48;
    /** After a candidate fails, candidates whose centres lie within this many blocks of it are not tried. */
    static final int RETRY_SPACING = 64;

    /** Where vanilla's spawn search starts (the middle of its origin chunk). */
    public record SpawnOrigin(int x, int z) {
    }

    public record Outcome(List<PlannedSite> sites, PlannerRun run) {
    }

    private SitePlanner() {
    }

    public static String relaxationName(int level) {
        return switch (level) {
            case STRICT -> "strict";
            case LOOSER_TERRAIN -> "looser terrain";
            case LOOSER_LAYOUT -> "looser layout";
            case LAST_RESORT -> "last resort";
            default -> "unknown";
        };
    }

    /** The grid area the planner needs sampled for a request: the playable area plus room for waterfront checks. */
    public static BlockRect samplingArea(PlanRequest request) {
        BlockRect map = new BlockRect(request.area().minBlockX(), request.area().minBlockZ(), request.area().maxBlockX(), request.area().maxBlockZ());
        return map.inflate(SiteEvaluator.WATERFRONT_DEPTH + TerrainGrid.STEP);
    }

    public static Outcome plan(PlanRequest request, TerrainGrid grid, long seed, @Nullable SpawnOrigin spawn) {
        if (!grid.bounds().contains(samplingArea(request))) {
            throw new IllegalArgumentException("the terrain grid " + grid.bounds() + " must cover " + samplingArea(request));
        }
        int judged = 0;
        for (int level = STRICT; level <= LAST_RESORT; level++) {
            PlanRequest relaxed = relax(request, level);
            List<List<Candidate>> orders = new ArrayList<>();
            for (SiteRequirement site : relaxed.sites()) {
                Ranked ranked = rank(relaxed, site, grid, seed, level, spawn);
                judged += ranked.judged();
                orders.add(ranked.order());
            }
            List<Candidate> chosen = new ArrayList<>();
            if (place(relaxed, orders, 0, chosen)) {
                List<PlannedSite> sites = new ArrayList<>();
                for (int s = 0; s < chosen.size(); s++) {
                    sites.add(chosen.get(s).resolve(relaxed.sites().get(s)));
                }
                return new Outcome(List.copyOf(sites), new PlannerRun(ALGORITHM, VERSION, level, judged));
            }
        }
        throw new IllegalStateException("the requested sites do not fit in the playable area " + request.area() + " even without terrain rules");
    }

    /** The request as the given fallback step relaxes it. */
    public static PlanRequest relax(PlanRequest request, int level) {
        if (level == STRICT) {
            return request;
        }
        List<SiteRequirement> sites = new ArrayList<>();
        for (SiteRequirement site : request.sites()) {
            SiteRules rules = site.rules();
            if (level >= LOOSER_TERRAIN) {
                rules = rules.withLimits(rules.limits().relaxed(0.10, 12, 8, 0.15));
            }
            if (level >= LOOSER_LAYOUT) {
                rules = rules.withLimits(rules.limits().relaxed(0.10, 12, 8, 0.15));
                if (rules.waterfront() == Waterfront.PREFERRED) {
                    rules = rules.withWaterfront(Waterfront.NONE);
                }
                rules = rules.withSpawnClearance(rules.spawnClearance() / 2);
            }
            if (level >= LAST_RESORT) {
                rules = new SiteRules(rules.footprint(), Waterfront.NONE, TerrainLimits.ANY, 0);
            }
            sites.add(site.withRules(rules));
        }
        List<SiteSpacing> spacings = new ArrayList<>();
        for (SiteSpacing spacing : request.spacings()) {
            if (level >= LAST_RESORT) {
                spacings.add(spacing.relaxed(0, Integer.MAX_VALUE));
            } else if (level >= LOOSER_LAYOUT) {
                spacings.add(spacing.relaxed(spacing.minGap() / 2, (int) Math.min(Integer.MAX_VALUE, spacing.maxCenterDistance() * 5L / 4)));
            } else {
                spacings.add(spacing);
            }
        }
        return request.with(sites, spacings);
    }

    private record Candidate(BlockRect bounds, BlockRect region, @Nullable Direction waterfront, SiteMetrics metrics, double score) {
        PlannedSite resolve(SiteRequirement site) {
            return new PlannedSite(site.id(), site.kind(), site.catalogId(), site.rules(), this.bounds, this.region,
                    Optional.ofNullable(this.waterfront), this.metrics);
        }
    }

    private record Ranked(List<Candidate> order, int judged) {
    }

    private static final Comparator<Candidate> BY_SCORE = Comparator.comparingDouble(Candidate::score)
            .thenComparingInt(c -> c.bounds().minZ())
            .thenComparingInt(c -> c.bounds().minX());

    // Every acceptable candidate for one site, in the order the planner tries them.
    private static Ranked rank(PlanRequest request, SiteRequirement site, TerrainGrid grid, long seed, int level, @Nullable SpawnOrigin spawn) {
        SiteRules rules = site.rules();
        Footprint footprint = rules.footprint();
        BlockRect usable = request.usableArea();
        int step = TerrainGrid.STEP;
        int firstX = ceilTo(usable.minX() + footprint.blend(), step);
        int firstZ = ceilTo(usable.minZ() + footprint.blend(), step);
        int lastX = usable.maxX() - footprint.blend() - footprint.sizeX() + 1;
        int lastZ = usable.maxZ() - footprint.blend() - footprint.sizeZ() + 1;

        List<Candidate> accepted = new ArrayList<>();
        int judged = 0;
        for (int z = firstZ; z <= lastZ; z += step) {
            for (int x = firstX; x <= lastX; x += step) {
                BlockRect bounds = BlockRect.ofSize(x, z, footprint.sizeX(), footprint.sizeZ());
                BlockRect region = bounds.inflate(footprint.blend());
                judged++;
                if (spawn != null && rules.spawnClearance() > 0 && region.distanceTo(spawn.x() + 0.5, spawn.z() + 0.5) < rules.spawnClearance()) {
                    continue;
                }
                Candidate best = null;
                if (rules.waterfront() == Waterfront.NONE) {
                    SiteMetrics metrics = SiteEvaluator.measure(grid, bounds, null);
                    if (SiteEvaluator.meets(metrics, rules.limits())) {
                        best = new Candidate(bounds, region, null, metrics, SiteEvaluator.score(metrics));
                    }
                } else {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        SiteMetrics metrics = SiteEvaluator.measure(grid, bounds, side);
                        if (SiteEvaluator.meets(metrics, rules.limits())) {
                            Candidate candidate = new Candidate(bounds, region, side, metrics, SiteEvaluator.score(metrics));
                            if (best == null || candidate.score() < best.score()) {
                                best = candidate;
                            }
                        }
                    }
                }
                if (best != null) {
                    accepted.add(best);
                }
            }
        }

        accepted.sort(BY_SCORE);
        int pool = 0;
        if (!accepted.isEmpty()) {
            double cutoff = accepted.getFirst().score() + POOL_SLACK;
            while (pool < accepted.size() && pool < POOL_SIZE && accepted.get(pool).score() <= cutoff) {
                pool++;
            }
        }
        // Fisher-Yates over the near-best with java.util.Random, whose sequence is fixed by its specification, so the
        // same seed gives the same order on every machine and Java version.
        Random random = new Random(mix(seed, site.id(), level));
        for (int k = pool - 1; k > 0; k--) {
            int other = random.nextInt(k + 1);
            Candidate swap = accepted.get(k);
            accepted.set(k, accepted.get(other));
            accepted.set(other, swap);
        }
        return new Ranked(accepted, judged);
    }

    private static boolean place(PlanRequest request, List<List<Candidate>> orders, int index, List<Candidate> chosen) {
        if (index == orders.size()) {
            return true;
        }
        SiteRequirement site = request.sites().get(index);
        List<Candidate> failed = new ArrayList<>();
        for (Candidate candidate : orders.get(index)) {
            if (!fits(request, site, candidate, chosen) || nearAny(candidate, failed)) {
                continue;
            }
            chosen.add(candidate);
            if (place(request, orders, index + 1, chosen)) {
                return true;
            }
            chosen.removeLast();
            failed.add(candidate);
            if (failed.size() >= MAX_TRIES) {
                return false;
            }
        }
        return false;
    }

    // A candidate next to one whose later sites failed would fail the same way: the next try moves elsewhere.
    private static boolean nearAny(Candidate candidate, List<Candidate> failed) {
        for (Candidate other : failed) {
            if (candidate.bounds().centerDistanceTo(other.bounds()) < RETRY_SPACING) {
                return true;
            }
        }
        return false;
    }

    // Whether a candidate keeps clear of, and keeps the spacing rules with, the sites already placed.
    private static boolean fits(PlanRequest request, SiteRequirement site, Candidate candidate, List<Candidate> chosen) {
        for (int p = 0; p < chosen.size(); p++) {
            Candidate other = chosen.get(p);
            if (candidate.region().intersects(other.region())) {
                return false;
            }
            String otherId = request.sites().get(p).id();
            for (SiteSpacing spacing : request.spacings()) {
                if (spacing.concerns(site.id(), otherId)) {
                    if (candidate.region().gapTo(other.region()) < spacing.minGap()
                            || candidate.bounds().centerDistanceTo(other.bounds()) > spacing.maxCenterDistance()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static long mix(long seed, String siteId, int level) {
        long h = seed ^ (siteId.hashCode() * 0x9E3779B97F4A7C15L) ^ ((level + 1) * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 29);
    }

    private static int ceilTo(int value, int step) {
        return Math.floorDiv(value + step - 1, step) * step;
    }
}
