package io.github.nick92461.inhabitedworld.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;

/*
  The exported D1 Harbour (tools/buildimport export d1_harbour) as the mod ships it: its manifest and terrain
  mask read back the way the game reads them, and agree with each other.
 */
class TownManifestTest {
    private static final String DIR = "/data/inhabited_world/towns/drachenfels/d1_harbour/v1/";
    private static final Identifier ID = Identifier.fromNamespaceAndPath("inhabited_world", "towns/drachenfels/d1_harbour/v1/manifest.json");

    private static TownManifest manifest() throws Exception {
        try (InputStream in = TownManifestTest.class.getResourceAsStream(DIR + "manifest.json")) {
            assertNotNull(in, "the D1 Harbour manifest must be in the mod's data");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return TownManifest.parse(ID, json);
        }
    }

    private static TownColumns columns() throws Exception {
        try (InputStream in = TownManifestTest.class.getResourceAsStream(DIR + "columns.nbt")) {
            assertNotNull(in, "the D1 Harbour columns file must be in the mod's data");
            return TownColumns.of(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        }
    }

    @Test
    void d1HarbourManifestIsComplete() throws Exception {
        TownManifest town = manifest();
        assertEquals("inhabited_world:drachenfels/d1_harbour", town.id());
        assertEquals("drachenfels/d1_harbour/v1", town.key());
        assertEquals(Identifier.fromNamespaceAndPath("inhabited_world", "towns/drachenfels/d1_harbour/v1/columns.nbt"), town.columnsId());
        assertEquals(63, town.targetSeaLevel());
        // the tiles cover the footprint without overlapping, each inside the town's box
        Set<BlockPos> corners = new HashSet<>();
        long blocks = 0;
        for (TownManifest.Tile t : town.tiles()) {
            assertTrue(corners.add(t.offset()), "two tiles at " + t.offset());
            assertTrue(t.offset().getX() + t.size().getX() <= town.size().getX() && t.offset().getZ() + t.size().getZ() <= town.size().getZ());
            assertEquals(town.size().getY(), t.size().getY(), "every tile spans the town's full height");
            assertTrue(t.nonAir() > 0 && t.nonAir() <= t.blocks());
            blocks += t.blocks();
        }
        assertTrue(blocks > 1_000_000, "D1 Harbour holds over a million blocks, got " + blocks);
        // the seeded markers, inside the box
        List<String> ids = town.markers().stream().map(TownManifest.Marker::id).toList();
        assertTrue(ids.containsAll(List.of("d1_quay", "d1_pier_north", "d1_pier_middle", "d1_pier_south", "d1_market_square", "d1_main_gate")), "markers: " + ids);
        for (TownManifest.Marker m : town.markers()) {
            assertTrue(m.pos().getX() >= 0 && m.pos().getX() < town.size().getX() && m.pos().getZ() >= 0 && m.pos().getZ() < town.size().getZ(), m.id());
        }
    }

    @Test
    void columnsAgreeWithTheManifest() throws Exception {
        TownManifest town = manifest();
        TownColumns cols = columns();
        assertEquals(town.size().getX(), cols.width());
        assertEquals(town.size().getZ(), cols.depth());
        int stored = 0;
        for (int z = 0; z < cols.depth(); z++) {
            for (int x = 0; x < cols.width(); x++) {
                if (cols.stored(x, z)) {
                    stored++;
                    assertTrue(cols.floor(x, z) >= 0 && cols.floor(x, z) <= cols.top(x, z) && cols.top(x, z) < town.size().getY(), "column " + x + "," + z);
                }
            }
        }
        assertTrue(stored > cols.width() * cols.depth() / 2, "most of the box is stored");
        // the waterfront is east: the box's east edge column is sea
        assertEquals(TownColumns.SEA, cols.zone(cols.width() - 1, cols.depth() / 2));
        // markers stand on or above their column's floor
        for (TownManifest.Marker m : town.markers()) {
            assertTrue(m.pos().getY() >= cols.floor(m.pos().getX(), m.pos().getZ()), m.id());
        }
    }

    @Test
    void signNotesWrapIntoThreeLines() {
        assertEquals(List.of("main street", "entry from the", "Castle Quarter"), TownPaste.wrap("main street entry from the Castle Quarter", 15, 3));
        assertEquals(List.of("a b c"), TownPaste.wrap(" a  b c ", 15, 3));
        assertEquals(List.of(), TownPaste.wrap("", 15, 3));
        assertEquals(3, TownPaste.wrap("one two three four five six seven eight nine ten eleven", 10, 3).size());
    }
}
