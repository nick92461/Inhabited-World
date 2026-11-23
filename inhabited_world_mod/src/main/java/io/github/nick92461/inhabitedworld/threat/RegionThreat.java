package io.github.nick92461.inhabitedworld.threat;

import java.util.Optional;

import io.github.nick92461.inhabitedworld.region.SemanticRegion;

import net.minecraft.resources.Identifier;

/*
  A semantic region bound to the threat profile that applies in it, for one running world: what the spawn path reads
  (RegionIndex.threatAt). Built when the region index is installed; the profile never changes while the world runs
  (a data-pack reload rebuilds the whole binding).

  population is the one mutable fact: how many ambient hostiles (monster category, not persistence-required) stand in
  the region, counted once a second by LevelThreats for profiles with a local cap. The spawn path only reads it; a
  value one second old is good enough for a cap (vanilla's own mob caps are counted once per tick in the same spirit).
 */
public final class RegionThreat {
    private final SemanticRegion region;
    private final ThreatProfile profile;
    private final Optional<Identifier> catalogId;
    private volatile int population;

    public RegionThreat(SemanticRegion region, ThreatProfile profile, Optional<Identifier> catalogId) {
        this.region = region;
        this.profile = profile;
        this.catalogId = catalogId;
    }

    /** A region with its engine profile (no catalog binding). */
    public static RegionThreat builtIn(SemanticRegion region) {
        return new RegionThreat(region, ThreatProfile.builtIn(region.profile()), Optional.empty());
    }

    public SemanticRegion region() {
        return this.region;
    }

    public ThreatProfile profile() {
        return this.profile;
    }

    /** The POI catalog entry that chose the profile, if one did. */
    public Optional<Identifier> catalogId() {
        return this.catalogId;
    }

    public int population() {
        return this.population;
    }

    public void setPopulation(int population) {
        this.population = population;
    }

    public String describe() {
        return this.profile.describe() + this.catalogId.map(c -> ", chosen by POI catalog entry " + c).orElse(", engine profile of " + this.region.id())
                + (this.profile.hasLocalCap() ? ", " + this.population + " of " + this.profile.localCap() + " ambient hostiles present" : "");
    }
}
