package io.github.nick92461.inhabitedworld.region;

import org.jspecify.annotations.Nullable;

/*
  Implemented by ServerLevel through ServerLevelMixin: each level carries its own region index, so the spawn path
  finds it with one field read, and nothing is kept in static state between worlds or servers.
 */
public interface RegionIndexHolder {
    @Nullable RegionIndex inhabitedWorld$regionIndex();

    void inhabitedWorld$setRegionIndex(@Nullable RegionIndex index);
}
