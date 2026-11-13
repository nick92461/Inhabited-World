package io.github.nick92461.inhabitedworld.worldstate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.nick92461.inhabitedworld.region.RegionEdges;
import io.github.nick92461.inhabitedworld.region.RegionIndex;
import io.github.nick92461.inhabitedworld.region.Regions;
import io.github.nick92461.inhabitedworld.region.SemanticRegion;
import io.github.nick92461.inhabitedworld.worldgen.planner.PlannedSite;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/*
  Development debug command (canon 10 section 60, Favor Inspectability): the WorldPlan's planned sites.

    /inhabited plan                 the planner, every site (bounds, region, waterfront, predicted and verified
                                    terrain), the distance and direction between sites, and where you stand
    /inhabited plan tp <site>       teleports you to the middle of a site, on its surface
    /inhabited plan region          the semantic region at your position (3D), its spawn profile and source site
    /inhabited plan edges           toggles particles along the edges of nearby regions

  Registered only in development runs (10 section 60: test conveniences never reach the shipped game).
 */
public final class WorldPlanCommand {
    private static final SimpleCommandExceptionType NO_PLAN =
            new SimpleCommandExceptionType(Component.literal("This world has no WorldPlan (it is not an Inhabited World, or was made before WorldPlans existed)"));
    private static final DynamicCommandExceptionType UNKNOWN_SITE =
            new DynamicCommandExceptionType(id -> Component.literal("The WorldPlan has no site called " + id));

