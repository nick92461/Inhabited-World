package io.github.nick92461.inhabitedworld.worldstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.github.nick92461.inhabitedworld.worldgen.planner.Footprint;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannerRun;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteKind;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;
import io.github.nick92461.inhabitedworld.worldgen.planner.SitePlanner;
import io.github.nick92461.inhabitedworld.worldgen.planner.SiteRules;
import io.github.nick92461.inhabitedworld.worldgen.planner.TerrainLimits;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldgen.planner.Waterfront;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

import org.junit.jupiter.api.Test;

// The persisted WorldPlan (canon 02 section 7, 08 section 49): sites, planner record, verification, and migration.
class WorldPlanTest {
    private static final UUID ID = UUID.fromString("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11");

    // The Harbour turned a quarter anticlockwise, so its east sea side faces north: 256 wide, 272 deep.
    static final PlannedSite TOWN = new PlannedSite(V01Sites.TOWN, SiteKind.SETTLEMENT, V01Sites.TOWN_CATALOG_ID,
            V01Sites.townRules(Waterfront.REQUIRED),
            BlockRect.ofSize(-272, -368, 256, 272), BlockRect.ofSize(-272, -368, 256, 272).inflate(32), Optional.of(Direction.NORTH),
            new SiteMetrics(0.99, 24, 80, 0.0, Optional.of(1.0), Optional.of(0.9)), Rotation.COUNTERCLOCKWISE_90);
    // A town as planner version 1 made it (P2A step 5, P2B): the square placeholder, unrotated, 48-block strip.
    static final PlannedSite LEGACY_TOWN = new PlannedSite(V01Sites.TOWN, SiteKind.SETTLEMENT, Identifier.parse("inhabited_world:placeholder_town"),
            new SiteRules(new Footprint(256, 256, 32), Waterfront.PREFERRED, new TerrainLimits(0.9, 24, 63, 110, 0.15), 0),
            BlockRect.ofSize(-272, -368, 256, 256), BlockRect.ofSize(-272, -368, 256, 256).inflate(32), Optional.of(Direction.NORTH),
            new SiteMetrics(0.99, 24, 80, 0.0, Optional.of(1.0)));
    static final PlannedSite POI = new PlannedSite(V01Sites.EXTERNAL_POI, SiteKind.POI, V01Sites.POI_CATALOG_ID,
            new SiteRules(V01Sites.POI_FOOTPRINT, Waterfront.NONE, V01Sites.POI_TERRAIN, V01Sites.POI_SPAWN_CLEARANCE),
            BlockRect.ofSize(192, -288, 96, 96), BlockRect.ofSize(192, -288, 96, 96).inflate(16), Optional.empty(),
            new SiteMetrics(1.0, 4, 64, 0.0, Optional.empty()));
    private static final PlannerRun RUN = new PlannerRun(SitePlanner.ALGORITHM, SitePlanner.VERSION, 0, 4490);
    private static final PlannerRun LEGACY_RUN = new PlannerRun(SitePlanner.ALGORITHM, 1, 0, 4490);

    private final WorldPlan plan = WorldPlan.create(ID, PlayableArea.V0_1, RUN, List.of(TOWN, POI));
    private final WorldPlan legacyPlan = WorldPlan.create(ID, PlayableArea.V0_1, LEGACY_RUN, List.of(LEGACY_TOWN, POI));

    private static JsonObject encode(WorldPlan plan) {
        return WorldPlan.CODEC.encodeStart(JsonOps.INSTANCE, plan).getOrThrow().getAsJsonObject();
    }

    @Test
    void savesAndLoadsUnchanged() {
        JsonObject json = encode(this.plan);
        assertEquals(5, json.get("data_version").getAsInt(), "every persisted domain carries a data version");
        assertEquals("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11", json.get("world_id").getAsString());
        assertEquals("inhabited_world:site_planner", json.getAsJsonObject("planner").get("algorithm").getAsString());
        JsonObject town = json.getAsJsonArray("sites").get(0).getAsJsonObject();
        assertEquals("settlement", town.get("kind").getAsString());
        assertEquals("inhabited_world:drachenfels/d1_harbour", town.get("catalog_id").getAsString());
        assertEquals("north", town.get("waterfront").getAsString());
        assertEquals("counterclockwise_90", town.get("rotation").getAsString());
        assertEquals("required", town.getAsJsonObject("rules").get("waterfront").getAsString());
        assertEquals("east", town.getAsJsonObject("rules").get("sea_side").getAsString());
        assertEquals(112, town.getAsJsonObject("rules").get("waterfront_depth").getAsInt());
        assertEquals(0.9, town.getAsJsonObject("predicted_terrain").get("strip_sea").getAsDouble(), 1e-9);
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
        assertEquals(WorldPlan.DATA_VERSION, encode(read).get("data_version").getAsInt(), "it is written back as the current version");
        assertTrue(read.regions().isEmpty(), "a world without sites has no regions: all wilderness");
        assertEquals(read, WorldPlan.CODEC.parse(JsonOps.INSTANCE, encode(read)).getOrThrow());
    }

