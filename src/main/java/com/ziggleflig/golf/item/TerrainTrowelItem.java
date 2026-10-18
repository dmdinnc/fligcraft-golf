package com.ziggleflig.golf.item;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainEditSnapshot;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;

public class TerrainTrowelItem extends Item {
    public enum Mode {
        JOINED, CORNER, LEVEL;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public record Edit(SculptedGroundBlockEntity ground, SculptedSurface surface, Corner corner) { }
    public record Plan(List<Edit> edits, boolean outsideRange) { }
    private static final String MODE_TAG = "TerrainTrowelMode";
    private static final String STEP_TAG = "TerrainTrowelStep";

    public TerrainTrowelItem(Properties properties) { super(properties); }
    public static Mode mode(ItemStack stack) {
        return Mode.values()[Math.floorMod(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(MODE_TAG), Mode.values().length)];
    }

    public static int step(ItemStack stack) {
        return switch (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(STEP_TAG)) {
            case 2 -> 2;
            case 4 -> 4;
            case 8 -> 8;
            default -> 1;
        };
    }
    public static ItemStack heldTool(Player player) {
        if (player == null) { return ItemStack.EMPTY; }
        if (player.getMainHandItem().getItem() instanceof TerrainTrowelItem) { return player.getMainHandItem(); }
        if (player.getMainHandItem().getItem() instanceof TerrainGraderItem) { return ItemStack.EMPTY; }
        if (player.getOffhandItem().getItem() instanceof TerrainTrowelItem) { return player.getOffhandItem(); }
        return ItemStack.EMPTY;
    }

