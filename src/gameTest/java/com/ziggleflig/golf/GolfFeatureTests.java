package com.ziggleflig.golf;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.inventory.GolfBallTrackerMenu;
import com.ziggleflig.golf.item.GolfBallItem;
import com.ziggleflig.golf.recipe.GolfBallDyeRecipe;
import com.ziggleflig.golf.tracker.GolfBallTrackerData;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class GolfFeatureTests {
    @GameTest(template = "empty")
    public static void dyeAndSaveAllColors(GameTestHelper helper) {
        GolfBallDyeRecipe recipe = new GolfBallDyeRecipe(CraftingBookCategory.MISC);
        for (DyeColor color : DyeColor.values()) {
            for (DyeColor startingColor : DyeColor.values()) {
                ItemStack coloredBall = new ItemStack(GolfMod.GOLF_BALL.get(), 2);
                GolfBallItem.setColor(coloredBall, startingColor);
                coloredBall.set(DataComponents.CUSTOM_NAME, Component.literal("Named colored ball"));
                // Dye first, ball diagonally opposite: the recipe must be shapeless in a 2x2 grid.
                CraftingInput recoloring = CraftingInput.of(2, 2, List.of(new ItemStack(DyeItem.byColor(color)),
                    ItemStack.EMPTY, ItemStack.EMPTY, coloredBall));
                var registered = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, recoloring, helper.getLevel());
                helper.assertTrue(registered.isPresent(), "Missing recoloring recipe: " + startingColor + " -> " + color);
                ItemStack recolored = registered.orElseThrow().value().assemble(recoloring, helper.getLevel().registryAccess());
                helper.assertTrue(recolored.getCount() == 1 && GolfBallItem.getColor(recolored) == color,
                    "Wrong recoloring result: " + startingColor + " -> " + color);
                helper.assertTrue(recolored.getHoverName().getString().equals("Named colored ball"), "Recoloring lost name");
                helper.assertTrue(coloredBall.getCount() == 2 && GolfBallItem.getColor(coloredBall) == startingColor,
                    "Recoloring mutated input");
            }
            ItemStack inputBall = new ItemStack(GolfMod.GOLF_BALL.get(), 3);
            inputBall.set(DataComponents.CUSTOM_NAME, Component.literal("Practice ball"));
            CraftingInput input = CraftingInput.of(2, 1, List.of(inputBall, new ItemStack(DyeItem.byColor(color))));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "Ball + dye must craft: " + color);
            ItemStack output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.getCount() == 1 && GolfBallItem.getColor(output) == color, "Wrong dyed result");
            helper.assertTrue(output.getHoverName().getString().equals("Practice ball"), "Recoloring lost custom name");
            helper.assertTrue(inputBall.getCount() == 3 && GolfBallItem.getColor(inputBall) == DyeColor.WHITE, "Recipe mutated input");
            GolfBallEntity ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
            ball.setColor(color);
            CompoundTag saved = new CompoundTag();
            ball.saveWithoutId(saved);
            GolfBallEntity restored = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
            restored.load(saved);
            helper.assertTrue(restored.getColor() == color, "Saved entity lost color");
            helper.assertTrue(restored.getTeamColor() == color.getTextColor(), "Outline color differs from dye");
            helper.assertTrue(GolfBallItem.getColor(restored.getItem()) == color, "Returned item lost dye");
            saved.remove("BallColor");
            restored.load(saved);
            helper.assertTrue(restored.getColor() == DyeColor.WHITE, "Legacy entity must load as white");
            GolfBallItem.setColor(output, DyeColor.WHITE);
            helper.assertFalse(output.has(DataComponents.BASE_COLOR), "White must stack with legacy balls");
        }
        helper.assertFalse(recipe.matches(CraftingInput.of(3, 1, List.of(new ItemStack(GolfMod.GOLF_BALL.get()),
            new ItemStack(Items.RED_DYE), new ItemStack(Items.BLUE_DYE))), helper.getLevel()), "Multiple dyes must fail");
        helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(GolfMod.GOLF_BALL.get()),
            new ItemStack(GolfMod.GOLF_BALL.get()))), helper.getLevel()), "Two balls must fail");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownershipAndDuplicateActions(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer other = player(helper);
        GolfBallEntity ball = ball(helper, owner, DyeColor.RED);
        GolfBallTrackerMenu menu = new GolfBallTrackerMenu(1, owner.getInventory());
        menu.act(owner, ball.getUUID(), true);
        helper.assertTrue(ball.isAlive(), "Closed menu must reject actions");
        owner.containerMenu = menu;
        menu.act(other, ball.getUUID(), true);
        helper.assertTrue(ball.isAlive(), "Another player must not use owner's menu");
        menu.act(owner, UUID.randomUUID(), true);
        helper.assertTrue(ball.isAlive(), "Unknown IDs must not delete another ball");
        ball.onRemovedFromLevel(); // A temporary loss of chunk visibility can clear the NeoForge added flag.
        ball.registerStroke(other);
        helper.assertTrue(GolfBallTrackerData.get(helper.getLevel()).find(ball.getUUID()).owner().equals(other.getUUID()),
            "Ownership must update even after a temporary visibility change");
        menu.act(owner, ball.getUUID(), true);
        helper.assertTrue(ball.isAlive(), "Stale menu must recheck current ownership");
        helper.assertTrue(owner.getInventory().countItem(GolfMod.GOLF_BALL.get()) == 0, "Rejected action returned an item");
        GolfBallTrackerMenu otherMenu = new GolfBallTrackerMenu(2, other.getInventory());
        other.containerMenu = otherMenu;
        otherMenu.act(other, ball.getUUID(), true);
        otherMenu.act(other, ball.getUUID(), true);
        helper.assertFalse(ball.isAlive(), "Owner's right-click must delete ball");
        helper.assertTrue(other.getInventory().countItem(GolfMod.GOLF_BALL.get()) == 1, "Repeated action returned ball twice");
        helper.assertTrue(other.getInventory().hasAnyMatching(stack -> stack.is(GolfMod.GOLF_BALL.get())
            && GolfBallItem.getColor(stack) == DyeColor.RED), "Deletion must preserve dye");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedDeletionSurvivesSave(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        GolfBallEntity ball = ball(helper, owner, DyeColor.BLUE);
        UUID id = ball.getUUID();
        Vec3 lastPosition = ball.position();
        ball.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        GolfBallTrackerData data = GolfBallTrackerData.get(helper.getLevel());
        helper.assertTrue(data.find(id) != null && data.find(id).position().equals(lastPosition), "Unloading must retain location");
        GolfBallTrackerMenu menu = new GolfBallTrackerMenu(1, owner.getInventory());
        owner.containerMenu = menu;
        menu.act(owner, id, true);
        menu.act(owner, id, true);
        helper.assertTrue(data.find(id) == null && data.isDeleted(id), "Deletion must remove listing and queue entity removal");
        helper.assertTrue(owner.getInventory().countItem(GolfMod.GOLF_BALL.get()) == 1, "Unloaded deletion returned wrong count");
        helper.assertTrue(owner.getInventory().hasAnyMatching(stack -> stack.is(GolfMod.GOLF_BALL.get())
            && GolfBallItem.getColor(stack) == DyeColor.BLUE), "Unloaded deletion lost dye");
        GolfBallTrackerData restored = GolfBallTrackerData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
            helper.getLevel().registryAccess());
        helper.assertTrue(restored.isDeleted(id) && restored.find(id) == null, "Queued deletion must survive saving");
        for (int i = 0; i < 2; i++) {
            GolfBallEntity reloaded = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
            reloaded.setUUID(id);
            reloaded.setPos(lastPosition);
            reloaded.setLastHitter(owner.getUUID());
            helper.assertFalse(helper.getLevel().addFreshEntity(reloaded), "Deleted ball must never reload");
        }
        helper.assertTrue(owner.getInventory().countItem(GolfMod.GOLF_BALL.get()) == 1, "Reload returned another item");
        GolfBallEntity hidden = ball(helper, owner, DyeColor.ORANGE);
        data.queueDeletion(hidden.getUUID());
        hidden.tick();
        helper.assertFalse(hidden.isAlive(), "Queued deletion must also remove an entity resuming ticks without rejoining");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void paginationAndDisplayCannotBeTaken(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        for (int i = 0; i < 46; i++) { ball(helper, owner, DyeColor.LIME); }
        GolfBallTrackerMenu menu = new GolfBallTrackerMenu(1, owner.getInventory());
        owner.containerMenu = menu;
        helper.assertTrue(menu.slots.size() == 54, "Tracker must have six chest rows");
        helper.assertTrue(countEntries(menu) == 45, "First page must contain 45 balls");
        ItemStack icon = menu.getSlot(0).getItem().copy();
        helper.assertFalse(menu.getSlot(0).mayPickup(owner), "Display items cannot be taken");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "Display cannot accept items");
        for (ClickType click : ClickType.values()) { menu.clicked(0, 0, click, owner); }
        helper.assertTrue(ItemStack.matches(icon, menu.getSlot(0).getItem()) && menu.getCarried().isEmpty(), "Click moved a display item");
        helper.assertTrue(menu.quickMoveStack(owner, 0).isEmpty(), "Shift-click must not take display items");
        helper.assertTrue(menu.clickMenuButton(owner, GolfBallTrackerMenu.NEXT) && countEntries(menu) == 1, "Next page missing remaining ball");
        UUID id = menu.getSlot(0).getItem().get(DataComponents.CUSTOM_DATA).copyTag().getUUID(GolfBallTrackerMenu.BALL_ID);
        menu.act(owner, id, true);
        helper.assertTrue(countEntries(menu) == 45 && menu.getSlot(GolfBallTrackerMenu.NEXT).getItem().isEmpty(), "Deleting last page must return to first page");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void locateClosesMenuAndRejectsLongPaths(GameTestHelper helper) {
        RecordingPlayer owner = player(helper);
        GolfBallEntity ball = ball(helper, owner, DyeColor.YELLOW);
        owner.setPos(ball.position().add(5, 0, 0));
        GolfBallTrackerMenu nearby = new GolfBallTrackerMenu(1, owner.getInventory());
        owner.containerMenu = nearby;
        nearby.act(owner, ball.getUUID(), false);
        helper.assertTrue(owner.containerMenu == owner.inventoryMenu && ball.isAlive(), "Locate must close menu and keep ball");
        owner.setPos(ball.position().add(129, 0, 0));
        GolfBallTrackerMenu distant = new GolfBallTrackerMenu(2, owner.getInventory());
        owner.containerMenu = distant;
        distant.act(owner, ball.getUUID(), false);
        helper.assertTrue(owner.saw("gui.fligcraft_golf.tracker.too_far"), "Long path must report its range limit");
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        nether.getChunk(0, 0);
        GolfBallEntity remote = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), nether);
        remote.setPos(2, 70, 2);
        remote.setLastHitter(owner.getUUID());
        nether.addFreshEntity(remote);
        GolfBallTrackerMenu acrossDimensions = new GolfBallTrackerMenu(3, owner.getInventory());
        owner.containerMenu = acrossDimensions;
        acrossDimensions.act(owner, remote.getUUID(), false);
        helper.assertTrue(owner.saw("gui.fligcraft_golf.tracker.other_dimension"), "Cross-dimension path must be rejected");
        remote.discard();
        ball.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        GolfBallTrackerMenu unloaded = new GolfBallTrackerMenu(4, owner.getInventory());
        owner.containerMenu = unloaded;
        unloaded.act(owner, ball.getUUID(), false);
        helper.assertTrue(owner.saw("gui.fligcraft_golf.tracker.unloaded"), "Unloaded location must be clearly marked");
        helper.succeed();
    }

    private static RecordingPlayer player(GameTestHelper helper) { return new RecordingPlayer(helper); }

    private static class RecordingPlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();
        RecordingPlayer(GameTestHelper helper) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "golf-test"));
        }
        @Override public void displayClientMessage(Component message, boolean actionBar) { messages.add(message); }
        boolean saw(String key) {
            return messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents text && text.getKey().equals(key));
        }
    }

    private static int countEntries(GolfBallTrackerMenu menu) {
        return (int) menu.slots.stream().filter(slot -> slot.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().hasUUID(GolfBallTrackerMenu.BALL_ID)).count();
    }

    private static GolfBallEntity ball(GameTestHelper helper, ServerPlayer owner, DyeColor color) {
        GolfBallEntity ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setColor(color);
        ball.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
        ball.setNoGravity(true);
        ball.setLastHitter(owner.getUUID());
        helper.assertTrue(helper.getLevel().addFreshEntity(ball), "Could not place test ball");
        return ball;
    }
}
