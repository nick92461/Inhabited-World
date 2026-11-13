package io.github.nick92461.inhabitedworld.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.town.TownColumns;
import io.github.nick92461.inhabitedworld.town.TownManifest;
import io.github.nick92461.inhabitedworld.town.TownPaste;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/*
  Track B1 job 2: the exported town templates (tools/buildimport export) work in the game.

  - townTileLoadsAndPlaces: one tile loads through vanilla's StructureTemplateManager, has the size the manifest
    says, and places with every block exactly as the file says (checked against the file read independently with
    NbtIo, not through the template's own data). It also checks block entities came along.
  - townRoundTrip: the whole town pasted with TownPaste (the workshop paste path) at ROUND_TRIP_ORIGIN, then every
    stored block of every tile compared with the world. The world is saved when the test server stops, so the
    offline check can compare the same paste with the original map:
        python -m buildimport roundtrip d1_harbour inhabited_world_mod/build/run/gameTest/world --origin <origin>
    (the origin is written to build/reports/town-roundtrip.txt).
 */
public class TownTemplateGameTests {
    static final String TOWN = "d1_harbour";
    // far outside the 1024 x 1024 map so no other test is touched; chunk-aligned like a workshop paste
    static final BlockPos ROUND_TRIP_ORIGIN = new BlockPos(3072, 0, 3072);
    static final BlockPos TILE_ORIGIN = new BlockPos(3072, 0, 4096);
    // a workshop-style paste (marker signs, blend fill), for the re-export check in tools/buildimport's tests:
    //   python -m buildimport export d1_harbour --from workshop --world <game-test world> --origin 3584,<y>,3072 --out <dir>
    static final BlockPos WORKSHOP_ORIGIN = new BlockPos(3584, 0, 3072);

    @GameTest
    public void townTileLoadsAndPlaces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownManifest town = TownManifest.find(level.getServer().getResourceManager(), TOWN).orElse(null);
        helper.assertTrue(town != null, "the mod's data must hold town " + TOWN);
        TownManifest.Tile tile = town.tiles().stream().max(Comparator.comparingInt(TownManifest.Tile::blocks)).orElseThrow();
        StructureTemplate template = level.getStructureTemplateManager().get(tile.id()).orElse(null);
        helper.assertTrue(template != null, "StructureTemplateManager must load " + tile.id());
        helper.assertTrue(template.getSize().equals(tile.size()), "template size " + template.getSize() + " must be the manifest's " + tile.size());

        BlockPos at = TILE_ORIGIN.atY(town.baseY());
        loadChunks(level, at, tile.size().getX(), tile.size().getZ());

        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        boolean placed = template.placeInWorld(level, at, at, settings, level.getRandom(), TownPaste.FLAGS);
        helper.assertTrue(placed, "placeInWorld must succeed");

        Check check = compareTile(level, tile, at);
        helper.assertTrue(check.blocks == tile.blocks(), "the tile file must hold " + tile.blocks() + " blocks, read " + check.blocks);
        // guards against a vacuous match: an unreadable palette reads as air on both sides
        helper.assertTrue(check.nonAir == tile.nonAir(), "the game must read " + tile.nonAir() + " non-air blocks from the tile, read " + check.nonAir);
        helper.assertTrue(check.mismatches == 0, check.mismatches + " of " + check.blocks + " blocks differ, e.g. " + check.examples);
        helper.assertTrue(check.blockEntitiesMissing == 0, check.blockEntitiesMissing + " of " + check.blockEntities + " block entities missing");
        helper.assertTrue(check.blockEntitiesDiffer == 0, check.blockEntitiesDiffer + " block entities hold other data than the file: " + check.examples);
        helper.assertTrue(check.blockEntities > 0, "the chosen tile should carry block entities");
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public void townRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownManifest town = TownManifest.find(level.getServer().getResourceManager(), TOWN).orElseThrow();
        TownColumns columns = TownColumns.load(level.getServer().getResourceManager(), town.columnsId());
        BlockPos origin = ROUND_TRIP_ORIGIN.atY(town.baseY());
        TownPaste.Result paste = TownPaste.paste(level, town, columns, origin, false);