    /** Use the vanilla swap action, independent of the aimed block and the client's chosen key binding. */
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || player.isSpectator()) { return; }
        ItemStack stack = heldTool(player);
        if (stack.isEmpty()) { return; }
        event.setCanceled(true);
        if (player.isShiftKeyDown()) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, next.ordinal()));
        } else {
            int next = step(stack) == 8 ? 1 : step(stack) * 2;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(STEP_TAG, next));
        }
        player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.settings", modeName(mode(stack)), step(stack)), true);
    }

    public static int targetHeight(ItemStack stack, SculptedSurface surface, Corner corner, boolean raising) {
        return Mth.clamp(surface.height(corner) + (raising ? step(stack) : -step(stack)), 0, SculptedSurface.MAX_HEIGHT);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        SculptedGroundBlockEntity ground = SculptedGroundBlock.source(context.getLevel(), context.getClickedPos());
        if (ground == null) { return InteractionResult.PASS; }
        Player player = context.getPlayer();
        if (player == null) { return InteractionResult.FAIL; }
        if (context.getLevel().isClientSide) { return InteractionResult.SUCCESS; }
        BlockPos pos = ground.getBlockPos();
        Corner corner = Corner.nearest(context.getClickLocation().x - pos.getX(), context.getClickLocation().z - pos.getZ());
        int targetHeight = targetHeight(context.getItemInHand(), ground.surface(), corner, player.isShiftKeyDown());
        return edit((ServerLevel) context.getLevel(), player, context.getItemInHand(), pos, context.getClickedFace(), corner, targetHeight);
    }

    /** Check every position before applying a shared edit; one protected neighbor cancels the whole operation. */
    public static InteractionResult edit(ServerLevel level, Player player, ItemStack stack, BlockPos pos, Direction face, Corner corner, int height) {
        if (player.isSpectator()) { return InteractionResult.FAIL; }
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { return InteractionResult.PASS; }
        SculptedGroundBlockEntity source = SculptedGroundBlock.source(level, pos);
        if (source == null) { return InteractionResult.PASS; }
        pos = source.getBlockPos();
        height = Mth.clamp(height, 0, SculptedSurface.MAX_HEIGHT);
        Plan plan = plan(level, stack, pos, corner, height);
        if (plan.outsideRange) {
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.height_range"), true);
            return InteractionResult.FAIL;
        }
        List<Edit> edits = new ArrayList<>(plan.edits);
        edits.removeIf(edit -> edit.ground().surface().equals(edit.surface()));
        if (edits.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.bound", height), true);
            return InteractionResult.CONSUME;
        }
        var positions = new LinkedHashSet<BlockPos>();
        List<TerrainEditSnapshot> snapshots = new ArrayList<>();
        for (Edit edit : edits) {
            snapshots.add(TerrainEditSnapshot.capture(level, edit.ground.getBlockPos()));
            if (!edit.ground.canAccommodate(edit.surface)) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.above_blocked"), true);
                return InteractionResult.FAIL;
            }
            positions.add(edit.ground.getBlockPos());
            if (edit.surface.hasOverflow() || edit.ground.surface().hasOverflow()) { positions.add(edit.ground.getBlockPos().above()); }
        }
        for (BlockPos target : positions) {
            if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, face, stack)
                    || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, target),
                        level.getBlockState(target.below()), player)).isCanceled()) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.protected"), true);
                return InteractionResult.FAIL;
            }
        }
        for (int i = 0; i < edits.size(); i++) {
            if (!snapshots.get(i).matches(level) || !edits.get(i).ground.canAccommodate(edits.get(i).surface)) {
                player.displayClientMessage(Component.translatable("message.fligcraft_golf.terrain.changed"), true);
                return InteractionResult.FAIL;
            }
        }
        for (Edit edit : edits) { edit.ground().setSurface(edit.surface()); }
        level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.65f, 1.2f);
        player.displayClientMessage(Component.translatable("message.fligcraft_golf.trowel.edited",
            Component.translatable("corner.fligcraft_golf." + corner.key()), height, edits.size()), true);
        return InteractionResult.CONSUME;
    }

    /** Shared world-height planning is used by both the server and the client preview. */
    public static Plan plan(Level level, ItemStack stack, BlockPos pos, Corner corner, int height) {
        SculptedGroundBlockEntity source = SculptedGroundBlock.source(level, pos);
        if (source == null) { return new Plan(List.of(), false); }
        pos = source.getBlockPos();
        Mode mode = mode(stack);
        List<Edit> edits = new ArrayList<>();
        if (mode == Mode.JOINED) {
            int worldX = pos.getX() + corner.x, worldZ = pos.getZ() + corner.z;
            int oldWorldHeight = pos.getY() * 16 + source.surface().height(corner);
            int newWorldHeight = pos.getY() * 16 + height;
            for (int x = worldX - 1; x <= worldX; x++) {
                for (int z = worldZ - 1; z <= worldZ; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) { continue; }
                    Corner shared = Corner.nearest(worldX - x, worldZ - z);
                    SculptedGroundBlockEntity ground = x == pos.getX() && z == pos.getZ() ? source : neighbor(level, x, pos.getY(), z, shared, oldWorldHeight);
                    if (ground == null) { continue; }
                    int local = newWorldHeight - ground.getBlockPos().getY() * 16;
                    if (local < 0 || local > SculptedSurface.MAX_HEIGHT) { return new Plan(List.of(), true); }
                    edits.add(new Edit(ground, ground.surface().withCorner(shared, local), shared));
                }
            }
        } else {
            edits.add(new Edit(source, mode == Mode.LEVEL ? SculptedSurface.flat(height) : source.surface().withCorner(corner, height), corner));
        }
        return new Plan(List.copyOf(edits), false);
    }
    private static SculptedGroundBlockEntity neighbor(Level level, int x, int y, int z, Corner corner, int worldHeight) {
        for (int offset : new int[] {0, 1, -1}) {
            SculptedGroundBlockEntity ground = SculptedGroundBlock.source(level, new BlockPos(x, y + offset, z));
            if (ground != null) {
                // A same-column course tile above this one owns the exposed surface.
                // Do not join a buried foundation before checking that tile.
                BlockPos above = ground.getBlockPos().above();
                if (level.getBlockState(above).getBlock() instanceof SculptedGroundBlock block && !block.isCap()) { continue; }
                int delta = Math.abs(ground.getBlockPos().getY() * 16 + ground.surface().height(corner) - worldHeight);
                // Different levels join only when their existing vertices are already close.
                if (ground.getBlockPos().getY() == y || delta <= 4) { return ground; }
            }
        }
        return null;
    }

    public static Component modeName(Mode mode) { return Component.translatable("mode.fligcraft_golf.trowel." + mode.key()); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("message.fligcraft_golf.trowel.mode", modeName(mode(stack))).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("message.fligcraft_golf.trowel.step", step(stack)).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.trowel.edit").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.trowel.step").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.fligcraft_golf.trowel.mode").withStyle(ChatFormatting.GRAY));
    }
}
