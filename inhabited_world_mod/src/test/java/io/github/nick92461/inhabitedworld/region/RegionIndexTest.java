package io.github.nick92461.inhabitedworld.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

/*
  Region membership (canon 06 section 17.4, 10 section 60 Testing Strategy): inclusive edges on all six faces, 3D
  bounds, most-specific-wins precedence, the wilderness everywhere else, and the per-chunk fast path, checked against
  a brute-force reference on random positions.
 */
class RegionIndexTest {
    static final SemanticRegion WILDERNESS = SiteRegions.wilderness(PlayableArea.V0_1);

    static SemanticRegion settlement(String id, RegionVolume volume) {
        return new SemanticRegion(id, RegionKind.SETTLEMENT, Optional.of(id), SpawnProfile.PROTECTED_SETTLEMENT, volume);
    }

    static SemanticRegion poi(String id, RegionVolume volume) {
        return new SemanticRegion(id, RegionKind.POI, Optional.of(id), SpawnProfile.POI_PLACEHOLDER, volume);
    }

    // The v0.1 layout: a town (y 20 to the sky) and a POI (the underground to y 100).
    static final SemanticRegion TOWN = settlement("town", new RegionVolume(112, 20, -320, 431, 319, -1));
    static final SemanticRegion MINE = poi("external_poi", new RegionVolume(-304, -64, -304, -177, 100, -177));

    private final RegionIndex index = RegionIndex.of(WILDERNESS, List.of(TOWN, MINE));

    @Test
    void insideARegionIsThatRegion() {
        assertSame(TOWN, this.index.regionAt(272, 70, -160));
        assertSame(MINE, this.index.regionAt(-240, 64, -240));
        assertSame(MINE, this.index.regionAt(-240, -60, -240), "the POI reaches the bottom of the world");
    }

    @Test
    void edgesAreInclusiveOnEveryFace() {
        RegionVolume v = TOWN.volume();
        int x = 272;
        int y = 70;
        int z = -160;
        assertSame(TOWN, this.index.regionAt(v.minX(), y, z));
        assertSame(TOWN, this.index.regionAt(v.maxX(), y, z));
        assertSame(TOWN, this.index.regionAt(x, y, v.minZ()));
        assertSame(TOWN, this.index.regionAt(x, y, v.maxZ()));
        assertSame(TOWN, this.index.regionAt(x, v.minY(), z));
        assertSame(TOWN, this.index.regionAt(x, v.maxY(), z));
        assertSame(WILDERNESS, this.index.regionAt(v.minX() - 1, y, z));
        assertSame(WILDERNESS, this.index.regionAt(v.maxX() + 1, y, z));
        assertSame(WILDERNESS, this.index.regionAt(x, y, v.minZ() - 1));
        assertSame(WILDERNESS, this.index.regionAt(x, y, v.maxZ() + 1));
        assertSame(WILDERNESS, this.index.regionAt(x, v.minY() - 1, z), "deep caves under the town are wilderness");
        assertSame(WILDERNESS, this.index.regionAt(x, v.maxY() + 1, z));
    }

    // 06 section 17.4: "an underground mine may exist underneath ordinary wilderness".
    @Test
    void regionsAreThreeDimensional() {
        assertSame(MINE, this.index.regionAt(-240, 100, -240));
        assertSame(WILDERNESS, this.index.regionAt(-240, 101, -240), "the sky above the POI's surface part is wilderness");
        assertSame(WILDERNESS, this.index.regionAt(272, 19, -160), "below the town's floor is wilderness");
    }

    @Test
    void outsideEveryRegionIsTheWildernessEvenBeyondTheMap() {
        assertSame(WILDERNESS, this.index.regionAt(0, 70, 0));
        assertSame(WILDERNESS, this.index.regionAt(5000, 70, -5000));
        assertSame(WILDERNESS, this.index.regionAt(Integer.MIN_VALUE, 0, Integer.MAX_VALUE));
        assertTrue(WILDERNESS.isWilderness());
        assertEquals(SpawnProfile.WILDERNESS, WILDERNESS.profile());
        assertEquals("wilderness", WILDERNESS.id());
    }

    // POI over settlement over wilderness (06 section 17.4), however the regions are listed.
    @Test
    void theMostSpecificRegionWins() {
        SemanticRegion town = settlement("town", new RegionVolume(0, 0, 0, 255, 319, 255));
        SemanticRegion shrine = poi("shrine", new RegionVolume(100, 60, 100, 120, 80, 120));
        SemanticRegion district = settlement("district", new RegionVolume(64, 0, 64, 191, 319, 191));
        for (List<SemanticRegion> order : List.of(List.of(town, shrine, district), List.of(shrine, district, town), List.of(district, town, shrine))) {
            RegionIndex nested = RegionIndex.of(WILDERNESS, order);
            assertSame(shrine, nested.regionAt(110, 70, 110), "a POI inside a settlement wins");
            assertSame(district, nested.regionAt(110, 90, 110), "above the POI: the smaller, more specific settlement");
            assertSame(district, nested.regionAt(70, 70, 70));
            assertSame(town, nested.regionAt(10, 70, 10));
            assertSame(WILDERNESS, nested.regionAt(300, 70, 10));
            assertEquals(List.of(shrine, district, town), nested.regionsAt(110, 70, 110), "diagnostics list every region, applying one first");
            assertEquals(List.of(shrine, district, town), nested.regions());
        }
    }

