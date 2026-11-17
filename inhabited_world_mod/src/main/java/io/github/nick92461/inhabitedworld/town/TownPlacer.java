package io.github.nick92461.inhabitedworld.town;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.mixin.StructureTemplateAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/*
  The block work of placing an exported town in a generated world, one tile or one blend column at a time
  (TownPlacement drives it). Every operation here is idempotent: it sets blocks to values that depend only on the
  town's files, the transform and the generated ground outside the site's region, so repeating it after a restart
  gives the same world.

  The placement contract of the export (docs/notes/2026-11-13_d1_export/REPORT.md section 2), per stored column:
    - above its top: air (whatever stood there is cleared)
    - the column itself: exactly the template's blocks (TownPaste.FLAGS: no neighbour or shape updates, no onPlace;
      IGNORE_WATERLOGGING)
    - below its floor: solid ground; air, fluid and replaceable blocks there are filled with stone down to the first
      solid block
  plus, for the ground the town now stands on (P2B known issue, brief item 2):
    - infested blocks become their plain host block, from the floor down to the town region's floor (no silverfish
      in town)
    - generated ores within ORE_DEPTH blocks under a floor or a blend surface become stone or deepslate, so no ore
      shows in a street, a cellar or a cut slope

  Template entities (none in D1 today) are filtered before a tile is placed: only explicitly allowed types are kept,
  and never vanilla's pseudo-humans (villagers, wandering traders, zombie villagers, witches, illagers) or hostiles
  (canon 06 section 13 and the P2B rule that town templates carry none).
 */
public final class TownPlacer {
    public static final int FLAGS = TownPaste.FLAGS;
    static final int ORE_DEPTH = 8;
    private static final int MAX_FILL = 384;

    /** Entity types a town template may bring along. None so far: every template entity is dropped. */
    static final Set<EntityType<?>> ALLOWED_TEMPLATE_ENTITIES = Set.of();
    // the same tag the spawn rules use for vanilla's pseudo-humans (spawn/SpawnGovernance)
    private static final TagKey<EntityType<?>> PROHIBITED_HUMAN_LIKE = TagKey.create(Registries.ENTITY_TYPE, InhabitedWorld.id("prohibited_human_like"));

    /** What placing one tile did. */
    public record TileResult(long templateBlocks, long cleared, long filled, long groundReplaced, int entitiesKept, Map<String, Integer> entitiesDropped) {
    }

    /** What blending one column did: whether its ground moved, and by how much. */
    public record BlendResult(boolean changed, int delta) {
    }

    private TownPlacer() {
    }

    /** Whether an entity may come with a town template. Pseudo-humans and hostiles never do, even if listed. */
    public static boolean allowedInTown(EntityType<?> type) {
        return ALLOWED_TEMPLATE_ENTITIES.contains(type) && !forbiddenInTown(type);
    }

    /** Vanilla pseudo-humans, illagers and raiders, and every hostile. */
    public static boolean forbiddenInTown(EntityType<?> type) {
        var holder = type.builtInRegistryHolder();
        return holder.is(PROHIBITED_HUMAN_LIKE) || holder.is(EntityTypeTags.ILLAGER) || holder.is(EntityTypeTags.RAIDERS)
                || type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER || type == EntityTypes.WITCH
                || type.getCategory() == MobCategory.MONSTER;
    }

    /*
      Loads one tile straight from the mod's data, data-fixed like vanilla's own loader, without going through the
      StructureTemplateManager's cache (which would keep every tile in memory, ~100 MB for the town). The caller drops
      it once placed.
     */
    public static StructureTemplate loadTile(MinecraftServer server, Identifier tileId) {
        Identifier file = Identifier.fromNamespaceAndPath(tileId.getNamespace(), "structure/" + tileId.getPath() + ".nbt");
        CompoundTag tag;
        try (InputStream in = server.getResourceManager().open(file)) {
            tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read town tile " + file, e);
        }
        StructureTemplate template = new StructureTemplate();
        int version = NbtUtils.getDataVersion(tag, 500);
        template.load(server.registryAccess().lookupOrThrow(Registries.BLOCK),
                DataFixTypes.STRUCTURE.updateToCurrentVersion(server.getFixerUpper(), tag, version));
        return template;
    }

