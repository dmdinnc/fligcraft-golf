package com.ziggleflig.golf.command;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfHelperItem;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;

public class GolfHelperManageCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("golf_helper_manage")
                .requires(source -> source.isPlayer())
                .executes(context -> execute(context.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) {
        Player player = source.getPlayer();
        if (player == null) {
            return 0;
        }

        List<GolfBallEntity> playerBalls = GolfHelperItem.getPlayerBalls(player.level(), player);
        if (playerBalls.isEmpty()) {
            GolfHelperItem.showNoBallsMessage(player);
        } else {
            GolfHelperItem.showBallList(player, playerBalls, player.level());
        }

        return 1;
    }
}
