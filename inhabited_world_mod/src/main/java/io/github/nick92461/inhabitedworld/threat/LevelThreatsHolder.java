package io.github.nick92461.inhabitedworld.threat;

import org.jspecify.annotations.Nullable;

/*
  Implemented by ServerLevel through ServerLevelRegionsMixin: a level carries its threat state (LevelThreats) beside its
  region index, so nothing is kept in static state between worlds or servers.
 */
public interface LevelThreatsHolder {
    @Nullable LevelThreats inhabitedWorld$threats();

    void inhabitedWorld$setThreats(@Nullable LevelThreats threats);
}
