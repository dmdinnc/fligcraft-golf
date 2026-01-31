package com.ziggleflig.golf.item;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GolfBallItem extends Item {

    public GolfBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        boolean isTee = level.getBlockState(pos).is(GolfMod.GOLF_TEE_BLOCK.get());

        if (!isTee && face != Direction.UP) {
            return InteractionResult.FAIL;
        }

        Vec3 spawnPos = Vec3.atCenterOf(pos).add(0.0D, 0.625D, 0.0D);

        GolfBallEntity ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), level);
        ball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        
        if (context.getPlayer() != null) {
            ball.setLastHitter(context.getPlayer().getUUID());
        }

        if (isTee) {
            ball.setOnTee(true);
        }

        level.addFreshEntity(ball);

        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.CONSUME;
    }
}
