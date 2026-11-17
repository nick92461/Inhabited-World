package io.github.nick92461.inhabitedworld.threat;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/*
  What a kind of POI means for threats (canon 02 section 10: a semantic POI has a threat profile and a hostile-spawn
  policy; 06 section 17.3: the semantic POI owns its threat meaning): the threat profile of its region and the
  guaranteed encounters it holds. Keyed by the POI's catalog ID, which the WorldPlan saves with the planned site, so a
  world finds its POI's threats from the POI's identity, never from coordinates (06 section 17.12 item 8).

    data/<namespace>/poi_threats/<catalog path>.json, for catalog ID <namespace>:<catalog path>
    {"threat_profile": "inhabited_world:placeholder_abandoned_mine",
     "encounters": ["inhabited_world:placeholder_mine_foreman"]}

  The encounters' anchors are relative to the site that has this catalog entry. Version 0.1 plans one such site; a
  catalog entry used by several sites would need per-site encounter IDs (not needed yet, 10 section 57).
 */
public record PoiThreatBinding(Identifier threatProfile, List<Identifier> encounters) {
    public static final Codec<PoiThreatBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Identifier.CODEC.fieldOf("threat_profile").forGetter(PoiThreatBinding::threatProfile),
                    Identifier.CODEC.listOf().optionalFieldOf("encounters", List.of()).forGetter(PoiThreatBinding::encounters))
            .apply(i, PoiThreatBinding::new));
}
