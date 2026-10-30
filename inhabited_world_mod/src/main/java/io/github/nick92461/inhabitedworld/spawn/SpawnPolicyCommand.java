package io.github.nick92461.inhabitedworld.spawn;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import io.github.nick92461.inhabitedworld.worldgen.RpgWorlds;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;

/*
  Development debug command (canon 10 section 60, Favor Inspectability): what the spawn policy decides for an
  entity type and spawn reason at the caller's position, and why.

    /inhabited spawnpolicy <entity> [<reason>]      reason defaults to natural

  For minecraft:creaking it also reports the creaking-heart rule (generated hearts stay dormant, placed ones work).

  Registered only in development runs (10 section 60: test conveniences never reach the shipped game).
  P2B adds the semantic region and the POI or settlement that supplied the rule.
 */
public final class SpawnPolicyCommand {
    private static final DynamicCommandExceptionType UNKNOWN_REASON =
            new DynamicCommandExceptionType(reason -> Component.literal("Unknown spawn reason: " + reason));

    private SpawnPolicyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("inhabited")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawnpolicy")
                        .then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                .executes(c -> report(c.getSource(), ResourceArgument.getResource(c, "entity", Registries.ENTITY_TYPE).value(),
                                        EntitySpawnReason.NATURAL))
                                .then(Commands.argument("reason", StringArgumentType.word())
                                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(EntitySpawnReason.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), builder))
                                        .executes(c -> report(c.getSource(), ResourceArgument.getResource(c, "entity", Registries.ENTITY_TYPE).value(),
                                                parseReason(StringArgumentType.getString(c, "reason"))))))));
    }

    private static EntitySpawnReason parseReason(String name) throws CommandSyntaxException {
        try {
            return EntitySpawnReason.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_REASON.create(name);
        }
    }

    private static int report(CommandSourceStack source, EntityType<?> type, EntitySpawnReason reason) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        boolean rpg = RpgWorlds.isRpg(level);
        boolean prohibited = SpawnGovernance.isProhibitedHumanLike(type);
        SpawnPolicy.Decision decision = SpawnGovernance.decide(type, level, reason);
        // The complete check a natural spawn would make here: vanilla's placement rules, then ours.
        boolean fullCheck = SpawnPlacements.checkSpawnRules(type, level, reason, pos, level.getRandom());

        String text = "Spawn policy at " + pos.toShortString() + " in " + level.dimension().identifier()
                + " (Inhabited World: " + yesNo(rpg) + ")\n"
                + EntityType.getKey(type) + ", reason " + reason.name().toLowerCase(Locale.ROOT)
                + " (" + SpawnPolicy.origin(reason).name().toLowerCase(Locale.ROOT) + "): "
                + (decision.allowed() ? "ALLOWED" : "REFUSED") + " - " + decision.explanation() + "\n"
                + "  prohibited human-like (tag " + SpawnGovernance.PROHIBITED_HUMAN_LIKE.location() + "): " + yesNo(prohibited) + "\n"
                + "  full spawn check at this spot (vanilla rules, then ours): " + (fullCheck ? "passes" : "fails") + "\n"
                + creakingHeartLines(type, rpg)
                + "  semantic region: none yet (regions arrive in Phase 2B)";
        source.sendSuccess(() -> Component.literal(text), false);
        return decision.allowed() ? 1 : 0;
    }

    // Hearts summon their creaking outside the spawn check above, so their rule is reported on its own.
    private static String creakingHeartLines(EntityType<?> type, boolean rpg) {
        if (type != EntityTypes.CREAKING) {
            return "";
        }
        SpawnPolicy.Decision natural = SpawnPolicy.decideCreakingHeart(rpg, true);
        SpawnPolicy.Decision placed = SpawnPolicy.decideCreakingHeart(rpg, false);
        return "  from a generated creaking heart (natural=true): " + (natural.allowed() ? "ALLOWED" : "REFUSED") + " - " + natural.explanation() + "\n"
                + "  from a placed creaking heart (natural=false): " + (placed.allowed() ? "ALLOWED" : "REFUSED") + " - " + placed.explanation() + "\n";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
