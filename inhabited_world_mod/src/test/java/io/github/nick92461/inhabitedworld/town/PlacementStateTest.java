package io.github.nick92461.inhabitedworld.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

// Phase 3: the saved progress of placing a new world's builds, which makes placement resumable and one-off.
class PlacementStateTest {
    private static JsonElement save(PlacementState state) {
        return PlacementState.codec().encodeStart(JsonOps.INSTANCE, state).getOrThrow();
    }

    private static PlacementState load(JsonElement json) {
        return PlacementState.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void progressSurvivesASaveAndStepsGoInOrder() {
        PlacementState state = PlacementState.start(List.of(new PlacementState.Pending("town", "drachenfels/d1_harbour/v1")));
        assertFalse(state.isComplete());
        PlacementState.Entry town = state.entries().getFirst();
        state.tilePlaced(town, 0);
        state.tilePlaced(town, 1);
        assertThrows(IllegalStateException.class, () -> state.tilePlaced(town, 5), "tiles are placed in order");
        assertThrows(IllegalStateException.class, () -> state.blendDone(town), "the blend comes after the tiles");

        JsonElement json = save(state);
        assertEquals(PlacementState.DATA_VERSION, json.getAsJsonObject().get("data_version").getAsInt());
        PlacementState.Entry resumed = load(json).entries().getFirst();
        assertEquals("town", resumed.site());
        assertEquals("drachenfels/d1_harbour/v1", resumed.build());
        assertEquals(PlacementState.Stage.TILES, resumed.stage());
        assertEquals(2, resumed.nextTile(), "a resumed placement starts at the first unplaced tile");

        state.tilesDone(town);
        state.blendDone(town);
        assertTrue(town.isDone());
        assertTrue(state.isComplete());
        PlacementState reread = load(save(state));
        assertTrue(reread.isComplete(), "a finished placement stays finished: nothing is placed again");
    }

    @Test
    void aWorldWithNothingToPlaceIsComplete() {
        assertTrue(PlacementState.start(List.of()).isComplete());
    }

    @Test
    void aFailedBuildDoesNotBlockTheWorld() {
        PlacementState state = PlacementState.start(List.of(new PlacementState.Pending("town", "drachenfels/d1_harbour/v9")));
        state.fail(state.entries().getFirst(), "the mod's data has no build drachenfels/d1_harbour/v9");
        assertTrue(state.isComplete());
        assertEquals("the mod's data has no build drachenfels/d1_harbour/v9", load(save(state)).entries().getFirst().failure().orElseThrow());
    }

    @Test
    void anUnknownVersionIsRefused() {
        JsonElement json = save(PlacementState.start(List.of()));
        json.getAsJsonObject().addProperty("data_version", PlacementState.DATA_VERSION + 1);
        assertTrue(PlacementState.codec().parse(JsonOps.INSTANCE, json).isError());
    }
}
