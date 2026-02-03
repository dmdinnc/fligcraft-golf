package com.ziggleflig.golf.inventory;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.item.GolfBallItem;
import com.ziggleflig.golf.item.GolfClubItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class GolfBagMenu extends AbstractContainerMenu {
    private static final int BAG_SLOTS = 27;
    private final Container bagContainer;
    private final ItemStack bagStack;

    public GolfBagMenu(int containerId, Inventory playerInventory, ItemStack bagStack) {
        super(GolfMod.GOLF_BAG_MENU.get(), containerId);
        this.bagStack = bagStack;
        this.bagContainer = new SimpleContainer(BAG_SLOTS);

        loadBagContents();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                this.addSlot(new GolfBagSlot(bagContainer, index, 8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
        }
    }

    private void loadBagContents() {
        ItemContainerContents contents = bagStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        for (int i = 0; i < Math.min(contents.getSlots(), BAG_SLOTS); i++) {
            bagContainer.setItem(i, contents.getStackInSlot(i));
        }
    }

    private void saveBagContents() {
        NonNullList<ItemStack> items = NonNullList.withSize(BAG_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < BAG_SLOTS; i++) {
            items.set(i, bagContainer.getItem(i));
        }
        bagStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            
            if (index < BAG_SLOTS) {
                if (!this.moveItemStackTo(slotStack, BAG_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, BAG_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveBagContents();
    }

    private static class GolfBagSlot extends Slot {
        public GolfBagSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof GolfClubItem ||
                   stack.getItem() instanceof GolfBallItem ||
                   stack.is(GolfMod.GOLF_TEE_ITEM.get()) ||
                   stack.is(GolfMod.GOLF_FLAG_ITEM.get()) ||
                   stack.is(GolfMod.GOLF_HELPER_ITEM.get());
        }
    }
}
