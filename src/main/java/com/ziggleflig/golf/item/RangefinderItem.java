package com.ziggleflig.golf.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RangefinderItem extends SpyglassItem {
    private static final double MAX_DISTANCE = 256.0D;
    private static final int UPDATE_INTERVAL_TICKS = 5;

    public RangefinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(livingEntity instanceof Player player)) {
            return;
        }

        int usedTicks = this.getUseDuration(stack, livingEntity) - remainingUseDuration;
        if (usedTicks % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        sendRangeMessage(level, player);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity livingEntity) {
        return 72000;
    }

    private void sendRangeMessage(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(MAX_DISTANCE));
        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.literal("No target in range.").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        double distance = hit.getLocation().distanceTo(eye);
        long meters = Math.round(distance);
        player.displayClientMessage(Component.literal("Range: " + meters + "m").withStyle(ChatFormatting.GOLD), true);
    }
}
