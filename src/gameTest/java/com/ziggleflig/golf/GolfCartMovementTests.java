package com.ziggleflig.golf;

import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.entity.GolfCartEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class GolfCartMovementTests {
    private record Run(double distance, double rise, int clipped, int airborne, double speed) { }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void idleCartKeepsGroundContact(GameTestHelper helper) {
        lane(helper, 3, 0);
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        double height = cart.getY();
        for (int tick = 0; tick < 12; tick++) {
            cart.tick();
            helper.assertTrue(cart.onGround(), "A stationary cart must keep support instead of alternating between grounded and airborne ticks");
        }
        helper.assertTrue(Math.abs(cart.getY() - height) < 0.00001, "Maintaining contact must not sink or bounce the cart");
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartKeepsSpeedOnGentleHill(GameTestHelper helper) {
        compareHill(helper, 2);
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartKeepsSpeedOnQuarterBlockHill(GameTestHelper helper) {
        compareHill(helper, 4);
    }

    private static void compareHill(GameTestHelper helper, int rise) {
        lane(helper, 3, 0);
        lane(helper, 8, rise);
        Run flat = drive(helper, 3, false, 45), uphill = drive(helper, 8, false, 45);
        GolfMod.LOGGER.info("CART_MOVEMENT rise={}/16 flat={} uphill={}", rise, flat, uphill);
        helper.assertTrue(uphill.distance >= flat.distance * 0.9 && uphill.speed > 0.5 && uphill.rise > 1,
            "Driving up ordinary course slopes must retain useful speed: flat=" + flat + ", uphill=" + uphill);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartCanDescendHill(GameTestHelper helper) {
        lane(helper, 3, 0);
        lane(helper, 8, 4);
        Run flat = drive(helper, 3, false, 45), downhill = drive(helper, 8, true, 45);
        helper.assertTrue(downhill.distance >= flat.distance * 0.9 && downhill.rise < -1,
            "Fixing uphill support must preserve driving downhill and gravity: flat=" + flat + ", downhill=" + downhill);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartStillStopsAtTallWall(GameTestHelper helper) {
        lane(helper, 3, 0);
        for (int y = 2; y <= 5; y++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(8, y, z), Blocks.STONE); }
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = driver(helper, cart, false);
        for (int tick = 0; tick < 40; tick++) { cart.tick(); }
        double wall = helper.absolutePos(new BlockPos(8, 2, 3)).getX();
        helper.assertTrue(cart.getBoundingBox().maxX <= wall + 0.00001 && cart.horizontalCollision,
            "Keeping hill momentum must not bypass full-height walls");
        helper.assertTrue(cart.getY() < helper.absolutePos(new BlockPos(8, 3, 3)).getY(), "A wall must not lift the cart onto its top");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartKeepsSpeedOnDiagonalHill(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
            int west = 16 + 2 * (x + z), y = 1 + (west - 1) / 16, local = west - (y - 1) * 16;
            for (int foundation = 0; foundation < y; foundation++) { helper.setBlock(new BlockPos(x, foundation, z), Blocks.STONE); }
            BlockPos pos = new BlockPos(x, y, z);
            helper.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get());
            SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(pos))
                .setSurface(new SculptedSurface(local, local + 2, local + 4, local + 2));
        }
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = driver(helper, cart, false);
        player.setYRot(-45);
        cart.setDeltaMovement(new Vec3(1, 0, 1).normalize().scale(0.6));
        Vec3 start = cart.position();
        for (int tick = 0; tick < 8; tick++) { cart.tick(); }
        GolfMod.LOGGER.info("CART_MOVEMENT diagonal distance={} speed={}", cart.position().subtract(start).horizontalDistance(), cart.getDeltaMovement().horizontalDistance());
        helper.assertTrue(cart.position().subtract(start).horizontalDistance() > 4.3
            && cart.getDeltaMovement().horizontalDistance() > 0.5 && cart.getY() > start.y + 0.5,
            "Climbing across both terrain axes at full speed must not lose momentum at the corners");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void drivenCartStillClimbsOrdinaryBlockStep(GameTestHelper helper) {
        lane(helper, 3, 0);
        for (int x = 8; x <= 40; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE); }
        Run run = drive(helper, 3, false, 45);
        helper.assertTrue(run.distance > 17 && run.rise > 0.9 && run.rise < 1.1,
            "The cart's existing one-block step height must still climb a normal block platform: " + run);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void cartFallsWhenDrivingOffLedge(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE); }
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = driver(helper, cart, false);
        cart.setDeltaMovement(new Vec3(0.6, 0, 0));
        double height = cart.getY();
        for (int tick = 0; tick < 10; tick++) { cart.tick(); }
        helper.assertTrue(!cart.onGround() && cart.getY() < height - 0.2 && cart.getDeltaMovement().y < 0,
            "The contact probe must not hold the cart in midair after it leaves a platform");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void movementSubstepsDoNotMultiplySlowBlockPenalty(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.SOUL_SAND); }
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = driver(helper, cart, false);
        cart.setDeltaMovement(new Vec3(0.6, 0, 0));
        double x = cart.getX();
        cart.tick();
        helper.assertTrue(cart.getX() - x > 0.55 && cart.getDeltaMovement().horizontalDistance() < 0.3,
            "Slow blocks must still apply their drag, without charging it again for each collision substep");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void groundProbePreservesUpwardBlockImpulse(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.SLIME_BLOCK); }
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = driver(helper, cart, false);
        cart.setDeltaMovement(new Vec3(0.6, -0.2, 0));
        double height = cart.getY();
        cart.tick();
        helper.assertTrue(cart.getDeltaMovement().y > 0 && cart.getY() >= height,
            "A block's upward bounce must survive the remaining movement substeps");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void serverDoesNotDriveAClientControlledCartAgain(GameTestHelper helper) {
        lane(helper, 3, 0);
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = remoteDriver(helper, cart);
        player.zza = 1;
        cart.setDeltaMovement(new Vec3(0.6, 0, 0));
        Vec3 reportedPosition = cart.position();
        for (int tick = 0; tick < 5; tick++) { cart.tick(); }
        helper.assertTrue(cart.position().distanceToSqr(reportedPosition) < 0.000001
            && cart.getDeltaMovement().equals(Vec3.ZERO),
            "The server must retain the client's reported cart position, rather than applying a second driving simulation");
        player.stopRiding();
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "cartMovement")
    public static void serverResumesEmptyCartPhysicsAfterDriverLeaves(GameTestHelper helper) {
        lane(helper, 3, 0);
        var cart = cart(helper, new Vec3(2.5, 32, 3.5));
        var player = remoteDriver(helper, cart);
        cart.tick();
        player.stopRiding();
        cart.setDeltaMovement(new Vec3(0.2, 0, 0));
        double x = cart.getX();
        cart.tick();
        helper.assertTrue(cart.getX() > x + 0.1 && cart.onGround(),
            "After the client releases control, the server must resume empty-cart collision, gravity, and friction");
        helper.succeed();
    }

    private static void lane(GameTestHelper helper, int centerZ, int rise) {
        for (int x = 0; x <= 40; x++) {
            int west = 16 + rise * x;
            int y = 1 + (west - 1) / 16;
            int local = west - (y - 1) * 16;
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                for (int foundation = 0; foundation < y; foundation++) { helper.setBlock(new BlockPos(x, foundation, z), Blocks.STONE); }
                BlockPos pos = new BlockPos(x, y, z);
                helper.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get());
                SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(pos))
                    .setSurface(new SculptedSurface(local, local + rise, local + rise, local));
            }
        }
    }

    private static Run drive(GameTestHelper helper, int z, boolean reverse, int ticks) {
        var cart = cart(helper, new Vec3(reverse ? 32.5 : 2.5, 32, z + 0.5));
        var player = driver(helper, cart, reverse);
        double x = cart.getX(), y = cart.getY();
        int clipped = 0, airborne = 0;
        for (int tick = 0; tick < ticks; tick++) {
            cart.tick();
            if (cart.horizontalCollision) { clipped++; }
            if (!cart.onGround()) { airborne++; }
        }
        player.stopRiding();
        return new Run(Math.abs(cart.getX() - x), cart.getY() - y, clipped, airborne, cart.getDeltaMovement().horizontalDistance());
    }

    private static GolfCartEntity cart(GameTestHelper helper, Vec3 local) {
        var cart = new GolfCartEntity(GolfMod.GOLF_CART_ENTITY.get(), helper.getLevel());
        cart.setPos(helper.absoluteVec(local));
        cart.move(MoverType.SELF, new Vec3(0, -40, 0));
        cart.setDeltaMovement(Vec3.ZERO);
        return cart;
    }

    private static Player driver(GameTestHelper helper, GolfCartEntity cart, boolean reverse) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(reverse ? 90 : -90);
        player.zza = 1;
        helper.assertTrue(player.startRiding(cart, true), "The fixture must exercise a player driving the cart");
        return player;
    }

    private static Player remoteDriver(GameTestHelper helper, GolfCartEntity cart) {
        // makeMockPlayer intentionally reports itself as local; a server-side remote driver does not.
        var player = new Player(helper.getLevel(), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "remote-cart-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.setYRot(-90);
        helper.assertTrue(player.startRiding(cart, true) && !cart.isControlledByLocalInstance(), "The fixture must represent a remote driver on the server");
        return player;
    }
}
