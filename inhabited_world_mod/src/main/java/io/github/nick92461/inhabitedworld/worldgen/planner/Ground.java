package io.github.nick92461.inhabitedworld.worldgen.planner;

/*
  The planner's coarse reading of one column's biome: enough to tell dry land from sea, beaches, rivers and
  mountain biomes. The Minecraft samplers map biome tags onto it; the planning logic never sees a biome.
 */
public enum Ground {
    LAND,
    MOUNTAIN,
    BEACH,
    RIVER,
    SEA
}
