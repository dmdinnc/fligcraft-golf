package com.ziggleflig.golf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
        grantGolfBalls(player, 1);
        player.displayClientMessage(
            Component.literal("Golf ball deleted and returned to your inventory.").withStyle(ChatFormatting.GREEN),
            false
        );

        return 1;
    }

    private static void grantGolfBalls(Player player, int count) {
        if (count <= 0) {
            return;
        }

        ItemStack ballStack = new ItemStack(GolfMod.GOLF_BALL.get());
        int maxStack = ballStack.getMaxStackSize();
        int remaining = count;

        while (remaining > 0) {
            int stackCount = Math.min(remaining, maxStack);
            ItemStack stack = ballStack.copy();
            stack.setCount(stackCount);
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackCount;
        }
    }
}
