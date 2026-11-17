package io.github.nick92461.inhabitedworld.threat;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.Heightmap;

/*
  Where a hotspot or an encounter stands, relative to the POI site it belongs to (canon 06 section 17.12 item 8: rules
  derive from semantic identity, not quest coordinates). The site is the planner's (its centre and its predicted
  ground), so the same definition lands in the right place in every world.

    {"offset": [0, -20, 0], "vertical": "ground"}

  - offset: blocks from the site's centre column (x, z) and from the vertical reference (y)
  - vertical: "surface", the top of the terrain at that column (read from the level once the chunk is loaded), or
    "ground", the site's predicted median ground height from the WorldPlan (a depth under the POI without reading blocks)

  Placeholder until Phase 3 part 3 builds the mine: its build will carry named semantic markers (02 section 16), and an
  anchor will then name one ("marker": "<id>"), resolved through the same resolve() call; nothing else changes.
 */
public record SiteAnchor(Vec3i offset, Vertical vertical) {
    public enum Vertical implements StringRepresentable {
        SURFACE,
        GROUND;

        public static final Codec<Vertical> CODEC = StringRepresentable.fromEnum(Vertical::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final SiteAnchor CENTRE_SURFACE = new SiteAnchor(Vec3i.ZERO, Vertical.SURFACE);

    public static final Codec<SiteAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(SiteAnchor::offset),
                    Vertical.CODEC.optionalFieldOf("vertical", Vertical.SURFACE).forGetter(SiteAnchor::vertical))
            .apply(i, SiteAnchor::new));

    /** Something that knows where an anchor is in a running level (a site anchor, a fixed test position, later a marker). */
    public interface Resolver {
        /** The anchor block, or empty while it can't be known without loading terrain (its chunk is not loaded). */
        Optional<BlockPos> resolve(ServerLevel level);

        /** Loads the terrain the anchor needs (development commands; the game itself waits for players to load it). */
        default void load(ServerLevel level) {
        }

        String describe();
    }

    /** This anchor at a planned site. */
    public Resolver at(PlannedSite site) {
        SiteAnchor anchor = this;
        return new Resolver() {
            @Override
            public Optional<BlockPos> resolve(ServerLevel level) {
                int x = site.centerX() + anchor.offset.getX();
                int z = site.centerZ() + anchor.offset.getZ();
                return switch (anchor.vertical) {
                    case GROUND -> Optional.of(new BlockPos(x, site.predicted().medianY() + anchor.offset.getY(), z));
                    case SURFACE -> level.hasChunk(x >> 4, z >> 4)
                            ? Optional.of(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + anchor.offset.getY(), z))
                            : Optional.empty();
                };
            }

            @Override
            public void load(ServerLevel level) {
                level.getChunk((site.centerX() + anchor.offset.getX()) >> 4, (site.centerZ() + anchor.offset.getZ()) >> 4);
            }

            @Override
            public String describe() {
                return "site " + site.id() + " centre " + (anchor.offset.equals(Vec3i.ZERO) ? "" : "+ " + anchor.offset.toShortString() + " ")
                        + "at the " + anchor.vertical.getSerializedName();
            }
        };
    }

    /** A fixed position (tests and development). */
    public static Resolver fixed(BlockPos pos) {
        return new Resolver() {
            @Override
            public Optional<BlockPos> resolve(ServerLevel level) {
                return Optional.of(pos);
            }

            @Override
            public String describe() {
                return "fixed " + pos.toShortString();
            }
        };
    }
}
