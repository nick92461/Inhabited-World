package io.github.nick92461.inhabitedworld.worldgen.pregen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/*
  How far pre-generation has come, saved with the overworld (dimensions/minecraft/overworld/data/inhabited_world/
  pregeneration.dat). If the game closes while the world is still being built, the next opening carries on from
  the first unfinished band; chunks already on disk just load. The schedule itself is saved too, so a resumed run
  walks exactly the same chunks in the same order.
 */
public final class PregenState extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<PregenState> CODEC = RecordCodecBuilder.<PregenState>create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(state -> DATA_VERSION),
                    PregenSchedule.CODEC.fieldOf("schedule").forGetter(state -> state.schedule),
                    Codec.INT.fieldOf("next_band").forGetter(state -> state.nextBand))
            .apply(i, PregenState::new))
            .validate(state -> state.dataVersion == DATA_VERSION
                    ? DataResult.success(state)
                    : DataResult.error(() -> "unsupported pre-generation data version " + state.dataVersion));

    // See WorldPlanData for the data-fix type.
    public static final SavedDataType<PregenState> TYPE = new SavedDataType<>(
            InhabitedWorld.id("pregeneration"),
            () -> {
                throw new IllegalStateException("pre-generation state is made from a WorldPlan");
            },
            CODEC,
            DataFixTypes.SAVED_DATA_STOPWATCHES);

    private final int dataVersion;
    private final PregenSchedule schedule;
    private int nextBand;

    private PregenState(int dataVersion, PregenSchedule schedule, int nextBand) {
        this.dataVersion = dataVersion;
        this.schedule = schedule;
        this.nextBand = Math.clamp(nextBand, 0, schedule.bandCount());
    }

    public static PregenState start(PregenSchedule schedule) {
        return new PregenState(DATA_VERSION, schedule, 0);
    }

    public PregenSchedule schedule() {
        return this.schedule;
    }

    public int nextBand() {
        return this.nextBand;
    }

    public boolean isComplete() {
        return this.nextBand >= this.schedule.bandCount();
    }

    public void completeBand(int band) {
        if (band != this.nextBand) {
            throw new IllegalStateException("bands complete in order: expected " + this.nextBand + ", got " + band);
        }
        this.nextBand++;
        this.setDirty();
    }

    static Codec<PregenState> codec() {
        return CODEC;
    }
}
