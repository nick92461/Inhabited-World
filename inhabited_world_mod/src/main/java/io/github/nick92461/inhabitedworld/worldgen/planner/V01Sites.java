package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.List;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import net.minecraft.resources.Identifier;

/*
  What a Version 0.1 world must contain (canon 09 section 3: "one small handcrafted town", "one significant external
  POI"), and every tunable number of that request, in one place. The planning algorithm itself is in SitePlanner
  and SiteEvaluator; this file is only the request.

  Town footprint, 256 x 256 blocks (16 x 16 chunks) plus a 32-block blend border: a PLACEHOLDER until the Drachenfels
  district's manifest exists (implementation plan, "Imported builds": the v0.1 town is one Drachenfels district
  sized for 09's 6-10 significant NPCs). 256 blocks fits a district of Drachenfels' massive buildings, and its
  region (320 x 320) leaves the island (roughly 600-700 blocks of land across) room for the wilderness and the POI.
  The real footprint replaces it when the manifest arrives; a smaller one only makes planning easier.

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

  Waterfront: the town PREFERS a waterfront (TOWN_WATERFRONT). Drachenfels' end state is one coastal city
  (implementation plan, 2026-11-11), and starting the first district on the shore keeps that growth path open.
  Switch it here: NONE for an inland town, REQUIRED to keep the coast even through the planner's layout fallback.

  Every region stays 32 blocks (two chunks) inside the world border, so no build or blend edge touches the border.
 */
public final class V01Sites {
    public static final String TOWN = "town";
    public static final String EXTERNAL_POI = "external_poi";

    public static final Identifier TOWN_CATALOG_ID = InhabitedWorld.id("placeholder_town");
    public static final Identifier POI_CATALOG_ID = InhabitedWorld.id("placeholder_external_poi");

    public static final Footprint TOWN_FOOTPRINT = new Footprint(256, 256, 32);
    public static final Footprint POI_FOOTPRINT = new Footprint(96, 96, 16);

    public static final Waterfront TOWN_WATERFRONT = Waterfront.PREFERRED;

    /** Town ground: almost all dry, gentle (24 blocks of spread over 256 blocks), not on peaks. */
    public static final TerrainLimits TOWN_TERRAIN = new TerrainLimits(0.9, 24, 63, 110, 0.15);
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
        SiteRequirement town = new SiteRequirement(TOWN, SiteKind.SETTLEMENT, TOWN_CATALOG_ID,
                new SiteRules(TOWN_FOOTPRINT, townWaterfront, TOWN_TERRAIN, 0));
        SiteRequirement poi = new SiteRequirement(EXTERNAL_POI, SiteKind.POI, POI_CATALOG_ID,
                new SiteRules(POI_FOOTPRINT, Waterfront.NONE, POI_TERRAIN, POI_SPAWN_CLEARANCE));
        return new PlanRequest(area, EDGE_MARGIN, List.of(town, poi),
                List.of(new SiteSpacing(TOWN, EXTERNAL_POI, MIN_WILDERNESS_GAP, MAX_CENTER_DISTANCE)));
    }
}
