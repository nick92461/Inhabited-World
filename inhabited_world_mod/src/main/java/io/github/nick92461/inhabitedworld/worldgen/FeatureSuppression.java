package io.github.nick92461.inhabitedworld.worldgen;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/*
  Generated features that would create exploration content outside the World Planner's control
  (canon 06 section 15, 02 section 5), such as monster-spawner dungeons, never place in an Inhabited World.
  The list is the placed-feature tag below, so it stays data. It is a denylist: re-audit the vanilla
  placed-feature list at every Minecraft version bump.
 */
public final class FeatureSuppression {
    public static final TagKey<PlacedFeature> SUPPRESSED =
            TagKey.create(Registries.PLACED_FEATURE, InhabitedWorld.id("suppressed_in_rpg_world"));

    private FeatureSuppression() {
    }

    /** The placed features suppressed for this generator's world: the tag in an Inhabited World, else nothing. */
    public static HolderSet<PlacedFeature> forGenerator(ChunkGenerator generator, HolderLookup.Provider registries) {
        if (!RpgWorlds.isRpg(generator)) {
            return HolderSet.empty();
        }
        return registries.lookupOrThrow(Registries.PLACED_FEATURE).get(SUPPRESSED)
                .<HolderSet<PlacedFeature>>map(tag -> tag)
                .orElse(HolderSet.empty());
    }

    /**
     * Whether the feature is one of the suppressed ones. Placed features are records, so this compares
     * identity with the registry's instances (a datapack copy with equal fields is a different feature).
     * Indexed access keeps it allocation-free; the tag holds a handful of entries.
     */
    public static boolean isSuppressed(HolderSet<PlacedFeature> suppressed, PlacedFeature feature) {
        for (int i = 0, n = suppressed.size(); i < n; i++) {
            if (suppressed.get(i).value() == feature) {
                return true;
            }
        }
        return false;
    }
}
