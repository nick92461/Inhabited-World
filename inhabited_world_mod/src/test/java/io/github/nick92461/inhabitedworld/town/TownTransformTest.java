package io.github.nick92461.inhabitedworld.town;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParser;

import io.github.nick92461.inhabitedworld.worldgen.planner.BlockRect;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.junit.jupiter.api.Test;

// Phase 3: where a rotated town's blocks, tiles, markers and facing land in the world (canon 02 section 16).
class TownTransformTest {
    private static final int W = 272;
    private static final int D = 256;

    private static TownTransform at(Rotation rotation) {
        boolean quarter = PlannedSite.quarterTurn(rotation);
        return TownTransform.of(BlockRect.ofSize(-272, -368, quarter ? D : W, quarter ? W : D), rotation, W, D, 2);
    }

    @Test
    void theTurnedTownFillsItsBoundsExactly() {
        for (Rotation rotation : Rotation.values()) {
            TownTransform t = at(rotation);
            BlockRect bounds = t.bounds();
            Set<Long> seen = new HashSet<>();
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++) {
                    int wx = t.worldX(x, z);
                    int wz = t.worldZ(x, z);
                    assertTrue(bounds.contains(wx, wz), rotation + ": template " + x + ", " + z + " lands outside the bounds");
                    assertTrue(seen.add(((long) wx << 32) ^ (wz & 0xffffffffL)), rotation + ": two template columns land on " + wx + ", " + wz);
                    assertArrayEquals(new int[]{x, z}, t.toTemplate(wx, wz), rotation + ": toTemplate inverts toWorld");
                }
            }
            assertEquals((long) bounds.sizeX() * bounds.sizeZ(), seen.size(), rotation + ": every bounds column holds a template column");
            assertNull(t.toTemplate(bounds.minX() - 1, bounds.minZ()), "outside the bounds is no template column");
        }
    }

    @Test
    void boundsThatDoNotFitTheTurnedTownAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TownTransform.of(BlockRect.ofSize(0, 0, W, D), Rotation.CLOCKWISE_90, W, D, 2));
        assertThrows(IllegalArgumentException.class, () -> TownTransform.of(BlockRect.ofSize(0, 0, D, W), Rotation.NONE, W, D, 2));
    }

    // The town's template east (+x, its sea side) faces the direction the plan's rotation turns east to.
    @Test
    void theSeaSideTurnsWithTheRotation() {
        for (Rotation rotation : Rotation.values()) {
            TownTransform t = at(rotation);
            Direction east = rotation.rotate(Direction.EAST);
            assertEquals(east.getStepX(), t.worldX(11, 7) - t.worldX(10, 7), rotation + " x step");
            assertEquals(east.getStepZ(), t.worldZ(11, 7) - t.worldZ(10, 7), rotation + " z step");
        }
    }

    // A tile placed by vanilla at tileOrigin, turned by the same rotation, puts each block exactly where toWorld says.
    @Test
    void tileOriginsAgreeWithVanillasRotation() {
        BlockPos[] offsets = {BlockPos.ZERO, new BlockPos(32, 0, 0), new BlockPos(224, 0, 96), new BlockPos(256, 0, 224)};
        BlockPos[] locals = {BlockPos.ZERO, new BlockPos(31, 0, 0), new BlockPos(0, 135, 31), new BlockPos(5, 40, 17), new BlockPos(15, 3, 31)};
        for (Rotation rotation : Rotation.values()) {
            TownTransform t = at(rotation);
            for (BlockPos o : offsets) {
                BlockPos origin = t.tileOrigin(o);
                for (BlockPos b : locals) {
                    if (o.getX() + b.getX() >= W || o.getZ() + b.getZ() >= D) {
                        continue;
                    }
                    BlockPos byVanilla = StructureTemplate.transform(b, Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
                    assertEquals(t.toWorld(o.offset(b)), byVanilla, rotation + ": tile " + o + " block " + b);
                }
            }
        }
    }

    @Test
    void templateYZeroLandsOnBaseY() {
        TownTransform t = at(Rotation.CLOCKWISE_180);
        assertEquals(2, t.toWorld(BlockPos.ZERO).getY());
        assertEquals(2 + 61, t.toWorld(new BlockPos(5, 61, 5)).getY(), "the town's sea, template y 61, lands on world y 63");
    }

    // Minecraft yaw: 0 south, 90 west, 180 north, 270 east; a clockwise quarter turn adds 90 degrees.
    @Test
    void yawTurnsWithTheRotation() {
        Map<Rotation, Float> eastTurned = Map.of(Rotation.NONE, 270f, Rotation.CLOCKWISE_90, 0f, Rotation.CLOCKWISE_180, 90f,
                Rotation.COUNTERCLOCKWISE_90, 180f);
        for (Rotation rotation : Rotation.values()) {
            TownTransform t = at(rotation);
            assertEquals(eastTurned.get(rotation), t.yaw(270f), 1e-4, rotation + ": facing east turns to " + rotation.rotate(Direction.EAST));
            assertEquals(yawOf(rotation.rotate(Direction.SOUTH)), t.yaw(0f), 1e-4, rotation + ": facing south");
            float y = t.yaw(-45f);
            assertTrue(y >= 0 && y < 360, "yaw is wrapped into [0, 360)");
        }
    }

    private static float yawOf(Direction d) {
        return switch (d) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> 270f;
        };
    }

    /*
      The Harbour's markers on the test seed's site (turned counterclockwise, bounds as in WorldPlanTest): the main gate
      (template x 2, the west edge, facing east into town) ends up 2 blocks from the bounds' south edge, facing north.
     */
    @Test
    void harbourMarkersResolveOnTheTestSeedsSite() throws Exception {
        TownManifest town;
        try (InputStream in = TownTransformTest.class.getResourceAsStream("/data/inhabited_world/towns/drachenfels/d1_harbour/v1/manifest.json")) {
            town = TownManifest.parse(Identifier.fromNamespaceAndPath("inhabited_world", "towns/drachenfels/d1_harbour/v1/manifest.json"),
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        TownTransform t = TownTransform.of(BlockRect.ofSize(-272, -368, 256, 272), Rotation.COUNTERCLOCKWISE_90, town.size().getX(), town.size().getZ(), town.baseY());
        TownManifest.Marker gate = town.markers().stream().filter(m -> m.id().equals("d1_main_gate")).findFirst().orElseThrow();
        assertEquals(new BlockPos(2, 100, 99), gate.pos());
        assertEquals(new BlockPos(-272 + 99, 102, -368 + 271 - 2), t.toWorld(gate.pos()));
        assertEquals(-97 - 2, t.toWorld(gate.pos()).getZ(), "2 blocks inside the south edge");
        assertEquals(180f, t.yaw(gate.yaw()), 1e-4, "facing north");
        for (TownManifest.Marker m : town.markers()) {
            BlockPos p = t.toWorld(m.pos());
            assertTrue(t.bounds().contains(p.getX(), p.getZ()), m.id() + " lies inside the bounds");
        }
    }
}
