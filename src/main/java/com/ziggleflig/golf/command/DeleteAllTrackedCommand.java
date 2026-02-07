package com.ziggleflig.golf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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

        grantGolfBalls(player, count);

        player.displayClientMessage(
            Component.literal("[OP] Deleted " + count + " golf ball(s) and returned them to your inventory.")
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );

        return count;
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
