package com.ziggleflig.golf.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainPlacement;
import com.ziggleflig.golf.terrain.TerrainEditSnapshot;

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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

public class LawnmowerItem extends Item {
    private static final String MODE_TAG = "LawnmowerMode";
    private static final int MODE_ROUGH = 1;
    private static final int MODE_PUTTING_GREEN = 2;
    private static final int MODE_SAND = 3;
    private static final int MODE_COUNT = 4;
    private record Edit(BlockPos pos, TerrainEditSnapshot before, SculptedSurface surface, boolean flag) { }
    private static final Set<Block> CONVERTIBLE_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK,
        Blocks.DIRT,
        Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,
        Blocks.PODZOL,
        Blocks.MYCELIUM,
        Blocks.MOSS_BLOCK,
        Blocks.MUD,
        Blocks.DIRT_PATH,
        Blocks.SAND,
        Blocks.RED_SAND
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

        int nextMode = (getMode(stack) + 1) % MODE_COUNT;
        setMode(stack, nextMode);
        ChatFormatting modeColor = switch (nextMode) {
            case MODE_ROUGH -> ChatFormatting.DARK_GREEN;
            case MODE_PUTTING_GREEN -> ChatFormatting.AQUA;
            case MODE_SAND -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GREEN;
        };
        player.displayClientMessage(
            Component.translatable("message.fligcraft_golf.lawnmower.mode", getTargetState(nextMode).getBlock().getName())
                .withStyle(modeColor),
            true
        );
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || player.isSpectator()) { return InteractionResult.FAIL; }
        BlockPos pos = context.getClickedPos();
        var clickedGround = SculptedGroundBlock.source(level, pos);
        if (clickedGround != null) { pos = clickedGround.getBlockPos(); }
        if (!isConvertible(level.getBlockState(pos))) { return InteractionResult.PASS; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        ItemStack stack = context.getItemInHand();
        BlockState targetState = getTargetState(getMode(stack));
        int radius = player.isShiftKeyDown() ? 0 : 1;
        List<Edit> edits = new ArrayList<>();
        var checked = new LinkedHashSet<BlockPos>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos candidate = pos.offset(x, 0, z);
                if (!level.hasChunk(candidate.getX() >> 4, candidate.getZ() >> 4)) { continue; }
                var ground = SculptedGroundBlock.source(level, candidate);
                if (ground != null) { candidate = ground.getBlockPos(); }
                BlockState before = level.getBlockState(candidate);
                if (!checked.add(candidate) || !isConvertible(before) || before.is(targetState.getBlock())) { continue; }
                // Plan from the original neighborhood so a 3x3 conversion has no placement-order seams.
                edits.add(new Edit(candidate, TerrainEditSnapshot.capture(level, candidate),
                    ground == null ? TerrainPlacement.fit(level, candidate) : ground.surface(), ground != null && ground.hasFlag()));
            }
        }
        // Validate the whole footprint, including caps, before changing any terrain.
        var permissions = new LinkedHashSet<BlockPos>();
        for (Edit edit : edits) {
            if (!TerrainPlacement.canFit(level, edit.pos, edit.surface)) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.terrain.match_blocked"), true);
                return InteractionResult.FAIL;
            }
            permissions.add(edit.pos);
            if (edit.surface.hasOverflow()) { permissions.add(edit.pos.above()); }
        }
        for (BlockPos checkedPos : permissions) {
            if (!level.mayInteract(player, checkedPos) || !player.mayUseItemAt(checkedPos, context.getClickedFace(), stack)
                    || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, checkedPos, level.getBlockState(checkedPos), player)).isCanceled()
                    || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, checkedPos),
                        level.getBlockState(checkedPos.below()), player)).isCanceled()) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.lawnmower.protected"), true);
                return InteractionResult.FAIL;
            }
        }
        for (Edit edit : edits) {
            if (!edit.before.matches(level) || !TerrainPlacement.canFit(level, edit.pos, edit.surface)) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.terrain.changed"), true);
                return InteractionResult.FAIL;
            }
        }
        int changed = 0;
        for (Edit edit : edits) {
            if (!level.setBlock(edit.pos, targetState, 3)) { continue; }
            if (level.getBlockEntity(edit.pos) instanceof SculptedGroundBlockEntity ground) {
                ground.setSurface(edit.surface);
                ground.setFlag(edit.flag);
            }
            changed++;
        }
        if (changed > 0) {
            EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(changed, player, slot);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.lawnmower.area").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.lawnmower.single").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.lawnmower.mode").withStyle(ChatFormatting.GRAY));
    }

    private boolean isConvertible(BlockState state) {
        Block block = state.getBlock();
        return CONVERTIBLE_BLOCKS.contains(block)
            || state.is(GolfMod.FAIRWAY_BLOCK.get())
            || state.is(GolfMod.ROUGH_BLOCK.get())
            || state.is(GolfMod.PUTTING_GREEN_BLOCK.get())
            || state.is(GolfMod.BUNKER_SAND_BLOCK.get());
    }

    private int getMode(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return Math.floorMod(data.copyTag().getInt(MODE_TAG), MODE_COUNT);
    }

    private void setMode(ItemStack stack, int mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, mode));
    }

    private BlockState getTargetState(int mode) {
        return switch (mode) {
            case MODE_ROUGH -> GolfMod.ROUGH_BLOCK.get().defaultBlockState();
            case MODE_PUTTING_GREEN -> GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState();
            case MODE_SAND -> GolfMod.BUNKER_SAND_BLOCK.get().defaultBlockState();
            default -> GolfMod.FAIRWAY_BLOCK.get().defaultBlockState();
        };
    }

}
