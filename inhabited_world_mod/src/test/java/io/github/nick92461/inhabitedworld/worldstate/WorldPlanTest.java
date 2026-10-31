package io.github.nick92461.inhabitedworld.worldstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

import org.junit.jupiter.api.Test;

// The first piece of the persisted WorldPlan (canon 02 section 7, 08 section 49).
class WorldPlanTest {
    private final WorldPlan plan = WorldPlan.create(UUID.fromString("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11"), PlayableArea.V0_1);

    @Test
    void savesAndLoadsUnchanged() {
        JsonObject json = WorldPlan.CODEC.encodeStart(JsonOps.INSTANCE, this.plan).getOrThrow().getAsJsonObject();
        assertEquals(1, json.get("data_version").getAsInt(), "every persisted domain carries a data version");
        assertEquals("7b0e4f43-58a4-4c1c-9b55-1f4b7f2d9c11", json.get("world_id").getAsString());
        assertEquals(this.plan, WorldPlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void refusesADataVersionItDoesNotKnow() {
        JsonObject json = WorldPlan.CODEC.encodeStart(JsonOps.INSTANCE, this.plan).getOrThrow().getAsJsonObject();
        json.addProperty("data_version", 2);
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, json).isError());
        json.remove("data_version");
        assertTrue(WorldPlan.CODEC.parse(JsonOps.INSTANCE, json).isError(), "a plan without a data version is refused");
    }

    // Only a new Inhabited World gets a plan: never a Default world, and never a second plan.
    @Test
    void onlyNewInhabitedWorldsGetAPlan() {
        assertTrue(WorldPlans.shouldCreate(true, false));
        assertFalse(WorldPlans.shouldCreate(true, true), "an existing plan is never replaced");
        assertFalse(WorldPlans.shouldCreate(false, false), "a Default world gets no plan");
        assertFalse(WorldPlans.shouldCreate(false, true));
    }
}
