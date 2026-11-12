package io.github.nick92461.inhabitedworld.worldstate;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.worldgen.planner.SiteMetrics;

import net.minecraft.core.BlockPos;

/*
  What verification after pre-generation found (canon 02 section 6: pre-generation "lets the planner verify that
  required POIs actually generated, rather than assuming placement succeeded"). Recorded once in the WorldPlan.

  - passed: every site's generated terrain meets the rules it was planned under (within SiteVerifier's tolerance),
    and the world spawn lies outside every POI region
  - sites: per site, the terrain found and any problems
  - spawn: the world spawn vanilla chose, if known when verification ran
 */
public record SiteVerification(boolean passed, List<SiteCheck> sites, Optional<BlockPos> spawn, List<String> problems) {
    public static final Codec<SiteVerification> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.BOOL.fieldOf("passed").forGetter(SiteVerification::passed),
                    SiteCheck.CODEC.listOf().fieldOf("sites").forGetter(SiteVerification::sites),
                    BlockPos.CODEC.optionalFieldOf("spawn").forGetter(SiteVerification::spawn),
                    Codec.STRING.listOf().optionalFieldOf("problems", List.of()).forGetter(SiteVerification::problems))
            .apply(i, SiteVerification::new));

    public SiteVerification {
        sites = List.copyOf(sites);
        problems = List.copyOf(problems);
    }

    /** One site's check: the terrain found in the generated chunks and what, if anything, fell short. */
    public record SiteCheck(String id, boolean passed, SiteMetrics generated, List<String> problems) {
        public static final Codec<SiteCheck> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("id").forGetter(SiteCheck::id),
                        Codec.BOOL.fieldOf("passed").forGetter(SiteCheck::passed),
                        SiteMetrics.CODEC.fieldOf("generated_terrain").forGetter(SiteCheck::generated),
                        Codec.STRING.listOf().optionalFieldOf("problems", List.of()).forGetter(SiteCheck::problems))
                .apply(i, SiteCheck::new));

        public SiteCheck {
            problems = List.copyOf(problems);
        }
    }
}