    /** Removes every template entity that may not come with a town; returns the dropped ones by type. */
    public static Map<String, Integer> filterEntities(StructureTemplate template, int[] kept) {
        Map<String, Integer> dropped = new TreeMap<>();
        List<StructureTemplate.StructureEntityInfo> entities = ((StructureTemplateAccessor) template).inhabitedWorld$entityInfoList();
        for (Iterator<StructureTemplate.StructureEntityInfo> it = entities.iterator(); it.hasNext(); ) {
            StructureTemplate.StructureEntityInfo info = it.next();
            String id = info.nbt.getStringOr("id", "?");
            Optional<EntityType<?>> type = Optional.ofNullable(Identifier.tryParse(id)).flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
            if (type.isPresent() && allowedInTown(type.get())) {
                kept[0]++;
            } else {
                it.remove();
                dropped.merge(id, 1, Integer::sum);
            }
        }
        return dropped;
    }

    /*
      Places tile `index` of the town: prepares its stored columns (clear above, fill below, ground cleanup down to
      cleanupMinY), then places the template turned by the transform's rotation. Loot seeds come from the world seed
      and the tile index, so the same world always gets the same chests.
     */
    public static TileResult placeTile(ServerLevel level, TownManifest town, TownColumns columns, TownTransform t, int index, int cleanupMinY) {
        TownManifest.Tile tile = town.tiles().get(index);
        StructureTemplate template = loadTile(level.getServer(), tile.id());
        if (!template.getSize().equals(tile.size())) {
            throw new IllegalStateException("tile " + tile.id() + " is " + template.getSize() + ", the manifest says " + tile.size());
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long cleared = 0;
        long filled = 0;
        long replaced = 0;
        int minY = level.getMinY();
        for (int tz = tile.offset().getZ(); tz < tile.offset().getZ() + tile.size().getZ(); tz++) {
            for (int tx = tile.offset().getX(); tx < tile.offset().getX() + tile.size().getX(); tx++) {
                if (tx >= columns.width() || tz >= columns.depth() || !columns.stored(tx, tz)) {
                    continue;
                }
                int wx = t.worldX(tx, tz);
                int wz = t.worldZ(tx, tz);
                int top = t.worldY(columns.top(tx, tz));
                int floor = t.worldY(columns.floor(tx, tz));
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                for (int y = top + 1; y < surface; y++) {
                    if (!level.getBlockState(p.set(wx, y, wz)).isAir()) {
                        level.setBlock(p, air, FLAGS);
                        cleared++;
                    }
                }
                filled += fillDown(level, p, wx, floor - 1, wz, minY);
                replaced += cleanGround(level, p, wx, wz, floor - 1, Math.max(minY, cleanupMinY), floor - 1 - ORE_DEPTH);
            }
        }

        int[] kept = {0};
        Map<String, Integer> dropped = placeTemplate(level, template, t, tile.offset(), RandomSource.create(level.getSeed() * 31 + index), kept);
        return new TileResult(tile.blocks(), cleared, filled, replaced, kept[0], dropped);
    }

    /*
      Places a template whose corner sits at template offset `offset`, turned by the transform's rotation, after dropping
      the entities a town may not carry (returned by type; kept[0] counts the kept ones).
     */
    public static Map<String, Integer> placeTemplate(ServerLevel level, StructureTemplate template, TownTransform t, BlockPos offset,
                                                     RandomSource random, int[] kept) {
        Map<String, Integer> dropped = filterEntities(template, kept);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(t.rotation())
                .setIgnoreEntities(kept[0] == 0)
                .setFinalizeEntities(false)
                .setKnownShape(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        BlockPos at = t.tileOrigin(offset);
        if (!template.placeInWorld(level, at, at, settings, random, FLAGS)) {
            throw new IllegalStateException("a town template did not place at " + at.toShortString());
        }
        return dropped;
    }

    /*
      Gives one blend column its new ground y `g` (TownBlend), with water up to waterTop above ground below it. A column
      whose ground is already within TownBlend.KEEP_WITHIN of g is left exactly as generated (with its plants and trees). Otherwise, from the top:
      air or water above g, the surface block at g with three blocks of subsoil under it (grass and dirt on land, with
      snow on it where the biome is cold enough; sand at the shore and on a shallow seabed; gravel deeper down), stone filled down through any air or fluid below,
      and the ground cleanup under it.
     */
    public static BlendResult blendColumn(ServerLevel level, int x, int z, int g, int waterTop) {
        int s = groundY(level, x, z);
        if (!TownBlend.needsRebuild(s, g)) {
            return new BlendResult(false, 0);
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, waterTop);
        for (int y = top; y > g; y--) {
            BlockState want = y <= waterTop ? water : air;
            if (level.getBlockState(p.set(x, y, z)) != want) {
                level.setBlock(p, want, FLAGS);
            }
        }
        BlockState surface;
        BlockState under;
        if (g >= waterTop + 2) {
            surface = Blocks.GRASS_BLOCK.defaultBlockState();
            under = Blocks.DIRT.defaultBlockState();
        } else if (g >= waterTop - 6) {
            surface = Blocks.SAND.defaultBlockState();
            under = Blocks.SAND.defaultBlockState();
        } else {
            surface = Blocks.GRAVEL.defaultBlockState();
            under = Blocks.GRAVEL.defaultBlockState();
        }
        // land in a biome cold enough to snow gets snowy grass under a snow layer, like the generated ground around it
        if (surface.is(Blocks.GRASS_BLOCK) && level.getBiome(p.set(x, g + 1, z)).value().coldEnoughToSnow(p, level.getSeaLevel())) {
            surface = surface.setValue(SnowyBlock.SNOWY, true);
            level.setBlock(p, Blocks.SNOW.defaultBlockState(), FLAGS);
        }
        level.setBlock(p.set(x, g, z), surface, FLAGS);
        for (int y = g - 1; y >= g - 3; y--) {
            level.setBlock(p.set(x, y, z), under, FLAGS);
        }
        fillDown(level, p, x, g - 4, z, level.getMinY());
        cleanGround(level, p, x, z, g - 4, g - 4 - ORE_DEPTH, g - 4 - ORE_DEPTH);
        return new BlendResult(true, g - s);
    }

    /*
      The ground under a column: its highest solid, opaque block that is not part of a tree or a plant (logs, leaves,
      mushroom blocks, pumpkins and melons are skipped, as are water, ice, snow layers and vegetation). A seabed reads
      as the ground under its water.
     */
    public static int groundY(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = level.getMinY();
        for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1; y > minY; y--) {
            if (isGround(level.getBlockState(p.set(x, y, z)))) {
                return y;
            }
        }
        return minY;
    }

    static boolean isGround(BlockState state) {
        return state.isSolidRender() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && !state.is(Blocks.BROWN_MUSHROOM_BLOCK) && !state.is(Blocks.RED_MUSHROOM_BLOCK) && !state.is(Blocks.MUSHROOM_STEM)
                && !state.is(Blocks.PUMPKIN) && !state.is(Blocks.MELON) && !state.is(Blocks.BEE_NEST);
    }

    // Fills air, fluid and replaceable blocks at and below y with stone, down to the first solid block.
    private static long fillDown(ServerLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, int minY) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        long n = 0;
        for (int i = 0; i < MAX_FILL && y >= minY; i++, y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (!(s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced())) {
                break;
            }
            level.setBlock(p, stone, FLAGS);
            n++;
        }
        return n;
    }

    /*
      From `from` down to `to` (inclusive): infested blocks become their host block; ores (and raw ore blocks from ore
      veins) become stone, or deepslate when they are deepslate ores, but only down to `oresTo`.
     */
    private static long cleanGround(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z, int from, int to, int oresTo) {
        long n = 0;
        for (int y = from; y >= to; y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            BlockState plain = null;
            if (s.getBlock() instanceof InfestedBlock infested) {
                plain = infested.hostStateByInfested(s);
            } else if (y >= oresTo && (s.is(BlockTags.ORES) || s.is(Blocks.RAW_IRON_BLOCK) || s.is(Blocks.RAW_COPPER_BLOCK) || s.is(Blocks.RAW_GOLD_BLOCK))) {
                plain = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("deepslate")
                        ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            }
            if (plain != null) {
                level.setBlock(p, plain, FLAGS);
                n++;
            }
        }
        return n;
    }

    /** The template's blocks (its only palette), for verification. */
    public static List<StructureTemplate.StructureBlockInfo> templateBlocks(StructureTemplate template) {
        List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).inhabitedWorld$palettes();
        return palettes.isEmpty() ? List.of() : palettes.getFirst().blocks();
    }
}
