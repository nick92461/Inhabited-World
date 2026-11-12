package io.github.nick92461.inhabitedworld.worldstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.SiteRegions;
import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannerRun;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;
import io.github.nick92461.inhabitedworld.worldgen.planner.SitePlanner;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteRules;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldgen.planner.Waterfront;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

// The persisted WorldPlan (canon 02 section 7, 08 section 49): sites, planner record, verification, and migration.
class WorldPlanTest {
    private static final UUID ID = UUID.fromString("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11");

    static final PlannedSite TOWN = new PlannedSite(V01Sites.TOWN, SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID,
            new SiteRules(V01Sites.TOWN_FOOTPRINT, Waterfront.PREFERRED, V01Sites.TOWN_TERRAIN, 0),
            BlockRect.ofSize(-272, -368, 256, 256), BlockRect.ofSize(-272, -368, 256, 256).inflate(32), Optional.of(Direction.NORTH),
            new SiteMetrics(0.99, 24, 80, 0.0, Optional.of(1.0)));
    static final PlannedSite POI = new PlannedSite(V01Sites.EXTERNAL_POI, SiteKind.POI, V01Sites.POI_CATALOG_ID,
            new SiteRules(V01Sites.POI_FOOTPRINT, Waterfront.NONE, V01Sites.POI_TERRAIN, V01Sites.POI_SPAWN_CLEARANCE),
            BlockRect.ofSize(192, -288, 96, 96), BlockRect.ofSize(192, -288, 96, 96).inflate(16), Optional.empty(),
            new SiteMetrics(1.0, 4, 64, 0.0, Optional.empty()));
    private static final PlannerRun RUN = new PlannerRun(SitePlanner.ALGORITHM, SitePlanner.VERSION, 0, 4490);

    private final WorldPlan plan = WorldPlan.create(ID, PlayableArea.V0_1, RUN, List.of(TOWN, POI));

    private static JsonObject encode(WorldPlan plan) {
        return WorldPlan.CODEC.encodeStart(JsonOps.INSTANCE, plan).getOrThrow().getAsJsonObject();
    }