        long blocks = 0;
        long nonAir = 0;
        long nonAirWanted = 0;
        long mismatches = 0;
        long bes = 0;
        long besMissing = 0;
        long besDiffer = 0;
        List<String> examples = new ArrayList<>();
        for (TownManifest.Tile tile : town.tiles()) {
            Check c = compareTile(level, tile, origin.offset(tile.offset()));
            blocks += c.blocks;
            nonAir += c.nonAir;
            nonAirWanted += tile.nonAir();
            mismatches += c.mismatches;
            bes += c.blockEntities;
            besMissing += c.blockEntitiesMissing;
            besDiffer += c.blockEntitiesDiffer;
            if (examples.size() < 10) {
                examples.addAll(c.examples);
            }
        }
        String report = "town " + town.id() + " v" + town.version() + "\norigin " + origin.getX() + "," + origin.getY() + "," + origin.getZ()
                + "\npaste " + paste + "\nblocks " + blocks + "\nmismatches " + mismatches + "\nblock_entities " + bes
                + "\nblock_entities_missing " + besMissing + "\nexamples " + examples + "\n";
        writeReport("town-roundtrip.txt", report);
        InhabitedWorld.LOGGER.info("[{}] town round trip: {}", InhabitedWorld.MOD_ID, report.replace('\n', ' '));
        helper.assertTrue(blocks == paste.templateBlocks(), "compared " + blocks + " blocks, the tiles hold " + paste.templateBlocks());
        helper.assertTrue(nonAir == nonAirWanted, "the game read " + nonAir + " non-air blocks from the tiles, the manifest says " + nonAirWanted);
        helper.assertTrue(mismatches == 0, mismatches + " of " + blocks + " pasted blocks differ from the templates, e.g. " + examples);
        helper.assertTrue(besMissing == 0, besMissing + " of " + bes + " block entities missing after the paste");
        helper.assertTrue(besDiffer == 0, besDiffer + " of " + bes + " block entities hold other data than the templates, e.g. " + examples);
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public void townWorkshopPaste(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TownManifest town = TownManifest.find(level.getServer().getResourceManager(), TOWN).orElseThrow();
        TownColumns columns = TownColumns.load(level.getServer().getResourceManager(), town.columnsId());
        BlockPos origin = WORKSHOP_ORIGIN.atY(town.baseY());
        TownPaste.Result paste = TownPaste.paste(level, town, columns, origin, true);
        helper.assertTrue(paste.markers() == town.markers().size(), "every marker must get its sign: " + paste.markers() + " of " + town.markers().size());
        for (TownManifest.Marker m : town.markers()) {
            BlockPos at = origin.offset(m.pos());
            BlockEntity be = level.getChunkAt(at).getBlockEntity(at, LevelChunk.EntityCreationType.CHECK);
            helper.assertTrue(be instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                            && sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(0).getString().equals(TownPaste.MARKER_PREFIX + m.id()),
                    "marker " + m.id() + " needs a sign reading " + TownPaste.MARKER_PREFIX + m.id() + " at " + at.toShortString());
        }
        writeReport("town-workshop-paste.txt", "town " + town.id() + " v" + town.version() + "\norigin " + origin.getX() + ","
                + origin.getY() + "," + origin.getZ() + "\npaste " + paste + "\n");
        helper.succeed();
    }

    record Check(long blocks, long nonAir, long mismatches, long blockEntities, long blockEntitiesMissing, long blockEntitiesDiffer, List<String> examples) {
    }

    // Reads the tile's .nbt directly and compares each listed block with the world.
    static Check compareTile(ServerLevel level, TownManifest.Tile tile, BlockPos at) {
        Identifier file = Identifier.fromNamespaceAndPath(tile.id().getNamespace(), "structure/" + tile.id().getPath() + ".nbt");
        CompoundTag root;
        try (InputStream in = level.getServer().getResourceManager().open(file)) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
        HolderGetter<Block> blocks = level.holderLookup(Registries.BLOCK);
        ListTag paletteTag = root.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i));
        }
        ListTag list = root.getListOrEmpty("blocks");
        long mismatches = 0;
        long bes = 0;
        long besMissing = 0;
        long besDiffer = 0;
        long nonAir = 0;
        List<String> examples = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompoundOrEmpty(i);
            ListTag pos = b.getListOrEmpty("pos");
            p.set(at.getX() + pos.getIntOr(0, 0), at.getY() + pos.getIntOr(1, 0), at.getZ() + pos.getIntOr(2, 0));
            BlockState want = palette[b.getIntOr("state", 0)];
            BlockState got = level.getBlockState(p);
            if (!want.isAir()) {
                nonAir++;
            }
            if (got != want) {
                mismatches++;
                if (examples.size() < 5) {
                    examples.add(p.toShortString() + " want " + want + " got " + got);
                }
            }
            CompoundTag nbt = b.getCompound("nbt").orElse(null);
            if (nbt != null) {
                bes++;
                // straight from the chunk: Level.getBlockEntity answers null off the level's own thread
                BlockEntity be = level.getChunkAt(p).getBlockEntity(p, LevelChunk.EntityCreationType.CHECK);
                if (be == null) {
                    besMissing++;

                } else {
                    CompoundTag saved = be.saveWithoutMetadata(level.registryAccess());
                    for (String key : nbt.keySet()) {
                        if (!key.equals("id") && !java.util.Objects.equals(saved.get(key), nbt.get(key))) {
                            besDiffer++;
                            if (examples.size() < 8) {
                                examples.add(p.toShortString() + " " + nbt.getStringOr("id", "?") + " data '" + key + "' differs: want "
                                        + nbt.get(key) + " got " + saved.get(key));
                            }
                            break;
                        }
                    }
                }
            }
        }
        return new Check(list.size(), nonAir, mismatches, bes, besMissing, besDiffer, examples);
    }

    static void loadChunks(ServerLevel level, BlockPos at, int w, int d) {
        for (int cz = at.getZ() >> 4; cz <= (at.getZ() + d - 1) >> 4; cz++) {
            for (int cx = at.getX() >> 4; cx <= (at.getX() + w - 1) >> 4; cx++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static void writeReport(String name, String text) {
        String dir = System.getProperty("inhabited_world.dev.reportDir");
        if (dir == null) {
            return;
        }
        try {
            Path p = Path.of(dir, name);
            Files.createDirectories(p.getParent());
            Files.writeString(p, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            InhabitedWorld.LOGGER.warn("[{}] could not write the town round-trip report", InhabitedWorld.MOD_ID, e);
        }
    }
}
