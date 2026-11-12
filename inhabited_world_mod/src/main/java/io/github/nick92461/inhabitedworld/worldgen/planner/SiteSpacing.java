package io.github.nick92461.inhabitedworld.worldgen.planner;

/*
  How two planned sites relate: their semantic regions keep at least minGap blocks of wilderness between them, and
  their centres lie at most maxCenterDistance blocks apart. Sites without a spacing rule only must not overlap.
 */
public record SiteSpacing(String first, String second, int minGap, int maxCenterDistance) {
    public boolean concerns(String a, String b) {
        return (this.first.equals(a) && this.second.equals(b)) || (this.first.equals(b) && this.second.equals(a));
    }

    public SiteSpacing relaxed(int minGap, int maxCenterDistance) {
        return new SiteSpacing(this.first, this.second, minGap, maxCenterDistance);
    }
}
