package io.github.nick92461.inhabitedworld.worldstate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/*
  A named place inside a placed handcrafted structure (canon 02 section 16): a build's semantic marker resolved to the
  world after the build was placed and rotated. NPC schedules and game systems name it by id ("d1_market_square"),
  never by coordinates.

  - id: the marker's id from the build's manifest, unique in the world
  - pos: the marker's block in the world
  - yaw: the direction it faces in the world (degrees, Minecraft convention: 0 south, 90 west, 180 north, 270 east)
  - site: the planned site whose build holds it
  - region: the semantic region (06 section 17) that applies at pos
 */
public record SemanticLocation(String id, BlockPos pos, float yaw, String site, String region) {
    public static final Codec<SemanticLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(SemanticLocation::id),
                    BlockPos.CODEC.fieldOf("pos").forGetter(SemanticLocation::pos),
                    Codec.FLOAT.fieldOf("yaw").forGetter(SemanticLocation::yaw),
                    Codec.STRING.fieldOf("site").forGetter(SemanticLocation::site),
                    Codec.STRING.fieldOf("region").forGetter(SemanticLocation::region))
            .apply(i, SemanticLocation::new));
}
