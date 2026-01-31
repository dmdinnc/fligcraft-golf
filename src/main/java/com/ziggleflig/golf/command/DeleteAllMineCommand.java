package com.ziggleflig.golf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class DeleteAllMineCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("golf_delete_all_mine")
                .requires(source -> source.isPlayer())
                .executes(DeleteAllMineCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();
        
        if (player == null) {
            return 0;
        }

        ServerLevel level = source.getLevel();
        
        List<GolfBallEntity> playerBalls = level.getEntitiesOfClass(GolfBallEntity.class, 
                player.getBoundingBox().inflate(1000.0D),
                ball -> ball.getLastHitter() != null && ball.getLastHitter().equals(player.getUUID()));

        if (playerBalls.isEmpty()) {
            player.displayClientMessage(
                Component.literal("No golf balls found to delete.").withStyle(ChatFormatting.YELLOW),
                false
            );
            return 0;
        }

        int count = playerBalls.size();
        
        for (GolfBallEntity ball : playerBalls) {
            ball.discard();
        }

        player.displayClientMessage(
            Component.literal("Deleted " + count + " golf ball(s).").withStyle(ChatFormatting.GREEN),
            false
        );

        return count;
    }
}
