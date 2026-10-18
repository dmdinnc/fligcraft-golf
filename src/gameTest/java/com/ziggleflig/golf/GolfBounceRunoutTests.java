package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class GolfBounceRunoutTests {
    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void elevatedHybridKeepsForwardMomentumAndRunsOutOnGentleUphill(GameTestHelper helper) {
        course(helper, 1, 80);
        elevatedHybrid(helper, 0.65, 5, "fairway-1/16");
    }

    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void elevatedHybridRunsOutOnTwoStepFairwayUphill(GameTestHelper helper) {
        course(helper, 2, 80);
        elevatedHybrid(helper, 0.45, 3.5, "fairway-2/16");
    }

    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void elevatedHybridMovesForwardOnTwoStepRoughUphill(GameTestHelper helper) {
        course(helper, 2, 80, GolfMod.ROUGH_BLOCK.get());
        elevatedHybrid(helper, 0.40, 2.5, "rough-2/16");
    }

    private static void elevatedHybrid(GameTestHelper helper, double minimumRetention, double minimumRunout, String label) {
        var ball = ball(helper, 2.5, 11.5, new Vec3(3.7, 1.8, 0));
        double landing = Double.NaN, incoming = 0, outgoing = 0;
        int airborneTicks = 0;
        for (int tick = 0; tick < 240; tick++) {
            Vec3 before = ball.getDeltaMovement();
            ball.tick();
            if (Double.isNaN(landing) && ball.getDeltaMovement().y > 0 && before.y < -0.1) {
                landing = ball.getX(); incoming = before.x; outgoing = ball.getDeltaMovement().x;
            }
            if (!Double.isNaN(landing) && !ball.onGround()) { airborneTicks++; }
            if (!Double.isNaN(landing) && ball.onGround() && ball.getDeltaMovement().lengthSqr() < 0.000001) { break; }
        }
        double runout = ball.getX() - landing;
        GolfMod.LOGGER.info("BOUNCE_RUNOUT elevated-hybrid {} incoming={} outgoing={} ratio={} runout={} airborneTicks={} final={}",
            label, incoming, outgoing, outgoing / incoming, runout, airborneTicks, ball.getDeltaMovement());
        helper.assertTrue(!Double.isNaN(landing), "The raised hybrid shot must land on the course");
        helper.assertTrue(outgoing > incoming * minimumRetention && runout > minimumRunout,
            "A gentle uphill landing must retain forward momentum and have useful runout: incoming=" + incoming + ", outgoing=" + outgoing + ", runout=" + runout);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void lowFastShotSkipsAndRunsAcrossGentleUphill(GameTestHelper helper) {
        course(helper, 1, 0);
        var ball = ball(helper, 10.5, 3, new Vec3(1.5, -0.3, 0));
        double landing = Double.NaN, outgoing = 0;
        for (int tick = 0; tick < 150; tick++) {
            Vec3 before = ball.getDeltaMovement();
            ball.tick();
            if (Double.isNaN(landing) && ball.getDeltaMovement().y > 0 && before.y < -0.1) {
                landing = ball.getX(); outgoing = ball.getDeltaMovement().x;
            }
            if (!Double.isNaN(landing) && ball.onGround() && ball.getDeltaMovement().lengthSqr() < 0.000001) { break; }
        }
        double runout = ball.getX() - landing;
        GolfMod.LOGGER.info("BOUNCE_RUNOUT low-fast outgoing={} runout={} final={}", outgoing, runout, ball.getDeltaMovement());
        helper.assertTrue(!Double.isNaN(landing) && outgoing > 0.8 && runout > 8,
            "A low fast landing must skip forward into a substantial roll: outgoing=" + outgoing + ", runout=" + runout);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void steepUphillStillRejectsAForwardShot(GameTestHelper helper) {
        BlockPos local = new BlockPos(20, 1, 6);
        helper.setBlock(local, GolfMod.FAIRWAY_BLOCK.get());
        var ground = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(local));
        ground.setSurface(new SculptedSurface(0, 20, 20, 0));
        var ball = ball(helper, 19.8, 2, new Vec3(1.5, -0.5, 0));
        ball.tick();
        helper.assertTrue(ball.getDeltaMovement().x < 0 && ball.getX() < ground.getBlockPos().getX() + 1,
            "Preserving shallow-slope runout must still allow a steep bank to turn the ball back: " + ball.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "bounceRunout")
    public static void savedBounceKeepsReducedAirLossButNextStrokeRestoresNormalFlight(GameTestHelper helper) {
        course(helper, 0, 0);
        var landed = ball(helper, 10.5, 2, new Vec3(0, -1, 0));
        for (int i = 0; i < 15; i++) { landed.tick(); }
        var save = new net.minecraft.nbt.CompoundTag(); landed.saveWithoutId(save);
        Vec3 launch = new Vec3(1.5, 0.5, 0);
        var rebound = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel()); rebound.load(save);
        rebound.setPos(helper.absoluteVec(new Vec3(10.5, 11.5, 6.5))); rebound.setOnGround(false); rebound.setDeltaMovement(launch);
        var nextShot = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel()); nextShot.load(save);
        nextShot.setPos(rebound.position()); nextShot.setOnGround(false); nextShot.setDeltaMovement(launch);
        nextShot.registerStroke(new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bounce-reset-test")));
        var fresh = ball(helper, 10.5, 11.5, launch);
        for (int i = 0; i < 10; i++) { rebound.tick(); nextShot.tick(); fresh.tick(); }
        helper.assertTrue(rebound.getX() > fresh.getX() + 0.5,
            "Reloaded rebounds must keep reduced air losses rather than repeatedly losing their runout");
        helper.assertTrue(nextShot.position().distanceToSqr(fresh.position()) < 0.000001,
            "Striking a previously landed ball must restore the same flight as a fresh shot");
        helper.succeed();
    }

    private static void course(GameTestHelper helper, int rise, int slopeStart) {
        course(helper, rise, slopeStart, GolfMod.FAIRWAY_BLOCK.get());
    }
    private static void course(GameTestHelper helper, int rise, int slopeStart, SculptedGroundBlock material) {
        for (int x = 1; x < 159; x++) for (int z = 2; z < 11; z++) {
            int west = 24 + Math.max(0, x - slopeStart) * rise;
            int east = 24 + Math.max(0, x + 1 - slopeStart) * rise;
            int y = Math.floorDiv(west - 1, 16);
            BlockPos local = new BlockPos(x, y, z);
            helper.setBlock(local, material);
            SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(local))
                .setSurface(new SculptedSurface(west - y * 16, east - y * 16, east - y * 16, west - y * 16));
        }
    }
    private static GolfBallEntity ball(GameTestHelper helper, double x, double y, Vec3 motion) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(helper.absoluteVec(new Vec3(x, y, 6.5)));
        ball.setDeltaMovement(motion);
        return ball;
    }
}
