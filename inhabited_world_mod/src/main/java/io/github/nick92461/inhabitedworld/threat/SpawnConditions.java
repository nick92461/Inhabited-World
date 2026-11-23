package io.github.nick92461.inhabitedworld.threat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.region.SiteRegions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;

/*
  Where a threat profile's own hostiles may appear (canon 06 section 17.3, "spawn conditions"). They replace vanilla's
  light rules for the profile's listed hostiles inside its region; the ground and collision rules stay vanilla's.

  - max_block_light: the brightest block light (torches, lanterns) a spot may have. Vanilla's overworld allows 0, so
    one torch makes a spot safe; a profile may allow more (an old mine whose lamps do not keep it safe).
  - max_brightness: the brightest the spot may be overall, as vanilla measures it for monsters (the higher of block
    light and sky light after night or a storm darkens it; 0 to 15). Vanilla draws a random limit from 0 to 7 each
    try; a profile names one fixed limit, so its rule is deterministic.
  - min_y / max_y: an optional height band, for a profile whose threat lives only at some depths.

  Light tests read only the lighting engine's stored values: no allocation, safe on the spawn path.
 */
public record SpawnConditions(int maxBlockLight, int maxBrightness, int minY, int maxY) {
    /** Vanilla's overworld monster rule, made deterministic: no block light, overall brightness at most 7. */
    public static final SpawnConditions VANILLA_LIKE = new SpawnConditions(0, 7, SiteRegions.WORLD_MIN_Y, SiteRegions.WORLD_MAX_Y);

    private static final Codec<Integer> LIGHT = Codec.intRange(0, 15);

    public static final Codec<SpawnConditions> CODEC = RecordCodecBuilder.<SpawnConditions>create(i -> i.group(
                    LIGHT.optionalFieldOf("max_block_light", VANILLA_LIKE.maxBlockLight()).forGetter(SpawnConditions::maxBlockLight),
                    LIGHT.optionalFieldOf("max_brightness", VANILLA_LIKE.maxBrightness()).forGetter(SpawnConditions::maxBrightness),
                    Codec.INT.optionalFieldOf("min_y", SiteRegions.WORLD_MIN_Y).forGetter(SpawnConditions::minY),
                    Codec.INT.optionalFieldOf("max_y", SiteRegions.WORLD_MAX_Y).forGetter(SpawnConditions::maxY))
            .apply(i, SpawnConditions::new))
            .validate(c -> c.minY() <= c.maxY() ? DataResult.success(c) : DataResult.error(() -> "spawn conditions: min_y " + c.minY() + " above max_y " + c.maxY()));

    /** Whether the light values and height allow a spawn here (pure facts; the level variant reads them). */
    public boolean allows(int y, int blockLight, int brightness) {
        return y >= this.minY && y <= this.maxY && blockLight <= this.maxBlockLight && brightness <= this.maxBrightness;
    }

    public boolean allows(ServerLevelAccessor level, BlockPos pos) {
        if (pos.getY() < this.minY || pos.getY() > this.maxY) {
            return false;
        }
        int brightness = level.getLevel().isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos);
        return this.allows(pos.getY(), level.getBrightness(LightLayer.BLOCK, pos), brightness);
    }

    public String describe() {
        return "block light <= " + this.maxBlockLight + ", brightness <= " + this.maxBrightness
                + (this.minY > SiteRegions.WORLD_MIN_Y || this.maxY < SiteRegions.WORLD_MAX_Y ? ", y " + this.minY + ".." + this.maxY : "");
    }
}
