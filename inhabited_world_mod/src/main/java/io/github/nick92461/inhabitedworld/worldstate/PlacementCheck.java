package io.github.nick92461.inhabitedworld.worldstate;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  Whether a placed build is really in the world (canon 02 section 6: verify "that required POIs actually generated,
  rather than assuming placement succeeded"): after placement, SiteVerifier reads back a deterministic sample of the
  build's template blocks, turned by the site's rotation, and compares them with the world.

  - passed: every sampled block matched
  - sampled, matched: how many template blocks were read back, and how many were as the template says
  - problems: the first few mismatches, for the log and /inhabited plan
 */
public record PlacementCheck(boolean passed, int sampled, int matched, List<String> problems) {
    public static final Codec<PlacementCheck> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.BOOL.fieldOf("passed").forGetter(PlacementCheck::passed),
                    Codec.INT.fieldOf("sampled").forGetter(PlacementCheck::sampled),
                    Codec.INT.fieldOf("matched").forGetter(PlacementCheck::matched),
                    Codec.STRING.listOf().optionalFieldOf("problems", List.of()).forGetter(PlacementCheck::problems))
            .apply(i, PlacementCheck::new));

    public PlacementCheck {
        problems = List.copyOf(problems);
    }

    public String describe() {
        return (this.passed ? "PASSED" : "FAILED") + ": " + this.matched + " of " + this.sampled + " sampled template blocks match"
                + (this.problems.isEmpty() ? "" : " (" + String.join("; ", this.problems) + ")");
    }
}
