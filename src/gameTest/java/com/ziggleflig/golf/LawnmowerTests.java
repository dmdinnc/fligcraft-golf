package com.ziggleflig.golf;

import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.item.LawnmowerItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class LawnmowerTests {
    @GameTest(template = "empty")
    public static void sandModeConvertsVanillaBunkersAndPreservesRaisedGeometry(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, z), (x + z) % 2 == 0 ? Blocks.SAND : Blocks.RED_SAND);
        }
        var player = player(helper);
        var mower = (LawnmowerItem) player.getMainHandItem().getItem();
        for (int i = 0; i < 3; i++) { mower.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); }
        helper.assertTrue(mow(player, helper.absolutePos(new BlockPos(3, 1, 3))).consumesAction(), "Sand mode must convert the selected bunker");
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.assertBlockPresent(GolfMod.BUNKER_SAND_BLOCK.get(), new BlockPos(x, 1, z)); }
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 9, "Each converted sand tile must cost the usual durability");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        var shape = new SculptedSurface(16, 20, 18, 16);
        var sand = SculptedGroundBlock.source(helper.getLevel(), pos); sand.setSurface(shape); sand.setFlag(true);
        mower.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); // Sand wraps to fairway.
        player.setShiftKeyDown(true);
        mow(player, pos);
        var fairway = SculptedGroundBlock.source(helper.getLevel(), pos);
        helper.assertTrue(fairway.getBlockState().is(GolfMod.FAIRWAY_BLOCK.get()) && fairway.surface().equals(shape) && fairway.hasFlag()
            && helper.getLevel().getBlockState(pos.above()).is(GolfMod.FAIRWAY_CAP.get()), "Converting sand back to grass must keep sculpting and cups");
        for (int i = 0; i < 3; i++) { mower.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); }
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        mow(player, pos.above());
        sand = SculptedGroundBlock.source(helper.getLevel(), pos);
        helper.assertTrue(sand.getBlockState().is(GolfMod.BUNKER_SAND_BLOCK.get()) && sand.surface().equals(shape) && sand.hasFlag()
            && helper.getLevel().getBlockState(pos.above()).is(GolfMod.BUNKER_SAND_CAP.get()), "Sand mode must persist on copied tools and work when clicking an upper cap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickMowsNineBlocksAndChargesPerChange(GameTestHelper helper) {
        patch(helper);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.GRASS_BLOCK);
        var player = player(helper);
        helper.assertTrue(mow(player, helper.absolutePos(new BlockPos(3, 1, 3))).consumesAction(), "A normal click must mow the selected patch");
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            var ground = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, z)));
            helper.assertTrue(ground != null && ground.getBlockState().is(GolfMod.FAIRWAY_BLOCK.get()) && ground.surface().equals(SculptedSurface.FULL),
                "All nine cells must become full-height editable fairway");
        }
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 9, "Nine changed cells must cost nine durability points");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(1, 1, 3));
        mow(player, helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 9, "Mowing already matching terrain must not cost durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakingMowsOneBlockUsingSelectedMode(GameTestHelper helper) {
        patch(helper);
        var player = player(helper);
        var mower = (LawnmowerItem) player.getMainHandItem().getItem();
        mower.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); // Rough.
        mower.use(helper.getLevel(), player, InteractionHand.MAIN_HAND); // Putting green.
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        player.setShiftKeyDown(true);
        mow(player, helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertBlockPresent(GolfMod.PUTTING_GREEN_BLOCK.get(), new BlockPos(3, 1, 3));
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            if (x != 3 || z != 3) { helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, 1, z)); }
        }
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "Sneaking must mow and charge for exactly one tile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matchingCenterStillMowsNeighborsAndSkipsOtherBlocks(GameTestHelper helper) {
        patch(helper);
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.FAIRWAY_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.GOLD_BLOCK);
        var player = player(helper);
        mow(player, helper.absolutePos(new BlockPos(3, 1, 3)));
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 6, "Already matching terrain and unrelated blocks must be excluded from the durability cost");
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 1, 3));
        helper.assertBlockPresent(GolfMod.FAIRWAY_BLOCK.get(), new BlockPos(4, 1, 4));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mowingPreservesCornersCapsAndFlags(GameTestHelper helper) {
        var expected = new SculptedSurface(17, 20, 18, 16);
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(x, 1, z), GolfMod.FAIRWAY_BLOCK.get());
            var ground = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, z)));
            ground.setSurface(expected);
            ground.setFlag(x == 3 && z == 3);
        }
        var player = player(helper);
        ((LawnmowerItem) player.getMainHandItem().getItem()).use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        mow(player, helper.absolutePos(new BlockPos(3, 1, 3)));
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            var pos = helper.absolutePos(new BlockPos(x, 1, z));
            var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
            helper.assertTrue(ground != null && ground.getBlockState().is(GolfMod.ROUGH_BLOCK.get()) && ground.surface().equals(expected),
                "Changing the terrain material must preserve every corner, including overflow heights");
            helper.assertTrue(ground.hasFlag() == (x == 3 && z == 3), "Repainting must preserve planted cups without adding duplicates");
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(GolfMod.ROUGH_CAP.get())
                && ((SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(pos.above())).surface().equals(expected),
                "Every cap must use the new material and keep the original geometry");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clickingUpperCapMowsItsOwner(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var ground = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)));
        var expected = new SculptedSurface(18, 20, 17, 19);
        ground.setSurface(expected);
        ground.setFlag(true);
        var player = player(helper);
        player.setShiftKeyDown(true);
        mow(player, ground.getBlockPos().above());
        var result = SculptedGroundBlock.source(helper.getLevel(), ground.getBlockPos());
        helper.assertTrue(result.getBlockState().is(GolfMod.FAIRWAY_BLOCK.get()) && result.surface().equals(expected) && result.hasFlag(),
            "A cap click must repaint the source tile while keeping its cup and heights");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "One source tile and its cap must count as one mowing operation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedNeighborCancelsWholeMowingOperation(GameTestHelper helper) {
        patch(helper);
        var player = player(helper);
        BlockPos protectedPos = helper.absolutePos(new BlockPos(4, 1, 4));
        Consumer<BlockEvent.BreakEvent> protect = event -> { if (event.getPos().equals(protectedPos)) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            helper.assertTrue(mow(player, helper.absolutePos(new BlockPos(3, 1, 3))) == InteractionResult.FAIL, "A protected neighbor must cancel mowing");
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, 1, z)); }
            helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "A rejected operation must not consume durability");
        } finally { NeoForge.EVENT_BUS.unregister(protect); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedCapPreventsMaterialChange(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var ground = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)));
        ground.setSurface(SculptedSurface.flat(18));
        ground.setFlag(true);
        var player = player(helper);
        player.setShiftKeyDown(true);
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(ground.getBlockPos().above())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            helper.assertTrue(mow(player, ground.getBlockPos()) == InteractionResult.FAIL && ground.hasFlag()
                && ground.surface().equals(SculptedSurface.flat(18)), "A protected extension must prevent removing or repainting the original terrain");
            helper.assertTrue(helper.getLevel().getBlockState(ground.getBlockPos()).is(GolfMod.PUTTING_GREEN_BLOCK.get())
                && helper.getLevel().getBlockState(ground.getBlockPos().above()).is(GolfMod.PUTTING_GREEN_CAP.get()), "Rejected mowing must preserve both blocks");
        } finally { NeoForge.EVENT_BUS.unregister(protect); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void adventurePlayerCannotMow(GameTestHelper helper) {
        patch(helper);
        var player = player(helper);
        player.getAbilities().mayBuild = false;
        helper.assertTrue(!mow(player, helper.absolutePos(new BlockPos(3, 1, 3))).consumesAction(), "Adventure restrictions must apply to every mower mode");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(3, 1, 3));
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "Rejected mowing must not wear out the tool");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terrainChangedByProtectionHookCancelsWholeMow(GameTestHelper helper) {
        patch(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, GolfMod.ROUGH_BLOCK.get().defaultBlockState(), 3);
        var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
        var changedShape = SculptedSurface.flat(8);
        Consumer<BlockEvent.EntityPlaceEvent> change = event -> {
            if (event.getPos().equals(pos)) { ground.setSurface(changedShape); ground.setFlag(true); }
        };
        NeoForge.EVENT_BUS.addListener(change);
        var player = player(helper);
        try { helper.assertTrue(mow(player, helper.absolutePos(new BlockPos(3, 1, 3))) == InteractionResult.FAIL,
            "Changing shape or flag metadata during protection checks must cancel the complete batch"); }
        finally { NeoForge.EVENT_BUS.unregister(change); }
        helper.assertTrue(ground.surface().equals(changedShape) && ground.hasFlag(), "Mowing must not overwrite the callback's edits");
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(3, 1, 3));
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "A stale batch must not wear the mower");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacedBlockCancelsWholeMow(GameTestHelper helper) {
        patch(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Consumer<BlockEvent.EntityPlaceEvent> change = event -> {
            if (event.getPos().equals(pos)) { helper.getLevel().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3); }
        };
        NeoForge.EVENT_BUS.addListener(change);
        var player = player(helper);
        try { helper.assertTrue(mow(player, helper.absolutePos(new BlockPos(3, 1, 3))) == InteractionResult.FAIL,
            "Replacing a planned block must cancel rather than partially mow the rest of the footprint"); }
        finally { NeoForge.EVENT_BUS.unregister(change); }
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(3, 1, 3));
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "A stale batch must not wear the mower");
        helper.succeed();
    }

    private static void patch(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK); }
    }
    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mower-test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.LAWNMOWER.get()));
        return player;
    }
    private static InteractionResult mow(FakePlayer player, BlockPos pos) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false)));
    }
}
