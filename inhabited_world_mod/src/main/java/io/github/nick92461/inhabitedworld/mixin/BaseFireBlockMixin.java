package io.github.nick92461.inhabitedworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
  No nether portals in an Inhabited World (canon 02 section 4, Version 0.1 is Overworld-only). Every way of lighting
  a portal frame ends in BaseFireBlock: flint and steel, fire charges and dispensers ask canBePlacedAt, and any fire
  appearing in a frame (those, spreading fire, lightning) runs onPlace. Both check inPortalDimension, which in
  vanilla only looks at the level key. Fire still lights normally; it just never becomes a portal.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    @ModifyReturnValue(method = "inPortalDimension", at = @At("RETURN"))
    private static boolean inhabitedWorld$noPortalsInRpgWorlds(boolean original, Level level) {
        return original && !RpgWorlds.isRpg(level);
    }
}
