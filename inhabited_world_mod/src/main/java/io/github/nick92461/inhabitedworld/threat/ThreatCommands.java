package io.github.nick92461.inhabitedworld.threat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.nick92461.inhabitedworld.encounter.EncounterData;
import io.github.nick92461.inhabitedworld.encounter.EncounterRecord;
import io.github.nick92461.inhabitedworld.encounter.EncounterRuntime;
import io.github.nick92461.inhabitedworld.encounter.Encounters;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/*
  Development debug commands for POI threats (canon 10 section 60, Favor Inspectability; development runs only):

    /inhabited encounter list               every encounter of this world: state, mob, counters
    /inhabited encounter info <id>          one encounter: definition, anchor, persistent record, its mob if loaded
    /inhabited encounter reset <id>         back to not spawned (removes its mob if loaded); it spawns on the next approach
    /inhabited encounter spawn <id>         spawn a not-spawned encounter now, whatever the player's distance
    /inhabited hotspot                      every hotspot: anchor, active or idle, population / target, own mobs / cap
    /inhabited hotspot show                 toggle a particle outline of the hotspot boxes near you (soul flames)

  /inhabited spawnpolicy (SpawnPolicyCommand) shows which threat profile applies where you stand.
 */
public final class ThreatCommands {
    private static final SimpleCommandExceptionType NO_THREATS = new SimpleCommandExceptionType(
            Component.literal("This world has no POI threats (not an Inhabited World with a WorldPlan)"));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(
            id -> Component.literal("No encounter " + id + " in this world"));

    private static final Set<UUID> WATCHING = new HashSet<>();
    private static final int OUTLINE_REACH = 48;

    private ThreatCommands() {
    }

