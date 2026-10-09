package com.ziggleflig.golf.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.tracker.GolfBallLocator;
import com.ziggleflig.golf.tracker.GolfBallTrackerData;
import com.ziggleflig.golf.tracker.GolfBallTrackerData.Snapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** A read-only chest grid; ball actions are identified by UUID, never a movable item slot. */
public class GolfBallTrackerMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 45;
    public static final int PREVIOUS = 45;
    public static final int REFRESH = 49;
    public static final int NEXT = 53;
    public static final String BALL_ID = "TrackedBall";
    private final SimpleContainer display = new SimpleContainer(54);
    private final ServerPlayer owner;
    public record TrackedBall(ServerLevel level, Snapshot snapshot) { }
    private List<TrackedBall> balls = List.of();
    private int page;
    private int lastRefreshTick = -20;

    public GolfBallTrackerMenu(int id, Inventory inventory) {
        super(GolfMod.GOLF_BALL_TRACKER_MENU.get(), id);
        owner = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(display, row * 9 + column, 8 + column * 18, 18 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }
        if (owner != null) {
            refresh();
        }
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
            new GolfBallTrackerMenu(id, inventory), Component.translatable("gui.fligcraft_golf.tracker.title")));
    }

    public static List<TrackedBall> trackedBalls(ServerPlayer player) {
        List<TrackedBall> result = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            GolfBallTrackerData data = GolfBallTrackerData.get(level);
            for (Snapshot snapshot : data.snapshots()) {
                Entity entity = level.getEntity(snapshot.id());
                if (entity instanceof GolfBallEntity ball) { data.track(ball); }
                Snapshot current = data.find(snapshot.id());
                if (current != null && player.getUUID().equals(current.owner())) {
                    result.add(new TrackedBall(level, current));
                }
            }
        }
        result.sort(Comparator.comparing((TrackedBall ball) -> ball.level() != player.level())
            .thenComparingDouble(ball -> ball.level() == player.level() ? ball.snapshot().position().distanceToSqr(player.position()) : 0)
            .thenComparing(ball -> ball.snapshot().id()));
        return result;
    }

    private void refresh() {
        balls = trackedBalls(owner);
        int pages = Math.max(1, (balls.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        display.clearContent();
        for (int slot = 0; slot < PAGE_SIZE && page * PAGE_SIZE + slot < balls.size(); slot++) {
            TrackedBall entry = balls.get(page * PAGE_SIZE + slot);
            Snapshot ball = entry.snapshot();
            ItemStack icon = ball.item();
            icon.set(DataComponents.CUSTOM_NAME, Component.literal("#" + (page * PAGE_SIZE + slot + 1) + " ")
                .append(icon.getHoverName()).withStyle(ChatFormatting.WHITE));
            CompoundTag identity = new CompoundTag();
            identity.putUUID(BALL_ID, ball.id());
            icon.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.location", String.format(Locale.ROOT,
                "%.1f, %.1f, %.1f", ball.position().x, ball.position().y, ball.position().z)).withStyle(ChatFormatting.GRAY));
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.dimension", entry.level().dimension().location().toString())
                .withStyle(ChatFormatting.GRAY));
            if (entry.level() == owner.level()) {
                lore.add(Component.translatable("gui.fligcraft_golf.tracker.distance",
                    String.format(Locale.ROOT, "%.1f", ball.position().distanceTo(owner.position()))).withStyle(ChatFormatting.GRAY));
            }
            if (!(entry.level().getEntity(ball.id()) instanceof GolfBallEntity)) {
                lore.add(Component.translatable("gui.fligcraft_golf.tracker.last_known").withStyle(ChatFormatting.YELLOW));
            }
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.state",
                Component.translatable("gui.fligcraft_golf.tracker.state." + ball.state())).withStyle(ChatFormatting.AQUA));
            ResourceLocation surfaceId = ResourceLocation.tryParse(ball.surface());
            Component surfaceName = surfaceId == null ? Component.literal(ball.surface()) : BuiltInRegistries.BLOCK.get(surfaceId).getName();
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.surface", surfaceName)
                .withStyle(ChatFormatting.GRAY));
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.strokes", ball.strokes()).withStyle(ChatFormatting.GRAY));
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.locate").withStyle(ChatFormatting.GREEN));
            lore.add(Component.translatable("gui.fligcraft_golf.tracker.delete").withStyle(ChatFormatting.RED));
            icon.set(DataComponents.LORE, new ItemLore(lore));
            display.setItem(slot, icon);
        }
        if (balls.isEmpty()) {
            display.setItem(22, named(Items.BARRIER, Component.translatable("gui.fligcraft_golf.tracker.empty")));
        }
        if (page > 0) {
            display.setItem(PREVIOUS, named(Items.ARROW, Component.translatable("gui.fligcraft_golf.tracker.previous")));
        }
        display.setItem(REFRESH, named(Items.COMPASS,
            Component.translatable("gui.fligcraft_golf.tracker.page", page + 1, pages, balls.size())));
        if (page + 1 < pages) {
            display.setItem(NEXT, named(Items.ARROW, Component.translatable("gui.fligcraft_golf.tracker.next")));
        }
        lastRefreshTick = owner.tickCount;
    }

    private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public void act(ServerPlayer player, UUID id, boolean delete) {
        if (player != owner || player.containerMenu != this) {
            return;
        }
        TrackedBall entry = balls.stream().filter(ball -> ball.snapshot().id().equals(id)).findFirst().orElse(null);
        // Ownership and existence can change while the GUI is open.
        if (entry == null) { refresh(); return; }
        GolfBallTrackerData data = GolfBallTrackerData.get(entry.level());
        Snapshot current = data.find(id);
        Entity entity = entry.level().getEntity(id);
        if (current == null || !player.getUUID().equals(current.owner())
                || entity instanceof GolfBallEntity ball && (!ball.isAlive() || !player.getUUID().equals(ball.getLastHitter()))) {
            refresh();
            return;
        }
        if (delete) {
            if (entity instanceof GolfBallEntity ball) {
                ball.returnToPlayer(player);
            } else {
                // The next chunk load removes the entity; its ID stays deleted across restarts.
                data.queueDeletion(id);
                ItemStack returned = current.item();
                if (!player.addItem(returned)) { player.drop(returned, false); }
            }
            refresh();
        } else {
            player.closeContainer();
            GolfBallLocator.stop(player);
            if (entry.level() != player.level()) {
                player.displayClientMessage(Component.translatable("gui.fligcraft_golf.tracker.other_dimension"), true);
            } else if (entity instanceof GolfBallEntity ball) {
                GolfBallLocator.start(player, ball);
            } else {
                player.displayClientMessage(Component.translatable("gui.fligcraft_golf.tracker.unloaded"), true);
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (owner == null || player != owner) { return false; }
        if (button == PREVIOUS && page > 0) { page--; }
        else if (button == NEXT && (page + 1) * PAGE_SIZE < balls.size()) { page++; }
        else if (button != REFRESH) { return false; }
        refresh();
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (owner != null && owner.tickCount - lastRefreshTick >= 20) { refresh(); }
        super.broadcastChanges();
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return owner == null || player == owner && player.isAlive(); }
}