    @Test
    void savesAndLoadsUnchanged() {
        JsonObject json = encode(this.plan);
        assertEquals(3, json.get("data_version").getAsInt(), "every persisted domain carries a data version");
        assertEquals("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11", json.get("world_id").getAsString());
        assertEquals("inhabited_world:site_planner", json.getAsJsonObject("planner").get("algorithm").getAsString());
        JsonObject town = json.getAsJsonArray("sites").get(0).getAsJsonObject();
        assertEquals("settlement", town.get("kind").getAsString());
        assertEquals("inhabited_world:placeholder_town", town.get("catalog_id").getAsString());
        assertEquals("north", town.get("waterfront").getAsString());
        assertEquals("preferred", town.getAsJsonObject("rules").get("waterfront").getAsString());
        assertEquals(-304, town.getAsJsonObject("region").get("min_x").getAsInt());
        assertEquals(this.plan, WorldPlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void verificationIsSavedWithThePlan() {
        SiteVerification verification = new SiteVerification(true, List.of(
                new SiteVerification.SiteCheck(V01Sites.TOWN, true, new SiteMetrics(1.0, 24, 80, 0.0, Optional.of(1.0)), List.of()),
                new SiteVerification.SiteCheck(V01Sites.EXTERNAL_POI, false, new SiteMetrics(0.5, 5, 64, 0.0, Optional.empty()), List.of("only 50% dry land"))),
                Optional.of(new BlockPos(-32, 72, -32)), List.of());
        WorldPlan verified = this.plan.withVerification(verification);
        assertEquals(verified, WorldPlan.CODEC.parse(JsonOps.INSTANCE, encode(verified)).getOrThrow());
        assertEquals(verified.sites(), this.plan.sites(), "verification never changes the sites");
    }

    // A step-4 world (data version 1) was generated before the planner existed: it reads as a plan without sites.
    @Test
    void aVersionOnePlanIsReadWithoutSites() {
        JsonObject v1 = JsonParser.parseString("""
                {"data_version": 1, "world_id": "7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11",
                 "playable_area": {"center_x": 0, "center_z": 0, "size": 1024}}""").getAsJsonObject();
        WorldPlan read = WorldPlan.CODEC.parse(JsonOps.INSTANCE, v1).getOrThrow();
        assertEquals(WorldPlan.DATA_VERSION, read.dataVersion());
        assertEquals(ID, read.worldId());
        assertFalse(read.hasSites());
        assertTrue(read.planner().isEmpty());
        assertEquals(3, encode(read).get("data_version").getAsInt(), "it is written back as the current version");
        assertTrue(read.regions().isEmpty(), "a world without sites has no regions: all wilderness");
        assertEquals(read, WorldPlan.CODEC.parse(JsonOps.INSTANCE, encode(read)).getOrThrow());
    }

    @Test
    void refusesWhatItCannotRead() {
        JsonObject newer = encode(this.plan);
        newer.addProperty("data_version", 4);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, newer).isError(), "a newer data version is refused");

        JsonObject unversioned = encode(this.plan);
        unversioned.remove("data_version");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, unversioned).isError(), "a plan without a data version is refused");

        JsonObject noPlanner = encode(this.plan);
        noPlanner.remove("planner");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, noPlanner).isError(), "planned sites always name their planner");

        JsonObject badRegion = encode(this.plan);
        badRegion.getAsJsonArray("sites").get(0).getAsJsonObject().getAsJsonObject("region").addProperty("min_x", 0);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, badRegion).isError(), "a region must contain its build");

        JsonObject v1WithSites = encode(this.plan);
        v1WithSites.addProperty("data_version", 1);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, v1WithSites).isError(), "version 1 had no sites");

        JsonObject v2WithRegions = encode(this.plan);
        v2WithRegions.addProperty("data_version", 2);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, v2WithRegions).isError(), "version 2 had no regions");

        JsonObject unprotectedTown = encode(this.plan);
        unprotectedTown.getAsJsonArray("regions").remove(0);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, unprotectedTown).isError(), "every planned site has its semantic region");

        JsonObject strayRegion = encode(this.plan);
        strayRegion.getAsJsonArray("regions").get(0).getAsJsonObject().getAsJsonObject("volume").addProperty("max_x", 5000);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, strayRegion).isError(), "a region stays inside its site's semantic boundary");

        JsonObject unknownSite = encode(this.plan);
        unknownSite.getAsJsonArray("regions").get(0).getAsJsonObject().addProperty("site", "elsewhere");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, unknownSite).isError(), "a region names one of the plan's sites");

        JsonObject unknownProfile = encode(this.plan);
        unknownProfile.getAsJsonArray("regions").get(0).getAsJsonObject().addProperty("spawn_profile", "inhabited_world:from_the_future");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, unknownProfile).isError(), "an unknown spawn profile is refused");
    }

    // P2B: the plan saves each site's 3D semantic region and its spawn profile (06 section 17.12 items 1 and 5).
    @Test
    void regionsAreSavedWithThePlan() {
        assertEquals(List.of(SiteRegions.derive(TOWN), SiteRegions.derive(POI)), this.plan.regions());
        JsonObject region = encode(this.plan).getAsJsonArray("regions").get(0).getAsJsonObject();
        assertEquals("town", region.get("id").getAsString());
        assertEquals("settlement", region.get("kind").getAsString());
        assertEquals("town", region.get("site").getAsString());
        assertEquals("inhabited_world:protected_settlement", region.get("spawn_profile").getAsString());
        assertEquals(-304, region.getAsJsonObject("volume").get("min_x").getAsInt());
        assertEquals(80 - 24 - SiteRegions.SETTLEMENT_DEPTH, region.getAsJsonObject("volume").get("min_y").getAsInt());
        assertEquals(SiteRegions.WORLD_MAX_Y, region.getAsJsonObject("volume").get("max_y").getAsInt());
        RegionIndex index = this.plan.buildRegionIndex();
        assertEquals("town", index.regionAt(-150, 80, -250).id());
        assertEquals("external_poi", index.regionAt(240, 10, -240).id());
        assertEquals("wilderness", index.regionAt(0, 80, 0).id());
    }

    /*
      A step-5 world (data version 2) is migrated once: its regions are derived from its saved sites exactly as a new
      plan's are, it reads as version 3, and the saved data is marked to be written back.
     */
    @Test
    void aVersionTwoPlanIsMigratedToRegions() {
        JsonObject v2 = encode(this.plan);
        v2.addProperty("data_version", 2);
        v2.remove("regions");
        WorldPlan read = WorldPlan.CODEC.parse(JsonOps.INSTANCE, v2).getOrThrow();
        assertEquals(WorldPlan.DATA_VERSION, read.dataVersion());
        assertEquals(this.plan, read, "a migrated plan equals the plan the same sites make today");

        WorldPlanData migrated = WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, v2).getOrThrow();
        assertTrue(migrated.isDirty(), "a migrated plan is written back at the next save, so the migration runs once");
        WorldPlanData current = WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, encode(this.plan)).getOrThrow();
        assertFalse(current.isDirty(), "a current plan is not rewritten");
    }

    // Only a new Inhabited World gets a plan: never a Default world, and never a second plan.
    @Test
    void onlyNewInhabitedWorldsGetAPlan() {
        assertTrue(WorldPlans.shouldCreate(true, false));
        assertFalse(WorldPlans.shouldCreate(true, true), "an existing plan is never replaced");
        assertFalse(WorldPlans.shouldCreate(false, false), "a Default world gets no plan");
        assertFalse(WorldPlans.shouldCreate(false, true));
    }

    @Test
    void verificationJudgesGeneratedTerrainByThePlannedRules() {
        assertTrue(SiteVerifier.judge(TOWN, TOWN.predicted()).isEmpty(), "the predicted terrain passes");
        assertTrue(SiteVerifier.judge(TOWN, new SiteMetrics(0.82, 32, 80, 0.2, Optional.of(0.5))).isEmpty(), "within tolerance");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(0.5, 24, 80, 0.0, Optional.of(1.0))).size(), "half under water fails");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(1.0, 40, 80, 0.0, Optional.of(1.0))).size(), "too rough fails");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(1.0, 20, 80, 0.0, Optional.of(0.3))).size(), "no sea at the waterfront fails");
        PlannedSite lastResort = new PlannedSite(TOWN.id(), TOWN.kind(), TOWN.catalogId(),
                new SiteRules(V01Sites.TOWN_FOOTPRINT, Waterfront.NONE, io.github.nick92461.inhabitedworld.worldgen.planner.TerrainLimits.ANY, 0),
                TOWN.bounds(), TOWN.region(), Optional.empty(), new SiteMetrics(0.5, 10, 70, 0.0, Optional.empty()));
        assertEquals(1, SiteVerifier.judge(lastResort, lastResort.predicted()).size(), "a last-resort site half in water still fails (VALID_GROUND)");
        assertTrue(SiteVerifier.judge(lastResort, new SiteMetrics(0.7, 40, 70, 0.0, Optional.empty())).isEmpty(), "but passes on valid ground");
        assertTrue(SiteVerifier.judgeSpawn(List.of(TOWN, POI), new BlockPos(-150, 70, -240)).isEmpty(), "spawning in town is fine");
        assertEquals(1, SiteVerifier.judgeSpawn(List.of(TOWN, POI), new BlockPos(240, 70, -240)).size(), "spawning inside the POI region is not");
    }

    @Test
    void sampledAreaReachesBeyondTheWaterfront() {
        BlockRect area = SiteVerifier.sampledArea(TOWN);
        assertEquals(TOWN.bounds().minZ() - 48, area.minZ());
        assertEquals(TOWN.bounds().maxZ(), area.maxZ());
        assertEquals(POI.bounds(), SiteVerifier.sampledArea(POI));
    }

    @Test
    void compassNamesEightDirections() {
        assertEquals("north", WorldPlanCommand.compass(0, -10));
        assertEquals("south-east", WorldPlanCommand.compass(10, 10));
        assertEquals("west", WorldPlanCommand.compass(-10, 1));
        assertEquals("north-west", WorldPlanCommand.compass(-10, -9));
    }
}
