package io.github.nick92461.inhabitedworld.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteRules;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldgen.planner.Waterfront;

import net.minecraft.core.Direction;

// Turning planned sites into semantic regions (canon 02 section 6, 06 sections 17.1 and 17.4).
class SiteRegionsTest {
    static PlannedSite town(SiteMetrics ground) {
        BlockRect bounds = BlockRect.ofSize(144, -288, 256, 256);
        return new PlannedSite(V01Sites.TOWN, SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID,
                new SiteRules(V01Sites.TOWN_FOOTPRINT, Waterfront.PREFERRED, V01Sites.TOWN_TERRAIN, 0), bounds, bounds.inflate(32),
                Optional.of(Direction.EAST), ground);
    }

    static PlannedSite poi(SiteMetrics ground) {
        BlockRect bounds = BlockRect.ofSize(-288, -288, 96, 96);
        return new PlannedSite(V01Sites.EXTERNAL_POI, SiteKind.POI, V01Sites.POI_CATALOG_ID,
                new SiteRules(V01Sites.POI_FOOTPRINT, Waterfront.NONE, V01Sites.POI_TERRAIN, V01Sites.POI_SPAWN_CLEARANCE), bounds, bounds.inflate(16),
                Optional.empty(), ground);
    }

    @Test
    void aTownIsProtectedFromBelowItsCellarsToTheSky() {
        SemanticRegion region = SiteRegions.derive(town(new SiteMetrics(0.96, 14, 68, 0.0, Optional.of(1.0))));
        assertEquals("town", region.id());
        assertEquals(RegionKind.SETTLEMENT, region.kind());
        assertEquals(Optional.of("town"), region.site());
        assertEquals(SpawnProfile.PROTECTED_SETTLEMENT, region.profile());
        assertEquals(town(new SiteMetrics(0.96, 14, 68, 0.0, Optional.empty())).region(), region.volume().footprint(), "the footprint is the site's semantic boundary");
        assertEquals(68 - 14 - SiteRegions.SETTLEMENT_DEPTH, region.volume().minY(), "32 blocks below the lowest ground (median minus relief)");
        assertEquals(SiteRegions.WORLD_MAX_Y, region.volume().maxY());
    }

    @Test
    void aPoiOwnsItsUndergroundAndLeavesTheSkyToTheWilderness() {
        SemanticRegion region = SiteRegions.derive(poi(new SiteMetrics(1.0, 7, 72, 0.0, Optional.empty())));
        assertEquals(RegionKind.POI, region.kind());
        assertEquals(SpawnProfile.POI_PLACEHOLDER, region.profile());
        assertEquals(SiteRegions.WORLD_MIN_Y, region.volume().minY());
        assertEquals(72 + 7 + SiteRegions.POI_HEADROOM, region.volume().maxY());
    }

    // Too little dry land to measure relief (9999): the extent clamps to the world's build range.
    @Test
    void unmeasurableReliefClampsToTheWorld() {
        SiteMetrics unmeasurable = new SiteMetrics(0.1, SiteMetrics.UNMEASURABLE_RELIEF, 63, 0.0, Optional.empty());
        assertEquals(SiteRegions.WORLD_MIN_Y, SiteRegions.derive(town(unmeasurable)).volume().minY());
        assertEquals(SiteRegions.WORLD_MAX_Y, SiteRegions.derive(poi(unmeasurable)).volume().maxY());
    }

    // Settlement-count agnostic: every site of a plan gets its own region, in order.
    @Test
    void everySiteGetsItsRegion() {
        SiteMetrics ground = new SiteMetrics(1.0, 10, 70, 0.0, Optional.empty());
        List<SemanticRegion> regions = SiteRegions.derive(List.of(town(ground), poi(ground)));
        assertEquals(List.of("town", "external_poi"), regions.stream().map(SemanticRegion::id).toList());
    }

    @Test
    void theWildernessIsThePlayableAreaAtFullHeight() {
        SemanticRegion wilderness = SiteRegions.wilderness(PlayableArea.V0_1);
        assertEquals(new RegionVolume(-512, -64, -512, 511, 319, 511), wilderness.volume());
        assertEquals(RegionKind.WILDERNESS, wilderness.kind());
        assertTrue(wilderness.site().isEmpty());
    }

    @Test
    void regionsRoundTripAndTheWildernessIsNeverSaved() {
        SemanticRegion region = SiteRegions.derive(town(new SiteMetrics(1.0, 10, 70, 0.0, Optional.empty())));
        assertEquals(region, SemanticRegion.CODEC.parse(JsonOps.INSTANCE, SemanticRegion.CODEC.encodeStart(JsonOps.INSTANCE, region).getOrThrow()).getOrThrow());
        assertTrue(SemanticRegion.CODEC.encodeStart(JsonOps.INSTANCE, SiteRegions.wilderness(PlayableArea.V0_1)).isError());
    }
}