    private WorldPlanCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("inhabited")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("plan")
                        .executes(c -> report(c.getSource()))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("site", StringArgumentType.word())
                                        .suggests((c, builder) -> {
                                            WorldPlan plan = WorldPlans.get(c.getSource().getServer());
                                            return SharedSuggestionProvider.suggest(plan == null ? List.of() : plan.sites().stream().map(PlannedSite::id).toList(), builder);
                                        })
                                        .executes(c -> teleport(c.getSource(), StringArgumentType.getString(c, "site")))))
                        .then(Commands.literal("region")
                                .executes(c -> regionHere(c.getSource())))
                        .then(Commands.literal("edges")
                                .executes(c -> toggleEdges(c.getSource())))));
    }

    // The semantic region that applies where you stand, every region containing that block, and the profile in force.
    private static int regionHere(CommandSourceStack source) throws CommandSyntaxException {
        plan(source);
        RegionIndex index = Regions.index(source.getLevel());
        BlockPos pos = BlockPos.containing(source.getPosition());
        List<String> lines = new ArrayList<>();
        if (index == null) {
            lines.add("This level has no semantic regions: all of it is wilderness.");
            send(source, lines);
            return 0;
        }
        SemanticRegion region = index.regionAt(pos.getX(), pos.getY(), pos.getZ());
        lines.add("Semantic region at " + pos.toShortString() + ": " + region.id() + " (" + region.kind().getSerializedName() + ")");
        lines.add("  spawn profile " + region.profile().id() + (region.profile().suppressesAmbientHostiles()
                ? ": ordinary hostile generation suppressed (hostiles may walk in)" : ": vanilla-style spawning"));
        lines.add("  supplied by " + region.site().map(s -> "site " + s).orElse("the wilderness (no settlement or POI covers this block)"));
        lines.add("  volume " + region.volume());
        List<SemanticRegion> all = index.regionsAt(pos.getX(), pos.getY(), pos.getZ());
        if (all.size() > 1) {
            lines.add("  overlapping here, most specific first: " + String.join(", ", all.stream().map(SemanticRegion::id).toList()));
        }
        lines.add("  this chunk column " + (index.isWildernessChunk(pos.getX() >> 4, pos.getZ() >> 4)
                ? "touches no region (spawn checks skip the region lookup)" : "touches a region"));
        lines.add("Try /inhabited spawnpolicy minecraft:zombie for the decision on a mob here.");
        send(source, lines);
        return region.isWilderness() ? 0 : 1;
    }

    private static int toggleEdges(CommandSourceStack source) throws CommandSyntaxException {
        plan(source);
        boolean on = RegionEdges.toggle(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal(on
                ? "Showing semantic-region edges near you (green: settlement, flames: POI). Run again to stop."
                : "Stopped showing semantic-region edges."), false);
        return on ? 1 : 0;
    }

    private static WorldPlan plan(CommandSourceStack source) throws CommandSyntaxException {
        WorldPlan plan = WorldPlans.get(source.getServer());
        if (plan == null) {
            throw NO_PLAN.create();
        }
        return plan;
    }

    private static int report(CommandSourceStack source) throws CommandSyntaxException {
        WorldPlan plan = plan(source);
        List<String> lines = new ArrayList<>();
        lines.add("WorldPlan of world " + plan.worldId() + " (data version " + plan.dataVersion() + ")");
        lines.add("Playable area: " + plan.playableArea().size() + " x " + plan.playableArea().size() + " centred on "
                + plan.playableArea().centerX() + ", " + plan.playableArea().centerZ());
        if (!plan.hasSites()) {
            lines.add("No planned sites: this world was created before the planner existed, and is never re-planned.");
            send(source, lines);
            return 0;
        }
        lines.add("Planner: " + plan.planner().map(r -> r.describe()).orElse("none"));
        for (PlannedSite site : plan.sites()) {
            lines.add("Site " + site.id() + " (" + site.kind().getSerializedName() + ", " + site.catalogId() + "): centre "
                    + site.centerX() + ", " + site.centerZ() + site.waterfront().map(d -> ", waterfront faces " + d.getName()).orElse("")
                    + (site.rotation() == Rotation.NONE ? "" : ", build rotated " + site.rotation().getSerializedName()));
            lines.add("  bounds " + site.bounds() + " (" + site.bounds().sizeX() + " x " + site.bounds().sizeZ() + "), semantic boundary " + site.region()
                    + site.waterfront().map(d -> ", waterfront strip " + site.rules().waterfrontDepth() + " blocks").orElse(""));
            for (SemanticRegion region : plan.regions()) {
                if (region.site().filter(site.id()::equals).isPresent()) {
                    lines.add("  region " + region.id() + ": " + region.volume() + ", spawn profile " + region.profile().id());
                }
            }
            lines.add("  predicted: " + site.predicted().describe());
            plan.verification().flatMap(v -> v.sites().stream().filter(c -> c.id().equals(site.id())).findFirst()).ifPresent(check ->
                    lines.add("  generated: " + check.generated().describe() + (check.passed() ? " - OK" : " - FAILED: " + String.join("; ", check.problems()))));
        }
        for (int a = 0; a < plan.sites().size(); a++) {
            for (int b = a + 1; b < plan.sites().size(); b++) {
                PlannedSite from = plan.sites().get(a);
                PlannedSite to = plan.sites().get(b);
                lines.add(String.format(Locale.ROOT, "%s is %.0f blocks %s of %s (centre to centre); %.0f blocks of wilderness between their regions",
                        to.id(), from.bounds().centerDistanceTo(to.bounds()), compass(to.bounds().centerX() - from.bounds().centerX(),
                                to.bounds().centerZ() - from.bounds().centerZ()), from.id(), from.region().gapTo(to.region())));
            }
        }
        lines.add("Verification: " + plan.verification().map(v -> (v.passed() ? "PASSED" : "FAILED")
                + v.spawn().map(p -> " (world spawn " + p.toShortString() + ")").orElse("")
                + (v.problems().isEmpty() ? "" : ": " + String.join("; ", v.problems()))).orElse("not yet (runs once pre-generation has finished)"));

        Vec3 here = source.getPosition();
        BlockPos block = BlockPos.containing(here);
        RegionIndex index = Regions.index(source.getLevel());
        if (index != null) {
            lines.add("You stand in semantic region " + index.regionAt(block.getX(), block.getY(), block.getZ()).id() + " (details: /inhabited plan region)");
        }
        for (PlannedSite site : plan.sites()) {
            boolean inside = site.region().contains(block.getX(), block.getZ());
            lines.add(String.format(Locale.ROOT, "You: %s - %.0f blocks from %s's centre, to the %s", inside ? "inside " + site.id() + "'s region" : "outside "
                    + site.id() + "'s region", Math.hypot(site.bounds().centerX() - here.x, site.bounds().centerZ() - here.z), site.id(),
                    compass(site.bounds().centerX() - here.x, site.bounds().centerZ() - here.z)));
        }
        send(source, lines);
        return plan.sites().size();
    }

    private static int teleport(CommandSourceStack source, String id) throws CommandSyntaxException {
        PlannedSite site = plan(source).site(id);
        if (site == null) {
            throw UNKNOWN_SITE.create(id);
        }
        Entity entity = source.getEntityOrException();
        ServerLevel level = source.getServer().overworld();
        int x = site.centerX();
        int z = site.centerZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        entity.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), entity.getYRot(), entity.getXRot(), true);
        source.sendSuccess(() -> Component.literal("Teleported to the middle of " + id + " at " + x + ", " + y + ", " + z), false);
        return 1;
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        String text = String.join("\n", lines);
        source.sendSuccess(() -> Component.literal(text), false);
    }

    /** One of eight compass directions for a horizontal offset (north is -z). */
    static String compass(double dx, double dz) {
        if (dx == 0 && dz == 0) {
            return "here";
        }
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        return names[sector];
    }
}