    /** Registered with the development commands only (the hotspot outline ticker). */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ThreatCommands::drawOutlines);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("inhabited")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("encounter")
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("info").then(Commands.argument("id", IdentifierArgument.id()).suggests(ThreatCommands::suggest)
                                .executes(c -> info(c.getSource(), IdentifierArgument.getId(c, "id")))))
                        .then(Commands.literal("reset").then(Commands.argument("id", IdentifierArgument.id()).suggests(ThreatCommands::suggest)
                                .executes(c -> reset(c.getSource(), IdentifierArgument.getId(c, "id")))))
                        .then(Commands.literal("spawn").then(Commands.argument("id", IdentifierArgument.id()).suggests(ThreatCommands::suggest)
                                .executes(c -> spawn(c.getSource(), IdentifierArgument.getId(c, "id"))))))
                .then(Commands.literal("hotspot")
                        .executes(c -> hotspots(c.getSource()))
                        .then(Commands.literal("show").executes(c -> toggleOutline(c.getSource())))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(Encounters.ids(c.getSource().getServer()), builder);
    }

    private static LevelThreats threats(CommandSourceStack source) throws CommandSyntaxException {
        LevelThreats threats = LevelThreats.of(source.getServer().overworld());
        if (threats == null || Encounters.store(source.getServer()) == null) {
            throw NO_THREATS.create();
        }
        return threats;
    }

    private static EncounterRuntime.Bound bound(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        EncounterRuntime.Bound bound = threats(source).encounters().get(id);
        if (bound == null) {
            throw UNKNOWN.create(id);
        }
        return bound;
    }

    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        LevelThreats threats = threats(source);
        EncounterData data = Encounters.store(source.getServer());
        List<String> lines = new ArrayList<>();
        lines.add("Guaranteed encounters of this world (" + threats.encounters().encounters().size() + "):");
        for (EncounterRuntime.Bound bound : threats.encounters().encounters()) {
            lines.add("  " + bound.id() + ": " + data.get(bound.id()).describe());
        }
        send(source, lines);
        return threats.encounters().encounters().size();
    }

    private static int info(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        EncounterRuntime.Bound bound = bound(source, id);
        ServerLevel level = source.getServer().overworld();
        EncounterData data = Encounters.store(source.getServer());
        EncounterRecord record = data.get(id);
        BlockPos anchor = bound.anchor(level);
        List<String> lines = new ArrayList<>();
        lines.add("Encounter " + id + " (store data version " + EncounterData.DATA_VERSION + ", world " + data.worldId() + ")");
        lines.add("  definition: " + bound.definition().describe());
        lines.add("  anchor: " + bound.anchorResolver().describe() + (anchor == null ? " (not resolved: its chunk isn't loaded)" : " = " + anchor.toShortString()));
        lines.add("  POI region: " + (bound.region() == null ? "none" : bound.region().id()) + "; tag " + bound.tag());
        lines.add("  state: " + record.describe());
        Mob mob = Encounters.entity(source.getServer(), id).orElse(null);
        lines.add("  mob: " + (mob == null ? (record.isActive() ? "not loaded (its chunk is unloaded)" : "none")
                : mob.getName().getString() + " at " + mob.blockPosition().toShortString() + ", health " + mob.getHealth() + "/" + mob.getMaxHealth()
                + ", persistence required " + mob.isPersistenceRequired() + (mob.getTarget() == null ? "" : ", fighting " + mob.getTarget().getName().getString())));
        if (bound.problem() != null) {
            lines.add("  last problem: " + bound.problem());
        }
        send(source, lines);
        return record.state().ordinal();
    }

    private static int reset(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        EncounterRuntime.Bound bound = bound(source, id);
        LevelThreats threats = threats(source);
        threats.encounters().reset(source.getServer().overworld(), Encounters.store(source.getServer()), bound);
        send(source, List.of("Encounter " + id + " reset: not spawned; it spawns when a player comes within " + bound.definition().triggerRadius()
                + " blocks of its anchor"));
        return 1;
    }

    private static int spawn(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        EncounterRuntime.Bound bound = bound(source, id);
        LevelThreats threats = threats(source);
        ServerLevel level = source.getServer().overworld();
        // Load the anchor's chunk, so a surface anchor resolves from anywhere.
        bound.anchorResolver().load(level);
        Mob mob = threats.encounters().spawn(level, Encounters.store(source.getServer()), bound);
        if (mob == null) {
            send(source, List.of("Encounter " + id + " not spawned: " + bound.problem() + (bound.problem() != null && bound.problem().startsWith("it is")
                    ? " (reset it first)" : "")));
            return 0;
        }
        send(source, List.of("Encounter " + id + " spawned: " + mob.getName().getString() + " at " + mob.blockPosition().toShortString()));
        return 1;
    }

    private static int hotspots(CommandSourceStack source) throws CommandSyntaxException {
        LevelThreats threats = threats(source);
        List<String> lines = new ArrayList<>();
        lines.add("Hotspots of this world (" + threats.hotspots().size() + "):");
        for (String line : Hotspot.describeAll(source.getServer().overworld(), threats.hotspots())) {
            lines.add("  " + line);
        }
        send(source, lines);
        return threats.hotspots().size();
    }

    private static int toggleOutline(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean on = WATCHING.add(player.getUUID()) || !WATCHING.remove(player.getUUID());
        send(source, List.of("Hotspot outlines " + (on ? "on (soul-fire particles along each box's edges near you)" : "off")));
        return on ? 1 : 0;
    }

    private static void drawOutlines(MinecraftServer server) {
        if (WATCHING.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        LevelThreats threats = LevelThreats.of(server.overworld());
        if (threats == null) {
            return;
        }
        ServerLevel level = server.overworld();
        for (UUID id : WATCHING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != level) {
                continue;
            }
            for (Hotspot hotspot : threats.hotspots()) {
                BlockPos at = hotspot.anchor(level);
                if (at != null) {
                    outline(level, player, hotspot.box(at));
                }
            }
        }
    }

    // The twelve edges of the box, a particle every two blocks, within reach of the player.
    private static void outline(ServerLevel level, ServerPlayer player, AABB box) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double y : ys) {
            for (double z : zs) {
                for (double x = box.minX; x <= box.maxX; x += 2) {
                    particle(level, player, x, y, z);
                }
            }
            for (double x : xs) {
                for (double z = box.minZ; z <= box.maxZ; z += 2) {
                    particle(level, player, x, y, z);
                }
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                for (double y = box.minY; y <= box.maxY; y += 2) {
                    particle(level, player, x, y, z);
                }
            }
        }
    }

    private static void particle(ServerLevel level, ServerPlayer player, double x, double y, double z) {
        if (Math.abs(x - player.getX()) <= OUTLINE_REACH && Math.abs(y - player.getY()) <= OUTLINE_REACH && Math.abs(z - player.getZ()) <= OUTLINE_REACH) {
            level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, true, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void send(CommandSourceStack source, List<String> lines) {
        String text = String.join("\n", lines);
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
