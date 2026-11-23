package io.github.nick92461.inhabitedworld.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
  Town placement runs before the first level tick, when nothing processes entity loads yet: it drives them itself so
  it can remove the generated entities standing where the town now is (town/TownPlacement).
 */
@Mixin(ServerLevel.class)
public interface ServerLevelEntitiesAccessor {
    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> inhabitedWorld$entityManager();
}
