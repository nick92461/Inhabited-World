package io.github.nick92461.inhabitedworld.town;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/*
  Development commands for exported towns (canon 10 section 60: dev runs only).

    /inhabited town list                     towns in the mod's data
    /inhabited town paste <town> <pos>       pastes the town with its template origin (north-west, lowest corner) at pos
    /inhabited town markers <town>           the town's markers, at their workshop-world positions

  <town> is a key like drachenfels/d1_harbour/v1, or just d1_harbour for its latest version. Use paste in any test
  world to see what the current templates hold, e.g. right after a re-export. Pasting is big (about 1.6 million
  blocks for D1 Harbour) and takes some seconds.
 */
public final class TownCommand {
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(n -> Component.literal("No town called " + n + " (see /inhabited town list)"));

    private TownCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("inhabited")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("town")
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("paste")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> paste(c.getSource(), StringArgumentType.getString(c, "town"),
                                                        BlockPosArgument.getLoadedBlockPos(c, "pos"))))))
                        .then(Commands.literal("markers")
                                .then(Commands.argument("town", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names(c.getSource().getServer()), b))
                                        .executes(c -> markers(c.getSource(), StringArgumentType.getString(c, "town")))))));
    }

    private static List<String> names(MinecraftServer server) {
        return List.copyOf(TownManifest.list(server.getResourceManager()).keySet());
    }

    private static TownManifest town(CommandSourceStack source, String name) throws CommandSyntaxException {
        return TownManifest.find(source.getServer().getResourceManager(), name).orElseThrow(() -> UNKNOWN.create(name));
    }

    private static int list(CommandSourceStack source) {
        List<String> names = names(source.getServer());
        source.sendSuccess(() -> Component.literal(names.isEmpty() ? "No towns in the mod's data." : "Towns: " + String.join(", ", names)), false);
        return names.size();
    }

    private static int paste(CommandSourceStack source, String name, BlockPos pos) throws CommandSyntaxException {
        TownManifest town = town(source, name);
        TownColumns columns = TownColumns.load(source.getServer().getResourceManager(), town.columnsId());
        TownPaste.Result r = TownPaste.paste(source.getLevel(), town, columns, pos, false);
        source.sendSuccess(() -> Component.literal("Pasted " + town.name() + " v" + town.version() + " ("
                + town.size().getX() + " x " + town.size().getZ() + ", " + town.size().getY() + " high) at " + pos.toShortString()
                + ": " + r), true);
        return 1;
    }

    private static int markers(CommandSourceStack source, String name) throws CommandSyntaxException {
        TownManifest town = town(source, name);
        List<String> lines = new ArrayList<>();
        lines.add(town.name() + " v" + town.version() + ": " + town.markers().size() + " markers (workshop-world positions)");
        for (TownManifest.Marker m : town.markers()) {
            BlockPos at = town.workshopOrigin().offset(m.pos());
            lines.add("  " + m.id() + " at " + at.toShortString() + ", facing " + Math.round(m.yaw()) + (m.note().isEmpty() ? "" : " (" + m.note() + ")"));
        }
        source.sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        return town.markers().size();
    }
}
