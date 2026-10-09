package com.ziggleflig.golf.recipe;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.item.GolfBallItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** One ball + one vanilla dye; recolor without losing the ball's other components. */
public class GolfBallDyeRecipe extends CustomRecipe {
    public GolfBallDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean ball = false;
        boolean dye = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof GolfBallItem && !ball) {
                ball = true;
            } else if (stack.getItem() instanceof DyeItem && !dye) {
                dye = true;
            } else {
                return false;
            }
        }
        return ball && dye;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        if (!matches(input, null)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        DyeItem dye = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof GolfBallItem) {
                result = stack.copyWithCount(1);
            } else if (stack.getItem() instanceof DyeItem dyeItem) {
                dye = dyeItem;
            }
        }
        GolfBallItem.setColor(result, dye.getDyeColor());
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GolfMod.GOLF_BALL_DYE_RECIPE.get();
    }
}
