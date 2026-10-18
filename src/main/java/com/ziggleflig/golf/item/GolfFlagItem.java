package com.ziggleflig.golf.item;

import java.util.List;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Sculpted ground owns its flag; ordinary ground still uses the original flag block. */
public class GolfFlagItem extends BlockItem {
    public GolfFlagItem(Block block, Properties properties) { super(block, properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var ground = SculptedGroundBlock.source(level, context.getClickedPos());
        if (ground == null) { return super.useOn(context); }
        var player = context.getPlayer();
        if (player == null || player.isSpectator()) { return InteractionResult.FAIL; }
        if (ground.hasFlag()) { return InteractionResult.CONSUME; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        BlockPos pos = ground.getBlockPos();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())
                || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos),
                    level.getBlockState(pos.below()), player)).isCanceled()) { return InteractionResult.FAIL; }
        if (SculptedGroundBlock.source(level, pos) != ground || !ground.setFlag(true)) { return InteractionResult.FAIL; }
        context.getItemInHand().consume(1, player);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7f, 1.0f);
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.flag.terrain").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.flag.remove").withStyle(ChatFormatting.GRAY));
    }
}
