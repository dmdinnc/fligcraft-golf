package com.ziggleflig.golf.item;

import com.ziggleflig.golf.inventory.GolfBagMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class GolfBagMenuProvider implements MenuProvider {
    private final ItemStack bagStack;

    public GolfBagMenuProvider(ItemStack bagStack) {
        this.bagStack = bagStack;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Golf Bag");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GolfBagMenu(containerId, playerInventory, bagStack);
    }
}
