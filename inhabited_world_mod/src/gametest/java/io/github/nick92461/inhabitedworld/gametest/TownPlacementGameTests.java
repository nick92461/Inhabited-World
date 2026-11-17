package io.github.nick92461.inhabitedworld.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.town.PlacementState;
import io.github.nick92461.inhabitedworld.town.TownBlend;
import io.github.nick92461.inhabitedworld.town.TownColumns;
import io.github.nick92461.inhabitedworld.town.TownManifest;
import io.github.nick92461.inhabitedworld.town.TownPlacement;
import io.github.nick92461.inhabitedworld.town.TownPlacer;
import io.github.nick92461.inhabitedworld.town.TownTransform;
import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;
import io.github.nick92461.inhabitedworld.worldgen.planner.V01Sites;
import io.github.nick92461.inhabitedworld.worldstate.PlacedStructure;
import io.github.nick92461.inhabitedworld.worldstate.SemanticLocation;
import io.github.nick92461.inhabitedworld.worldstate.SemanticLocations;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlan;
import io.github.nick92461.inhabitedworld.worldstate.WorldPlans;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
  Phase 3 part 1: the D1 Harbour placed in this game-test run's own new world (seed 0), by world creation itself
  (TownPlacement, at the start of level preparation), and the rotation, entity and idempotency rules behind it.
 */
