package io.github.nick92461.inhabitedworld.gametest.mixin;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Lets the phantom test make it night for one synchronous check (vanilla recomputes it from the time every tick).
@Mixin(Level.class)
public interface LevelSkyDarkenAccessor {
    @Accessor("skyDarken")
    void inhabitedWorldTest$setSkyDarken(int skyDarken);
}
