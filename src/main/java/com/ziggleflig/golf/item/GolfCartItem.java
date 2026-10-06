package com.ziggleflig.golf.item;

import java.util.List;
import java.util.function.Predicate;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfCartEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GolfCartItem extends Item {
    private static final Predicate<Entity> ENTITY_PREDICATE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    public GolfCartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 look = player.getViewVector(1.0F);
        List<Entity> entities = level.getEntities(player, player.getBoundingBox().expandTowards(look.scale(5.0)).inflate(1.0), ENTITY_PREDICATE);
        if (!entities.isEmpty()) {
            Vec3 eye = player.getEyePosition();
            for (Entity entity : entities) {
                AABB bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
                if (bounds.contains(eye)) {
                    return InteractionResultHolder.pass(stack);
                }
            }
        }

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        Vec3 spawnPos = blockHit.getLocation().add(0.0D, 0.05D, 0.0D);
        GolfCartEntity cart = new GolfCartEntity(GolfMod.GOLF_CART_ENTITY.get(), level);
        cart.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        cart.setYRot(player.getYRot());
        cart.yRotO = cart.getYRot();

        if (!level.noCollision(cart, cart.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            if (level instanceof ServerLevel serverLevel) {
                EntityType.<GolfCartEntity>createDefaultStackConfig(serverLevel, stack, player).accept(cart);
            }
            level.addFreshEntity(cart);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, spawnPos);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
