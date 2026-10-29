package io.github.nick92461.inhabitedworld.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

// Matching a placed feature against the suppressed set (canon 06 section 15).
class FeatureSuppressionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> Holder.Reference<T> reference(ResourceKey<T> key) {
        return Holder.Reference.createStandAlone(new HolderOwner<>() {
        }, key);
    }

    private static PlacedFeature placed(String feature) {
        return new PlacedFeature(reference(ResourceKey.create(Registries.FEATURE, Identifier.withDefaultNamespace(feature))), List.of());
    }

    @Test
    void matchesTheSuppressedInstancesOnly() {
        PlacedFeature monsterRoom = placed("monster_room");
        PlacedFeature geode = placed("amethyst_geode");
        HolderSet<PlacedFeature> suppressed = HolderSet.direct(Holder.direct(monsterRoom));

        assertTrue(FeatureSuppression.isSuppressed(suppressed, monsterRoom));
        assertFalse(FeatureSuppression.isSuppressed(suppressed, geode));
        assertFalse(FeatureSuppression.isSuppressed(HolderSet.empty(), monsterRoom));
    }

    // Placed features are records: an equal copy (e.g. from another datapack entry) is not the suppressed entry.
    @Test
    void comparesIdentityNotRecordEquality() {
        PlacedFeature monsterRoom = placed("monster_room");
        PlacedFeature copy = new PlacedFeature(monsterRoom.feature(), monsterRoom.placement());

        assertEquals(monsterRoom, copy);
        assertFalse(FeatureSuppression.isSuppressed(HolderSet.direct(Holder.direct(monsterRoom)), copy));
    }

    // A Default world suppresses nothing; the registries aren't even consulted.
    @Test
    void defaultWorldSuppressesNothing() {
        Holder<Biome> plains = reference(Biomes.PLAINS);
        NoiseBasedChunkGenerator vanilla = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), reference(NoiseGeneratorSettings.OVERWORLD));

        assertEquals(0, FeatureSuppression.forGenerator(vanilla, null).size());
    }
}
