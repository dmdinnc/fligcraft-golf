package com.ziggleflig.golf;

import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfHelperItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.SculptedBallPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class SculptedFlagTests {
    @GameTest(template = "empty")
    public static void adventurePlayersCannotChangeSculptedFlags(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(8));
        Player player = player(helper);
        player.getAbilities().mayBuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.GOLF_FLAG_ITEM.get()));
        helper.assertTrue(!plant(player, ground.getBlockPos()).consumesAction() && !ground.hasFlag()
            && player.getMainHandItem().getCount() == 1, "Adventure mode must not bypass normal placement restrictions");
        ground.setFlag(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.assertTrue(retrieve(player, ground.getBlockPos()) == InteractionResult.FAIL && ground.hasFlag(), "Adventure mode must not retrieve another player's cup");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void plantsOnSteepTerrainAndConsumesOnlyOneFlag(GameTestHelper helper) {
        var ground = ground(helper, new SculptedSurface(0, 20, 16, 2));
        Player player = player(helper);
        ItemStack flags = new ItemStack(GolfMod.GOLF_FLAG_ITEM.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, flags);
        helper.assertTrue(plant(player, ground.getBlockPos()) == InteractionResult.CONSUME && ground.hasFlag(), "Any supported slope must accept a flag");
        helper.assertTrue(flags.getCount() == 2 && ground.surface().equals(new SculptedSurface(0, 20, 16, 2)), "Planting must consume one item without flattening terrain");
        plant(player, ground.getBlockPos());
        helper.assertTrue(flags.getCount() == 2, "Planting on an existing cup must not consume or duplicate flags");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void capPlacementPersistsAndHeightEditsKeepOneFlag(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(20));
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.GOLF_FLAG_ITEM.get()));
        helper.assertTrue(plant(player, ground.getBlockPos().above()) == InteractionResult.CONSUME && ground.hasFlag(), "Clicking the cap must plant on its source");
        var saved = ground.saveWithoutMetadata(helper.getLevel().registryAccess());
        ground.setFlag(false);
        ground.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(ground.hasFlag() && Boolean.TRUE.equals(ground.getModelData().get(SculptedGroundBlockEntity.HAS_FLAG)), "Saved flags must restore and synchronize to the model");
        var cap = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(ground.getBlockPos().above());
        helper.assertFalse(cap.hasFlag(), "The upper cap must never own a second flag");
        helper.assertTrue(GolfHelperItem.flagPosition(helper.getLevel(), cap.getBlockPos()).equals(
            new Vec3(ground.getBlockPos().getX() + 0.5, ground.getBlockPos().getY() + 1.25, ground.getBlockPos().getZ() + 0.5)),
            "The golf helper must find the cap's flag at the actual surface connection");
        ground.setSurface(SculptedSurface.flat(4));
        helper.assertTrue(ground.hasFlag() && helper.getLevel().getBlockState(ground.getBlockPos().above()).isAir(), "Lowering must remove the cap while preserving the cup");
        ground.setSurface(SculptedSurface.flat(18));
        helper.assertTrue(ground.hasFlag(), "Raising and rebuilding a cap must preserve the flag");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.assertTrue(retrieve(player, ground.getBlockPos().above()) == InteractionResult.CONSUME && !ground.hasFlag(), "Retrieval from the upper cap must remove its source flag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placementAndRetrievalRespectProtection(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(8));
        Player player = player(helper);
        ItemStack flag = new ItemStack(GolfMod.GOLF_FLAG_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, flag);
        Consumer<BlockEvent.EntityPlaceEvent> place = event -> { if (event.getPos().equals(ground.getBlockPos())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(place);
        try { helper.assertTrue(plant(player, ground.getBlockPos()) == InteractionResult.FAIL && !ground.hasFlag() && flag.getCount() == 1, "Protected placement must preserve terrain and inventory"); }
        finally { NeoForge.EVENT_BUS.unregister(place); }
        plant(player, ground.getBlockPos());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        Consumer<BlockEvent.BreakEvent> remove = event -> { if (event.getPos().equals(ground.getBlockPos())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(remove);
        try { helper.assertTrue(retrieve(player, ground.getBlockPos()) == InteractionResult.FAIL && ground.hasFlag(), "Protected retrieval must leave the cup in place"); }
        finally { NeoForge.EVENT_BUS.unregister(remove); }
        helper.assertTrue(retrieve(player, ground.getBlockPos()) == InteractionResult.CONSUME && !ground.hasFlag(), "Sneak with an empty hand must retrieve the flag");
        helper.assertTrue(player.getInventory().countItem(GolfMod.GOLF_FLAG_ITEM.get()) == 1, "Retrieval must return exactly one flag");
        helper.assertTrue(ground.surface().equals(SculptedSurface.flat(8)), "Retrieving must keep terrain intact");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolsDoNotRetrieveFlagsAndBreakingReturnsOne(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(20));
        ground.setFlag(true);
        Player player = player(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.TERRAIN_TROWEL.get()));
        helper.assertTrue(retrieve(player, ground.getBlockPos()) == InteractionResult.PASS && ground.hasFlag(), "Sneak with a tool must keep normal editing controls");
        var cap = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(ground.getBlockPos().above());
        var capDrops = Block.getDrops(cap.getBlockState(), helper.getLevel(), cap.getBlockPos(), cap);
        var baseDrops = Block.getDrops(ground.getBlockState(), helper.getLevel(), ground.getBlockPos(), ground);
        helper.assertTrue(capDrops.isEmpty(), "The cap must not duplicate terrain or flag drops");
        helper.assertTrue(baseDrops.stream().filter(stack -> stack.is(GolfMod.GOLF_FLAG_ITEM.get())).mapToInt(ItemStack::getCount).sum() == 1
            && baseDrops.stream().filter(stack -> stack.is(GolfMod.PUTTING_GREEN_ITEM.get())).mapToInt(ItemStack::getCount).sum() == 1,
            "Breaking the source must return one terrain block and its flag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poleDeflectsAirborneBallsAndCapturesSlowGroundPutts(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(18));
        ground.setFlag(true);
        var airborne = ball(helper, ground, 0.05, 0.5, 1.4, new Vec3(0.6, 0, 0));
        airborne.tick();
        helper.assertTrue(!airborne.isRemoved() && airborne.getDeltaMovement().x < 0
            && airborne.getX() < ground.getBlockPos().getX() + 0.5, "An airborne shot must deflect from the raised flagpole without scoring");
        var putt = ball(helper, ground, 0.15, 0.5, 0, new Vec3(0.12, 0, 0));
        putt.setOnGround(true);
        for (int tick = 0; tick < 8 && putt.isAlive(); tick++) { putt.tick(); }
        helper.assertTrue(putt.isRemoved(), "A slow putt must roll into the forgiving capture area without bouncing off the pole");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cupsScoreAtSlopedAndRaisedConnection(GameTestHelper helper) {
        var ground = ground(helper, new SculptedSurface(4, 10, 10, 4));
        ground.setFlag(true);
        var rolling = ball(helper, ground, 0.48, 0.5, 0, new Vec3(0.04, 0.015, 0));
        rolling.setOnGround(true);
        rolling.tick();
        helper.assertTrue(rolling.isRemoved(), "A slow putt reaching the sloped cup must score");
        ground.setSurface(new SculptedSurface(17, 20, 19, 18));
        var raised = ball(helper, ground, 0.5, 0.5, 0, Vec3.ZERO);
        raised.setOnGround(true);
        raised.tick();
        helper.assertTrue(raised.isRemoved(), "The cup must follow edits above the source block boundary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nearbyStoppedAndAirborneBallsDoNotScore(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(4));
        ground.setFlag(true);
        var outside = ball(helper, ground, 0.95, 0.95, 0, Vec3.ZERO);
        outside.setOnGround(true);
        outside.tick();
        helper.assertFalse(outside.isRemoved(), "A stopped ball outside the cup must not score just because it shares the tile");
        var above = ball(helper, ground, 0.5, 0.5, 0.6, Vec3.ZERO);
        above.tick();
        helper.assertFalse(above.isRemoved(), "A ball overhead must reach the actual surface before scoring");
        var fast = ball(helper, ground, 0.5, 0.5, 0, new Vec3(1.3, 0, 0));
        fast.setOnGround(true);
        fast.tick();
        helper.assertFalse(fast.isRemoved(), "A shot well above the forgiving capture speed must still pass through");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modestCupToleranceAndNearlyStoppedBallsScore(GameTestHelper helper) {
        var ground = ground(helper, new SculptedSurface(14, 16, 16, 14));
        ground.setFlag(true);
        var putt = ball(helper, ground, 0.3, 0.67, 0, new Vec3(0.08, 0.01, 0));
        putt.setOnGround(true);
        for (int tick = 0; tick < 8 && putt.isAlive(); tick++) { putt.tick(); }
        helper.assertTrue(putt.isRemoved(), "A controlled putt just outside the original cup radius must still receive modest assistance");
        var resting = ball(helper, ground, 0.94, 0.5, 0, new Vec3(-0.015, 0, 0));
        resting.setOnGround(true);
        resting.tick();
        helper.assertTrue(resting.isRemoved(), "Nearly stopped balls within half a block must receive cup assistance on a slope");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollingSphereClearsPoleOnFlatAndSteepGreens(GameTestHelper helper) {
        for (var surface : java.util.List.of(SculptedSurface.flat(18), new SculptedSurface(0, 20, 20, 0), new SculptedSurface(0, 20, 0, 20))) {
            var ground = ground(helper, surface);
            ground.setFlag(true);
            var roller = ball(helper, ground, 0.3, 0.5, 0, Vec3.ZERO);
            Vec3 end = new Vec3(ground.getBlockPos().getX() + 0.7,
                ground.getBlockPos().getY() + surface.ballFeetHeight(0.7, 0.5, roller.getBbWidth() / 2.0), ground.getBlockPos().getZ() + 0.5);
            var hit = SculptedBallPhysics.sweep(helper.getLevel(), roller.position(), end.subtract(roller.position()), roller.getBbWidth() / 2.0, false);
            helper.assertTrue(hit == null || !hit.flag(), "Rolling spheres must clear the lower pole even on steep and creased tiles");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fastGroundBallPassesUnderPoleWithoutScoring(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(18));
        ground.setFlag(true);
        var fast = ball(helper, ground, 0.05, 0.5, 0, new Vec3(1.2, 0, 0));
        fast.setOnGround(true);
        fast.tick();
        helper.assertTrue(!fast.isRemoved() && fast.getX() > ground.getBlockPos().getX() + 0.8 && fast.getDeltaMovement().x > 0,
            "Ground-level pole clearance must let even an uncaptured fast roller pass straight through");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryCupUsesApproachSpeedBeforeSurfaceDrag(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 0, 3), net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 3), net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
        }
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var ball = ordinaryBall(helper, new Vec3(3.2, 2, 3.5), new Vec3(0.55, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(!ball.isRemoved() && ball.getDeltaMovement().length() < 0.4
            && ball.getX() > helper.absolutePos(new BlockPos(3, 2, 3)).getX() + 0.7,
            "Crossing too quickly must miss even if sand drag slows the ball below the capture limit by the end of the tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryFlagsClearRollersAndStillBlockFlyingBalls(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var roller = ordinaryBall(helper, new Vec3(3.1, 2, 3.5), new Vec3(0.7, 0, 0));
        roller.move(net.minecraft.world.entity.MoverType.SELF, roller.getDeltaMovement());
        helper.assertTrue(roller.getX() > helper.absolutePos(new BlockPos(3, 2, 3)).getX() + 0.7, "The original flag's base must let rollers pass under");
        var flyer = ordinaryBall(helper, new Vec3(3.1, 2.8, 3.5), new Vec3(0.7, 0, 0));
        flyer.move(net.minecraft.world.entity.MoverType.SELF, flyer.getDeltaMovement());
        helper.assertTrue(flyer.getX() < helper.absolutePos(new BlockPos(3, 2, 3)).getX() + 0.5, "The remaining ordinary flag collision must still stop an airborne ball");
        var putt = ordinaryBall(helper, new Vec3(3.28, 2, 3.67), new Vec3(0.07, 0, 0));
        putt.setOnGround(true);
        for (int tick = 0; tick < 8 && putt.isAlive(); tick++) { putt.tick(); }
        helper.assertTrue(putt.isRemoved(), "An ordinary flag must accept a controlled slightly off-center putt along its traveled path");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void moderatelyOverpoweredSculptedPuttMisses(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(18));
        ground.setFlag(true);
        var ball = ball(helper, ground, 0.1, 0.5, 0, new Vec3(0.65, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(!ball.isRemoved() && ball.getX() > ground.getBlockPos().getX() + 0.65 && ball.getDeltaMovement().x > 0,
            "A moderately overpowered putt must run across the cup without being captured or hitting the pole");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingBallOutsideModestCaptureRadiusMisses(GameTestHelper helper) {
        var ground = ground(helper, SculptedSurface.flat(18));
        ground.setFlag(true);
        var ball = ball(helper, ground, 0.3, 0.77, 0, new Vec3(0.36, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(!ball.isRemoved() && ball.getDeltaMovement().length() > 0.04,
            "A still-moving ball noticeably outside the cup must miss despite being within the former large capture radius");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonCourseGroundStillPlacesOriginalFlagBlock(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), net.minecraft.world.level.block.Blocks.DIRT);
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.GOLF_FLAG_ITEM.get()));
        helper.assertTrue(plant(player, helper.absolutePos(new BlockPos(3, 1, 3))).consumesAction(), "Original course ground must still accept flags");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 3))).is(GolfMod.GOLF_FLAG_BLOCK.get()), "Ordinary ground must preserve the original flag block");
        helper.succeed();
    }

    private static SculptedGroundBlockEntity ground(GameTestHelper helper, SculptedSurface surface) {
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 3)));
        ground.setSurface(surface);
        return ground;
    }
    private static Player player(GameTestHelper helper) { return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "flag-test")); }
    private static InteractionResult plant(Player player, BlockPos pos) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos)));
    }
    private static BlockHitResult hit(BlockPos pos) { return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false); }
    private static InteractionResult retrieve(Player player, BlockPos pos) {
        return player.level().getBlockState(pos).useWithoutItem(player.level(), player, hit(pos));
    }
    private static GolfBallEntity ball(GameTestHelper helper, SculptedGroundBlockEntity ground, double x, double z, double elevation, Vec3 velocity) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        var pos = ground.getBlockPos();
        ball.setPos(pos.getX() + x, pos.getY() + ground.surface().ballFeetHeight(x, z, ball.getBbWidth() / 2.0) + elevation, pos.getZ() + z);
        ball.setDeltaMovement(velocity);
        return ball;
    }
    private static GolfBallEntity ordinaryBall(GameTestHelper helper, Vec3 position, Vec3 velocity) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(helper.absoluteVec(position));
        ball.setDeltaMovement(velocity);
        return ball;
    }
}
