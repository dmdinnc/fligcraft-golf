package com.ziggleflig.golf.command;


import com.mojang.brigadier.CommandDispatcher;
import com.ziggleflig.golf.inventory.GolfBallTrackerMenu;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class GolfHelperManageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("golf_helper_manage")
                .requires(source -> source.isPlayer())
                .executes(context -> execute(context.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        GolfBallTrackerMenu.open(player);

        return 1;
    }
}
