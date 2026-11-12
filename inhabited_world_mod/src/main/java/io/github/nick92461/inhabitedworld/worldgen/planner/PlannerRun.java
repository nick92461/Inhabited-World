package io.github.nick92461.inhabitedworld.worldgen.planner;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/*
  Which planner made a WorldPlan's sites, and how (canon 02 sections 6 and 7: a world keeps its plan, so the plan
  records the algorithm and version that made it; a later mod version with a newer planner never re-plans it).

  - algorithm, version: SitePlanner.ALGORITHM and SitePlanner.VERSION at creation
  - relaxation: the fallback step the plan needed (0 = every rule met as asked; see SitePlanner)
  - candidates: how many candidate sites the planner judged, for diagnostics
 */
public record PlannerRun(Identifier algorithm, int version, int relaxation, int candidates) {
    public static final Codec<PlannerRun> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Identifier.CODEC.fieldOf("algorithm").forGetter(PlannerRun::algorithm),
                    Codec.INT.fieldOf("version").forGetter(PlannerRun::version),
                    Codec.INT.fieldOf("relaxation").forGetter(PlannerRun::relaxation),
                    Codec.INT.fieldOf("candidates").forGetter(PlannerRun::candidates))
            .apply(i, PlannerRun::new));

    public String describe() {
        return String.format(Locale.ROOT, "%s v%d, fallback step %d (%s), %d candidates judged", this.algorithm, this.version, this.relaxation,
                SitePlanner.relaxationName(this.relaxation), this.candidates);
    }
}
