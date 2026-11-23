package io.github.nick92461.inhabitedworld.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.util.StringRepresentable;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/*
  How far the placement of a new world's handcrafted builds has come, saved with the overworld
  (dimensions/minecraft/overworld/data/inhabited_world/structure_placement.dat), like pre-generation's progress.

  It is made once, when the WorldPlan is made (WorldPlans.createAtWorldCreation), with one entry per planned site
  that has a build to place, naming the exact build version chosen then. Nothing else ever adds an entry, so:
    - a world created before placement existed has no such file, and nothing is ever placed into it
    - a placed build is DONE and never placed again, whatever later loads or mod versions do
    - a world closed in the middle of creation resumes at the first unfinished step

  Steps (TownPlacement): TILES (one per template tile, nextTile counts them), BLEND, then DONE once the placed
  structure is recorded in the WorldPlan. Every step is idempotent (it sets blocks to values that depend only on the
  town's data and the generated ground outside the site's region), so a step repeated after a crash gives the same
  world; the saved progress may lag behind the saved chunks, never the other way round (TownPlacement only writes
  it together with a full chunk save).
 */
public final class PlacementState extends SavedData {
    public static final int DATA_VERSION = 1;

    public enum Stage implements StringRepresentable {
        TILES, BLEND, DONE;

        public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** One site's placement: the build (a town manifest key such as "drachenfels/d1_harbour/v1") and its progress. */
    public static final class Entry {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("site").forGetter(Entry::site),
                        Codec.STRING.fieldOf("build").forGetter(Entry::build),
                        Stage.CODEC.fieldOf("stage").forGetter(Entry::stage),
                        Codec.INT.optionalFieldOf("next_tile", 0).forGetter(Entry::nextTile),
                        Codec.STRING.optionalFieldOf("failure").forGetter(Entry::failure))
                .apply(i, Entry::new));

        private final String site;
        private final String build;
        private Stage stage;
        private int nextTile;
        private Optional<String> failure;

        Entry(String site, String build, Stage stage, int nextTile, Optional<String> failure) {
            this.site = site;
            this.build = build;
            this.stage = stage;
            this.nextTile = Math.max(0, nextTile);
            this.failure = failure;
        }

        public String site() {
            return this.site;
        }

        public String build() {
            return this.build;
        }

        public Stage stage() {
            return this.stage;
        }

        public int nextTile() {
            return this.nextTile;
        }

        /** Why placing this build stopped for good (a missing or broken build file), if it did. */
        public Optional<String> failure() {
            return this.failure;
        }

        public boolean isDone() {
            return this.stage == Stage.DONE;
        }

        @Override
        public String toString() {
            return this.site + " (" + this.build + "): " + this.stage.getSerializedName()
                    + (this.stage == Stage.TILES ? " from tile " + this.nextTile : "") + this.failure.map(f -> ", failed: " + f).orElse("");
        }
    }

    private static final Codec<PlacementState> CODEC = RecordCodecBuilder.<PlacementState>create(i -> i.group(
                    Codec.INT.fieldOf("data_version").forGetter(state -> DATA_VERSION),
                    Entry.CODEC.listOf().fieldOf("sites").forGetter(state -> state.entries))
            .apply(i, PlacementState::new))
            .validate(state -> state.dataVersion == DATA_VERSION
                    ? DataResult.success(state)
                    : DataResult.error(() -> "unsupported structure placement data version " + state.dataVersion));

    // See WorldPlanData for the data-fix type.
    public static final SavedDataType<PlacementState> TYPE = new SavedDataType<>(
            InhabitedWorld.id("structure_placement"),
            () -> {
                throw new IllegalStateException("structure placement state is only made with a new WorldPlan");
            },
            CODEC,
            DataFixTypes.SAVED_DATA_STOPWATCHES);

    private final int dataVersion;
    private final List<Entry> entries;

    private PlacementState(int dataVersion, List<Entry> entries) {
        this.dataVersion = dataVersion;
        this.entries = new ArrayList<>(entries);
    }

    /** A site with a build to place, chosen when the plan is made. */
    public record Pending(String site, String build) {
    }

    /** A new world's placements: one entry per pending site, nothing placed yet. */
    public static PlacementState start(List<Pending> pending) {
        List<Entry> entries = new ArrayList<>();
        for (Pending p : pending) {
            entries.add(new Entry(p.site(), p.build(), Stage.TILES, 0, Optional.empty()));
        }
        return new PlacementState(DATA_VERSION, entries);
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    public boolean isComplete() {
        return this.entries.stream().allMatch(e -> e.isDone() || e.failure.isPresent());
    }

    public void tilePlaced(Entry entry, int tile) {
        if (entry.stage != Stage.TILES || tile != entry.nextTile) {
            throw new IllegalStateException("tiles are placed in order: expected " + entry.nextTile + " in stage " + entry.stage + ", got " + tile);
        }
        entry.nextTile++;
        this.setDirty();
    }

    public void tilesDone(Entry entry) {
        this.advance(entry, Stage.TILES, Stage.BLEND);
    }

    public void blendDone(Entry entry) {
        this.advance(entry, Stage.BLEND, Stage.DONE);
    }

    public void fail(Entry entry, String why) {
        entry.failure = Optional.of(why);
        this.setDirty();
    }

    private void advance(Entry entry, Stage from, Stage to) {
        if (entry.stage != from) {
            throw new IllegalStateException("site " + entry.site + " is at " + entry.stage + ", not " + from);
        }
        entry.stage = to;
        this.setDirty();
    }

    static Codec<PlacementState> codec() {
        return CODEC;
    }
}
