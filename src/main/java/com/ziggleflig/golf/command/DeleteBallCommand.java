package com.ziggleflig.golf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class DeleteBallCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("golf_delete_ball")
                .requires(source -> source.isPlayer())
                .then(Commands.argument("entityId", IntegerArgumentType.integer())
                    .executes(DeleteBallCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();
        
        if (player == null) {
            return 0;
        }

        int entityId = IntegerArgumentType.getInteger(context, "entityId");
        ServerLevel level = source.getLevel();
        
        Entity entity = level.getEntity(entityId);
        
        if (!(entity instanceof GolfBallEntity ball)) {
            player.displayClientMessage(
                Component.literal("Ball not found (may have already been deleted).").withStyle(ChatFormatting.RED),
                false
            );
            return 0;
        }

        if (ball.getLastHitter() == null || !ball.getLastHitter().equals(player.getUUID())) {
            player.displayClientMessage(
                Component.literal("You can only delete balls you've hit!").withStyle(ChatFormatting.RED),
                false
            );
            return 0;
        }

        ball.discard();
        player.displayClientMessage(
            Component.literal("Golf ball deleted.").withStyle(ChatFormatting.GREEN),
            false
        );

        return 1;
    }
}
