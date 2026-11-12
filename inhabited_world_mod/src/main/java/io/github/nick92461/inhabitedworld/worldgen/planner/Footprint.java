package io.github.nick92461.inhabitedworld.worldgen.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*
  The ground a site's build occupies, in blocks, plus the blend border around it where generated terrain is adapted
  to the build (Phase 3: beardifier, kept-terrain mask edge). The build's bounds plus the blend border form the
  site's semantic region.

  Sizes are whole chunks (multiples of 16): the planner works on a chunk lattice, and a chunk-aligned build is what
  the structure system places in Phase 3.
 */
public record Footprint(int sizeX, int sizeZ, int blend) {
    private record Fields(int sizeX, int sizeZ, int blend) {
    }

    public static final Codec<Footprint> CODEC = RecordCodecBuilder.<Fields>create(i -> i.group(
                    Codec.INT.fieldOf("size_x").forGetter(Fields::sizeX),
                    Codec.INT.fieldOf("size_z").forGetter(Fields::sizeZ),
                    Codec.INT.fieldOf("blend").forGetter(Fields::blend))
            .apply(i, Fields::new))
            .flatXmap(
                    f -> {
                        String problem = problem(f.sizeX(), f.sizeZ(), f.blend());
                        return problem == null ? DataResult.success(new Footprint(f.sizeX(), f.sizeZ(), f.blend())) : DataResult.error(() -> problem);
                    },
                    fp -> DataResult.success(new Fields(fp.sizeX(), fp.sizeZ(), fp.blend())));

    public Footprint {
        String problem = problem(sizeX, sizeZ, blend);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    private static String problem(int sizeX, int sizeZ, int blend) {
        if (sizeX <= 0 || sizeZ <= 0 || sizeX % 16 != 0 || sizeZ % 16 != 0) {
            return "footprint sizes must be positive whole chunks (multiples of 16), got " + sizeX + " x " + sizeZ;
        }
        if (blend < 0) {
            return "blend border must not be negative, got " + blend;
        }
        return null;
    }
}
