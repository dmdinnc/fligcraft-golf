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

public class DeleteAllTrackedCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("golf_delete_all_tracked")
                .requires(source -> source.hasPermission(2))
                .executes(DeleteAllTrackedCommand::execute)
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();
        
        if (player == null) {
            return 0;
        }

        ServerLevel level = source.getLevel();
        
        List<GolfBallEntity> allBalls = level.getEntitiesOfClass(GolfBallEntity.class, 
                player.getBoundingBox().inflate(1000.0D));

        if (allBalls.isEmpty()) {
            player.displayClientMessage(
                Component.literal("No golf balls found to delete.").withStyle(ChatFormatting.YELLOW),
                false
            );
            return 0;
        }

        int count = allBalls.size();
        
        for (GolfBallEntity ball : allBalls) {
            ball.discard();
        }

        player.displayClientMessage(
            Component.literal("[OP] Deleted " + count + " golf ball(s).").withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );

        return count;
    }
}
