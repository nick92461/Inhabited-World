package io.github.nick92461.inhabitedworld.worldgen.planner;

import net.minecraft.resources.Identifier;

/*
  One site the world must contain (canon 02 section 6: "the planner must guarantee that required gameplay elements
  exist"):

  - id: the site's semantic ID in this world's plan (canon 08 section 53 prefers semantic IDs to coordinates)
  - kind: settlement or POI
  - catalogId: which handcrafted place goes there (02 section 9: the place's identity is handcrafted, its incarnation
    in a world procedural). Version 0.1's town is Drachenfels district D1 Harbour; its POI is still a placeholder.
  - rules: footprint, waterfront, terrain limits, spawn clearance
 */
public record SiteRequirement(String id, SiteKind kind, Identifier catalogId, SiteRules rules) {
    public SiteRequirement withRules(SiteRules rules) {
        return new SiteRequirement(this.id, this.kind, this.catalogId, rules);
    }
}
