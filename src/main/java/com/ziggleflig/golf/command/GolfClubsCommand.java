package com.ziggleflig.golf.command;

import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ziggleflig.golf.GolfClubConfig;
import com.ziggleflig.golf.GolfMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class GolfClubsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> clubs = Commands.literal("clubs")
            .then(Commands.literal("show").executes(context -> show(context.getSource())));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set")
            .requires(source -> source.hasPermission(2));
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("all").executes(context -> update(context.getSource(), "all", 1.0D)));
        GolfClubConfig.MULTIPLIERS.keySet().forEach(name -> {
            set.then(Commands.literal(name)
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(
                        GolfClubConfig.MIN_MULTIPLIER, GolfClubConfig.MAX_MULTIPLIER))
                    .executes(context -> update(context.getSource(), name,
                        DoubleArgumentType.getDouble(context, "multiplier")))));
            reset.then(Commands.literal(name)
                .executes(context -> update(context.getSource(), name, 1.0D)));
        });
        dispatcher.register(Commands.literal("golf").then(clubs.then(set).then(reset)));
    }

    private static int show(CommandSourceStack source) {
        StringBuilder message = new StringBuilder("Golf club power multipliers (global × club):");
        Map<String, Double> values = GolfClubConfig.forServer(source.getServer()).multipliers();
        values.forEach((name, value) -> {
            message.append("\n").append(name).append(": ").append(format(value)).append("x");
            if (!name.equals("global")) {
                message.append(" (effective ").append(format(values.get("global") * value)).append("x)");
            }
        });
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int update(CommandSourceStack source, String name, double multiplier) {
        // Brigadier validates the range; also reject non-finite inputs explicitly.
        if (!Double.isFinite(multiplier) || multiplier < GolfClubConfig.MIN_MULTIPLIER
                || multiplier > GolfClubConfig.MAX_MULTIPLIER) {
            source.sendFailure(Component.literal("Multiplier must be between "
                + GolfClubConfig.MIN_MULTIPLIER + " and " + GolfClubConfig.MAX_MULTIPLIER + "."));
            return 0;
        }
        try {
            GolfClubConfig.forServer(source.getServer()).set(name, multiplier);
        } catch (RuntimeException exception) {
            GolfMod.LOGGER.error("Could not save golf club multipliers", exception);
            source.sendFailure(Component.literal("Could not save club settings. Change not applied; check the server log."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Golf club power: " + name + " = " + format(multiplier)
            + "x. Saved; applies to subsequent shots."), true);
        return 1;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private GolfClubsCommand() {
    }
}
