package com.ziggleflig.golf;

import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfClubItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class CupCaptureTests {
    @GameTest(template = "cup_capture")
    public static void fullPowerDownhillPuttRunsPastCup(GameTestHelper helper) {
        SculptedGroundBlockEntity cup = downhillLane(helper);
        GolfBallEntity control = fullPowerPutt(helper);
        Vec3 launch = control.getDeltaMovement();
        double cupX = cup.getBlockPos().getX() + 0.5;
        double crossingSpeed = 0;
        for (int tick = 0; tick < 400 && control.getX() < cupX + 15; tick++) {
            control.tick();
            if (Math.abs(control.getX() - cupX) < 0.2) { crossingSpeed = control.getDeltaMovement().length(); }
        }
        helper.assertTrue(control.getX() >= cupX + 15,
            "The real full-power putter must run at least fifteen blocks beyond the unflagged cup in this reproduction");
        control.discard();
        cup.setFlag(true);
        GolfBallEntity shot = fullPowerPutt(helper);
        helper.assertTrue(shot.getDeltaMovement().distanceToSqr(launch) < 1.0e-10, "The flagged and unflagged launches must match");
        for (int tick = 0; tick < 400 && shot.isAlive() && shot.getX() < cupX + 15; tick++) { shot.tick(); }
        helper.assertTrue(!shot.isRemoved() && shot.getX() >= cupX + 15,
            "The same downhill putt must run past the flagged cup instead of scoring; unflagged cup speed=" + crossingSpeed);
        shot.discard();
        helper.succeed();
    }

    @GameTest(template = "cup_capture")
    public static void controlledDownhillPuttStillScores(GameTestHelper helper) {
        var cup = downhillLane(helper);
        cup.setFlag(true);
        var ball = atCup(helper, cup, 0.2, 0, new Vec3(0.12, -0.0225, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(ball.isAlive(), "A moving putt must not score just from its first sample inside the cup");
        for (int tick = 0; tick < 12 && ball.isAlive(); tick++) { ball.tick(); }
        helper.assertTrue(ball.isRemoved() && ball.getDeltaMovement().length() > 0.04,
            "A controlled downhill putt must score while still rolling, without depending on stopped-ball assistance");
        helper.succeed();
    }

    @GameTest(template = "cup_capture")
    public static void downhillGroundStateDoesNotGrantFastCapture(GameTestHelper helper) {
        var cup = downhillLane(helper);
        cup.setFlag(true);
        var ball = atCup(helper, cup, 0.1, 0, new Vec3(0.32, -0.06, 0));
        ball.setOnGround(false);
        for (int tick = 0; tick < 8 && ball.isAlive(); tick++) { ball.tick(); }
        helper.assertTrue(ball.isAlive() && ball.getX() > cup.getBlockPos().getX() + 1,
            "A fast downhill roller with temporarily cleared onGround must pass the cup");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryAndSculptedCupsRequireTimeForMovingBalls(GameTestHelper helper) {
        flatLane(helper, false);
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var ordinary = ball(helper, new Vec3(3.31, 2, 3.5), new Vec3(0.13, 0, 0));
        ordinary.setOnGround(true);
        ordinary.tick();
        ordinary.tick();
        helper.assertTrue(ordinary.isAlive(), "Two ordinary movement samples must not instantly capture a moving ball");
        ordinary.tick();
        helper.assertTrue(ordinary.isRemoved() && ordinary.getDeltaMovement().length() > 0.04,
            "A slow ordinary-ground putt must score after spending enough time over the cup");

        helper.setBlock(new BlockPos(3, 2, 3), net.minecraft.world.level.block.Blocks.AIR);
        flatLane(helper, true);
        var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 3)));
        ground.setFlag(true);
        var sculpted = atCup(helper, ground, 0.31, 0, new Vec3(0.13, 0, 0));
        sculpted.setOnGround(true);
        sculpted.tick();
        sculpted.tick();
        helper.assertTrue(sculpted.isAlive(), "Sculpted movement substeps must not multiply the time credited inside the cup");
        sculpted.tick();
        helper.assertTrue(sculpted.isRemoved() && sculpted.getDeltaMovement().length() > 0.04,
            "The equivalent sculpted putt must score with the same time requirement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedFastPassesDoNotAccumulateCaptureTime(GameTestHelper helper) {
        flatLane(helper, true);
        var cup = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 3)));
        cup.setFlag(true);
        var ball = atCup(helper, cup, 0.05, 0, Vec3.ZERO);
        ball.setOnGround(true);
        double cupX = cup.getBlockPos().getX() + 0.5;
        for (int pass = 0; pass < 6; pass++) {
            int direction = pass % 2 == 0 ? 1 : -1;
            ball.setDeltaMovement(new Vec3(direction * 0.32, 0, 0));
            for (int tick = 0; tick < 8 && ball.isAlive() && (ball.getX() - cupX) * direction < 0.4; tick++) { ball.tick(); }
            helper.assertTrue(ball.isAlive() && (ball.getX() - cupX) * direction >= 0.4,
                "Each fast pass must leave the cup and reset the capture time, including pass " + pass);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shallowFastBounceAcrossCupDoesNotScore(GameTestHelper helper) {
        flatLane(helper, true);
        var cup = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 3)));
        cup.setFlag(true);
        var ball = atCup(helper, cup, 0.1, 0.15, new Vec3(0.38, -0.08, 0));
        for (int tick = 0; tick < 4 && ball.isAlive(); tick++) { ball.tick(); }
        helper.assertTrue(ball.isAlive() && ball.getX() > cup.getBlockPos().getX() + 1,
            "A fast shallow bounce must cross the hole without scoring from extra contact callbacks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hoveringAboveOrdinaryCupDoesNotScore(GameTestHelper helper) {
        flatLane(helper, false);
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var ball = ball(helper, new Vec3(3.5, 2.4, 3.5), Vec3.ZERO);
        ball.setNoGravity(true);
        for (int tick = 0; tick < 10; tick++) { ball.tick(); }
        helper.assertTrue(ball.isAlive(), "An ordinary cup must also require reaching the ground, rather than accepting a ball overhead");
        helper.succeed();
    }

    private static void flatLane(GameTestHelper helper, boolean sculpted) {
        for (int x = 1; x <= 6; x++) {
            BlockPos pos = new BlockPos(x, 1, 3);
            helper.setBlock(pos, sculpted ? GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState()
                : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
            if (sculpted) {
                var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                ground.setSurface(SculptedSurface.flat(16));
            }
        }
    }

    private static GolfBallEntity atCup(GameTestHelper helper, SculptedGroundBlockEntity cup, double x, double elevation, Vec3 velocity) {
        var pos = cup.getBlockPos();
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(pos.getX() + x, pos.getY() + cup.surface().ballFeetHeight(x, 0.5, ball.getBbWidth() / 2.0) + elevation, pos.getZ() + 0.5);
        ball.setDeltaMovement(velocity);
        return ball;
    }

    private static GolfBallEntity ball(GameTestHelper helper, Vec3 feet, Vec3 velocity) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(helper.absoluteVec(feet));
        ball.setDeltaMovement(velocity);
        return ball;
    }

    private static SculptedGroundBlockEntity downhillLane(GameTestHelper helper) {
        SculptedGroundBlockEntity cup = null;
        for (int x = 1; x < 30; x++) {
            int west = 128 - 3 * (x - 1), east = west - 3, y = Math.floorDiv(east, 16);
            for (int z = 2; z <= 4; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                helper.setBlock(pos, GolfMod.PUTTING_GREEN_BLOCK.get());
                var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                ground.setSurface(new SculptedSurface(west - y * 16, east - y * 16, east - y * 16, west - y * 16));
                if (x == 8 && z == 3) { cup = ground; }
            }
        }
        return cup;
    }

    private static GolfBallEntity fullPowerPutt(GameTestHelper helper) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        Vec3 feet = helper.absoluteVec(new Vec3(2.1, (125 - 0.3) / 16.0, 3.5));
        // Sphere support is slightly above the height directly below its center on a slope.
        double radius = ball.getBbWidth() / 2.0;
        ball.setPos(feet.add(0, radius * (Math.sqrt(1 + 0.1875 * 0.1875) - 1), 0));
        ball.setOnGround(true);
        helper.getLevel().addFreshEntity(ball);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cup-test"));
        player.setPos(ball.getX() - 0.75, ball.getY() - player.getEyeHeight() + 0.15, ball.getZ());
        player.setYRot(-90);
        player.setXRot(0);
        var stack = new ItemStack(GolfMod.PUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var putter = (GolfClubItem) stack.getItem();
        try {
            putter.releaseUsing(stack, helper.getLevel(), player, putter.getUseDuration(stack, player) - 60);
            helper.assertTrue(GolfClubItem.getPendingShot(player) != null, "The putter must acquire the test ball");
            GolfClubItem.executePendingShot(player, GolfModConfig.COMMON.accuracyMeterCycleMs.get() / 4);
        } finally {
            GolfClubItem.clearPendingShot(player.getUUID());
        }
        return ball;
    }
}
