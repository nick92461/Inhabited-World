package io.github.nick92461.inhabitedworld.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

// World identity (canon 02 section 4) and the structure allowlist view (P2A step 2).
class RpgWorldsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <T> Holder.Reference<T> reference(ResourceKey<T> key) {
        return Holder.Reference.createStandAlone(new HolderOwner<>() {
        }, key);
    }

    private static NoiseBasedChunkGenerator generatorWith(ResourceKey<NoiseGeneratorSettings> settings) {
        Holder<Biome> plains = reference(Biomes.PLAINS);
        return new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), reference(settings));
    }

    @Test
    void ourNoiseSettingsMakeAnRpgWorld() {
        assertTrue(RpgWorlds.isRpg(generatorWith(RpgWorlds.NOISE_SETTINGS)));
    }

    @Test
    void vanillaNoiseSettingsDoNot() {
        assertFalse(RpgWorlds.isRpg(generatorWith(NoiseGeneratorSettings.OVERWORLD)));
    }

    private static final ResourceKey<StructureSet> VILLAGES =
            ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("villages"));
    private static final ResourceKey<StructureSet> MINESHAFTS =
            ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("mineshafts"));
    private static final TagKey<StructureSet> SOME_TAG =
            TagKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("some_tag"));

    // A stand-in for the real structure-set lookup holding two vanilla sets and one tag.
    private static final HolderLookup<StructureSet> ALL = new HolderLookup<>() {
        private final List<Holder.Reference<StructureSet>> sets = List.of(reference(VILLAGES), reference(MINESHAFTS));
        // emptyNamed is deprecated for game code (tags should come from a registry); fine for a test stand-in.
        @SuppressWarnings("deprecation")
        private final HolderSet.Named<StructureSet> tag = HolderSet.emptyNamed(new HolderOwner<>() {
        }, SOME_TAG);

        @Override
        public Stream<Holder.Reference<StructureSet>> listElements() {
            return sets.stream();
        }

        @Override
        public Stream<HolderSet.Named<StructureSet>> listTags() {
            return Stream.of(tag);
        }

        @Override
        public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
            return sets.stream().filter(set -> set.is(key)).findFirst();
        }

        @Override
        public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> key) {
            return key.equals(SOME_TAG) ? Optional.of(tag) : Optional.empty();
        }
    };

    @Test
    void allowlistHidesSetsThatAreNotAllowed() {
        HolderLookup<StructureSet> view = RpgWorlds.allowlisted(ALL);

        assertEquals(0, view.listElements().count());
        assertTrue(view.get(VILLAGES).isEmpty());
        assertTrue(ALL.get(VILLAGES).isPresent(), "the underlying lookup still has it");
    }

    @Test
    void allowlistLeavesTagsAlone() {
        HolderLookup<StructureSet> view = RpgWorlds.allowlisted(ALL);

        assertSame(ALL.get(SOME_TAG).orElseThrow(), view.get(SOME_TAG).orElseThrow());
        assertEquals(1, view.listTags().count());
    }
}
