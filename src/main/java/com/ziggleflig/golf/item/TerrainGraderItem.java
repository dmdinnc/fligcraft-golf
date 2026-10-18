package com.ziggleflig.golf.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import com.ziggleflig.golf.terrain.TerrainGrading;
import com.ziggleflig.golf.terrain.TerrainGrading.Anchor;
import com.ziggleflig.golf.terrain.TerrainGrading.Edit;
import com.ziggleflig.golf.terrain.TerrainGrading.Fill;
import com.ziggleflig.golf.terrain.TerrainGrading.Mode;
import com.ziggleflig.golf.terrain.TerrainGrading.Plan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;

public class TerrainGraderItem extends Item {
    private static final String MODE_TAG = "TerrainGraderMode";
    private static final String POINTS_TAG = "TerrainGraderPoints";
    private static final String DIMENSION_TAG = "TerrainGraderDimension";
    private static final String SELECTION_TAG = "TerrainGraderSelection";
    // One bounded undo per player for this server session; never overwrite another player's later work.
    private record Undo(String dimension, List<Edit> edits, List<Fill> fills, boolean refund) { }
    private static final Map<Player, Undo> UNDOS = new WeakHashMap<>();
    private record Confirmation(String token, ItemStack tool, String dimension, List<Anchor> anchors, Mode mode, Plan plan, Plan repairs) { }
    private static final Map<Player, Confirmation> CONFIRMATIONS = new WeakHashMap<>();

