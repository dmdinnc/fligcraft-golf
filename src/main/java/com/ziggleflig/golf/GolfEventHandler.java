package com.ziggleflig.golf;

import com.ziggleflig.golf.item.GolfClubItem;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = GolfMod.MODID)
public class GolfEventHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        
        if (stack.getItem() instanceof GolfClubItem) {
            if (GolfClubItem.getPendingShot(player) != null) {
                if (player.level().isClientSide) {
                    GolfClubItem.handleClientLeftClick(player);
                }
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof GolfClubItem && player.level().isClientSide) {
            if (GolfClubItem.getPendingShot(player) != null) {
                GolfClubItem.handleClientLeftClick(player);
            }
        }
    }
}
