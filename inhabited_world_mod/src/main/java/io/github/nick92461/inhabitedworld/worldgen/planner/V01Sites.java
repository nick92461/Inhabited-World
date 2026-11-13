package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.List;
import java.util.Optional;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/*
  What a Version 0.1 world must contain (canon 09 section 3: "one small handcrafted town", "one significant external
  POI"), and every tunable number of that request, in one place. The planning algorithm itself is in SitePlanner
  and SiteEvaluator; this file is only the request.

  The town is Drachenfels district D1 Harbour (user decision 2026-11-13; survey in
  docs/notes/2026-11-12_drachenfels_survey/REPORT.md section 4): the quay, three piers and two moored ships, sea on
  its east side. Its numbers are written here until Phase 3 reads them from the district's manifest (the Track B1
  export, catalog ID TOWN_CATALOG_ID); the planner itself knows nothing of Drachenfels and plans any list of sites.
  - Footprint 272 x 256 blocks (17 x 16 chunks), plus the same 32-block blend border: the exported template's box,
    x 128..399, z -1264..-1009 in the Drachenfels save. The survey's district box (273 x 221, z -1248..-1025) cut
    through D1's own buildings (a row of houses on the north street reaches z -1255, a hall z -1022), so the export
    keeps 272 x 236 (z -1256..-1021) and rounds it out to whole chunks (export session, 2026-11-13). Its region
    (336 x 320) leaves the island (roughly 600-700 blocks of land across) room for the wilderness and the POI.
  - Sea side: east (TOWN_SEA_SIDE). The planner may rotate the build, so "waterfront facing north" means the build
    turned a quarter anticlockwise; the plan records the rotation.
  - Waterfront strip 112 blocks deep (TOWN_WATERFRONT_DEPTH, 7 chunks): the template's sea part, from the quay edge
    (x ~285 in the save) to the end of the piers and ships (x ~388). It is left out of the land measurements, at
    least TOWN_MIN_STRIP_SEA of it must already be open sea (piers need water; the blend can dig out a little shore,
    not a harbour), and the 112 blocks beyond the build must be mostly open sea (SiteEvaluator.MIN_SEA), so ships
    have more than the survey's ~160 blocks of open water from the quay.
  - Waterfront REQUIRED (user decision 2026-11-12, "the town defaults to the waterfront", and the Harbour cannot
    exist without the sea): only the planner's last resort, which guarantees the town exists at all, drops it.
  - Relief limit 32 (TOWN_TERRAIN): the Harbour's land part (160 x 256) rises from y ~72 at the quay to y ~100 at the
    back street, about 30 blocks, so ground with up to about that much spread is ground the build already matches;
    more is left to the looser fallback steps and the blend. The district's 38-140 height range is its buildings
    (pier footings to roofs), not its ground, so it does not set this limit.

  The square 256 x 256 placeholder of planner version 1 is gone from new plans; worlds planned with it keep it
  (02 section 7).

  POI footprint, 96 x 96 plus a 16-block border: a placeholder for the external POI's surface part (an abandoned
  mine's entrance, ruins or an encampment, 09 section 3). Underground extent is Phase 2B/3 work on 3D regions.

  Town-to-POI spacing:
  - at least 128 blocks of wilderness between the two regions. 09 section 3 makes town, wilderness and POI three
    different hostile regimes, so the walk between them must cross real wilderness; 128 blocks is vanilla's mob
    spawning radius around a player, so mid-way a player is out of reach of both regions' rules.
  - at most 640 blocks between centres. 02 section 8's example geography fact is "mine is approximately 430 blocks
    away", and the quest (09 section 54, Phase 4) is a walk there and back; 640 blocks is about two minutes on foot,
    and on a 1024 map the island rarely allows more anyway.

  The POI keeps 128 blocks clear of vanilla's spawn search origin (the island's middle), so the player never starts
  inside the POI's hostile region. Vanilla's spawn near the centre (user decision 2026-11-11) is unchanged.

  Every region stays 32 blocks (two chunks) inside the world border, so no build or blend edge touches the border.
  The sea checked beyond a waterfront then reaches at most TOWN_WATERFRONT_DEPTH - EDGE_MARGIN - blend = 48 blocks
  past the border, well inside the 12-chunk (192-block) ocean ring that pre-generation makes, so verification reads
  only pre-generated chunks.
 */
public final class V01Sites {
    public static final String TOWN = "town";
    public static final String EXTERNAL_POI = "external_poi";

    /** Drachenfels district D1 Harbour; Phase 3 reads its footprint and sea side from this entry's manifest. */
    public static final Identifier TOWN_CATALOG_ID = InhabitedWorld.id("drachenfels/d1_harbour");
    public static final Identifier POI_CATALOG_ID = InhabitedWorld.id("placeholder_external_poi");

    public static final Footprint TOWN_FOOTPRINT = new Footprint(272, 256, 32);
    public static final Footprint POI_FOOTPRINT = new Footprint(96, 96, 16);

    public static final Waterfront TOWN_WATERFRONT = Waterfront.REQUIRED;
    public static final Direction TOWN_SEA_SIDE = Direction.EAST;
    public static final int TOWN_WATERFRONT_DEPTH = 112;
    public static final double TOWN_MIN_STRIP_SEA = 0.5;

    /** Town ground under its land part: almost all dry, gentle (32 blocks of spread), not on peaks. */
    public static final TerrainLimits TOWN_TERRAIN = new TerrainLimits(0.9, 32, 63, 110, 0.15);
    /** POI ground: dry, may be hillier (a mine likes hills). */
    public static final TerrainLimits POI_TERRAIN = new TerrainLimits(0.85, 32, 63, 140, 0.5);

    public static final int MIN_WILDERNESS_GAP = 128;
    public static final int MAX_CENTER_DISTANCE = 640;
    public static final int POI_SPAWN_CLEARANCE = 128;
    public static final int EDGE_MARGIN = 32;

    private V01Sites() {
    }

    public static PlanRequest request(PlayableArea area) {
        return request(area, TOWN_WATERFRONT);
    }

    public static PlanRequest request(PlayableArea area, Waterfront townWaterfront) {
        SiteRequirement town = new SiteRequirement(TOWN, SiteKind.SETTLEMENT, TOWN_CATALOG_ID, townRules(townWaterfront));
        SiteRequirement poi = new SiteRequirement(EXTERNAL_POI, SiteKind.POI, POI_CATALOG_ID,
                new SiteRules(POI_FOOTPRINT, Waterfront.NONE, POI_TERRAIN, POI_SPAWN_CLEARANCE));
        return new PlanRequest(area, EDGE_MARGIN, List.of(town, poi),
                List.of(new SiteSpacing(TOWN, EXTERNAL_POI, MIN_WILDERNESS_GAP, MAX_CENTER_DISTANCE)));
    }

    /** The town's rules with the given waterfront setting (the survey compares settings; worlds use TOWN_WATERFRONT). */
    public static SiteRules townRules(Waterfront townWaterfront) {
        return new SiteRules(TOWN_FOOTPRINT, townWaterfront, Optional.of(TOWN_SEA_SIDE), TOWN_WATERFRONT_DEPTH, TOWN_MIN_STRIP_SEA, TOWN_TERRAIN, 0);
    }
}