    public TerrainGraderItem(Properties properties) { super(properties); }
    public static Mode mode(ItemStack stack) {
        return Mode.values()[Math.floorMod(data(stack).getInt(MODE_TAG), Mode.values().length)];
    }
    public static Component modeName(Mode mode) {
        return Component.translatable("mode.fligcraft_golf.grader." + mode.name().toLowerCase(java.util.Locale.ROOT));
    }
    public static List<Anchor> anchors(ItemStack stack) {
        int[] values = data(stack).getIntArray(POINTS_TAG);
        if (values.length % 5 != 0 || values.length > 20) { return List.of(); }
        List<Anchor> points = new ArrayList<>();
        for (int i = 0; i < values.length; i += 5) {
            if (values[i + 3] < 0 || values[i + 3] >= Corner.values().length) { return List.of(); }
            points.add(new Anchor(new BlockPos(values[i], values[i + 1], values[i + 2]), Corner.values()[values[i + 3]], values[i + 4]));
        }
        return List.copyOf(points);
    }
    public static boolean sameDimension(ItemStack stack, Level level) {
        return anchors(stack).isEmpty() || data(stack).getString(DIMENSION_TAG).equals(level.dimension().location().toString());
    }
    public static ItemStack heldTool(Player player) {
        if (player == null) { return ItemStack.EMPTY; }
        if (player.getMainHandItem().getItem() instanceof TerrainGraderItem) { return player.getMainHandItem(); }
        if (player.getMainHandItem().getItem() instanceof TerrainTrowelItem) { return ItemStack.EMPTY; }
        return player.getOffhandItem().getItem() instanceof TerrainGraderItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }
    public static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> { tag.remove(POINTS_TAG); tag.remove(DIMENSION_TAG); tag.remove(SELECTION_TAG); });
    }
    public static UUID selectionId(ItemStack stack) {
        var tag = data(stack);
        return tag.hasUUID(SELECTION_TAG) ? tag.getUUID(SELECTION_TAG) : null;
    }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !anchors(stack).isEmpty() && selectionId(stack) == null) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(SELECTION_TAG, UUID.randomUUID()));
        }
    }
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || player.isSpectator()) { return; }
        ItemStack stack = heldTool(player);
        if (stack.isEmpty()) { return; }
        event.setCanceled(true);
        CONFIRMATIONS.remove(player);
        if (player.isShiftKeyDown()) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_TAG, next.ordinal()));
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.settings", modeName(next), anchors(stack).size()), true);
            showPlan(player, stack);
        } else {
            clear(stack);
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.cleared"), true);
        }
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isSpectator()) { return InteractionResult.FAIL; }
        if (context.getLevel().isClientSide) { return InteractionResult.SUCCESS; }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) { return undo((ServerLevel) context.getLevel(), player, stack); }
        var ground = SculptedGroundBlock.source(context.getLevel(), context.getClickedPos());
        if (anchors(stack).size() == 4) {
            // A completed preview applies on the next click, without requiring the player to aim at air.
            // A changed reference can still be recaptured deliberately before applying.
            boolean recapture = ground != null && anchors(stack).stream().anyMatch(anchor -> {
                Corner corner = Corner.nearest(context.getClickLocation().x - ground.getBlockPos().getX(), context.getClickLocation().z - ground.getBlockPos().getZ());
                return anchor.x() == ground.getBlockPos().getX() + corner.x && anchor.z() == ground.getBlockPos().getZ() + corner.z
                    && anchor.height() != ground.getBlockPos().getY() * 16 + ground.surface().height(corner);
            });
            if (!recapture) { return grade((ServerLevel) context.getLevel(), player, stack); }
        }
        if (ground == null) {
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.aim"), true);
            return InteractionResult.CONSUME;
        }
        if (!sameDimension(stack, context.getLevel())) { return problem(player, "dimension", null); }
        var pos = ground.getBlockPos();
        Corner corner = Corner.nearest(context.getClickLocation().x - pos.getX(), context.getClickLocation().z - pos.getZ());
        Anchor point = new Anchor(pos, corner, pos.getY() * 16 + ground.surface().height(corner));
        List<Anchor> points = new ArrayList<>(anchors(stack));
        int existing = -1;
        for (int i = 0; i < points.size(); i++) { if (points.get(i).x() == point.x() && points.get(i).z() == point.z()) { existing = i; break; } }
        if (existing >= 0) { points.set(existing, point); }
        else if (points.size() < 4) { points.add(point); }
        savePoints(stack, context.getLevel(), points);
        CONFIRMATIONS.remove(player);
        player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.selected", points.size(), point.x(), point.height() / 16.0, point.z()), true);
        showPlan(player, stack);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSpectator()) { return InteractionResultHolder.fail(stack); }
        if (level.isClientSide) { return InteractionResultHolder.success(stack); }
        InteractionResult result = player.isShiftKeyDown() ? undo((ServerLevel) level, player, stack) : grade((ServerLevel) level, player, stack);
        return new InteractionResultHolder<>(result, stack);
    }
    public static InteractionResult grade(ServerLevel level, Player player, ItemStack stack) {
        CONFIRMATIONS.remove(player);
        if (player.isSpectator()) { return InteractionResult.FAIL; }
        if (!sameDimension(stack, level)) { return problem(player, "dimension", null); }
        var plan = TerrainGrading.plan(level, anchors(stack), mode(stack));
        if (!plan.valid()) { showFailure(player, stack, plan); return InteractionResult.FAIL; }
        return applyPlan(level, player, stack, plan.edits(), List.of(), 0);
    }
    /** The chat button authorizes only the displayed plan, with the same tool and selection. */
    public static InteractionResult confirmGrade(ServerPlayer player, String token) {
        Confirmation confirmation = CONFIRMATIONS.get(player);
        if (confirmation == null || !confirmation.token().equals(token)) { return problem(player, "confirmation_expired", null); }
        CONFIRMATIONS.remove(player);
        ItemStack stack = heldTool(player);
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || stack != confirmation.tool()
                || !level.dimension().location().toString().equals(confirmation.dimension())
                || !sameDimension(stack, level) || !anchors(stack).equals(confirmation.anchors()) || mode(stack) != confirmation.mode()) {
            return problem(player, "confirmation_expired", null);
        }
        // Re-plan before applying: a changed obstacle or neighboring tile needs a fresh confirmation.
        Plan current = TerrainGrading.plan(level, anchors(stack), mode(stack));
        Plan repairs = TerrainGrading.planWithFills(level, anchors(stack), mode(stack));
        if (!current.edits().equals(confirmation.plan().edits()) || !current.problems().equals(confirmation.plan().problems())
                || !repairs.edits().equals(confirmation.repairs().edits()) || !repairs.fills().equals(confirmation.repairs().fills())
                || !repairs.problems().equals(confirmation.repairs().problems())) {
            problem(player, "confirmation_changed", null);
            showPlan(player, stack);
            return InteractionResult.FAIL;
        }
        if (!current.localProblemsOnly()) { return problem(player, "confirmation_expired", null); }
        Plan available = TerrainGrading.availableFills(player, repairs);
        if (available.edits().isEmpty() && available.fills().isEmpty()) {
            player.displayClientMessage(available.problem().message(), false);
            return InteractionResult.FAIL;
        }
        return applyPlan(level, player, stack, available.edits(), available.fills(), available.problems().size());
    }
    private static InteractionResult applyPlan(ServerLevel level, Player player, ItemStack stack, List<Edit> edits, List<Fill> fills, int skipped) {
        if (edits.isEmpty() && fills.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.unchanged"), true);
            return InteractionResult.CONSUME;
        }
        var problem = TerrainGrading.apply(level, player, stack, edits, fills);
        if (problem != null) { player.displayClientMessage(problem.message(), false); return InteractionResult.FAIL; }
        UNDOS.put(player, new Undo(level.dimension().location().toString(), edits.stream().map(Edit::reversed).toList(), fills, !player.getAbilities().instabuild));
        CONFIRMATIONS.remove(player);
        clear(stack);
        level.playSound(null, edits.isEmpty() ? fills.getFirst().pos() : edits.getFirst().pos(), SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.65f, 1.1f);
        player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader." + (!fills.isEmpty() ? "applied_fills" : skipped == 0 ? "applied" : "applied_partial"),
            edits.size(), skipped, fills.size()), false);
        return InteractionResult.CONSUME;
    }
    public static InteractionResult undo(ServerLevel level, Player player, ItemStack stack) {
        CONFIRMATIONS.remove(player);
        if (player.isSpectator()) { return InteractionResult.FAIL; }
        Undo undo = UNDOS.get(player);
        if (undo == null) { return problem(player, "no_undo", null); }
        if (!undo.dimension().equals(level.dimension().location().toString())) { return problem(player, "undo_dimension", null); }
        var problem = TerrainGrading.undo(level, player, stack, undo.edits(), undo.fills());
        if (problem != null) { player.displayClientMessage(problem.message(), false); return InteractionResult.FAIL; }
        UNDOS.remove(player);
        if (undo.refund()) {
            var refunds = new java.util.LinkedHashMap<Item, Integer>();
            for (Fill fill : undo.fills()) { refunds.merge(fill.state().getBlock().asItem(), 1, Integer::sum); }
            refunds.forEach((item, count) -> {
                while (count > 0) {
                    ItemStack returned = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
                    count -= returned.getCount();
                    if (!player.getInventory().add(returned)) { player.drop(returned, false); }
                }
            });
        }
        player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.undone", undo.edits().size() + undo.fills().size()), false);
        return InteractionResult.CONSUME;
    }
    private static void showPlan(Player player, ItemStack stack) {
        CONFIRMATIONS.remove(player);
        if (anchors(stack).size() != 4 || !sameDimension(stack, player.level())) { return; }
        var plan = TerrainGrading.plan(player.level(), anchors(stack), mode(stack));
        if (!plan.valid()) { showFailure(player, stack, plan); }
        else { player.displayClientMessage(Component.translatable("message.fligcraft_golf.grader.preview", plan.area().width(),
            plan.area().depth(), plan.edits().size(), plan.area().maxAnchorAdjustment(mode(stack))), false); }
    }
    private static void showFailure(Player player, ItemStack stack, Plan plan) {
        var message = plan.message().copy();
        Plan repairs = plan.localProblemsOnly() ? TerrainGrading.planWithFills(player.level(), anchors(stack), mode(stack)) : plan;
        if (plan.localProblemsOnly() && (!repairs.edits().isEmpty() || !repairs.fills().isEmpty())) {
            String token = UUID.randomUUID().toString();
            CONFIRMATIONS.put(player, new Confirmation(token, stack, player.level().dimension().location().toString(),
                anchors(stack), mode(stack), plan, repairs));
            var button = Component.translatable("message.fligcraft_golf.grader.confirm_partial")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/golf grade confirm " + token))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.fligcraft_golf.grader.confirm_partial_hover", repairs.edits().size(), repairs.fills().size(), repairs.problems().size()))));
            message.append("\n").append(button);
        }
        player.displayClientMessage(message, false);
    }
    private static InteractionResult problem(Player player, String key, BlockPos pos) {
        player.displayClientMessage(new TerrainGrading.Problem(key, pos).message(), false);
        return InteractionResult.FAIL;
    }
    private static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private static void savePoints(ItemStack stack, Level level, List<Anchor> points) {
        int[] values = new int[points.size() * 5];
        for (int i = 0; i < points.size(); i++) {
            Anchor point = points.get(i);
            values[i * 5] = point.source().getX(); values[i * 5 + 1] = point.source().getY(); values[i * 5 + 2] = point.source().getZ();
            values[i * 5 + 3] = point.corner().ordinal(); values[i * 5 + 4] = point.height();
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putIntArray(POINTS_TAG, values); tag.putString(DIMENSION_TAG, level.dimension().location().toString());
            if (!tag.hasUUID(SELECTION_TAG)) { tag.putUUID(SELECTION_TAG, UUID.randomUUID()); }
        });
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("message.fligcraft_golf.grader.settings", modeName(mode(stack)), anchors(stack).size()).withStyle(ChatFormatting.GOLD));
        for (String key : new String[] {"select", "apply", "undo", "clear", "mode"}) {
            tooltip.add(Component.translatable("tooltip.fligcraft_golf.grader." + key).withStyle(ChatFormatting.GRAY));
        }
    }
}
