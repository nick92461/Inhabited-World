package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.nick92461.inhabitedworld.worldgen.PlayableArea;

/*
  Everything the planner is asked to place: the sites, in planning order (the most constrained first), and the
  spacing rules between them, inside the playable area. Every site's semantic region stays at least edgeMargin
  blocks inside the world border.

  The list holds any number of sites: Version 0.1 asks for one settlement and one POI (V01Sites); a larger world
  would ask for more (implementation plan, long-term vision) through the same request. The playable area is the
  only source of the map's size.
 */
public record PlanRequest(PlayableArea area, int edgeMargin, List<SiteRequirement> sites, List<SiteSpacing> spacings) {
    public PlanRequest {
        sites = List.copyOf(sites);
        spacings = List.copyOf(spacings);
        Set<String> ids = new HashSet<>();
        for (SiteRequirement site : sites) {
            if (!ids.add(site.id())) {
                throw new IllegalArgumentException("duplicate site id " + site.id());
            }
        }
        for (SiteSpacing spacing : spacings) {
            if (!ids.contains(spacing.first()) || !ids.contains(spacing.second())) {
                throw new IllegalArgumentException("spacing rule names an unknown site: " + spacing);
            }
        }
        if (edgeMargin < 0) {
            throw new IllegalArgumentException("edge margin must not be negative");
        }
    }

    /** The block rectangle every semantic region must stay inside. */
    public BlockRect usableArea() {
        return new BlockRect(this.area.minBlockX(), this.area.minBlockZ(), this.area.maxBlockX(), this.area.maxBlockZ()).inflate(-this.edgeMargin);
    }

    public PlanRequest with(List<SiteRequirement> sites, List<SiteSpacing> spacings) {
        return new PlanRequest(this.area, this.edgeMargin, sites, spacings);
    }
}
