package io.github.nick92461.inhabitedworld.town;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/*
  Pastes an exported town into a level, unrotated, with template origin at `origin`. Development use: the workshop
  world, the /inhabited town command and the round-trip game test. Phase 3 placement in generated worlds is a
  separate job built on the same files.

  The placement contract of the exported columns (columns.nbt):
    - above a stored column's top: air (whatever stood there is cleared)
    - the column itself: exactly the template's blocks, floor to top (air and water included)
    - below its floor: solid ground; air or fluid there is filled with stone down to the first solid block
  Blocks are set without neighbour or shape updates or onPlace reactions (they keep the states they had in the
  source), and template
  blocks never take water from the world (IGNORE_WATERLOGGING), so the pasted town matches its templates exactly.

  In a workshop paste, blend columns inside the box are also filled with plain ground at the height of the nearest
  stored column (a rough stand-in for Phase 3's blend), and every marker gets its sign back (markers are not blocks
  of the town; the exporter reads them from these signs).
 */
public final class TownPaste {
    // no neighbour or shape updates and no onPlace reactions (rails re-shaping, fire going out, hoppers switching):
    // the pasted blocks are exactly the template's
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    private static final int MAX_FILL = 384;
    public static final String MARKER_PREFIX = "@";

    public record Result(int tiles, long templateBlocks, long cleared, long filled, long blendColumns, int markers, long millis) {
        @Override
        public String toString() {
            return tiles + " tiles, " + templateBlocks + " template blocks, " + cleared + " blocks cleared above, " + filled
                    + " filled below, " + blendColumns + " blend columns filled, " + markers + " marker signs, " + millis + " ms";
        }
    }

    private TownPaste() {
    }

    public static Result paste(ServerLevel level, TownManifest town, TownColumns columns, BlockPos origin, boolean workshop) {
        long start = System.currentTimeMillis();
        List<StructureTemplate> templates = new ArrayList<>();
        for (TownManifest.Tile tile : town.tiles()) {
            templates.add(level.getStructureTemplateManager().get(tile.id())
                    .orElseThrow(() -> new IllegalStateException("missing structure template " + tile.id())));
        }
        int w = columns.width();
        int d = columns.depth();
        // load every chunk of the box first (pasting into a chunk that is not loaded would generate it mid-paste)
        for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + d - 1) >> 4; cz++) {
            for (int cx = origin.getX() >> 4; cx <= (origin.getX() + w - 1) >> 4; cx++) {
                level.getChunk(cx, cz);
            }
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long cleared = 0;
        long filled = 0;
        int minY = level.getMinY();
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!columns.stored(x, z)) {
                    continue;
                }
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                int top = origin.getY() + columns.top(x, z);
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                for (int y = top + 1; y < surface; y++) {
                    if (!level.getBlockState(p.set(wx, y, wz)).isAir()) {
                        level.setBlock(p, air, FLAGS);
                        cleared++;
                    }
                }
                filled += fillDown(level, p, wx, origin.getY() + columns.floor(x, z) - 1, wz, minY, stone);
            }
        }
        long blend = workshop ? fillBlend(level, columns, origin) : 0;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        long blocks = 0;
        for (int i = 0; i < templates.size(); i++) {
            TownManifest.Tile tile = town.tiles().get(i);
            BlockPos at = origin.offset(tile.offset());
            templates.get(i).placeInWorld(level, at, at, settings, level.getRandom(), FLAGS);
            blocks += tile.blocks();
        }
        int markers = workshop ? placeMarkerSigns(level, town, origin) : 0;
        Result result = new Result(templates.size(), blocks, cleared, filled, blend, markers, System.currentTimeMillis() - start);
        InhabitedWorld.LOGGER.info("[{}] pasted town {} v{} at {}: {}", InhabitedWorld.MOD_ID, town.id(), town.version(), origin.toShortString(), result);
        return result;
    }

    // Fills air and fluid below y (inclusive) with `with`, down to the first solid block.
    private static long fillDown(ServerLevel level, BlockPos.MutableBlockPos p, int x, int y, int z, int minY, BlockState with) {
        long n = 0;
        for (int i = 0; i < MAX_FILL && y >= minY; i++, y--) {
            BlockState s = level.getBlockState(p.set(x, y, z));
            if (!(s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced())) {
                break;
            }
            level.setBlock(p, with, FLAGS);
            n++;
        }
        return n;
    }

    // Workshop only: each blend column gets plain ground at the ground height of its nearest stored column.
    private static long fillBlend(ServerLevel level, TownColumns columns, BlockPos origin) {
        int w = columns.width();
        int d = columns.depth();
        int[] ground = new int[w * d];
        boolean[] seen = new boolean[w * d];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < w * d; i++) {
            if (columns.zone()[i] != TownColumns.BLEND) {
                // land columns hand on their ground; sea columns hand on sea level, so blend next to water stays low
                ground[i] = columns.zone()[i] == TownColumns.SEA ? columns.top()[i] : columns.ground()[i];
                seen[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w;
            int z = i / w;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = x + s[0];
                int nz = z + s[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= d) {
                    continue;
                }
                int j = nz * w + nx;
                if (!seen[j]) {
                    seen[j] = true;
                    ground[j] = ground[i];
                    queue.add(j);
                }
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        long n = 0;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (columns.stored(x, z)) {
                    continue;
                }
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                int g = origin.getY() + ground[z * w + x];
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                for (int y = g + 1; y < surface; y++) {
                    level.setBlock(p.set(wx, y, wz), air, FLAGS);
                }
                fillDown(level, p, wx, g - 1, wz, level.getMinY(), dirt);
                level.setBlock(p.set(wx, g, wz), grass, FLAGS);
                n++;
            }
        }
        return n;
    }

    // One standing oak sign per marker: "@<id>" on the first line, the note on the others.
    static int placeMarkerSigns(ServerLevel level, TownManifest town, BlockPos origin) {
        int n = 0;
        for (TownManifest.Marker m : town.markers()) {
            BlockPos at = origin.offset(m.pos());
            boolean water = !level.getFluidState(at).isEmpty();
            int rotation = Mth.floor(m.yaw() / 22.5f + 0.5f) & 15;
            BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                    .setValue(StandingSignBlock.ROTATION, rotation)
                    .setValue(BlockStateProperties.WATERLOGGED, water);
            level.setBlock(at, sign, Block.UPDATE_CLIENTS);
            BlockEntity be = level.getBlockEntity(at);
            if (be instanceof SignBlockEntity s) {
                SignText.Mutable text = s.getText(SignTextSlot.FRONT).asMutable().setLine(0, Component.literal(MARKER_PREFIX + m.id()));
                List<String> lines = wrap(m.note(), 15, 3);
                for (int i = 0; i < lines.size(); i++) {
                    text.setLine(i + 1, Component.literal(lines.get(i)));
                }
                s.setText(text.asImmutable(), SignTextSlot.FRONT);
                s.setChanged();
                n++;
            }
        }
        return n;
    }

    static List<String> wrap(String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
                if (out.size() == maxLines) {
                    return out;
                }
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty() && out.size() < maxLines) {
            out.add(line.toString());
        }
        return out;
    }
}
