package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.terrain.SculptedBallPhysics;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class SculptedBallPhysicsTests {
    @GameTest(template = "empty")
    public static void ordinaryBounceBesideCourseTerrainRecordsFirstLanding(GameTestHelper helper) {
        ground(helper, 3, 3, GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.FULL);
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
        var ball = ball(helper, 4.05, 2.2, 3.5, new Vec3(0.2, -0.5, 0));
        ball.setDrivingRangeBall(true);
        ball.registerStroke(new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "mixed-bounce-test")));
        ball.tick();
        var saved = new net.minecraft.nbt.CompoundTag(); ball.saveWithoutId(saved);
        helper.assertTrue(ball.getDeltaMovement().y > 0 && saved.getDouble("CarryDistance") > 0.01,
            "A rebound from an ordinary block beside course terrain must finish carry on its first landing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandAbsorbsAnImpactAndStopsRollingBeforeRough(GameTestHelper helper) {
        var sand = ground(helper, 3, 3, GolfMod.BUNKER_SAND_BLOCK.get(), SculptedSurface.flat(8));
        var landing = ball(helper, 3.5, 3, 3.5, new Vec3(0, -3, 0));
        for (int i = 0; i < 4; i++) { landing.tick(); }
        helper.assertTrue(landing.onGround() && landing.getDeltaMovement().lengthSqr() < 0.000001
            && Math.abs(landing.getY() - sand.getBlockPos().getY() - 0.5) < 0.0001, "Sand must absorb a descending ball without tunneling or bouncing upward");
        for (int x = 1; x <= 6; x++) {
            ground(helper, x, 1, GolfMod.ROUGH_BLOCK.get(), SculptedSurface.flat(8));
            ground(helper, x, 5, GolfMod.BUNKER_SAND_BLOCK.get(), SculptedSurface.flat(8));
        }
        var roughBall = ball(helper, 2.5, 0, 1.5, new Vec3(0.3, 0, 0)); settle(roughBall, helper, 2.5, 1.5);
        var sandBall = ball(helper, 2.5, 0, 5.5, new Vec3(0.3, 0, 0)); settle(sandBall, helper, 2.5, 5.5);
        double start = sandBall.getX();
        for (int i = 0; i < 20; i++) { roughBall.tick(); sandBall.tick(); }
        helper.assertTrue(sandBall.getX() > start && sandBall.getX() + 0.15 < roughBall.getX()
            && sandBall.getDeltaMovement().lengthSqr() < 0.000001, "A ball must move through sand, then stop sooner than the same roll through rough");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void obliqueBounceUsesRealNormal(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(16, 8, 8, 16));
        var ball = ball(helper, 3.5, 3, 3.5, new Vec3(0, -3, 0));
        ball.tick();
        Vec3 normal = ground.surface().normalAt(0.5, 0.5);
        helper.assertTrue(ball.getDeltaMovement().x > 0.5 && ball.getDeltaMovement().dot(normal) > 0.5,
            "A vertical hit must rebound outward and downhill from the sloped plane");
        helper.assertTrue(ball.getX() > ground.getBlockPos().getX() + 0.5 && !ball.onGround(), "Bounce must move the ball rather than stick to a voxel stair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fastDropDoesNotTunnel(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.flat(8));
        var ball = ball(helper, 3.5, 7, 3.5, new Vec3(0, -30, 0));
        ball.tick();
        helper.assertTrue(ball.getY() > ground.getBlockPos().getY() + 0.5 && ball.getDeltaMovement().y > 5,
            "Fast shots must hit the finite surface and consume the remaining tick in the bounce direction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terrainSideIsSolidForBall(GameTestHelper helper) {
        ground(helper, 3, 3, GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.FULL);
        var ball = ball(helper, 2, 1.4, 3.5, new Vec3(2, 0, 0));
        ball.tick();
        helper.assertTrue(ball.getDeltaMovement().x < 0 && ball.getX() < helper.absolutePos(new BlockPos(3, 1, 3)).getX(),
            "Exact ball contacts must include the sides of a raised tile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void greenBreaksTowardDownhill(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            ground(helper, x, z, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(16 - z, 16 - z, 15 - z, 15 - z));
        }
        var ball = ball(helper, 2.5, 0, 2.5, new Vec3(0.12, 0, 0));
        settle(ball, helper, 2.5, 2.5);
        double startZ = ball.getZ();
        double downhillSpeed = 0;
        for (int i = 0; i < 20; i++) { ball.tick(); downhillSpeed = Math.max(downhillSpeed, ball.getDeltaMovement().z); }
        helper.assertTrue(ball.getZ() > startZ + 0.08 && downhillSpeed > 0.005,
            "A cross-slope putt on a green must acquire downhill motion");
        helper.assertTrue(ball.onGround() && Math.abs(ball.getDeltaMovement().dot(new Vec3(0, 1, 1.0 / 16).normalize())) < 0.00001,
            "Rolling velocity must stay tangent to the green");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stationaryBallsStayOnMildSlopes(GameTestHelper helper) {
        var blocks = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.BUNKER_SAND_BLOCK.get()};
        for (int i = 0; i < blocks.length; i++) {
            int z = 1 + i * 2;
            for (int x = 1; x <= 6; x++) { ground(helper, x, z, blocks[i], new SculptedSurface(16 - x, 15 - x, 15 - x, 16 - x)); }
            var ball = ball(helper, 2.5, 0, z + 0.5, Vec3.ZERO);
            settle(ball, helper, 2.5, z + 0.5);
            Vec3 start = ball.position();
            for (int tick = 0; tick < 100; tick++) { ball.tick(); }
            helper.assertTrue(ball.position().distanceToSqr(start) < 0.000001 && ball.getDeltaMovement().lengthSqr() < 0.000001,
                "Static friction must hold a resting ball on mild " + blocks[i].material().getSerializedName() + " slopes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollingBallsSettleOnMildSlopesAndSurviveReload(GameTestHelper helper) {
        var blocks = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.BUNKER_SAND_BLOCK.get()};
        for (int i = 0; i < blocks.length; i++) {
            int z = 1 + i * 2;
            for (int x = 1; x <= 6; x++) { ground(helper, x, z, blocks[i], new SculptedSurface(16 - x, 15 - x, 15 - x, 16 - x)); }
            var ball = ball(helper, 2.5, 0, z + 0.5, new Vec3(0.08, 0, 0));
            settle(ball, helper, 2.5, z + 0.5);
            double startX = ball.getX();
            for (int tick = 0; tick < 100; tick++) { ball.tick(); }
            helper.assertTrue(ball.getX() > startX + 0.02 && ball.getDeltaMovement().lengthSqr() < 0.000001,
                "A moving ball should slow and stop partway down the " + blocks[i].material().getSerializedName() + " slope");
            Vec3 stopped = ball.position();
            var saved = new net.minecraft.nbt.CompoundTag();
            ball.saveWithoutId(saved);
            var restored = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
            restored.load(saved);
            restored.setOnGround(false); // Support must recover even without a remembered grounded flag.
            for (int tick = 0; tick < 100; tick++) { restored.tick(); }
            helper.assertTrue(restored.position().distanceToSqr(stopped) < 0.000001,
                "Reloading a settled ball must not restart its downhill roll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steepSlopesReleaseRestingBalls(GameTestHelper helper) {
        var blocks = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.BUNKER_SAND_BLOCK.get()};
        for (int i = 0; i < blocks.length; i++) {
            int z = 1 + i * 2;
            ground(helper, 3, z, blocks[i], new SculptedSurface(16, 4, 4, 16));
            var ball = ball(helper, 3.5, 0, z + 0.5, Vec3.ZERO);
            settle(ball, helper, 3.5, z + 0.5);
            double startX = ball.getX();
            for (int tick = 0; tick < 4; tick++) { ball.tick(); }
            helper.assertTrue(ball.getX() > startX + 0.01 && ball.getDeltaMovement().x > 0.003,
                "A steep " + blocks[i].material().getSerializedName() + " slope must overcome static friction: distance=" + (ball.getX() - startX) + ", velocity=" + ball.getDeltaMovement());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roughAndFairwayHoldWhereGreenSlides(GameTestHelper helper) {
        var blocks = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get()};
        for (int i = 0; i < blocks.length; i++) {
            int z = 1 + i * 2;
            ground(helper, 3, z, blocks[i], new SculptedSurface(16, 13, 13, 16));
            var ball = ball(helper, 3.5, 0, z + 0.5, Vec3.ZERO);
            settle(ball, helper, 3.5, z + 0.5);
            double startX = ball.getX();
            for (int tick = 0; tick < 10; tick++) { ball.tick(); }
            helper.assertTrue(i == 0 ? ball.getX() > startX + 0.04 : Math.abs(ball.getX() - startX) < 0.0001,
                "Fairway and rough must hold a ball on a slope that overcomes green's static friction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void settledBallCanBeStruckAndReleasedByTerrainEdit(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(16, 15, 15, 16));
        var ball = ball(helper, 3.5, 0, 3.5, Vec3.ZERO);
        settle(ball, helper, 3.5, 3.5);
        ball.tick();
        double startX = ball.getX();
        ball.setDeltaMovement(new Vec3(0.12, 0, 0));
        ball.tick();
        helper.assertTrue(ball.getX() > startX + 0.08, "A new putt must release a ball held by static friction");
        ball.setDeltaMovement(Vec3.ZERO);
        settle(ball, helper, ball.getX() - helper.absolutePos(BlockPos.ZERO).getX(), 3.5);
        ball.tick();
        startX = ball.getX();
        ground.setSurface(new SculptedSurface(16, 4, 4, 16));
        for (int tick = 0; tick < 20; tick++) { ball.tick(); }
        helper.assertTrue(ball.getX() > startX + 0.08, "Steepening the terrain must release a previously settled ball");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void roughSlowsMoreThanFairwayAndGreen(GameTestHelper helper) {
        var blocks = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get()};
        var balls = new GolfBallEntity[3];
        for (int i = 0; i < 3; i++) {
            for (int x = 1; x <= 5; x++) { ground(helper, x, 1 + i * 2, blocks[i], SculptedSurface.flat(8)); }
            balls[i] = ball(helper, 2.5, 1.5, 1.5 + i * 2, new Vec3(0.15, 0, 0));
            balls[i].setOnGround(true);
            for (int tick = 0; tick < 4; tick++) { balls[i].tick(); }
        }
        helper.assertTrue(balls[0].getDeltaMovement().x > balls[1].getDeltaMovement().x
            && balls[1].getDeltaMovement().x > balls[2].getDeltaMovement().x * 1.5, "Material drag must distinguish green, fairway, and rough");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void uphillRollSlowsAndReverses(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) { ground(helper, x, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(3 * x, 3 * x + 3, 3 * x + 3, 3 * x)); }
        var ball = ball(helper, 3.5, 0, 3.5, new Vec3(0.01, 0, 0));
        settle(ball, helper, 3.5, 3.5);
        double start = ball.getX();
        for (int i = 0; i < 25; i++) { ball.tick(); }
        helper.assertTrue(ball.getDeltaMovement().x < -0.005 && ball.getX() < start,
            "A weak uphill putt must lose speed and roll back downhill");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void joinedTilesDoNotKickBallUpward(GameTestHelper helper) {
        ground(helper, 2, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8));
        ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8));
        var ball = ball(helper, 2.8, 1.5, 3.5, new Vec3(0.35, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(ball.getX() > helper.absolutePos(new BlockPos(3, 1, 3)).getX(), "Putt must cross the shared edge");
        helper.assertTrue(ball.onGround() && Math.abs(ball.getDeltaMovement().y) < 0.000001 && Math.abs(ball.getY() - helper.absoluteVec(new Vec3(0, 1.5, 0)).y) < 0.0001,
            "A coplanar seam must not create a bounce or a height step");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flatRestAndLiveHeightEdit(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8));
        var ball = ball(helper, 3.5, 1.5, 3.5, Vec3.ZERO);
        helper.getLevel().addFreshEntity(ball);
        ball.setOnGround(true);
        for (int i = 0; i < 40; i++) { ball.tick(); }
        helper.assertTrue(ball.getDeltaMovement().lengthSqr() < 0.000001 && Math.abs(ball.getY() - ground.getBlockPos().getY() - 0.5) < 0.0001,
            "A flat resting ball must not jitter or gain energy");
        ground.setSurface(SculptedSurface.flat(12));
        ball.tick();
        helper.assertTrue(ball.onGround() && Math.abs(ball.getY() - ground.getBlockPos().getY() - 0.75) < 0.0001,
            "Editing terrain under a ball must move it onto the live surface");
        ball.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raisingCornerLiftsBallEvenWhenCoarseBoxesDoNotChange(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(12, 12, 12, 4));
        var ball = ball(helper, 3.1, 0, 3.9, Vec3.ZERO);
        settle(ball, helper, 3.1, 3.9);
        helper.getLevel().addFreshEntity(ball);
        double startY = ball.getY();
        ground.setSurface(new SculptedSurface(12, 12, 12, 5));
        double targetY = ground.getBlockPos().getY() + ground.surface().ballFeetHeight(0.1, 0.9, ball.getBbWidth() / 2.0);
        helper.assertTrue(ball.getY() > startY + 0.02 && Math.abs(ball.getY() - targetY) < 0.0001,
            "Live corner edits must lift the ball to the real surface, independently of player collision boxes");
        ball.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overflowIsOneContinuousContactSurface(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(16, 16, 16, 20));
        Vec3 feet = helper.absoluteVec(new Vec3(3.1, 3, 3.9));
        var contact = SculptedBallPhysics.sweep(helper.getLevel(), feet, new Vec3(0, -2, 0), 0.125, true);
        helper.assertTrue(contact != null && contact.source().equals(ground.getBlockPos()) && contact.material() == TerrainMaterial.GREEN,
            "The raised cap must refer to the original material and source geometry");
        helper.assertTrue(feet.y - 2 * contact.time() > ground.getBlockPos().getY() + 1.1, "Contact must use height above the original block boundary");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollsAcrossUndulationsWithoutEnteringSurface(GameTestHelper helper) {
        int[] vertices = {8, 10, 8, 6, 8, 8, 8};
        for (int x = 1; x <= 6; x++) {
            ground(helper, x, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), new SculptedSurface(vertices[x - 1], vertices[x], vertices[x], vertices[x - 1]));
        }
        var ball = ball(helper, 1.7, 0, 3.5, new Vec3(0.3, 0, 0));
        settle(ball, helper, 1.7, 3.5);
        boolean crossedCrest = false;
        for (int tick = 0; tick < 100; tick++) {
            ball.tick();
            BlockPos pos = new BlockPos((int) Math.floor(ball.getX()), helper.absolutePos(new BlockPos(0, 1, 0)).getY(), (int) Math.floor(ball.getZ()));
            var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
            if (ground != null) {
                double surfaceY = pos.getY() + ground.surface().heightAt(ball.getX() - pos.getX(), ball.getZ() - pos.getZ());
                helper.assertTrue(ball.getY() >= surfaceY - 0.0001, "Rolling across a crest or valley must not enter the ground");
            }
            crossedCrest |= ball.getX() > helper.absolutePos(new BlockPos(2, 1, 3)).getX();
        }
        helper.assertTrue(crossedCrest, "The test putt must cross the crest and encounter the downslope");
        helper.assertTrue(ball.getDeltaMovement().length() < 0.06, "Undulations must not create energy or endless bouncing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryWallStillBlocksTerrainPutt(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) { ground(helper, x, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8)); }
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 3), Blocks.STONE);
        var ball = ball(helper, 4.5, 1.5, 3.5, new Vec3(1, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(ball.getDeltaMovement().x < 0 && ball.getX() < helper.absolutePos(new BlockPos(5, 1, 3)).getX(), "Using exact terrain must retain ordinary obstacle collisions");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rollingOffEdgeFalls(GameTestHelper helper) {
        var ground = ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8));
        var ball = ball(helper, 3.9, 1.5, 3.5, new Vec3(0.3, 0, 0));
        ball.setOnGround(true);
        for (int tick = 0; tick < 8; tick++) { ball.tick(); }
        helper.assertTrue(ball.getY() < ground.getBlockPos().getY() + 0.2, "Leaving the edge must release support and let gravity act");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void holeOnLoweredGreenStillScores(GameTestHelper helper) {
        ground(helper, 3, 3, GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(8));
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var ball = ball(helper, 3.5, 1.5, 3.5, new Vec3(0.02, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertFalse(ball.isAlive(), "A flag above a lowered green must score against the actual green surface");
        helper.succeed();
    }

    private static SculptedGroundBlockEntity ground(GameTestHelper helper, int x, int z, SculptedGroundBlock block, SculptedSurface surface) {
        BlockPos local = new BlockPos(x, 1, z);
        helper.setBlock(local, block);
        var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(local));
        helper.assertTrue(ground.setSurface(surface) || ground.surface().equals(surface), "Could not create test surface");
        return ground;
    }
    private static GolfBallEntity ball(GameTestHelper helper, double x, double y, double z, Vec3 velocity) {
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(helper.absoluteVec(new Vec3(x, y, z)));
        ball.setDeltaMovement(velocity);
        return ball;
    }
    private static void settle(GolfBallEntity ball, GameTestHelper helper, double x, double z) {
        BlockPos pos = helper.absolutePos(new BlockPos((int) x, 1, (int) z));
        var ground = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(pos);
        ball.setPos(ball.getX(), pos.getY() + ground.surface().ballFeetHeight(x % 1, z % 1, 0.125) + 0.00001, ball.getZ());
        ball.setOnGround(true);
    }
}
