package com.ziggleflig.golf.item;

import java.util.Set;

import com.ziggleflig.golf.GolfMod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class LawnmowerItem extends Item {
    private static final String MODE_TAG = "LawnmowerMode";
    private static final int MODE_ROUGH = 1;
    private static final int MODE_PUTTING_GREEN = 2;
    private static final Set<Block> CONVERTIBLE_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK,
        Blocks.DIRT,
        Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,
        Blocks.PODZOL,
        Blocks.MYCELIUM,
        Blocks.MOSS_BLOCK,
        Blocks.MUD,
        Blocks.DIRT_PATH
    );

    public LawnmowerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int nextMode = (getMode(stack) + 1) % 3;
        setMode(stack, nextMode);
        ChatFormatting modeColor = switch (nextMode) {
            case MODE_ROUGH -> ChatFormatting.DARK_GREEN;
            case MODE_PUTTING_GREEN -> ChatFormatting.AQUA;
            default -> ChatFormatting.GREEN;
        };
        player.displayClientMessage(
            Component.literal("Lawnmower mode: " + getModeLabel(nextMode))
                .withStyle(modeColor),
            true
        );
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!isConvertible(state)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        BlockState targetState = getTargetState(getMode(stack));

        if (state.is(targetState.getBlock())) {
            return InteractionResult.PASS;
        }

        level.setBlock(pos, targetState, 3);

        Player player = context.getPlayer();
        if (player != null) {
            EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        return InteractionResult.CONSUME;
    }

    private boolean isConvertible(BlockState state) {
        Block block = state.getBlock();
        return CONVERTIBLE_BLOCKS.contains(block)
            || state.is(GolfMod.FAIRWAY_BLOCK.get())
            || state.is(GolfMod.ROUGH_BLOCK.get())
            || state.is(GolfMod.PUTTING_GREEN_BLOCK.get());
    }

    private int getMode(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return Math.floorMod(data.copyTag().getInt(MODE_TAG), 3);
    }

    private void setMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, mode));
    }

    private BlockState getTargetState(int mode) {
        return switch (mode) {
            case MODE_ROUGH -> GolfMod.ROUGH_BLOCK.get().defaultBlockState();
            case MODE_PUTTING_GREEN -> GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState();
            default -> GolfMod.FAIRWAY_BLOCK.get().defaultBlockState();
        };
    }

    private String getModeLabel(int mode) {
        return switch (mode) {
            case MODE_ROUGH -> "Rough";
            case MODE_PUTTING_GREEN -> "Putting Green";
            default -> "Fairway";
        };
    }
}
