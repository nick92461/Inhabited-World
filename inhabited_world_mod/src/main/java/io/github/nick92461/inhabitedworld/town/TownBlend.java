package io.github.nick92461.inhabitedworld.town;

import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;

/*
  The ground height of every blend column around a placed town (export report section 2: "Generated terrain only
  meets it at the box's east edge, where Phase 3 blends the seabed"; plan: "generated terrain blended up to the
  kept-terrain mask edge").

  Blend columns are the town's own BLEND columns inside its box plus the border around it (the site's region minus
  its bounds). Their new ground is the harmonic interpolation (a discrete Laplace equation, solved by successive
  over-relaxation) between two fixed edges:
    - inside: the ground of every stored town column (TownColumns.ground: the terrain under a building, a street's
      ground, the seabed under the town's sea)
    - outside: the generated ground one block beyond the region (never changed by placement)
  A harmonic surface has no peaks or pits of its own (every value lies between its neighbours'), so it can't make a
  cliff or a wall the edges don't already have. It follows the Harbour's ~30-block climb from quay to back street
  along the edge, and it blends seabed to seabed at the sea side the same way land meets land.

  Everything here is plain arrays, so the result depends only on the town's data and the ring of generated ground
  around the region: placing again after a restart computes exactly the same heights (TownPlacement relies on
  that). Unit-tested in TownBlendTest.
 */
public final class TownBlend {
    /** Columns fixed by an edge (a stored town column, or the generated ring outside the region). */
    public static final byte FIXED = 0;
    /** Columns whose ground the blend chooses. */
    public static final byte FREE = 1;
    /** Columns that are neither: a free neighbour ignores them. */
    public static final byte NONE = 2;

    static final double OMEGA = 1.85;
    static final double TOLERANCE = 0.005;
    static final int MAX_ITERATIONS = 20_000;

    /** The solved heights over a grid, and how the solve went. */
    public record Field(BlockRect area, double[] heights, byte[] kind, int iterations, double residual) {
        public double at(int x, int z) {
            return this.heights[index(x, z)];
        }

        public boolean free(int x, int z) {
            return this.kind[index(x, z)] == FREE;
        }

        public int index(int x, int z) {
            return (z - this.area.minZ()) * this.area.sizeX() + (x - this.area.minX());
        }

        /** The rounded ground y of a column. */
        public int ground(int x, int z) {
            return (int) Math.round(this.at(x, z));
        }
    }

    private TownBlend() {
    }

    /*
      Solves a grid. `kind` and `heights` are indexed (z - minZ) * sizeX + (x - minX) over `area`; FIXED cells hold their
      edge height, FREE cells are solved, NONE cells are neither (a FREE cell next to one ignores it). Cells on the grid's
      own outer edge must not be FREE.
     */
    public static Field solve(BlockRect area, byte[] kind, double[] heights) {
        int w = area.sizeX();
        int d = area.sizeZ();
        if (kind.length != w * d || heights.length != w * d) {
            throw new IllegalArgumentException("grid arrays do not match " + w + " x " + d);
        }
        double[] h = heights.clone();
        // a free cell starts at the mean of the fixed cells, which keeps the first sweeps from overshooting
        double sum = 0;
        int fixed = 0;
        for (int i = 0; i < h.length; i++) {
            if (kind[i] == FIXED) {
                sum += h[i];
                fixed++;
            }
        }
        double start = fixed == 0 ? 0 : sum / fixed;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                if (kind[i] == FREE) {
                    if (x == 0 || z == 0 || x == w - 1 || z == d - 1) {
                        throw new IllegalArgumentException("a free cell on the grid's edge at " + (area.minX() + x) + ", " + (area.minZ() + z));
                    }
                    h[i] = start;
                }
            }
        }
        int iterations = 0;
        double change = Double.MAX_VALUE;
        while (change > TOLERANCE && iterations < MAX_ITERATIONS) {
            change = 0;
            for (int z = 1; z < d - 1; z++) {
                for (int x = 1; x < w - 1; x++) {
                    int i = z * w + x;
                    if (kind[i] != FREE) {
                        continue;
                    }
                    double s = 0;
                    int n = 0;
                    if (kind[i - 1] != NONE) {
                        s += h[i - 1];
                        n++;
                    }
                    if (kind[i + 1] != NONE) {
                        s += h[i + 1];
                        n++;
                    }
                    if (kind[i - w] != NONE) {
                        s += h[i - w];
                        n++;
                    }
                    if (kind[i + w] != NONE) {
                        s += h[i + w];
                        n++;
                    }
                    if (n == 0) {
                        continue;
                    }
                    double delta = OMEGA * (s / n - h[i]);
                    h[i] += delta;
                    change = Math.max(change, Math.abs(delta));
                }
            }
            iterations++;
        }
        return new Field(area, h, kind, iterations, change);
    }

    /** How far around a stored column edgeGround looks. */
    public static final int EDGE_MEDIAN = 2;
    /** A blend column whose ground is within this many blocks of its blended height is left as generated. */
    public static final int KEEP_WITHIN = 1;

    /** The median ground (template y) of the stored columns within EDGE_MEDIAN of a stored column, itself included. */
    public static int edgeGround(TownColumns columns, int x, int z) {
        int[] values = new int[(2 * EDGE_MEDIAN + 1) * (2 * EDGE_MEDIAN + 1)];
        int n = 0;
        for (int dz = -EDGE_MEDIAN; dz <= EDGE_MEDIAN; dz++) {
            for (int dx = -EDGE_MEDIAN; dx <= EDGE_MEDIAN; dx++) {
                int nx = x + dx;
                int nz = z + dz;
                if (nx >= 0 && nz >= 0 && nx < columns.width() && nz < columns.depth() && columns.stored(nx, nz)) {
                    values[n++] = columns.ground(nx, nz);
                }
            }
        }
        java.util.Arrays.sort(values, 0, n);
        return values[n / 2];
    }

    /** Whether a blend column with ground s must be rebuilt to reach blended ground g. */
    public static boolean needsRebuild(int s, int g) {
        return Math.abs(s - g) > KEEP_WITHIN;
    }

    /** The steepest step between two neighbouring blend columns of a solved field, in blocks (after rounding). */
    public static int steepestStep(Field field) {
        BlockRect a = field.area();
        int steepest = 0;
        for (int z = a.minZ(); z <= a.maxZ(); z++) {
            for (int x = a.minX(); x <= a.maxX(); x++) {
                if (!field.free(x, z)) {
                    continue;
                }
                int g = field.ground(x, z);
                if (x < a.maxX() && field.kind()[field.index(x + 1, z)] != NONE) {
                    steepest = Math.max(steepest, Math.abs(g - field.ground(x + 1, z)));
                }
                if (z < a.maxZ() && field.kind()[field.index(x, z + 1)] != NONE) {
                    steepest = Math.max(steepest, Math.abs(g - field.ground(x, z + 1)));
                }
                if (x > a.minX() && !field.free(x - 1, z) && field.kind()[field.index(x - 1, z)] != NONE) {
                    steepest = Math.max(steepest, Math.abs(g - field.ground(x - 1, z)));
                }
                if (z > a.minZ() && !field.free(x, z - 1) && field.kind()[field.index(x, z - 1)] != NONE) {
                    steepest = Math.max(steepest, Math.abs(g - field.ground(x, z - 1)));
                }
            }
        }
        return steepest;
    }
}
