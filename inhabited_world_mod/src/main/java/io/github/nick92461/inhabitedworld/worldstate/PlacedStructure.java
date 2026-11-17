package io.github.nick92461.inhabitedworld.worldstate;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/*
  A handcrafted build placed at a planned site when its world was created (canon 02 sections 9, 10 and 16; 08
  section 49), recorded in the WorldPlan once, when placement finished, and never changed:

  - site: the planned site it stands on (one structure per site)
  - catalogId: the build's catalog ID (the site's catalog ID, e.g. inhabited_world:drachenfels/d1_harbour)
  - build: the exact build version placed, as its manifest key (e.g. drachenfels/d1_harbour/v1): a later re-export
    is a new version (02 section 6), and this world keeps what it was made with
  - rotation: how the build was turned (the site's rotation)
  - origin: where template (0, 0, 0) landed in the world
  - locations: its semantic markers resolved to world positions (SemanticLocation)
  - check: the read-back of sampled template blocks after placement (PlacementCheck)
 */
public record PlacedStructure(String site, Identifier catalogId, String build, Rotation rotation, BlockPos origin,
                              List<SemanticLocation> locations, PlacementCheck check) {
    public static final Codec<PlacedStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("site").forGetter(PlacedStructure::site),
                    Identifier.CODEC.fieldOf("catalog_id").forGetter(PlacedStructure::catalogId),
                    Codec.STRING.fieldOf("build").forGetter(PlacedStructure::build),
                    Rotation.CODEC.fieldOf("rotation").forGetter(PlacedStructure::rotation),
                    BlockPos.CODEC.fieldOf("origin").forGetter(PlacedStructure::origin),
                    SemanticLocation.CODEC.listOf().fieldOf("locations").forGetter(PlacedStructure::locations),
                    PlacementCheck.CODEC.fieldOf("check").forGetter(PlacedStructure::check))
            .apply(i, PlacedStructure::new));

    public PlacedStructure {
        locations = List.copyOf(locations);
    }
}