public class TownPlacementGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("inhabited_world/gametest");
    // far outside the map, clear of TownTemplateGameTests' pastes at x 3072..3855, z 3072..4127
    private static final int ROTATION_X = 3072;
    private static final int ROTATION_Z = 5120;
    private static final BlockPos ENTITY_SOURCE = new BlockPos(3072, 100, 6144);

    record Placed(WorldPlan plan, PlannedSite site, PlacedStructure structure, TownManifest town, TownColumns columns, TownTransform t) {
    }

    private static Placed placed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorldPlan plan = WorldPlans.get(server);
        helper.assertTrue(plan != null, "a new Inhabited World has a WorldPlan");
        PlannedSite site = plan.site(V01Sites.TOWN);
        PlacedStructure structure = plan.structure(V01Sites.TOWN).orElse(null);
        helper.assertTrue(structure != null, "world creation placed the town and recorded it in the plan; placement state "
                + TownPlacement.state(server.overworld()).entries());
        TownManifest town = TownManifest.find(server.getResourceManager(), structure.build()).orElseThrow();
        TownColumns columns = TownColumns.load(server.getResourceManager(), town.columnsId());
        return new Placed(plan, site, structure, town, columns, TownTransform.forSite(site, columns.width(), columns.depth(), town.baseY()));
    }

    // Every stored block of every tile is in the world, turned by the plan's rotation, block states and block entities included.
    @GameTest(maxTicks = 400)
    public void theTownIsPlacedAtItsSite(GameTestHelper helper) {
        Placed p = placed(helper);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(p.structure().build().equals("drachenfels/d1_harbour/v1"), "the Harbour's v1 build was placed: " + p.structure().build());
        helper.assertTrue(p.structure().rotation() == p.site().rotation(), "turned as the site says");
        helper.assertTrue(p.structure().check().passed(), "the read-back check after placement passed: " + p.structure().check().describe());
        helper.assertTrue(TownPlacement.state(level).isComplete(), "placement is complete");

        Match m = new Match();
        for (TownManifest.Tile tile : p.town().tiles()) {
            StructureTemplate template = TownPlacer.loadTile(level.getServer(), tile.id());
            compare(level, template, p.t(), tile.offset(), p.columns(), m);
        }
        LOGGER.info("placed town vs templates ({}): {} stored blocks, {} mismatches, {} block entities ({} missing, {} differing), {} facing blocks checked",
                p.site().rotation().getSerializedName(), m.blocks, m.mismatches, m.blockEntities, m.blockEntitiesMissing, m.blockEntitiesDiffer, m.facings);
        helper.assertTrue(m.blocks > 1_500_000, "most of the town's 1.63M blocks are in stored columns: " + m.blocks);
        helper.assertTrue(m.mismatches == 0, m.mismatches + " of " + m.blocks + " blocks differ, e.g. " + m.examples);
        helper.assertTrue(m.blockEntitiesMissing == 0 && m.blockEntitiesDiffer == 0, m.blockEntitiesMissing + " block entities missing, "
                + m.blockEntitiesDiffer + " differ, e.g. " + m.examples);
        helper.assertTrue(m.facings > 1000, "the town has facing blocks (stairs, doors) to check: " + m.facings);
        helper.succeed();
    }

    // One tile placed under each of the four rotations: every block where the transform says, every state turned with it.
    @GameTest(maxTicks = 200)
    public void aTileMatchesItsTemplateUnderEveryRotation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownManifest town = TownManifest.find(level.getServer().getResourceManager(), TownTemplateGameTests.TOWN).orElseThrow();
        TownManifest.Tile tile = town.tiles().stream().max(Comparator.comparingInt(TownManifest.Tile::blocks)).orElseThrow();
        int k = 0;
        for (Rotation rotation : Rotation.values()) {
            BlockRect bounds = BlockRect.ofSize(ROTATION_X + k * 64, ROTATION_Z, tile.size().getX(), tile.size().getZ());
            TownTransform t = TownTransform.of(bounds, rotation, tile.size().getX(), tile.size().getZ(), town.baseY());
            TownTemplateGameTests.loadChunks(level, new BlockPos(bounds.minX(), 0, bounds.minZ()), bounds.sizeX(), bounds.sizeZ());
            StructureTemplate template = TownPlacer.loadTile(level.getServer(), tile.id());
            TownPlacer.placeTemplate(level, template, t, BlockPos.ZERO, RandomSource.create(1), new int[1]);
            Match m = new Match();
            compare(level, template, t, BlockPos.ZERO, null, m);
            helper.assertTrue(m.blocks == tile.blocks(), rotation + ": compared " + m.blocks + " of " + tile.blocks() + " blocks");
            helper.assertTrue(m.mismatches == 0, rotation + ": " + m.mismatches + " blocks differ, e.g. " + m.examples);
            helper.assertTrue(m.blockEntitiesMissing == 0 && m.blockEntitiesDiffer == 0, rotation + ": block entities " + m.examples);
            helper.assertTrue(m.facings > 0, rotation + ": the tile has blocks with a facing to check");
            k++;
        }
        helper.succeed();
    }

    // Opening the world again (or calling placement again) places nothing: a placed town is never placed twice.
    @GameTest
    public void placingAgainChangesNothing(GameTestHelper helper) {
        Placed p = placed(helper);
        ServerLevel level = helper.getLevel();
        PlacementState state = TownPlacement.state(level);
        helper.assertTrue(state.entries().stream().allMatch(PlacementState.Entry::isDone), "every scheduled build is done: " + state.entries());
        // a player's change inside the town: placement must not undo it
        TownManifest.Marker square = p.town().markers().stream().filter(mk -> mk.id().equals("d1_market_square")).findFirst().orElseThrow();
        BlockPos at = p.t().toWorld(square.pos()).above(3);
        BlockState before = level.getBlockState(at);
        level.setBlock(at, Blocks.GOLD_BLOCK.defaultBlockState(), TownPlacer.FLAGS);
        int structures = p.plan().structures().size();
        TownPlacement.placeIfNeeded(level.getServer());
        boolean kept = level.getBlockState(at).is(Blocks.GOLD_BLOCK);
        level.setBlock(at, before, TownPlacer.FLAGS);
        helper.assertTrue(kept, "placing again left the world alone");
        helper.assertTrue(WorldPlans.get(level.getServer()).structures().size() == structures, "and recorded nothing new");
        helper.succeed();
    }

    /*
      A world closed in the middle of creation repeats the step it was in. Repeating the blend on the finished world
      must change no column (it computes the same heights from the same edges), and repeating a tile must leave it as
      the template says.
     */
    @GameTest(maxTicks = 200)
    public void repeatingAStepGivesTheSameWorld(GameTestHelper helper) {
        Placed p = placed(helper);
        ServerLevel level = helper.getLevel();
        BlockRect ring = p.site().region().inflate(1);
        // heights read from unloaded chunks are the world's floor: load them first, as placement does
        TownTemplateGameTests.loadChunks(level, new BlockPos(ring.minX(), 0, ring.minZ()), ring.sizeX(), ring.sizeZ());
        TownBlend.Field field = TownPlacement.blendField(level, p.columns(), p.t(), p.site().region());
        int free = 0;
        int changed = 0;
        List<String> examples = new ArrayList<>();
        for (int z = p.site().region().minZ(); z <= p.site().region().maxZ(); z++) {
            for (int x = p.site().region().minX(); x <= p.site().region().maxX(); x++) {
                if (!field.free(x, z)) {
                    continue;
                }
                free++;
                int ground = TownPlacer.groundY(level, x, z);
                if (TownBlend.needsRebuild(ground, field.ground(x, z))) {
                    changed++;
                    if (examples.size() < 5) {
                        examples.add(x + ", " + z + ": ground " + ground + ", the blend says " + field.ground(x, z));
                    }
                }
            }
        }
        LOGGER.info("blend repeated: {} blend columns, {} would change, steepest step {}", free, changed, TownBlend.steepestStep(field));
        helper.assertTrue(free > 30_000, "the border and the town's own blend columns are blended: " + free);
        helper.assertTrue(changed == 0, changed + " blend columns would change on a repeat, e.g. " + examples);

        // a tile on the sea side: RegionGameTests builds its town chamber on the inland side, above the roofs, and
        // placing a tile clears everything above its columns
        int index = -1;
        for (int i = 0; i < p.town().tiles().size(); i++) {
            TownManifest.Tile candidate = p.town().tiles().get(i);
            if (candidate.offset().getX() >= 192 && (index < 0 || candidate.blocks() > p.town().tiles().get(index).blocks())) {
                index = i;
            }
        }
        helper.assertTrue(index >= 0, "the town has sea-side tiles");
        TownManifest.Tile tile = p.town().tiles().get(index);
        TownPlacer.placeTile(level, p.town(), p.columns(), p.t(), index, level.getMinY());
        Match m = new Match();
        compare(level, TownPlacer.loadTile(level.getServer(), tile.id()), p.t(), tile.offset(), p.columns(), m);
        helper.assertTrue(m.mismatches == 0, "a repeated tile matches its template: " + m.mismatches + " differ, e.g. " + m.examples);
        helper.succeed();
    }

    // The manifest's markers are the plan's semantic locations, where the rotation puts them, in the town's region.
    @GameTest
    public void markersLandWhereTheRotationSays(GameTestHelper helper) {
        Placed p = placed(helper);
        SemanticLocations registry = SemanticLocations.of(helper.getLevel().getServer());
        helper.assertTrue(registry.size() == p.town().markers().size(), "one location per marker: " + registry.size() + " of " + p.town().markers().size());
        for (TownManifest.Marker m : p.town().markers()) {
            SemanticLocation l = registry.get(m.id()).orElse(null);
            helper.assertTrue(l != null, "marker " + m.id() + " is a semantic location");
            helper.assertTrue(l.pos().equals(p.t().toWorld(m.pos())), m.id() + " at " + l.pos().toShortString() + ", the rotation says " + p.t().toWorld(m.pos()));
            helper.assertTrue(Math.abs(l.yaw() - p.t().yaw(m.yaw())) < 1e-3, m.id() + " faces " + l.yaw() + ", the rotation says " + p.t().yaw(m.yaw()));
            helper.assertTrue(p.site().bounds().contains(l.pos().getX(), l.pos().getZ()), m.id() + " lies inside the town's bounds");
            helper.assertTrue(l.site().equals(V01Sites.TOWN) && l.region().equals("town"), m.id() + " belongs to the town and its region: " + l);
        }
        helper.succeed();
    }

    /*
      When the world opened, no entity was left in the town's region: nothing came with the templates and the generated
      animals were removed. (Counted by placement itself, before any test ran: RegionGameTests spawns zombies in town.)
     */
    @GameTest
    public void theTownHoldsNoForbiddenEntities(GameTestHelper helper) {
        placed(helper);
        TownPlacement.Result r = TownPlacement.results().getFirst();
        helper.assertTrue(r.entitiesLeft().isEmpty(), "entities left in the town's region after placement: " + r.entitiesLeft());
        helper.assertTrue(r.templateEntitiesDropped().isEmpty(), "the D1 templates carry no entities: " + r.templateEntitiesDropped());
        helper.succeed();
    }

    // A template that does carry a villager, a zombie, a witch and a pig: placing it as town keeps none of them.
    @GameTest
    public void placementDropsEntitiesATownMayNotCarry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownTemplateGameTests.loadChunks(level, ENTITY_SOURCE, 96, 16);
        List<EntityType<?>> types = List.of(EntityTypes.VILLAGER, EntityTypes.ZOMBIE, EntityTypes.WITCH, EntityTypes.PIG);
        for (int i = 0; i < types.size(); i++) {
            level.setBlock(ENTITY_SOURCE.offset(i * 2, -1, 0), Blocks.STONE.defaultBlockState(), TownPlacer.FLAGS);
            Entity e = types.get(i).spawn(level, ENTITY_SOURCE.offset(i * 2, 0, 0), EntitySpawnReason.COMMAND);
            helper.assertTrue(e != null, "spawned " + types.get(i));
        }
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, ENTITY_SOURCE.offset(0, -1, 0), new net.minecraft.core.Vec3i(8, 3, 1), true, List.of());
        for (Entity e : level.getEntities((Entity) null, new AABB(ENTITY_SOURCE).inflate(10), e -> !(e instanceof Player))) {
            e.discard();
        }
        BlockPos target = ENTITY_SOURCE.offset(64, 0, 0);
        TownTransform t = TownTransform.of(BlockRect.ofSize(target.getX(), target.getZ(), 1, 8), Rotation.CLOCKWISE_90, 8, 1, target.getY() - 1);
        int[] kept = new int[1];
        Map<String, Integer> dropped = TownPlacer.placeTemplate(level, template, t, BlockPos.ZERO, RandomSource.create(1), kept);
        helper.assertTrue(kept[0] == 0, "no entity type is allowed in a town template yet, kept " + kept[0]);
        helper.assertTrue(dropped.size() == types.size(), "all four were dropped: " + dropped);
        List<Entity> placed = level.getEntities((Entity) null, new AABB(target).inflate(10), e -> !(e instanceof Player));
        helper.assertTrue(placed.isEmpty(), "nothing came along with the template: " + placed);
        for (EntityType<?> type : List.of(EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER, EntityTypes.WITCH, EntityTypes.ZOMBIE,
                EntityTypes.PILLAGER, EntityTypes.EVOKER, EntityTypes.CREEPER)) {
            helper.assertTrue(TownPlacer.forbiddenInTown(type) && !TownPlacer.allowedInTown(type), type + " is never allowed in a town");
        }
        helper.succeed();
    }

    // Placement's numbers for this world, for the report (seed 0 here; -PgameTestSeed=20261029 measures the test seed).
    @GameTest
    public void placementReportsItsNumbers(GameTestHelper helper) {
        Placed p = placed(helper);
        List<TownPlacement.Result> results = TownPlacement.results();
        helper.assertTrue(results.size() == 1 && results.getFirst().finished(), "this run placed the town once: " + results.size());
        TownPlacement.Result r = results.getFirst();
        LOGGER.info("town placement on seed {}: site centre {}, {}, bounds {}, turned {}; {}", helper.getLevel().getSeed(), p.site().centerX(),
                p.site().centerZ(), p.site().bounds(), p.site().rotation().getSerializedName(), r.describe());
        helper.assertTrue(r.tiles() == p.town().tiles().size(), "every tile placed in one go: " + r.tiles());
        helper.assertTrue(r.blendColumns() > 30_000, "blend columns: " + r.blendColumns());
        helper.succeed();
    }

    /*
      Top-down pictures of the placed town and its blend, for the report (build/reports/town-topdown.png and
      town-heights.png): map colours shaded by slope, and ground heights with a contour every 4 blocks. Red marks the
      town's bounds, yellow its region (the blend border's outer edge). Also logs the steepest steps of the blend.
     */
    @GameTest(maxTicks = 200)
    public void writesTopDownImages(GameTestHelper helper) throws java.io.IOException {
        Placed p = placed(helper);
        ServerLevel level = helper.getLevel();
        BlockRect bounds = p.site().bounds();
        BlockRect region = p.site().region();
        BlockRect area = region.inflate(32);
        TownTemplateGameTests.loadChunks(level, new BlockPos(area.minX(), 0, area.minZ()), area.sizeX(), area.sizeZ());
        int w = area.sizeX();
        int d = area.sizeZ();
        int[] top = new int[w * d];
        int[] ground = new int[w * d];
        java.awt.image.BufferedImage map = new java.awt.image.BufferedImage(w, d, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.image.BufferedImage heights = new java.awt.image.BufferedImage(w, d, java.awt.image.BufferedImage.TYPE_INT_RGB);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int wx = area.minX() + x;
                int wz = area.minZ() + z;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                top[z * w + x] = y;
                ground[z * w + x] = TownPlacer.groundY(level, wx, wz);
                BlockState state = level.getBlockState(pos.set(wx, y, wz));
                int north = z > 0 ? top[(z - 1) * w + x] : y;
                net.minecraft.world.level.material.MapColor.Brightness b = y > north ? net.minecraft.world.level.material.MapColor.Brightness.HIGH
                        : y < north ? net.minecraft.world.level.material.MapColor.Brightness.LOW : net.minecraft.world.level.material.MapColor.Brightness.NORMAL;
                int abgr = state.getMapColor(level, pos).calculateARGBColor(b);
                map.setRGB(x, z, abgr & 0xffffff);
                int g = ground[z * w + x];
                int shade = Math.max(0, Math.min(255, (g - 30) * 3));
                int rgb = g < level.getSeaLevel() - 1 ? (shade / 3) << 8 | Math.min(255, 80 + shade) : shade << 16 | shade << 8 | shade;
                if (g % 4 == 0) {
                    rgb = 0x804020;
                }
                heights.setRGB(x, z, rgb);
            }
        }
        for (java.awt.image.BufferedImage img : List.of(map, heights)) {
            outline(img, area, bounds, 0xff0000);
            outline(img, area, region, 0xffff00);
        }
        String dir = System.getProperty("inhabited_world.dev.reportDir");
        if (dir != null) {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
            javax.imageio.ImageIO.write(scale(map, 2), "png", java.nio.file.Path.of(dir, "town-topdown.png").toFile());
            javax.imageio.ImageIO.write(scale(heights, 2), "png", java.nio.file.Path.of(dir, "town-heights.png").toFile());
        }

        // the steepest steps between a blend column and any neighbour, where they are
        TownBlend.Field field = TownPlacement.blendField(level, p.columns(), p.t(), region);
        List<int[]> steps = new ArrayList<>();
        int[] histogram = new int[8];
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                if (!field.free(x, z)) {
                    continue;
                }
                int worst = 0;
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] n : nb) {
                    int nx = x + n[0];
                    int nz = z + n[1];
                    worst = Math.max(worst, Math.abs(ground[(z - area.minZ()) * w + x - area.minX()] - ground[(nz - area.minZ()) * w + nx - area.minX()]));
                }
                histogram[Math.min(7, worst)]++;
                steps.add(new int[]{worst, x, z});
            }
        }
        steps.sort((a, b) -> Integer.compare(b[0], a[0]));
        List<String> worst = new ArrayList<>();
        for (int i = 0; i < Math.min(8, steps.size()); i++) {
            int[] st = steps.get(i);
            int[] tc = p.t().toTemplate(st[1], st[2]);
            worst.add(st[0] + " at " + st[1] + ", " + st[2] + (tc == null ? " (border)" : " (template " + tc[0] + ", " + tc[1] + ")"));
        }
        LOGGER.info("blend step histogram (largest step to a neighbour, 0..6, 7+ blocks): {}; steepest: {}", java.util.Arrays.toString(histogram), worst);

        // the water surface: the town's stored sea against the generated sea around the region
        Map<Integer, Integer> townWater = new java.util.TreeMap<>();
        Map<Integer, Integer> outsideWater = new java.util.TreeMap<>();
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int wx = area.minX() + x;
                int wz = area.minZ() + z;
                BlockState topState = level.getBlockState(pos.set(wx, top[z * w + x], wz));
                if (!topState.getFluidState().isSource()) {
                    continue;
                }
                int[] tc = p.t().toTemplate(wx, wz);
                if (tc != null && p.columns().zone(tc[0], tc[1]) == TownColumns.SEA) {
                    townWater.merge(top[z * w + x], 1, Integer::sum);
                } else if (!region.contains(wx, wz)) {
                    outsideWater.merge(top[z * w + x], 1, Integer::sum);
                }
            }
        }
        LOGGER.info("water surface y (top water block): town sea columns {}, generated water outside the region {}", townWater, outsideWater);
        helper.succeed();
    }

    private static void outline(java.awt.image.BufferedImage img, BlockRect area, BlockRect r, int rgb) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            img.setRGB(x - area.minX(), r.minZ() - area.minZ(), rgb);
            img.setRGB(x - area.minX(), r.maxZ() - area.minZ(), rgb);
        }
        for (int z = r.minZ(); z <= r.maxZ(); z++) {
            img.setRGB(r.minX() - area.minX(), z - area.minZ(), rgb);
            img.setRGB(r.maxX() - area.minX(), z - area.minZ(), rgb);
        }
    }

    private static java.awt.image.BufferedImage scale(java.awt.image.BufferedImage img, int by) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(img.getWidth() * by, img.getHeight() * by, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(img, 0, 0, img.getWidth() * by, img.getHeight() * by, null);
        g.dispose();
        return out;
    }

    static final class Match {
        long blocks;
        long mismatches;
        long blockEntities;
        long blockEntitiesMissing;
        long blockEntitiesDiffer;
        long facings;
        final List<String> examples = new ArrayList<>();
    }

    // Compares a template's blocks (only stored columns when `columns` is given) with the world under the transform.
    static void compare(ServerLevel level, StructureTemplate template, TownTransform t, BlockPos offset, TownColumns columns, Match m) {
        for (StructureTemplate.StructureBlockInfo b : TownPlacer.templateBlocks(template)) {
            BlockPos tp = offset.offset(b.pos());
            if (columns != null && !columns.stored(tp.getX(), tp.getZ())) {
                continue;
            }
            BlockPos at = t.toWorld(tp);
            BlockState want = b.state().rotate(t.rotation());
            BlockState got = level.getBlockState(at);
            m.blocks++;
            if (got != want) {
                m.mismatches++;
                if (m.examples.size() < 5) {
                    m.examples.add(at.toShortString() + " want " + want + " got " + got);
                }
            }
            if (b.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction original = b.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
                if (got.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && got.getValue(BlockStateProperties.HORIZONTAL_FACING) == t.rotation().rotate(original)) {
                    m.facings++;
                } else if (m.examples.size() < 5) {
                    m.mismatches++;
                    m.examples.add(at.toShortString() + " faces " + (got.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? got.getValue(BlockStateProperties.HORIZONTAL_FACING) : "nothing") + ", should face " + t.rotation().rotate(original));
                }
            }
            if (b.nbt() != null) {
                m.blockEntities++;
                BlockEntity be = level.getChunkAt(at).getBlockEntity(at, LevelChunk.EntityCreationType.CHECK);
                if (be == null) {
                    m.blockEntitiesMissing++;
                } else {
                    CompoundTag saved = be.saveWithoutMetadata(level.registryAccess());
                    for (String key : b.nbt().keySet()) {
                        if (!key.equals("id") && !key.equals("LootTableSeed") && !Objects.equals(saved.get(key), b.nbt().get(key))) {
                            m.blockEntitiesDiffer++;
                            if (m.examples.size() < 8) {
                                m.examples.add(at.toShortString() + " " + b.nbt().getStringOr("id", "?") + " '" + key + "' differs");
                            }
                            break;
                        }
                    }
                }
            }
        }
        InhabitedWorld.LOGGER.debug("compared {} blocks", m.blocks);
    }
}
