package com.ziggleflig.golf;

import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.entity.GolfBallFlightTrail;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class GolfBallVisibilityTests {
    @GameTest(template = "empty")
    public static void distantBallRenderingPreservesPhysicalSize(GameTestHelper helper) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        helper.assertTrue(ball.shouldRenderAtSqrDistance(300 * 300), "A long drive must not disappear because of its tiny hitbox");
        helper.assertTrue(!ball.shouldRenderAtSqrDistance(600 * 600), "Rendering must still have a bounded range");
        helper.assertTrue(ball.getBbWidth() == 0.25f && ball.getBbHeight() == 0.25f, "Visibility changes must preserve the actual ball size");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trailKeepsRecentFlightAndFadesAfterStoppingButNeverShowsPutts(GameTestHelper helper) {
        var trail = new GolfBallFlightTrail();
        for (int tick = 0; tick <= 100; tick++) { trail.update(tick, new Vec3(tick * 0.5, 4, 0), 1, false); }
        helper.assertTrue(trail.samples().size() <= 20 && trail.samples().size() > 1, "Even a long flight must retain only a short recent trail");
        helper.assertTrue(trail.samples().getLast().position().equals(new Vec3(50, 4, 0)), "The trail must end at the observed ball position");
        for (int tick = 101; tick <= 125; tick++) { trail.update(tick, new Vec3(50, 4, 0), 1, false); }
        helper.assertTrue(trail.samples().isEmpty() && trail.opacity(125) == 0, "A stopped ball must lose its trail and marker");
        for (int tick = 126; tick <= 150; tick++) { trail.update(tick, new Vec3(tick, 4, 0), 2, true); }
        helper.assertTrue(trail.samples().isEmpty() && trail.opacity(150) == 0, "Putts must not acquire a flight guide");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newShotsAndTeleportsDoNotConnectUnrelatedFlightSegments(GameTestHelper helper) {
        var trail = new GolfBallFlightTrail();
        trail.update(0, Vec3.ZERO, 1, false);
        trail.update(1, new Vec3(1, 1, 0), 1, false);
        helper.assertTrue(!trail.samples().isEmpty(), "A moving shot must build a trail");
        trail.update(2, new Vec3(1, 1, 0), 2, false);
        helper.assertTrue(trail.samples().isEmpty(), "A fresh shot must discard the previous shot's trail");
        trail.update(3, new Vec3(2, 2, 0), 2, false);
        trail.update(4, new Vec3(100, 2, 0), 2, false);
        helper.assertTrue(trail.samples().isEmpty() && trail.opacity(4) == 0, "A teleport must not draw a line across the world");
        trail.update(5, new Vec3(101, 2, 0), 2, false);
        helper.assertTrue(trail.samples().getFirst().position().x >= 100, "A new observed segment may resume at the teleport destination");
        helper.succeed();
    }
}
