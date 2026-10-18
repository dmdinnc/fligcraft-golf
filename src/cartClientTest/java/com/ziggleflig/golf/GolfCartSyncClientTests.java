package com.ziggleflig.golf;

import com.ziggleflig.golf.entity.GolfCartEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Opt-in connected client/server regressions in a disposable world, outside the normal mod jar. */
@EventBusSubscriber(modid = GolfMod.MODID, value = Dist.CLIENT)
public final class GolfCartSyncClientTests {
    private static final long STARTED_AT = System.nanoTime();
    private record Snapshot(Vec3 position, boolean ridden) { }
    private static volatile UUID cartId;
    private static volatile Snapshot serverSnapshot;
    private static volatile double extraServerMovement;
    private static Vec3 beforeServerTick;
    private static boolean started, prepared, finished;
    private static int phase, ticks, round;
    private static double driveStart, walkStart, maxStoppedError, maxHandoffError;

    @SubscribeEvent public static void beforeEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof GolfCartEntity cart && !cart.level().isClientSide && cart.getUUID().equals(cartId)) {
            beforeServerTick = cart.getControllingPassenger() instanceof ServerPlayer ? cart.position() : null;
        }
    }

    @SubscribeEvent public static void afterEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof GolfCartEntity cart && !cart.level().isClientSide && cart.getUUID().equals(cartId)) {
            if (beforeServerTick != null) { extraServerMovement = Math.max(extraServerMovement, beforeServerTick.distanceTo(cart.position())); }
            serverSnapshot = new Snapshot(cart.position(), cart.getControllingPassenger() instanceof ServerPlayer);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (finished) { return; }
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() - STARTED_AT > 240_000_000_000L) { throw new IllegalStateException("Cart client test timed out in phase " + phase); }
            if (!started && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) { mc.screen.onClose(); return; }
            if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                var settings = new LevelSettings("Cart synchronization test", GameType.SURVIVAL, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("cart-sync-" + System.currentTimeMillis(), settings,
                    new WorldOptions(0, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            if (!started || mc.level == null || mc.player == null) { return; }
            mc.player.setYRot(-90); mc.player.setXRot(0);
            if (!prepared) {
                prepared = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var level = server.overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(-33, -60, 34, -90, 0);
                    var cart = new GolfCartEntity(GolfMod.GOLF_CART_ENTITY.get(), level);
                    cart.setPos(-33, -60, 34);
                    cart.setOnGround(true);
                    level.addFreshEntity(cart);
                    cartId = cart.getUUID();
                });
                return;
            }
            GolfCartEntity cart = null;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity.getUUID().equals(cartId) && entity instanceof GolfCartEntity candidate) { cart = candidate; break; }
            }
            if (cart == null || serverSnapshot == null) { return; }
            if (phase == 0) {
                if (mc.player.tickCount < 30 || mc.player.distanceToSqr(cart) > 16) { return; }
                checkRotationPackets(mc);
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var vehicle = server.overworld().getEntity(cartId);
                    check(server.getPlayerList().getPlayers().getFirst().startRiding(vehicle, true), "The server must mount the real client player");
                });
                phase = 1; ticks = 0;
            } else if (phase == 1) {
                if (mc.player.getVehicle() != cart) { return; }
                driveStart = cart.getX();
                mc.options.keyUp.setDown(true);
                phase = 2; ticks = 0;
            } else if (phase == 2) {
                if (++ticks < 35) { return; }
                check(cart.getX() > driveStart + 7, "The test must actually drive at speed through the normal client controls");
                check(extraServerMovement < 0.000001, "Server cart ticks must not simulate the driver's movement again: " + extraServerMovement);
                check(cart.getPositionCodec().getBase().distanceTo(cart.position()) < 0.65,
                    "Driving must refresh the relative-packet base instead of retaining the pre-mount position");
                mc.options.keyUp.setDown(false);
                // Repeat after remounting, this time dismounting immediately while still coasting.
                if (round == 1) { mc.options.keyShift.setDown(true); }
                phase = round == 0 ? 3 : 4; ticks = 0;
            } else if (phase == 3) {
                if (++ticks < 40) { return; }
                check(cart.getDeltaMovement().horizontalDistance() < 0.005, "The test must brake to a stop before dismounting");
                check(cart.position().distanceTo(serverSnapshot.position()) < 0.05, "Stopped client and server cart positions must agree while mounted");
                check(cart.getPositionCodec().getBase().distanceTo(cart.position()) < 0.005, "A stopped cart must have a current packet-position base");
                mc.options.keyShift.setDown(true);
                phase = 4; ticks = 0;
            } else if (phase == 4) {
                if (mc.player.isPassenger() || serverSnapshot.ridden()) { return; }
                mc.options.keyShift.setDown(false);
                phase = 5; ticks = 0;
            } else if (phase == 5) {
                double error = cart.position().distanceTo(serverSnapshot.position());
                maxHandoffError = Math.max(maxHandoffError, error);
                Vec3 rendered = new Vec3(Mth.lerp(0.5, cart.xo, cart.getX()), Mth.lerp(0.5, cart.yo, cart.getY()), Mth.lerp(0.5, cart.zo, cart.getZ()));
                // A moving handoff can need the normal three-tick packet interpolation.
                if (round == 0 || ticks >= 6) {
                    maxStoppedError = Math.max(maxStoppedError, error);
                    check(maxStoppedError < 0.05 && rendered.distanceTo(serverSnapshot.position()) < 0.05,
                        "Dismounting must not leave the visible cart/hitbox behind its server position: " + error);
                }
                if (++ticks < 20) { return; }
                walkStart = mc.player.getX();
                mc.options.keyUp.setDown(true);
                phase = 6; ticks = 0;
            } else if (phase == 6 && ++ticks >= 25) {
                mc.options.keyUp.setDown(false);
                check(mc.player.getX() > walkStart + 2, "After dismounting, the player must walk freely rather than stick in an invisible cart");
                check(cart.position().distanceTo(serverSnapshot.position()) < 0.05, "The empty parked cart must remain synchronized while the player walks away");
                if (round == 0) {
                    GolfMod.LOGGER.info("CART_SYNC_CLIENT stopped exit passed; remounting for a moving exit");
                    round++;
                    mc.getSingleplayerServer().execute(() -> {
                        var server = mc.getSingleplayerServer();
                        check(server.getPlayerList().getPlayers().getFirst().startRiding(server.overworld().getEntity(cartId), true), "The client must be able to remount");
                    });
                    phase = 1; ticks = 0;
                } else {
                    GolfMod.LOGGER.info("CART_SYNC_CLIENT_RESULT PASS extraServerMovement={} stoppedError={} handoffError={} walkDistance={}",
                        extraServerMovement, maxStoppedError, maxHandoffError, mc.player.getX() - walkStart);
                    finish(mc, "PASS");
                }
            }
        } catch (Throwable failure) {
            GolfMod.LOGGER.error("CART_SYNC_CLIENT_RESULT FAIL in phase " + phase, failure);
            try { finish(mc, "FAIL"); } catch (Exception ignored) { mc.stop(); }
        }
    }

    private static void checkRotationPackets(Minecraft mc) {
        var cart = new GolfCartEntity(GolfMod.GOLF_CART_ENTITY.get(), mc.level);
        cart.setId(900000001);
        cart.setPos(-40, -60, 38);
        cart.syncPacketPositionCodec(cart.getX(), cart.getY(), cart.getZ());
        mc.level.addEntity(cart);
        try {
            double targetX = cart.getX() + 1.25;
            mc.getConnection().handleMoveEntity(new ClientboundMoveEntityPacket.Pos(cart.getId(), (short)5120, (short)0, (short)0, true));
            mc.getConnection().handleMoveEntity(new ClientboundMoveEntityPacket.Rot(cart.getId(), (byte)64, (byte)0, true));
            for (int tick = 0; tick < 3; tick++) { cart.tick(); }
            check(Math.abs(cart.getX() - targetX) < 0.000001, "A rotation-only packet must retain the pending position target");
            check(Math.abs(Mth.wrapDegrees(cart.getYRot() - 90)) < 0.001, "Observer carts must interpolate their received rotation too");
            cart.setYRot(179);
            cart.lerpTo(cart.getX(), cart.getY(), cart.getZ(), -179, 0, 3);
            cart.tick();
            check(Math.abs(Mth.wrapDegrees(cart.getYRot() - 179)) < 2, "Remote rotation must use the short arc across the yaw seam");
        } finally { mc.level.removeEntity(cart.getId(), Entity.RemovalReason.DISCARDED); }
    }

    private static void finish(Minecraft mc, String result) throws Exception {
        finished = true;
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);
        Files.writeString(Path.of("cart-sync-result.txt"), result);
        mc.stop();
    }

    private static void check(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
}
