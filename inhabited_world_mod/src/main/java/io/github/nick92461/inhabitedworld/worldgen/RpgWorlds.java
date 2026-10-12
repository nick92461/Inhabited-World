package io.github.nick92461.inhabitedworld.worldgen;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/*
  What makes a world an Inhabited World (canon 02 section 4): its noise-generator settings are ours.
  The settings entry is saved with the world, so the identity survives every reload.
 */
public final class RpgWorlds {
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, InhabitedWorld.id("rpg_overworld"));

    // Structure sets allowed to generate in an Inhabited world.
    private static final Set<Identifier> ALLOWED_STRUCTURE_SETS = Set.of();

    private RpgWorlds() {
    }

    public static boolean isRpg(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().is(NOISE_SETTINGS);
    }

    /**
     * A view of the structure-set lookup exposing only allowlisted sets. Vanilla's structure-state builder
     * only lists elements, so seeding, biome checks and ring placement all stay vanilla.
     */

    public static HolderLookup<StructureSet> allowlisted(HolderLookup<StructureSet> all) {
        return new HolderLookup<StructureSet>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return all.listElements().filter(RpgWorlds::isAllowed);
            }

            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return all.listTags();
            }

            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
                return all.get(key).filter(RpgWorlds::isAllowed);
            }

            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) {
                return all.get(tag);
            }
        };
    }

    private static boolean isAllowed(Holder.Reference<StructureSet> set) {
        return ALLOWED_STRUCTURE_SETS.contains(set.key().identifier());
    }
}
