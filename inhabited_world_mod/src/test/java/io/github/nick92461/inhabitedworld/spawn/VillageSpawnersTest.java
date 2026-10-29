package io.github.nick92461.inhabitedworld.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;

// Which of the overworld's custom spawners an Inhabited World keeps.
class VillageSpawnersTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dropsTraderAndSiegeKeepsTheRestInOrder() {
        PhantomSpawner phantoms = new PhantomSpawner();
        PatrolSpawner patrols = new PatrolSpawner();
        CatSpawner cats = new CatSpawner();
        // The vanilla overworld list, in MinecraftServer.createLevels order. The trader spawner only stores
        // its saved-data storage until it ticks, so null is fine here.
        List<CustomSpawner> vanilla = List.of(phantoms, patrols, cats, new VillageSiege(), new WanderingTraderSpawner(null));

        assertEquals(List.of(phantoms, patrols, cats), VillageSpawners.without(vanilla));
    }

    @Test
    void recognisesOnlyTheVillageSpawners() {
        assertTrue(VillageSpawners.isVillageSpawner(new VillageSiege()));
        assertTrue(VillageSpawners.isVillageSpawner(new WanderingTraderSpawner(null)));
        assertFalse(VillageSpawners.isVillageSpawner(new PhantomSpawner()));
        assertFalse(VillageSpawners.isVillageSpawner(new CatSpawner()));
    }
}
