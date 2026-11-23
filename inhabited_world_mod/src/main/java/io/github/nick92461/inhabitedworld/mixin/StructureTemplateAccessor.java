package io.github.nick92461.inhabitedworld.mixin;

import java.util.List;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
  Town placement (town/TownPlacer) reads a tile's blocks for verification and filters its entities before placing
  it: vanilla offers no public view of either list.
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> inhabitedWorld$palettes();

    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> inhabitedWorld$entityInfoList();
}
