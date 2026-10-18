package com.ziggleflig.golf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.item.TerrainGraderItem;
import com.ziggleflig.golf.item.TerrainTrowelItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import com.ziggleflig.golf.terrain.TerrainGrading;
import com.ziggleflig.golf.terrain.TerrainGrading.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class TerrainGradingTests {
    @GameTest(template = "empty")
    public static void gradingUsesExistingLowerTileWhenItsNewCapCanReach(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var lower = ground(helper, 3, 0, 3);
        var plan = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.fills().isEmpty(), "A height within 32/16 of existing ground must not add another terrain tile");
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME
                && lower.surface().equals(SculptedSurface.flat(24))
                && helper.getBlockState(new BlockPos(3, 1, 3)).is(GolfMod.PUTTING_GREEN_CAP.get()),
            "The lower tile and its cap must cover the former gap");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME
                && lower.surface().equals(SculptedSurface.FULL)
                && helper.getBlockState(new BlockPos(3, 1, 3)).isAir(),
            "Undo must restore the lower tile and remove its cap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gradingFillsSandFromBelowAndFromBunkerMajority(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 24, 24, 24, 24);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.BUNKER_SAND_BLOCK.get());
        player.getInventory().setItem(1, new ItemStack(GolfMod.BUNKER_SAND_ITEM.get()));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && execute(player, confirmation(player)) == 1,
            "Survival must fill a mixed-material gap with the sand below it");
        helper.assertTrue(ground(helper, 3, 1, 3).getBlockState().is(GolfMod.BUNKER_SAND_BLOCK.get())
            && ground(helper, 3, 1, 3).surface().equals(SculptedSurface.flat(24)) && player.getInventory().countItem(GolfMod.BUNKER_SAND_ITEM.get()) == 0,
            "Sand fillings must be graded and consume their matching inventory block");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME && player.getInventory().countItem(GolfMod.BUNKER_SAND_ITEM.get()) == 1,
            "Undo must remove and refund newly filled sand");
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.AIR);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
                helper.setBlock(new BlockPos(3 + dx, 1, 3 + dz), GolfMod.BUNKER_SAND_BLOCK.get());
                ground(helper, 3 + dx, 1, 3 + dz).setSurface(SculptedSurface.flat(8));
            }
        }
        select(helper, player, 2, 5, 24, 24, 24, 24);
        var plan = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.valid() && !plan.fills().isEmpty() && plan.fills().stream().allMatch(fill -> fill.state().is(GolfMod.BUNKER_SAND_BLOCK.get()))
            && plan.fills().stream().anyMatch(fill -> fill.pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))),
            "Surrounding bunker sand must win material selection when no course block is below: " + plan);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fillsCompleteFoundationBelowDistantTargetAndRefundsSurvivalSupplies(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            helper.setBlock(new BlockPos(x, 3, z), GolfMod.PUTTING_GREEN_BLOCK.get());
            ground(helper, x, 3, z).setSurface(SculptedSurface.flat(8));
        }
        var player = player(helper);
        click(player, ground(helper, 2, 3, 2), Corner.NORTH_WEST);
        click(player, ground(helper, 4, 3, 2), Corner.NORTH_EAST);
        click(player, ground(helper, 4, 3, 4), Corner.SOUTH_EAST);
        click(player, ground(helper, 2, 3, 4), Corner.SOUTH_WEST);
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.ROUGH_BLOCK.get());
        var plan = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.valid() && plan.fills().size() == 3 && plan.fills().stream().allMatch(fill -> fill.state().is(GolfMod.ROUGH_BLOCK.get())),
            "A lower course foundation beyond the usual preview search must fill every intervening cell using its material: " + plan);
        player.getInventory().setItem(1, new ItemStack(GolfMod.ROUGH_ITEM.get(), 3));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && execute(player, confirmation(player)) == 1,
            "Survival confirmation must place the complete foundation");
        for (int y = 1; y <= 3; y++) {
            helper.assertTrue(ground(helper, 3, y, 3).surface().equals(y == 3 ? SculptedSurface.flat(8) : SculptedSurface.FULL),
                "Foundations must be full blocks with only their top graded");
        }
        helper.assertTrue(player.getInventory().countItem(GolfMod.ROUGH_ITEM.get()) == 0 && use(player, true) == InteractionResult.CONSUME,
            "Each new base consumes one survival item and the entire operation remains undoable");
        for (int y = 1; y <= 3; y++) { helper.assertTrue(helper.getBlockState(new BlockPos(3, y, 3)).isAir(), "Undo must remove every new foundation block"); }
        helper.assertTrue(ground(helper, 3, 0, 3).surface().equals(SculptedSurface.FULL) && player.getInventory().countItem(GolfMod.ROUGH_ITEM.get()) == 3,
            "Undo must preserve original support and refund only the consumed supplies");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFillingSkipsOccupiedGapWithoutReplacingOrdinaryBlocks(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); player.getAbilities().instabuild = true;
        select(helper, player, 2, 5, 8, 8, 8, 8);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        var repair = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(repair.fills().isEmpty() && repair.problems().stream().anyMatch(problem -> problem.key().equals("missing")),
            "An occupied gap must remain a problem even in creative");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && execute(player, confirmation(player)) == 1,
            "Confirmation can still grade the reachable terrain around the obstruction");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.STONE), "Force grading must never destroy the obstruction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeFillsGapsUsingTheBlockBelowAndUndoRemovesThem(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); player.getAbilities().instabuild = true;
        select(helper, player, 2, 5, 24, 24, 24, 24);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.ROUGH_BLOCK.get());
        var before = snapshot(helper, 1, 5, new BlockPos(3, 1, 3));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Automatic filling must still require confirmation");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).isAir(), "Previewing must not place blocks");
        helper.assertTrue(execute(player, confirmation(player)) == 1, "Creative must fill without inventory supplies");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).is(GolfMod.ROUGH_BLOCK.get())
            && ground(helper, 3, 1, 3).surface().equals(SculptedSurface.flat(24)), "The block below must determine material even against a different surrounding majority");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "The complete operation must undo");
        assertSnapshot(helper, before);
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).isAir() && player.getInventory().countItem(GolfMod.ROUGH_ITEM.get()) == 0,
            "Creative undo must remove the new block without creating inventory refunds");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fillMaterialUsesSurroundingMajorityAndThinTerrainTieOrder(GameTestHelper helper) {
        patch(helper, 1, 6);
        var player = player(helper); select(helper, player, 1, 6, 8, 8, 8, 8);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.AIR);
        var choices = new net.minecraft.world.level.block.Block[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.BUNKER_SAND_BLOCK.get()};
        int[][] votes = {{3, 3, 2, 0}, {2, 3, 3, 0}, {1, 3, 4, 0}, {0, 0, 4, 4}, {0, 0, 3, 5}};
        int[] winners = {0, 1, 2, 2, 3};
        for (int trial = 0; trial < votes.length; trial++) {
            int index = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) { continue; }
                int material = index < votes[trial][0] ? 0 : index < votes[trial][0] + votes[trial][1] ? 1
                    : index < votes[trial][0] + votes[trial][1] + votes[trial][2] ? 2 : 3;
                helper.setBlock(new BlockPos(3 + dx, 1, 3 + dz), choices[material]); index++;
            }
            var plan = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
            var fill = plan.fills().stream().filter(f -> f.pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))).findFirst().orElseThrow();
            helper.assertTrue(fill.state().is(choices[winners[trial]]), "Material votes must prefer green, fairway, then rough in ties, while any material can win a true majority");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalConsumesMatchingFillBlocksAndUndoRefundsOnlyWhatWasUsed(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); select(helper, player, 2, 5, 24, 24, 24, 24);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR); helper.setBlock(new BlockPos(4, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.ROUGH_BLOCK.get());
        helper.setBlock(new BlockPos(4, 0, 3), GolfMod.FAIRWAY_BLOCK.get());
        player.getInventory().setItem(1, new ItemStack(GolfMod.ROUGH_ITEM.get()));
        player.getInventory().setItem(2, new ItemStack(GolfMod.FAIRWAY_ITEM.get(), 2));
        use(player, false);
        helper.assertTrue(execute(player, confirmation(player)) == 1, "Survival can use its matching supplies");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).is(GolfMod.ROUGH_BLOCK.get())
            && helper.getBlockState(new BlockPos(4, 1, 3)).is(GolfMod.FAIRWAY_BLOCK.get()), "Each gap must use its own material priority");
        helper.assertTrue(player.getInventory().countItem(GolfMod.ROUGH_ITEM.get()) == 0 && player.getInventory().countItem(GolfMod.FAIRWAY_ITEM.get()) == 1,
            "Exactly one matching item must be consumed per placed terrain block");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Survival filling must undo");
        helper.assertTrue(player.getInventory().countItem(GolfMod.ROUGH_ITEM.get()) == 1 && player.getInventory().countItem(GolfMod.FAIRWAY_ITEM.get()) == 2,
            "Undo must return the exact supplies used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingSuppliesSkipFillsAndCanBeAddedBeforeRetry(GameTestHelper helper) {
        patch(helper, 1, 5);
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) { ground(helper, x, 1, z).setSurface(SculptedSurface.flat(24)); }
        var player = player(helper); select(helper, player, 2, 5, 24, 24, 24, 24);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        player.getInventory().setItem(1, new ItemStack(GolfMod.FAIRWAY_ITEM.get(), 4));
        var fillPreview = TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(!fillPreview.fills().isEmpty(), "The target above 32/16 must require a fill: " + fillPreview);
        use(player, false);
        helper.assertTrue(confirmation(player) != null && execute(player, confirmation(player)) == 0,
            "A fill-only preview must be confirmable, but cannot manufacture survival supplies: "
                + TerrainGrading.planWithFills(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE));
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 3)).isAir() && TerrainGraderItem.anchors(player.getMainHandItem()).size() == 4,
            "If nothing can be applied, the selection must remain available for retry");
        player.getInventory().setItem(2, new ItemStack(GolfMod.PUTTING_GREEN_ITEM.get()));
        use(player, false);
        helper.assertTrue(execute(player, confirmation(player)) == 1 && helper.getBlockState(new BlockPos(3, 1, 3)).is(GolfMod.PUTTING_GREEN_BLOCK.get()),
            "Adding the preferred material must allow the same gap to be filled");
        helper.assertTrue(player.getInventory().countItem(GolfMod.FAIRWAY_ITEM.get()) == 4, "Other materials must not be substituted or consumed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raisingLowColumnsUsesOneTileAndUndoRestoresTheOriginalCap(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); player.getAbilities().instabuild = true;
        select(helper, player, 2, 5, 20, 20, 20, 20);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var foundation = ground(helper, 3, 0, 3); foundation.setSurface(SculptedSurface.flat(20));
        use(player, false);
        helper.assertTrue(execute(player, confirmation(player)) == 1, "A low column may be raised by adding terrain blocks");
        var top = ground(helper, 3, 1, 3);
        helper.assertTrue(!top.isCap() && top.surface().equals(SculptedSurface.flat(20)),
            "A taller cap must avoid creating an unnecessary second foundation block");
        helper.assertTrue(foundation.surface().equals(SculptedSurface.FULL)
            && helper.getBlockState(new BlockPos(3, 2, 3)).is(GolfMod.PUTTING_GREEN_CAP.get()),
            "The old cap must be trimmed while the new top receives its own cap");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Raised columns must undo as one operation");
        helper.assertTrue(foundation.surface().equals(SculptedSurface.flat(20)) && ground(helper, 3, 1, 3).isCap()
            && helper.getBlockState(new BlockPos(3, 2, 3)).isAir(), "Undo must restore the original managed cap and remove the added blocks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalWillNotPartlyBuildAnUnaffordableRaisedColumn(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); select(helper, player, 2, 5, 20, 20, 20, 20);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 0, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        ground(helper, 3, 0, 3).setSurface(SculptedSurface.flat(20));
        player.getInventory().setItem(1, ItemStack.EMPTY);
        use(player, false); execute(player, confirmation(player));
        helper.assertTrue(ground(helper, 3, 0, 3).surface().equals(SculptedSurface.flat(20)) && ground(helper, 3, 1, 3).isCap()
            && helper.getBlockState(new BlockPos(3, 2, 3)).isAir(), "An insufficient column budget must not trim its parent or build half a foundation");
        helper.assertTrue(player.getInventory().countItem(GolfMod.PUTTING_GREEN_ITEM.get()) == 0, "Skipped columns must not manufacture supplies");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newFillProtectionAndLaterEditsAreRespected(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper); select(helper, player, 2, 5, 24, 24, 24, 24);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR); helper.setBlock(new BlockPos(3, 0, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        player.getInventory().setItem(1, new ItemStack(GolfMod.PUTTING_GREEN_ITEM.get()));
        var before = snapshot(helper, 1, 5, new BlockPos(3, 1, 3));
        use(player, false);
        BlockPos protectedPos = helper.absolutePos(new BlockPos(3, 1, 3));
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(protectedPos)) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try { helper.assertTrue(execute(player, confirmation(player)) == 0, "A protected fill must cancel before changing any terrain"); }
        finally { NeoForge.EVENT_BUS.unregister(protect); }
        assertSnapshot(helper, before);
        helper.assertTrue(player.getInventory().countItem(GolfMod.PUTTING_GREEN_ITEM.get()) == 1, "A rejected batch must not consume inventory");
        use(player, false); helper.assertTrue(execute(player, confirmation(player)) == 1, "An allowed fill can apply");
        var filled = ground(helper, 3, 1, 3); filled.setFlag(true);
        helper.assertTrue(use(player, true) == InteractionResult.FAIL && filled.hasFlag(), "Undo must not delete a flag added to the new tile afterward");
        filled.setFlag(false); filled.setSurface(SculptedSurface.flat(23));
        helper.assertTrue(use(player, true) == InteractionResult.FAIL, "Undo must not overwrite later geometry on a filled tile");
        filled.setSurface(SculptedSurface.flat(24));
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME && player.getInventory().countItem(GolfMod.PUTTING_GREEN_ITEM.get()) == 1,
            "Undo must remain available and refund once after the conflict is resolved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void confirmsReachableBlocksWithoutClampingRejectedTilesAndUndoRestoresEdits(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 20, 0, 0, 0);
        var before = snapshot(helper, 1, 5);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.canApplyPartial(), "An unreachable corner must still allow reachable tiles to be confirmed");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "The first attempt must require confirmation");
        assertSnapshot(helper, before);
        String command = confirmation(player);
        helper.assertTrue(command != null && execute(player, command) == 1, "The actual chat button must work without operator or cheat permissions");
        for (var edit : plan.edits()) {
            var tile = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(edit.pos());
            helper.assertTrue(tile.surface().equals(edit.after()), "Reachable tiles must receive the proposed geometry");
        }
        for (var problem : plan.problems()) {
            var tile = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(problem.pos());
            BlockPos relative = before.keySet().stream().filter(pos -> helper.absolutePos(pos).equals(problem.pos())).findFirst().orElseThrow();
            helper.assertTrue(tile.surface().equals(before.get(relative)), "Every corner of a rejected tile must stay untouched at " + relative);
        }
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).isEmpty(), "Partial success must clear the selection");
        helper.assertTrue(execute(player, command) == 0, "A confirmation must be consumed once");
        // Skipped blocks are deliberately outside this operation's undo, even if repaired afterward.
        var skipped = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(plan.problems().getFirst().pos());
        skipped.setSurface(SculptedSurface.flat(7));
        BlockPos skippedPos = before.keySet().stream().filter(pos -> helper.absolutePos(pos).equals(skipped.getBlockPos())).findFirst().orElseThrow();
        before.put(skippedPos, skipped.surface());
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Partial grading must have the usual undo");
        assertSnapshot(helper, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialGradeSkipsMissingTerrainAndBlockedCaps(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        var before = snapshot(helper, 1, 5, new BlockPos(3, 1, 3));
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.canApplyPartial() && plan.problems().stream().anyMatch(p -> p.key().equals("missing"))
            && plan.problems().stream().anyMatch(p -> p.key().equals("blocked")), "Both local problem types must be skippable");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && execute(player, confirmation(player)) == 1, "Explicit confirmation must apply the rest of the patch");
        helper.assertTrue(ground(helper, 3, 1, 2).surface().equals(before.get(new BlockPos(3, 1, 2))), "A blocked tile must keep all its original corners");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 2)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(3, 1, 3)).isAir(), "Skipped obstructions and missing terrain must not be replaced");
        helper.assertTrue(plan.edits().stream().allMatch(edit -> helper.getLevel().getBlockState(edit.pos().above()).getBlock()
            instanceof com.ziggleflig.golf.block.SculptedGroundBlock), "Valid raises must create their caps");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Partial raises must be undoable");
        assertSnapshot(helper, before);
        for (var edit : plan.edits()) {
            if (edit.before().hasOverflow()) {
                var cap = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(edit.pos().above());
                helper.assertTrue(cap != null && cap.isCap() && cap.surface().equals(edit.before()), "Undo must restore pre-existing caps");
            } else {
                helper.assertTrue(helper.getLevel().getBlockState(edit.pos().above()).isAir(), "Undo must remove newly created caps");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changedTerrainRequiresFreshPartialConfirmation(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        use(player, false);
        String oldCommand = confirmation(player);
        ground(helper, 3, 1, 2).setSurface(SculptedSurface.flat(10));
        var before = snapshot(helper, 1, 5, new BlockPos(3, 1, 3));
        helper.assertTrue(execute(player, oldCommand) == 0, "Changed reachable terrain must reject the old preview before applying anything");
        assertSnapshot(helper, before);
        String freshCommand = confirmation(player);
        helper.assertTrue(freshCommand != null && !freshCommand.equals(oldCommand), "The updated failure message must offer a fresh confirmation");
        helper.assertTrue(execute(player, freshCommand) == 1, "The refreshed plan can be confirmed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialConfirmationCannotApplyAnotherSelectionPlayerOrTool(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        use(player, false);
        String command = confirmation(player);
        var before = snapshot(helper, 1, 5, new BlockPos(3, 1, 3));
        helper.assertTrue(execute(player(helper), command) == 0, "Another player cannot use this confirmation");
        swap(player, true);
        String fresh = confirmation(player);
        helper.assertTrue(fresh != null, "The new mode must have its own confirmation");
        helper.assertTrue(execute(player, command) == 0, "Changing grading modes must invalidate old buttons");
        ItemStack tool = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertTrue(execute(player, fresh) == 0, "The confirmation must require the selected grader to be held");
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        use(player, false);
        String cleared = confirmation(player);
        swap(player, false);
        helper.assertTrue(execute(player, cleared) == 0, "Clearing the selection must invalidate its confirmation");
        assertSnapshot(helper, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void partialConfirmationStillChecksProtectionBeforeChangingAnything(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        use(player, false);
        String command = confirmation(player);
        var before = snapshot(helper, 1, 5);
        BlockPos protectedCap = helper.absolutePos(new BlockPos(3, 2, 3));
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(protectedCap)) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try { helper.assertTrue(execute(player, command) == 0, "Partial confirmation must honor protection on valid tiles and their caps"); }
        finally { NeoForge.EVENT_BUS.unregister(protect); }
        assertSnapshot(helper, before);
        helper.assertTrue(helper.getLevel().getBlockState(protectedCap).isAir(), "A protected partial batch must not create any caps");
        use(player, false);
        helper.assertTrue(execute(player, confirmation(player)) == 1, "A fresh confirmation can apply when protection permits the batch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unusableOrStalePreviewsHaveNoPartialButton(GameTestHelper helper) {
        patch(helper, 1, 5);
        var player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        for (int x = 1; x <= 5; x++) { for (int z = 1; z <= 5; z++) { helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE); } }
        var before = snapshot(helper, 1, 5);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.edits().isEmpty() && !plan.canApplyPartial(), "A completely blocked plan has nothing to confirm");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && confirmation(player) == null, "No button should be offered when no block can change");
        assertSnapshot(helper, before);
        ground(helper, 2, 1, 2).setSurface(SculptedSurface.flat(9));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL && confirmation(player) == null, "Stale reference heights cannot be bypassed with partial apply");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectsEveryUnreachableTileOnceAndKeepsWholePatch(GameTestHelper helper) {
        patch(helper, 1, 5);
        for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(5, 1, z), Blocks.AIR);
            helper.setBlock(new BlockPos(5, 2, z), GolfMod.PUTTING_GREEN_BLOCK.get());
            ground(helper, 5, 2, z).setSurface(SculptedSurface.flat(8));
        }
        Player player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        var before = snapshot(helper, 1, 4);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        var problems = plan.problems().stream().filter(problem -> problem.key().equals("range")).toList();
        helper.assertTrue(problems.size() == 3, "All three unreachable neighbors must be reported once, even when several corners fail");
        for (int z = 2; z <= 4; z++) {
            BlockPos pos = helper.absolutePos(new BlockPos(5, 2, z));
            helper.assertTrue(problems.stream().anyMatch(problem -> pos.equals(problem.pos())), "Every unreachable tile must be available to the highlight overlay");
        }
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Multiple invalid neighbors must cancel the entire grade");
        assertSnapshot(helper, before);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).size() == 4, "Selection must remain available to inspect all problems");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectsEveryBlockedExtensionAtOnce(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        var before = snapshot(helper, 1, 5);
        var obstacles = List.of(new BlockPos(3, 2, 2), new BlockPos(3, 2, 3), new BlockPos(4, 2, 3));
        for (var pos : obstacles) { helper.setBlock(pos, Blocks.STONE); }
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.problems().size() == 3, "All blocked extensions must be reported together");
        for (var pos : obstacles) {
            helper.assertTrue(plan.problems().contains(new TerrainGrading.Problem("blocked", helper.absolutePos(pos))), "Each obstruction must have its own highlight");
        }
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "The invalid batch must leave even reachable tiles untouched");
        assertSnapshot(helper, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void planeGradesSharedVerticesAndUndoRestoresPatch(GameTestHelper helper) {
        patch(helper, 0, 6);
        Player player = player(helper);
        select(helper, player, 2, 5, 4, 12, 16, 8);
        var snapshot = snapshot(helper, 0, 6);
        var anchors = TerrainGraderItem.anchors(player.getMainHandItem());
        var plan = TerrainGrading.plan(helper.getLevel(), anchors, Mode.PLANE);
        helper.assertTrue(plan.valid(), "An ordinary sloping rectangle must preview successfully");
        helper.assertTrue(ground(helper, 3, 1, 3).surface().equals(SculptedSurface.FULL), "Four clicks must only select, without grading the interior");
        ItemStack restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) player.getMainHandItem().save(helper.getLevel().registryAccess()));
        helper.assertTrue(TerrainGraderItem.anchors(restored).equals(anchors), "Reference heights and source positions must survive item saving");
        player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "Right-click air must apply the preview");
        assertPatch(helper, 2, 5, plan.area(), Mode.PLANE);
        var outside = ground(helper, 1, 1, 3).surface();
        helper.assertTrue(outside.northWest() == 16 && outside.southWest() == 16, "Touching tiles must preserve their unselected outer corners");
        helper.assertTrue(outside.northEast() == ground(helper, 2, 1, 3).surface().northWest()
            && outside.southEast() == ground(helper, 2, 1, 3).surface().southWest(), "Outside boundary vertices must join the graded patch");
        helper.assertTrue(ground(helper, 0, 1, 3).surface().equals(SculptedSurface.FULL), "Grading must not spread to a second ring");
        helper.assertTrue(TerrainGraderItem.anchors(restored).isEmpty(), "Successful grading must clear the selection for the next patch");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Sneak-right-click must undo");
        assertSnapshot(helper, snapshot);
        helper.assertTrue(use(player, true) == InteractionResult.FAIL, "Undo must be consumed once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void planeAndBlendSupportDifferentGreenLayouts(GameTestHelper helper) {
        patch(helper, 1, 6);
        Player player = player(helper);
        select(helper, player, 2, 6, 4, 16, 4, 16);
        var original = TerrainGraderItem.anchors(player.getMainHandItem());
        var plane = TerrainGrading.plan(helper.getLevel(), original, Mode.PLANE);
        helper.assertTrue(plane.valid() && plane.area().maxAnchorAdjustment(Mode.PLANE) == 6, "Plane preview must disclose adjustments for non-coplanar references");
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "Best-fit plane must grade a twisted reference layout");
        helper.assertTrue(ground(helper, 2, 1, 2).surface().northWest() == 10 && ground(helper, 4, 1, 4).surface().isFlat(), "Plane mode must flatten this saddle layout");
        use(player, true);
        select(helper, player, 2, 6, 4, 16, 4, 16);
        swap(player, true);
        var blend = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.BLEND);
        helper.assertTrue(blend.valid() && blend.area().maxAnchorAdjustment(Mode.BLEND) == 0, "Corner Blend must preserve all four references");
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "Corner Blend must apply");
        assertPatch(helper, 2, 6, blend.area(), Mode.BLEND);
        helper.assertTrue(ground(helper, 2, 1, 2).surface().northWest() == 4
            && !ground(helper, 2, 1, 2).surface().isFlat(), "Corner Blend must create a curved layout rather than flattening the anchors");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsOutOfRangeWithoutClampingOrClearingSelection(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 20, 0, 0, 0);
        var snapshot = snapshot(helper, 1, 5);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(!plan.valid() && plan.problem().key().equals("range"), "Plane corners below ground must be rejected before SculptedSurface can clamp them");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Out-of-range apply must fail");
        assertSnapshot(helper, snapshot);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).size() == 4, "A failed operation must keep the selection for inspection");
        swap(player, false);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).isEmpty(), "Swap must clear an invalid selection anywhere");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void occupiedExtensionsRejectWholeBatchAndUndoRemovesCaps(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        var snapshot = snapshot(helper, 1, 5);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(!plan.valid() && plan.problem().key().equals("blocked"), "A blocked cap must be visible in the preview");
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "An occupied extension must cancel the batch");
        assertSnapshot(helper, snapshot);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "Clearing the obstruction must allow the same selection to apply");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 3))).getBlock() instanceof com.ziggleflig.golf.block.SculptedGroundBlock,
            "A successful raise must create the center cap");
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Undo must restore the raised patch");
        assertSnapshot(helper, snapshot);
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 3))).isAir(), "Undo must remove newly created caps");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectsTouchingTilesAndUndoCannotOverwriteLaterEdits(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        var snapshot = snapshot(helper, 1, 5);
        BlockPos protectedPos = helper.absolutePos(new BlockPos(1, 1, 3));
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(protectedPos)) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try { helper.assertTrue(use(player, false) == InteractionResult.FAIL, "A protected touching tile must cancel the whole batch"); }
        finally { NeoForge.EVENT_BUS.unregister(protect); }
        assertSnapshot(helper, snapshot);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "Apply must succeed once protection permits it");
        var changed = ground(helper, 3, 1, 3);
        changed.setSurface(changed.surface().withCorner(Corner.NORTH_WEST, 7));
        var graded = snapshot(helper, 1, 5);
        helper.assertTrue(use(player, true) == InteractionResult.FAIL, "Undo must not overwrite any subsequent corner edit");
        assertSnapshot(helper, graded);
        changed.setSurface(SculptedSurface.flat(8));
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "A rejected undo must remain available after resolving its conflict");
        assertSnapshot(helper, snapshot);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedEdgesJoinAcrossBlockLevelsAndRejectUnreachableNeighbors(GameTestHelper helper) {
        patch(helper, 1, 5);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(5, 2, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var higher = ground(helper, 5, 2, 3);
        higher.setSurface(SculptedSurface.flat(8));
        Player player = player(helper);
        select(helper, player, 2, 5, 20, 20, 20, 20);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "The touching tile one level higher must receive matching world heights");
        helper.assertTrue(higher.surface().northWest() == 4 && higher.surface().southWest() == 4 && higher.surface().northEast() == 8,
            "Cross-level boundary editing must affect only the shared side");
        helper.assertTrue(higher.getBlockPos().getY() * 16 + higher.surface().northWest()
            == ground(helper, 4, 1, 3).getBlockPos().getY() * 16 + ground(helper, 4, 1, 3).surface().northEast(), "Shared heights must agree in world coordinates");
        use(player, true);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        var snapshot = snapshot(helper, 1, 4);
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "A touching higher tile that would need a negative height must reject the whole operation");
        assertSnapshot(helper, snapshot);
        helper.assertTrue(higher.surface().equals(SculptedSurface.flat(8)), "An unreachable touching tile must stay unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleReferencesCanBeRecapturedAndBadSelectionsCanBeCleared(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 8, 8, 8, 8);
        ground(helper, 2, 1, 2).setSurface(SculptedSurface.flat(9));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Changing a reference after selection must invalidate the preview");
        click(player, ground(helper, 2, 1, 2), Corner.NORTH_WEST);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).size() == 4 && use(player, false) == InteractionResult.CONSUME,
            "Clicking an existing reference must recapture its new height without adding a fifth point");
        click(player, ground(helper, 2, 1, 2), Corner.NORTH_WEST);
        click(player, ground(helper, 3, 1, 2), Corner.NORTH_WEST);
        click(player, ground(helper, 4, 1, 2), Corner.NORTH_WEST);
        click(player, ground(helper, 5, 1, 2), Corner.NORTH_WEST);
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Four collinear points do not enclose an area and must fail");
        swap(player, false);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).isEmpty(), "Invalid selections must be clearable");
        var pos = ground(helper, 2, 1, 2).getBlockPos();
        List<TerrainGrading.Anchor> huge = List.of(new TerrainGrading.Anchor(pos, Corner.NORTH_WEST, 0),
            new TerrainGrading.Anchor(pos.offset(65, 0, 0), Corner.NORTH_WEST, 0), new TerrainGrading.Anchor(pos.offset(65, 0, 1), Corner.NORTH_WEST, 0),
            new TerrainGrading.Anchor(pos.offset(0, 0, 1), Corner.NORTH_WEST, 0));
        helper.assertTrue(TerrainGrading.plan(helper.getLevel(), huge, Mode.PLANE).problem().key().equals("size"), "Oversized selections must reject before scanning the world");
        var remote = huge.stream().map(a -> new TerrainGrading.Anchor(a.source().offset(100000, 0, 0), a.corner(), a.height())).toList();
        var unloaded = List.of(remote.get(0), new TerrainGrading.Anchor(remote.get(0).source().offset(2, 0, 0), Corner.NORTH_WEST, 0),
            new TerrainGrading.Anchor(remote.get(0).source().offset(2, 0, 2), Corner.NORTH_WEST, 0), new TerrainGrading.Anchor(remote.get(0).source().offset(0, 0, 2), Corner.NORTH_WEST, 0));
        helper.assertTrue(TerrainGrading.plan(helper.getLevel(), unloaded, Mode.PLANE).problem().key().equals("unloaded"), "The planner must reject unloaded chunks without forcing a load");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void graderControlsPersistAndRespectMainHandTool(GameTestHelper helper) {
        Player player = player(helper);
        CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> tag.putString("Unrelated", "kept"));
        swap(player, true);
        helper.assertTrue(TerrainGraderItem.mode(player.getMainHandItem()) == Mode.BLEND, "Sneak-swap must cycle modes using the vanilla action");
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) player.getMainHandItem().save(helper.getLevel().registryAccess()));
        helper.assertTrue(TerrainGraderItem.mode(restored) == Mode.BLEND, "Mode must persist on the tool");
        swap(player, false);
        helper.assertTrue(TerrainGraderItem.mode(player.getMainHandItem()) == Mode.BLEND
            && player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getString("Unrelated").equals("kept"), "Clearing must preserve mode and other custom data");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(GolfMod.TERRAIN_TROWEL.get()));
        swap(player, true);
        helper.assertTrue(TerrainGraderItem.mode(player.getMainHandItem()) == Mode.PLANE && TerrainTrowelItem.mode(player.getOffhandItem()) == TerrainTrowelItem.Mode.JOINED,
            "Only the main-hand terrain tool should respond when both tools are held");
        player.setItemInHand(InteractionHand.OFF_HAND, player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        swap(player, true);
        helper.assertTrue(player.getMainHandItem().is(Items.STICK) && TerrainGraderItem.mode(player.getOffhandItem()) == Mode.BLEND, "Offhand grader must work without swapping either item");
        var input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, new ItemStack(Items.COMPASS), new ItemStack(Items.STICK)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent() && recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess()).is(GolfMod.TERRAIN_GRADER.get()), "Grader shapeless recipe must load and craft");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overlappingRectanglesExtendTheSameSlope(GameTestHelper helper) {
        patch(helper, 1, 6);
        Player player = player(helper);
        select(helper, player, 2, 4, 8, 12, 12, 8);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "First broad slope must apply");
        var overlappingTile = ground(helper, 3, 1, 3).surface();
        select(helper, player, 3, 5, 10, 14, 14, 10);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME, "An overlapping rectangle must extend the layout");
        assertPatch(helper, 3, 5, plan.area(), Mode.PLANE);
        helper.assertTrue(ground(helper, 3, 1, 3).surface().equals(overlappingTile), "The overlap must retain the existing slope when its reference heights agree");
        helper.assertTrue(ground(helper, 4, 1, 4).surface().northEast() == 14, "The next part of the slope must reach its new reference height");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void broadSlopeCrossesTerrainLevelsWithoutEditingBuriedTiles(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                if (x == 3) { helper.setBlock(new BlockPos(x, 1, z), GolfMod.ROUGH_BLOCK.get()); }
                helper.setBlock(new BlockPos(x, x == 2 ? 1 : 2, z), GolfMod.FAIRWAY_BLOCK.get());
            }
        }
        Player player = player(helper);
        var a = ground(helper, 2, 1, 2); var b = ground(helper, 4, 2, 2);
        var c = ground(helper, 4, 2, 4); var d = ground(helper, 2, 1, 4);
        b.setSurface(b.surface().withCorner(Corner.NORTH_EAST, 8));
        c.setSurface(c.surface().withCorner(Corner.SOUTH_EAST, 8));
        click(player, a, Corner.NORTH_WEST); click(player, b, Corner.NORTH_EAST);
        click(player, c, Corner.SOUTH_EAST); click(player, d, Corner.SOUTH_WEST);
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.valid() && use(player, false) == InteractionResult.CONSUME, "A broad slope must span different source block levels");
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                var surface = ground(helper, x, x == 2 ? 1 : 2, z);
                for (Corner corner : Corner.values()) {
                    helper.assertTrue(surface.getBlockPos().getY() * 16 + surface.surface().height(corner)
                        == plan.area().height(Mode.PLANE, surface.getBlockPos().getX() + corner.x, surface.getBlockPos().getZ() + corner.z),
                        "Every visible tile must use the same target world heights across levels");
                }
            }
        }
        helper.assertTrue(ground(helper, 3, 1, 3).surface().equals(SculptedSurface.FULL), "Buried sculptable blocks must not be selected in place of the visible terrain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingTilesAndProtectedUpperCellsCancelEntireOperation(GameTestHelper helper) {
        patch(helper, 1, 5);
        Player player = player(helper);
        select(helper, player, 2, 5, 18, 18, 18, 18);
        var center = ground(helper, 3, 1, 3);
        BlockPos centerPos = center.getBlockPos();
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        var missing = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(!missing.valid() && missing.problem().key().equals("missing") && use(player, false) == InteractionResult.FAIL, "Missing interior terrain must cancel grading");
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.FAIRWAY_BLOCK.get());
        var snapshot = snapshot(helper, 1, 5);
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(centerPos.above())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try { helper.assertTrue(use(player, false) == InteractionResult.FAIL, "A protected upper cell must cancel an otherwise-valid raise"); }
        finally { NeoForge.EVENT_BUS.unregister(protect); }
        assertSnapshot(helper, snapshot);
        helper.assertTrue(helper.getLevel().getBlockState(centerPos.above()).isAir(), "A rejected raise must leave the protected upper cell untouched");
        CustomData.update(DataComponents.CUSTOM_DATA, player.getMainHandItem(), tag -> tag.putString("TerrainGraderDimension", "minecraft:the_nether"));
        helper.assertTrue(use(player, false) == InteractionResult.FAIL, "Selections cannot be applied in another dimension");
        swap(player, false);
        helper.assertTrue(TerrainGraderItem.anchors(player.getMainHandItem()).isEmpty(), "Selections from another dimension must still be clearable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unevenCornersBlendInAnyOrderAndNextBlockClickApplies(GameTestHelper helper) {
        patch(helper, 0, 7);
        Player player = player(helper);
        swap(player, true);
        selectPoints(helper, player, new int[][] {{2, 2, 4}, {5, 6, 8}, {6, 3, 14}, {1, 5, 16}});
        var tool = player.getMainHandItem();
        var anchors = TerrainGraderItem.anchors(tool);
        var plan = TerrainGrading.plan(helper.getLevel(), anchors, Mode.BLEND);
        var before = snapshot(helper, 0, 7);
        helper.assertTrue(plan.valid(), "Unequal, skewed sides selected in crossing click order must produce a valid preview");
        helper.assertTrue(ground(helper, 3, 1, 3).surface().equals(SculptedSurface.FULL), "The fourth click must preview without applying");
        helper.assertTrue(click(player, ground(helper, 1, 1, 5), Corner.NORTH_WEST) == InteractionResult.CONSUME,
            "Clicking the last selected corner again must apply instead of silently recapturing it");
        helper.assertTrue(TerrainGraderItem.anchors(tool).isEmpty(), "The next block click must finish and clear the selection");
        for (var anchor : anchors) {
            var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(anchor.source());
            helper.assertTrue(ground.getBlockPos().getY() * 16 + ground.surface().height(anchor.corner()) == anchor.height(), "Corner Blend must retain every uneven reference height");
        }
        assertSelectedVertices(helper, before, plan.area(), Mode.BLEND);
        helper.assertTrue(use(player, true) == InteractionResult.CONSUME, "Undo must also restore an irregular area");
        assertSnapshot(helper, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatedAreaIgnoresMissingBoundingBoxCornersAndAppliesOnOtherBlocks(GameTestHelper helper) {
        patch(helper, 0, 7);
        Player player = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        selectPoints(helper, player, new int[][] {{4, 1, 8}, {4, 7, 8}, {7, 4, 8}, {1, 4, 8}});
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        helper.assertTrue(plan.valid(), "A diamond must not require terrain in untouched corners of its bounding box");
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        BlockPos stone = helper.absolutePos(new BlockPos(0, 0, 0));
        var result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false)));
        helper.assertTrue(result == InteractionResult.CONSUME && TerrainGraderItem.anchors(player.getMainHandItem()).isEmpty(), "A completed selection must apply when clicking an ordinary block too");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(1, 1, 1))).isAir(), "Untouched bounding-box gaps must stay empty");
        helper.assertTrue(ground(helper, 3, 1, 3).surface().equals(SculptedSurface.flat(8)), "The rotated area's interior must be graded");
        helper.assertTrue(ground(helper, 6, 1, 6).surface().southEast() == 16, "The bounding box must not be filled outside the selected shape");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void interiorAndStraightEdgeReferencesStillPreserveFourHeights(GameTestHelper helper) {
        patch(helper, 0, 7);
        Player player = player(helper);
        swap(player, true);
        selectPoints(helper, player, new int[][] {{1, 1, 8}, {7, 1, 8}, {1, 7, 8}, {3, 3, 4}});
        var anchors = TerrainGraderItem.anchors(player.getMainHandItem());
        var plan = TerrainGrading.plan(helper.getLevel(), anchors, Mode.BLEND);
        helper.assertTrue(plan.valid() && plan.area().maxAnchorAdjustment(Mode.BLEND) == 0, "A fourth reference inside a triangle must remain usable");
        helper.assertTrue(use(player, false) == InteractionResult.CONSUME && ground(helper, 3, 1, 3).surface().northWest() == 4,
            "An interior reference must shape a depression instead of being discarded");
        helper.assertTrue(ground(helper, 6, 1, 6).surface().equals(SculptedSurface.FULL), "The area outside the triangular hull must be untouched");
        use(player, true);
        selectPoints(helper, player, new int[][] {{1, 1, 8}, {4, 1, 4}, {7, 1, 8}, {4, 7, 12}});
        var edgePlan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.BLEND);
        helper.assertTrue(edgePlan.valid() && edgePlan.area().maxAnchorAdjustment(Mode.BLEND) == 0 && use(player, false) == InteractionResult.CONSUME,
            "Three references on one boundary edge and a fourth off it must enclose a valid area");
        helper.assertTrue(ground(helper, 4, 1, 1).surface().northWest() == 4, "A reference along a straight edge must preserve its own height");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arbitraryPlaneFitsSlopeAndThinAreasKeepSharedVertices(GameTestHelper helper) {
        patch(helper, 0, 7);
        Player player = player(helper);
        selectPoints(helper, player, new int[][] {{2, 2, 4}, {5, 6, 10}, {6, 3, 12}, {1, 5, 2}});
        var plan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        var before = snapshot(helper, 0, 7);
        helper.assertTrue(plan.valid() && plan.area().maxAnchorAdjustment(Mode.PLANE) == 0 && use(player, false) == InteractionResult.CONSUME,
            "A sloping plane must fit reference corners with unequal sides");
        helper.assertTrue(ground(helper, 3, 1, 3).surface().northWest() < ground(helper, 3, 1, 3).surface().northEast(), "The applied area must slope toward its higher references");
        assertSelectedVertices(helper, before, plan.area(), Mode.PLANE);
        use(player, true);
        selectPoints(helper, player, new int[][] {{1, 1, 8}, {6, 6, 8}, {2, 1, 8}, {5, 6, 8}});
        var thinPlan = TerrainGrading.plan(helper.getLevel(), TerrainGraderItem.anchors(player.getMainHandItem()), Mode.PLANE);
        var thinBefore = snapshot(helper, 0, 7);
        helper.assertTrue(thinPlan.valid() && use(player, false) == InteractionResult.CONSUME, "A narrow diagonal area must work without requiring a full rectangular grid");
        assertSelectedVertices(helper, thinBefore, thinPlan.area(), Mode.PLANE);
        helper.succeed();
    }

    private static void selectPoints(GameTestHelper helper, Player player, int[][] points) {
        for (int[] point : points) {
            var ground = ground(helper, point[0], 1, point[1]);
            ground.setSurface(ground.surface().withCorner(Corner.NORTH_WEST, point[2]));
            click(player, ground, Corner.NORTH_WEST);
        }
    }
    private static void assertSelectedVertices(GameTestHelper helper, Map<BlockPos, SculptedSurface> before,
            com.ziggleflig.golf.terrain.GradingArea area, Mode mode) {
        for (var entry : before.entrySet()) {
            var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(entry.getKey()));
            for (Corner corner : Corner.values()) {
                int vx = ground.getBlockPos().getX() + corner.x, vz = ground.getBlockPos().getZ() + corner.z;
                if (area.contains(vx, vz)) {
                    helper.assertTrue(ground.getBlockPos().getY() * 16 + ground.surface().height(corner) == area.height(mode, vx, vz),
                        "Every tile sharing a selected vertex must receive the same world height");
                } else {
                    helper.assertTrue(ground.surface().height(corner) == entry.getValue().height(corner), "Vertices outside the polygon must keep their original heights");
                }
            }
        }
    }

    private static void patch(GameTestHelper helper, int min, int max) {
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                helper.setBlock(new BlockPos(x, 1, z), switch ((x + z) % 3) {
                    case 0 -> GolfMod.FAIRWAY_BLOCK.get(); case 1 -> GolfMod.ROUGH_BLOCK.get(); default -> GolfMod.PUTTING_GREEN_BLOCK.get();
                });
            }
        }
    }
    private static SculptedGroundBlockEntity ground(GameTestHelper helper, int x, int y, int z) {
        return (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, y, z)));
    }
    private static class GraderPlayer extends FakePlayer {
        private Component message = Component.empty();
        GraderPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "grading-test")); }
        @Override public void displayClientMessage(Component message, boolean actionBar) {
            if (!actionBar) { this.message = message; }
        }
    }
    private static String confirmation(GraderPlayer player) {
        return player.message.visit((style, text) -> {
            ClickEvent click = style.getClickEvent();
            return click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND
                ? Optional.of(click.getValue()) : Optional.<String>empty();
        }, Style.EMPTY).orElse(null);
    }
    private static int execute(GraderPlayer player, String command) {
        if (command == null) { throw new IllegalStateException("Expected a clickable grading confirmation"); }
        try {
            return player.server.getCommands().getDispatcher().execute(command.substring(1), player.createCommandSourceStack().withPermission(0));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new IllegalStateException(exception); }
    }
    private static GraderPlayer player(GameTestHelper helper) {
        var player = new GraderPlayer(helper.getLevel());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.TERRAIN_GRADER.get()));
        return player;
    }
    private static void select(GameTestHelper helper, Player player, int min, int max, int nw, int ne, int se, int sw) {
        var a = ground(helper, min, 1, min); a.setSurface(a.surface().withCorner(Corner.NORTH_WEST, nw));
        var b = ground(helper, max - 1, 1, min); b.setSurface(b.surface().withCorner(Corner.NORTH_EAST, ne));
        var c = ground(helper, max - 1, 1, max - 1); c.setSurface(c.surface().withCorner(Corner.SOUTH_EAST, se));
        var d = ground(helper, min, 1, max - 1); d.setSurface(d.surface().withCorner(Corner.SOUTH_WEST, sw));
        // Deliberately select in a different order from the surface representation.
        click(player, b, Corner.NORTH_EAST); click(player, d, Corner.SOUTH_WEST); click(player, a, Corner.NORTH_WEST); click(player, c, Corner.SOUTH_EAST);
    }
    private static InteractionResult click(Player player, SculptedGroundBlockEntity ground, Corner corner) {
        BlockPos pos = ground.getBlockPos();
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(new Vec3(pos.getX() + (corner.x == 0 ? 0.02 : 0.98), pos.getY() + 1, pos.getZ() + (corner.z == 0 ? 0.02 : 0.98)), Direction.UP, pos, false)));
    }
    private static InteractionResult use(Player player, boolean shift) {
        player.setShiftKeyDown(shift);
        var result = player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND).getResult();
        player.setShiftKeyDown(false);
        return result;
    }
    private static void swap(Player player, boolean shift) {
        player.setShiftKeyDown(shift);
        CommonHooks.onLivingSwapHandItems(player);
        player.setShiftKeyDown(false);
    }
    private static Map<BlockPos, SculptedSurface> snapshot(GameTestHelper helper, int min, int max) {
        return snapshot(helper, min, max, null);
    }
    private static Map<BlockPos, SculptedSurface> snapshot(GameTestHelper helper, int min, int max, BlockPos skip) {
        Map<BlockPos, SculptedSurface> snapshot = new LinkedHashMap<>();
        for (int x = min; x <= max; x++) { for (int z = min; z <= max; z++) {
            BlockPos pos = new BlockPos(x, 1, z);
            if (!pos.equals(skip)) { snapshot.put(pos, ground(helper, x, 1, z).surface()); }
        } }
        return snapshot;
    }
    private static void assertSnapshot(GameTestHelper helper, Map<BlockPos, SculptedSurface> snapshot) {
        for (var entry : snapshot.entrySet()) {
            var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(entry.getKey()));
            helper.assertTrue(ground.surface().equals(entry.getValue()), "Entire patch must be preserved/restored at " + entry.getKey());
        }
    }
    private static void assertPatch(GameTestHelper helper, int min, int max, com.ziggleflig.golf.terrain.GradingArea rectangle, Mode mode) {
        for (int x = min; x < max; x++) {
            for (int z = min; z < max; z++) {
                var ground = ground(helper, x, 1, z);
                for (Corner corner : Corner.values()) {
                    helper.assertTrue(ground.getBlockPos().getY() * 16 + ground.surface().height(corner)
                        == rectangle.height(mode, ground.getBlockPos().getX() + corner.x, ground.getBlockPos().getZ() + corner.z), "Every shared vertex must match the selected layout");
                }
                helper.assertTrue(ground.getBlockState().is(switch ((x + z) % 3) { case 0 -> GolfMod.FAIRWAY_BLOCK.get(); case 1 -> GolfMod.ROUGH_BLOCK.get(); default -> GolfMod.PUTTING_GREEN_BLOCK.get(); }), "Grading must preserve material");
            }
        }
    }
}
