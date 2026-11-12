package io.github.nick92461.inhabitedworld.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.spawn.SpawnProfile;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;

/*
  The semantic regions of planned sites (canon 02 section 6: "the town receives an authoritative semantic settlement
  boundary, the external POI an authoritative semantic region"; 06 section 17.4: regions are 3D). Run once, when the
  WorldPlan is made (or when an older plan is migrated); the result is saved in the plan and never derived again, so
  a later change to these rules never moves the regions of an existing world (02 section 7).

  Each site gets one region: its footprint is the site's 2D semantic boundary (the build plus its blend border), and
  its vertical extent depends on what the site is.

  Settlement: from SETTLEMENT_DEPTH blocks below its lowest ground up to the top of the world.
  - Canon protects the town's "streets and buildings" (06 section 17.1), so the region must hold everything the town
    is: buildings, towers and roofs (so up to the sky, where phantoms would otherwise appear above the streets), and
    cellars, crypts and the ground just under the streets, where a dark pocket would otherwise breed mobs that come up
    into a house.
  - It stops below that, because 06 section 17.4 makes regions 3D precisely so that layered spaces keep their own
    rules, and 06 section 17.2 keeps "cave danger" in the wilderness: deep caves under a town are wilderness. A mob from
    them that climbs into town is a hostile entering from outside, which canon allows (17.1, 17.12 item 3).
  - 32 blocks (two chunk sections) under the lowest ground: deeper than cellars of the imported Drachenfels buildings
    are likely to go, and the town's own site manifest replaces this guess in Phase 3. The lowest ground is the
    planner's median height minus its relief (the 10th-to-90th percentile spread), which is below nearly every street.

  POI: from the bottom of the world up to POI_HEADROOM blocks above its highest ground.
  - The v0.1 POI may be an abandoned mine (09 section 3): its tunnels can reach any depth, and Phase 3's structure
    defines them. Until then the region claims the whole underground under its footprint, and the open sky above its
    surface part stays wilderness.

  Both extents use the RPG overworld's fixed build range. Its dimension type (inhabited_world:rpg_overworld) is a
  shipped configuration that is never edited in place (02 section 6), so these numbers hold for every world.
 */
public final class SiteRegions {
    /** Bottom and top block of the RPG overworld (dimension type rpg_overworld: min_y -64, height 384). */
    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_MAX_Y = 319;

    public static final int SETTLEMENT_DEPTH = 32;
    public static final int POI_HEADROOM = 32;

    private SiteRegions() {
    }

    public static List<SemanticRegion> derive(List<PlannedSite> sites) {
        List<SemanticRegion> regions = new ArrayList<>(sites.size());
        for (PlannedSite site : sites) {
            regions.add(derive(site));
        }
        return List.copyOf(regions);
    }

    public static SemanticRegion derive(PlannedSite site) {
        RegionKind kind = RegionKind.of(site.kind());
        SiteMetrics ground = site.predicted();
        return switch (kind) {
            case SETTLEMENT -> new SemanticRegion(site.id(), kind, Optional.of(site.id()), SpawnProfile.PROTECTED_SETTLEMENT,
                    RegionVolume.of(site.region(), clampY(lowestGround(ground) - SETTLEMENT_DEPTH), WORLD_MAX_Y));
            case POI -> new SemanticRegion(site.id(), kind, Optional.of(site.id()), SpawnProfile.POI_PLACEHOLDER,
                    RegionVolume.of(site.region(), WORLD_MIN_Y, clampY(highestGround(ground) + POI_HEADROOM)));
            case WILDERNESS -> throw new IllegalArgumentException("a planned site is never wilderness");
        };
    }

    /** The wilderness of a world: its playable area at full height (beyond it, the lookup answers wilderness too). */
    public static SemanticRegion wilderness(PlayableArea area) {
        return SemanticRegion.wilderness(new RegionVolume(area.minBlockX(), WORLD_MIN_Y, area.minBlockZ(), area.maxBlockX(), WORLD_MAX_Y, area.maxBlockZ()));
    }

    // Relief is unmeasurable (9999) on terrain with too little dry land; the clamp then reaches the world's bottom or top.
    private static long lowestGround(SiteMetrics ground) {
        return (long) ground.medianY() - ground.relief();
    }

    private static long highestGround(SiteMetrics ground) {
        return (long) ground.medianY() + ground.relief();
    }

    private static int clampY(long y) {
        return (int) Math.max(WORLD_MIN_Y, Math.min(WORLD_MAX_Y, y));
    }
}