    @Test
    void refusesWhatItCannotRead() {
        JsonObject newer = encode(this.plan);
        newer.addProperty("data_version", WorldPlan.DATA_VERSION + 1);
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

        JsonObject unturned = encode(this.plan);
        unturned.getAsJsonArray("sites").get(0).getAsJsonObject().remove("rotation");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, unturned).isError(), "an unrotated 272 x 256 build cannot have 256 x 272 bounds");

        JsonObject wrongTurn = encode(this.plan);
        wrongTurn.getAsJsonArray("sites").get(0).getAsJsonObject().addProperty("rotation", "clockwise_90");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, wrongTurn).isError(), "a rotation that turns the sea side away from the waterfront is refused");

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

    // Phase 3: the town placed at its site, with its markers resolved, and the read-back check.
    static PlacedStructure harbour(String... locationIds) {
        List<SemanticLocation> locations = new java.util.ArrayList<>();
        int i = 0;
        for (String id : locationIds) {
            locations.add(new SemanticLocation(id, new BlockPos(-173 + i, 102, -99 - i), 180f, V01Sites.TOWN, "town"));
            i++;
        }
        return new PlacedStructure(V01Sites.TOWN, V01Sites.TOWN_CATALOG_ID, "drachenfels/d1_harbour/v1", Rotation.COUNTERCLOCKWISE_90,
                new BlockPos(-272, 2, -97), locations, new PlacementCheck(true, 264, 264, List.of()));
    }

    @Test
    void placedStructuresAreSavedWithThePlan() {
        WorldPlan placed = this.plan.withStructure(harbour("d1_main_gate", "d1_market_square"));
        JsonObject json = encode(placed);
        JsonObject structure = json.getAsJsonArray("structures").get(0).getAsJsonObject();
        assertEquals("town", structure.get("site").getAsString());
        assertEquals("drachenfels/d1_harbour/v1", structure.get("build").getAsString());
        assertEquals("counterclockwise_90", structure.get("rotation").getAsString());
        JsonObject gate = structure.getAsJsonArray("locations").get(0).getAsJsonObject();
        assertEquals("d1_main_gate", gate.get("id").getAsString());
        assertEquals(180f, gate.get("yaw").getAsFloat());
        assertEquals("town", gate.get("region").getAsString());
        assertTrue(structure.getAsJsonObject("check").get("passed").getAsBoolean());
        assertEquals(placed, WorldPlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(this.plan.sites(), placed.sites(), "placing a build never changes the sites");
        assertEquals(this.plan.regions(), placed.regions());

        SemanticLocations locations = SemanticLocations.of(placed);
        assertEquals(2, locations.size());
        assertEquals(new BlockPos(-173, 102, -99), locations.get("d1_main_gate").orElseThrow().pos());
        assertEquals("town", locations.get("d1_market_square").orElseThrow().site());
        assertEquals(List.of("d1_main_gate", "d1_market_square"), locations.ofSite("town").stream().map(SemanticLocation::id).toList());
        assertTrue(locations.get("blackwood_smithy").isEmpty());
        assertEquals(0, SemanticLocations.of(this.plan).size(), "a plan without placed structures has no locations");
    }

    @Test
    void placedStructuresAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> this.plan.withStructure(harbour("d1_quay", "d1_quay")), "location ids are unique");
        WorldPlan placed = this.plan.withStructure(harbour("d1_quay"));
        assertThrows(IllegalArgumentException.class, () -> placed.withStructure(harbour("d1_pier_north")), "one structure per site");
        PlacedStructure h = harbour("d1_quay");
        assertThrows(IllegalArgumentException.class, () -> this.plan.withStructure(new PlacedStructure("elsewhere", h.catalogId(), h.build(),
                h.rotation(), h.origin(), List.of(), h.check())), "a structure stands on one of the plan's sites");
        assertThrows(IllegalArgumentException.class, () -> this.plan.withStructure(new PlacedStructure(V01Sites.TOWN, V01Sites.POI_CATALOG_ID,
                h.build(), h.rotation(), h.origin(), List.of(), h.check())), "the site's own build is placed there");
        assertThrows(IllegalArgumentException.class, () -> this.plan.withStructure(new PlacedStructure(V01Sites.TOWN, h.catalogId(), h.build(),
                Rotation.NONE, h.origin(), List.of(), h.check())), "turned as the site says");

        JsonObject strayLocation = encode(placed);
        strayLocation.getAsJsonArray("structures").get(0).getAsJsonObject().getAsJsonArray("locations").get(0).getAsJsonObject()
                .addProperty("site", V01Sites.EXTERNAL_POI);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, strayLocation).isError(), "a location belongs to its structure's site");
    }

    /*
      A planner-version-2 world (data version 4, before builds were placed): it reads with no placed structures, every
      other field exactly as saved, and is written back once as version 5. Nothing is ever placed into it (no
      PlacementState, see TownPlacement), so it stays without a town.
     */
    @Test
    void aVersionFourPlanReadsWithoutPlacedStructures() {
        SiteVerification verification = new SiteVerification(true, List.of(
                new SiteVerification.SiteCheck(V01Sites.TOWN, true, new SiteMetrics(1.0, 23, 86, 0.0, Optional.of(1.0), Optional.of(0.54)), List.of()),
                new SiteVerification.SiteCheck(V01Sites.EXTERNAL_POI, true, new SiteMetrics(0.97, 2, 64, 0.0, Optional.empty()), List.of())),
                Optional.of(new BlockPos(0, 70, 0)), List.of());
        JsonObject v4 = encode(this.plan.withVerification(verification));
        v4.addProperty("data_version", 4);
        assertFalse(v4.has("structures"), "a plan without placed structures writes none");

        WorldPlan read = WorldPlan.CODEC.parse(JsonOps.INSTANCE, v4).getOrThrow();
        assertEquals(WorldPlan.DATA_VERSION, read.dataVersion());
        assertEquals(this.plan.withVerification(verification), read, "sites, regions and verification read exactly as saved");
        assertTrue(read.structures().isEmpty());
        assertTrue(WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, v4).getOrThrow().isDirty(), "written back once as version 5");

        JsonObject v4WithStructures = encode(this.plan.withStructure(harbour("d1_quay")));
        v4WithStructures.addProperty("data_version", 4);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, v4WithStructures).isError(), "version 4 had no placed structures");
    }

    /*
      A step-5 world (data version 2) is migrated once: its regions are derived from its saved sites exactly as a new
      plan's are, it reads as version 3, and the saved data is marked to be written back.
     */
    @Test
    void aVersionTwoPlanIsMigratedToRegions() {
        JsonObject v2 = encode(this.legacyPlan);
        v2.addProperty("data_version", 2);
        v2.remove("regions");
        WorldPlan read = WorldPlan.CODEC.parse(JsonOps.INSTANCE, v2).getOrThrow();
        assertEquals(WorldPlan.DATA_VERSION, read.dataVersion());
        assertEquals(this.legacyPlan, read, "a migrated plan equals the plan the same sites make today");

        WorldPlanData migrated = WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, v2).getOrThrow();
        assertTrue(migrated.isDirty(), "a migrated plan is written back at the next save, so the migration runs once");
        WorldPlanData current = WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, encode(this.plan)).getOrThrow();
        assertFalse(current.isDirty(), "a current plan is not rewritten");
    }

    /*
      A P2B world (data version 3, planner version 1): nothing in it changes. Its sites read with planner version 1's
      waterfront rules (no sea side, a 48-block strip, no sea needed in it, no rotation), which is how it was planned;
      its regions and verification are kept as saved; only the version number it is written back with moves on.
     */
    @Test
    void aVersionThreePlanReadsAsItWasPlanned() {
        JsonObject v3 = encode(this.legacyPlan);
        v3.addProperty("data_version", 3);
        JsonObject site = v3.getAsJsonArray("sites").get(0).getAsJsonObject();
        for (String newField : new String[]{"rotation"}) {
            assertFalse(site.has(newField), "a planner version 1 site writes no " + newField);
        }
        for (String newField : new String[]{"sea_side", "waterfront_depth", "min_strip_sea"}) {
            assertFalse(site.getAsJsonObject("rules").has(newField), "planner version 1 rules write no " + newField);
        }
        assertFalse(site.getAsJsonObject("predicted_terrain").has("strip_sea"));
        // A verification recorded before the upgrade stays exactly as it was.
        SiteVerification verification = new SiteVerification(true, List.of(
                new SiteVerification.SiteCheck(V01Sites.TOWN, true, new SiteMetrics(1.0, 20, 80, 0.0, Optional.of(0.9)), List.of()),
                new SiteVerification.SiteCheck(V01Sites.EXTERNAL_POI, true, new SiteMetrics(1.0, 5, 64, 0.0, Optional.empty()), List.of())),
                Optional.of(new BlockPos(-32, 72, -32)), List.of());
        v3.add("verification", SiteVerification.CODEC.encodeStart(JsonOps.INSTANCE, verification).getOrThrow());

        WorldPlan read = WorldPlan.CODEC.parse(JsonOps.INSTANCE, v3).getOrThrow();
        assertEquals(WorldPlan.DATA_VERSION, read.dataVersion());
        assertEquals(this.legacyPlan.withVerification(verification), read, "sites, regions and verification read exactly as saved");
        PlannedSite town = read.site(V01Sites.TOWN);
        assertEquals(Rotation.NONE, town.rotation());
        assertEquals(Optional.empty(), town.rules().seaSide());
        assertEquals(48, town.rules().waterfrontDepth());
        assertEquals(0.0, town.rules().minStripSea());
        assertEquals(1, read.planner().orElseThrow().version(), "the plan still names the planner that made it");
        assertEquals(town.bounds().minZ() - 48, SiteVerifier.sampledArea(town).minZ(), "an unverified old site is verified with its own 48-block strip");

        WorldPlanData migrated = WorldPlanData.TYPE.codec().parse(JsonOps.INSTANCE, v3).getOrThrow();
        assertTrue(migrated.isDirty(), "it is written back once as the current data version");
        JsonObject rewritten = encode(read);
        rewritten.addProperty("data_version", 3);
        assertEquals(v3, rewritten, "and nothing but the version number changes");
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
        assertTrue(SiteVerifier.judge(TOWN, new SiteMetrics(0.82, 40, 80, 0.2, Optional.of(0.5), Optional.of(0.4))).isEmpty(), "within tolerance");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(0.5, 24, 80, 0.0, Optional.of(1.0), Optional.of(1.0))).size(), "half under water fails");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(1.0, 48, 80, 0.0, Optional.of(1.0), Optional.of(1.0))).size(), "too rough fails");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(1.0, 20, 80, 0.0, Optional.of(0.3), Optional.of(1.0))).size(), "no sea at the waterfront fails");
        assertEquals(1, SiteVerifier.judge(TOWN, new SiteMetrics(1.0, 20, 80, 0.0, Optional.of(1.0), Optional.of(0.2))).size(), "a harbour strip on land fails");
        assertTrue(SiteVerifier.judge(LEGACY_TOWN, new SiteMetrics(1.0, 20, 80, 0.0, Optional.of(1.0), Optional.of(0.0))).isEmpty(),
                "planner version 1's shore strip needs no sea");
        PlannedSite lastResort = new PlannedSite(TOWN.id(), TOWN.kind(), TOWN.catalogId(),
                new SiteRules(V01Sites.TOWN_FOOTPRINT, Waterfront.NONE, TerrainLimits.ANY, 0),
                TOWN.bounds(), TOWN.region(), Optional.empty(), new SiteMetrics(0.5, 10, 70, 0.0, Optional.empty()), TOWN.rotation());
        assertEquals(1, SiteVerifier.judge(lastResort, lastResort.predicted()).size(), "a last-resort site half in water still fails (VALID_GROUND)");
        assertTrue(SiteVerifier.judge(lastResort, new SiteMetrics(0.7, 40, 70, 0.0, Optional.empty())).isEmpty(), "but passes on valid ground");
        assertTrue(SiteVerifier.judgeSpawn(List.of(TOWN, POI), new BlockPos(-150, 70, -240)).isEmpty(), "spawning in town is fine");
        assertEquals(1, SiteVerifier.judgeSpawn(List.of(TOWN, POI), new BlockPos(240, 70, -240)).size(), "spawning inside the POI region is not");
    }

    @Test
    void sampledAreaReachesBeyondTheWaterfront() {
        BlockRect area = SiteVerifier.sampledArea(TOWN);
        assertEquals(TOWN.bounds().minZ() - 112, area.minZ(), "the Harbour's own 112-block depth");
        assertEquals(TOWN.bounds().maxZ(), area.maxZ());
        assertEquals(TOWN.bounds().minX(), area.minX());
        assertEquals(TOWN.bounds().maxX(), area.maxX());
        assertEquals(LEGACY_TOWN.bounds().minZ() - 48, SiteVerifier.sampledArea(LEGACY_TOWN).minZ());
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
