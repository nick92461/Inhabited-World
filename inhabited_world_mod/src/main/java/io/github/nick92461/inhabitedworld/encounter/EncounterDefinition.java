package io.github.nick92461.inhabitedworld.encounter;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.threat.SiteAnchor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/*
  A guaranteed encounter (canon 06 sections 17.7 and 17.8): a specific hostile that exists because the POI (and later
  a quest) requires it, not because a spawn roll happened. Data, data/<namespace>/encounter/<path>.json; its file ID is
  its semantic ID, which its persistent state and the Phase 4 quest system use (Encounters.state / entity).

    {"entity": "minecraft:zombie", "name": "Foreman (placeholder)",
     "equipment": {"head": "minecraft:iron_helmet", "mainhand": "minecraft:iron_pickaxe"},
     "anchor": {"vertical": "surface"}, "trigger_radius": 32, "home_radius": 12, "respawns": false}

  - entity: a mob type; it keeps vanilla AI (06 section 17.9)
  - name: its custom name (optional)
  - equipment: slot to item; this is its whole equipment. Vanilla's random spawn extras (babies, jockeys, random gear)
    are not applied, so the encounter is the same mob in every world
  - anchor: where it appears, relative to the POI site that holds it (SiteAnchor; a semantic marker in Phase 3 part 3)
  - trigger_radius: it is spawned lazily, the first time a player comes this close to the anchor
  - home_radius: its wandering stays within this distance of where it appeared (vanilla's home restriction); a mob
    that left its POI region and has nothing to fight is brought back to that spot
  - respawns: whether a defeated encounter comes back (when no player is within the trigger radius); false keeps it
    defeated for good, as a required quest enemy should be (06 section 17.8)
 */
public record EncounterDefinition(EntityType<?> entity, Optional<String> name, Map<EquipmentSlot, Item> equipment, SiteAnchor anchor,
                                  int triggerRadius, int homeRadius, boolean respawns) {
    public static final Codec<EncounterDefinition> CODEC = RecordCodecBuilder.<EncounterDefinition>create(i -> i.group(
                    BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(EncounterDefinition::entity),
                    Codec.STRING.optionalFieldOf("name").forGetter(EncounterDefinition::name),
                    Codec.unboundedMap(EquipmentSlot.CODEC, BuiltInRegistries.ITEM.byNameCodec()).optionalFieldOf("equipment", Map.of())
                            .forGetter(EncounterDefinition::equipment),
                    SiteAnchor.CODEC.optionalFieldOf("anchor", SiteAnchor.CENTRE_SURFACE).forGetter(EncounterDefinition::anchor),
                    Codec.intRange(1, 128).optionalFieldOf("trigger_radius", 32).forGetter(EncounterDefinition::triggerRadius),
                    Codec.intRange(1, 64).optionalFieldOf("home_radius", 12).forGetter(EncounterDefinition::homeRadius),
                    Codec.BOOL.optionalFieldOf("respawns", false).forGetter(EncounterDefinition::respawns))
            .apply(i, EncounterDefinition::new))
            .validate(d -> d.entity().getCategory() == MobCategory.MISC
                    ? DataResult.error(() -> EntityType.getKey(d.entity()) + " is not a mob and cannot be an encounter")
                    : DataResult.success(d));

    public String describe() {
        return EntityType.getKey(this.entity) + this.name.map(n -> " \"" + n + "\"").orElse("")
                + (this.equipment.isEmpty() ? "" : ", equipment " + this.equipment.entrySet().stream()
                .map(e -> e.getKey().getSerializedName() + "=" + BuiltInRegistries.ITEM.getKey(e.getValue())).sorted().toList())
                + ", trigger radius " + this.triggerRadius + ", home radius " + this.homeRadius + (this.respawns ? ", respawns" : ", never respawns");
    }
}