    @Test
    void equalRegionsAreOrderedById() {
        RegionVolume same = new RegionVolume(0, 0, 0, 31, 31, 31);
        SemanticRegion b = settlement("b", same);
        SemanticRegion a = settlement("a", same);
        assertSame(a, RegionIndex.of(WILDERNESS, List.of(b, a)).regionAt(5, 5, 5));
        assertSame(a, RegionIndex.of(WILDERNESS, List.of(a, b)).regionAt(5, 5, 5));
    }

    // A chunk column that no region touches skips region checks (06 section 17.6).
    @Test
    void chunksNoRegionTouchesAreWildernessChunks() {
        assertTrue(this.index.isWildernessChunk(0, 0));
        assertTrue(this.index.isWildernessChunk(1000, 1000));
        assertFalse(this.index.isWildernessChunk(272 >> 4, -160 >> 4));
        assertFalse(this.index.isWildernessChunk(112 >> 4, -1 >> 4), "the chunk holding the town's corner block");
        assertTrue(this.index.isWildernessChunk((112 >> 4) - 1, -160 >> 4), "the chunk west of the town's west edge");
        // A region that ends mid-chunk still marks the whole chunk column, and the box check sorts out the rest.
        RegionIndex small = RegionIndex.of(WILDERNESS, List.of(settlement("hut", new RegionVolume(3, 60, 3, 5, 70, 5))));
        assertFalse(small.isWildernessChunk(0, 0));
        assertSame(WILDERNESS, small.regionAt(10, 65, 10));
        assertEquals(1, small.gridCells());
    }

    @Test
    void anIndexWithoutRegionsIsAllWilderness() {
        RegionIndex none = RegionIndex.of(WILDERNESS, List.of());
        assertSame(WILDERNESS, none.regionAt(0, 0, 0));
        assertTrue(none.isWildernessChunk(0, 0));
        assertEquals(0, none.gridCells());
    }

    @Test
    void negativeCoordinatesUseFloorChunks() {
        RegionIndex index = RegionIndex.of(WILDERNESS, List.of(settlement("west", new RegionVolume(-16, 0, -16, -1, 10, -1))));
        assertFalse(index.isWildernessChunk(-1, -1));
        assertSame(index.regions().get(0), index.regionAt(-16, 5, -1));
        assertSame(WILDERNESS, index.regionAt(0, 5, -1));
        assertSame(WILDERNESS, index.regionAt(-17, 5, -1));
    }

    @Test
    void theWildernessIsNeverIndexedAsARegion() {
        assertThrows(IllegalArgumentException.class, () -> RegionIndex.of(TOWN, List.of()));
        assertThrows(IllegalArgumentException.class, () -> RegionIndex.of(WILDERNESS, List.of(WILDERNESS)));
        assertThrows(IllegalArgumentException.class, () -> new RegionVolume(0, 10, 0, 5, 9, 5), "an empty volume");
    }

    // The index agrees with checking every region by brute force, on a world with many settlements and POIs.
    @Test
    void agreesWithBruteForceOnAManyTownWorld() {
        Random random = new Random(20261112L);
        List<SemanticRegion> regions = manyRegions(random, 120, 8192);
        RegionIndex many = RegionIndex.of(WILDERNESS, regions);
        for (int k = 0; k < 200_000; k++) {
            int x = random.nextInt(8400) - 4200;
            int y = random.nextInt(400) - 70;
            int z = random.nextInt(8400) - 4200;
            assertSame(bruteForce(regions, x, y, z), many.regionAt(x, y, z), "at " + x + ", " + y + ", " + z);
        }
    }

    /** Random settlements (some with nested POIs and districts) spread over a square world of the given size. */
    static List<SemanticRegion> manyRegions(Random random, int settlements, int worldSize) {
        List<SemanticRegion> regions = new ArrayList<>();
        for (int s = 0; s < settlements; s++) {
            int size = 64 + random.nextInt(320);
            int x = random.nextInt(worldSize - size) - worldSize / 2;
            int z = random.nextInt(worldSize - size) - worldSize / 2;
            int floor = random.nextInt(60);
            regions.add(settlement("town_" + s, new RegionVolume(x, floor, z, x + size - 1, 319, z + size - 1)));
            if (random.nextBoolean()) {
                regions.add(poi("shrine_" + s, new RegionVolume(x + 8, floor, z + 8, x + 40, floor + 40, z + 40)));
            }
            if (random.nextInt(4) == 0) {
                regions.add(settlement("district_" + s, new RegionVolume(x, floor, z, x + size / 2, 319, z + size / 2)));
            }
            int mx = random.nextInt(worldSize - 128) - worldSize / 2;
            int mz = random.nextInt(worldSize - 128) - worldSize / 2;
            regions.add(poi("mine_" + s, new RegionVolume(mx, -64, mz, mx + 95, 40 + random.nextInt(80), mz + 95)));
        }
        return regions;
    }

    // The reference: every region containing the block, most specific kind first, then the smaller volume, then the ID.
    private static SemanticRegion bruteForce(List<SemanticRegion> regions, int x, int y, int z) {
        SemanticRegion best = WILDERNESS;
        for (SemanticRegion region : regions) {
            if (!region.volume().contains(x, y, z)) {
                continue;
            }
            if (best.isWilderness() || region.kind().specificity() > best.kind().specificity()
                    || region.kind() == best.kind() && (region.volume().blockCount() < best.volume().blockCount()
                    || region.volume().blockCount() == best.volume().blockCount() && region.id().compareTo(best.id()) < 0)) {
                best = region;
            }
        }
        return best;
    }
}
