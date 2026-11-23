package io.github.nick92461.inhabitedworld.threat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

/*
  One hostile a threat profile lists (canon 06 section 17.3: allowed and weighted hostile types): its entity type, its
  weight among the profile's hostiles, and the group size natural spawning draws (vanilla's biome spawner entries
  have the same three facts). Only monster-category types: a profile governs hostiles.

    {"type": "minecraft:cave_spider", "weight": 20, "min_group": 1, "max_group": 3}
 */
public record HostileEntry(EntityType<?> type, int weight, int minGroup, int maxGroup) {
    public static final Codec<HostileEntry> CODEC = RecordCodecBuilder.<HostileEntry>create(i -> i.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(HostileEntry::type),
                    Codec.intRange(1, 10_000).fieldOf("weight").forGetter(HostileEntry::weight),
                    Codec.intRange(1, 16).optionalFieldOf("min_group", 1).forGetter(HostileEntry::minGroup),
                    Codec.intRange(1, 16).optionalFieldOf("max_group", 1).forGetter(HostileEntry::maxGroup))
            .apply(i, HostileEntry::new))
            .validate(HostileEntry::check);

    private static DataResult<HostileEntry> check(HostileEntry e) {
        if (e.type().getCategory() != MobCategory.MONSTER) {
            return DataResult.error(() -> EntityType.getKey(e.type()) + " is not a hostile (monster category) and cannot be a profile hostile");
        }
        if (e.minGroup() > e.maxGroup()) {
            return DataResult.error(() -> EntityType.getKey(e.type()) + ": min_group " + e.minGroup() + " above max_group " + e.maxGroup());
        }
        return DataResult.success(e);
    }

    /** As vanilla's natural spawner wants it (NaturalSpawner reads spawn lists of these). */
    public MobSpawnSettings.SpawnerData spawnerData() {
        return new MobSpawnSettings.SpawnerData(this.type, UniformInt.of(this.minGroup, this.maxGroup));
    }

    public String describe() {
        return EntityType.getKey(this.type) + " x" + this.weight + (this.maxGroup > 1 ? " (groups " + this.minGroup + "-" + this.maxGroup + ")" : "");
    }
}
