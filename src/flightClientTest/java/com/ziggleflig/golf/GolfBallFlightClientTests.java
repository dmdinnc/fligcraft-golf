package com.ziggleflig.golf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ziggleflig.golf.client.GolfClientConfig;
import com.ziggleflig.golf.entity.GolfBallEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.DyeColor;
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

/** Opt-in real client/network/renderer check in a disposable world and profile. */
@EventBusSubscriber(modid = GolfMod.MODID, value = Dist.CLIENT)
public final class GolfBallFlightClientTests {
    private static final long STARTED_AT = System.nanoTime();
    private static boolean started, prepared;
    private static int phase, ticks;
    private static volatile List<UUID> balls = List.of();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() - STARTED_AT > 180_000_000_000L) { throw new IllegalStateException("Client flight test timed out"); }
            if (!started && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) { mc.screen.onClose(); return; }
            if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(12);
                mc.options.hideGui = true;
                var settings = new LevelSettings("Ball flight client test", GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("ball-flight-" + System.currentTimeMillis(), settings,
                    new WorldOptions(0, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            if (!started || mc.level == null || mc.player == null) { return; }
            mc.player.setYRot(0); mc.player.setXRot(-1);
            if (!prepared) {
                prepared = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var level = server.overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(0.5, -58, 0.5, 0, -1);
                    var ids = new ArrayList<UUID>();
                    DyeColor[] colors = {DyeColor.BLUE, DyeColor.WHITE, DyeColor.BLACK, DyeColor.RED};
                    double[][] positions = {{-14, 70}, {-5, 184}, {20, 184}, {-25, 175}};
                    for (int i = 0; i < colors.length; i++) {
                        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), level);
                        ball.setPos(positions[i][0], -53, positions[i][1]);
                        ball.setNoGravity(true);
                        ball.setColor(colors[i]);
                        ball.registerStroke(player);
                        ball.setPuttShot(i == 3);
                        ball.setDeltaMovement(new Vec3(i == 0 ? -0.2 : 0.2, 0, 0));
                        level.addFreshEntity(ball);
                        ids.add(ball.getUUID());
                    }
                    balls = List.copyOf(ids);
                });
            }
            if (balls.size() != 4) { return; }
            var live = new ArrayList<GolfBallEntity>();
            for (UUID id : balls) {
                GolfBallEntity ball = null;
                for (var entity : mc.level.entitiesForRendering()) {
                    if (id.equals(entity.getUUID()) && entity instanceof GolfBallEntity golfBall) { ball = golfBall; break; }
                }
                if (ball == null) { return; }
                live.add(ball);
            }
            if (phase == 0) {
                if (live.subList(0, 3).stream().anyMatch(ball -> ball.flightTrail().samples().size() < 3)) { return; }
                check(live.get(1).distanceToSqr(mc.player) > 160 * 160, "The server must track a ball beyond the old 160-block limit");
                GolfClientConfig.FLIGHT_GUIDES.set(true);
                for (int i = 0; i < 3; i++) {
                    var rendered = capture(mc, live.get(i));
                    check(markerVertices(rendered) == 12 && trailVertices(rendered) > 2, "Moving colored balls must emit a marker and a trail");
                    int color = live.get(i).getColor().getTextureDiffuseColor() & 0xFFFFFF;
                    check(rendered.get(RenderType.debugStructureQuads()).colors.contains(color), "Marker must include the ball's actual dye color");
                }
                var putt = capture(mc, live.get(3));
                check(markerVertices(putt) == 0 && trailVertices(putt) == 0, "The putt flag must synchronize and suppress flight guides on the client");
                GolfMod.LOGGER.info("BALL_FLIGHT_CLIENT tracked beyond 160; blue/white/black markers and trails; putt suppression passed");
                phase = 1; ticks = 0;
            } else if (++ticks >= 10) {
                ticks = 0;
                if (phase == 1) {
                    screenshot(mc, "ball-flight-guides.png");
                    GolfClientConfig.FLIGHT_GUIDES.set(false);
                    var hidden = capture(mc, live.get(1));
                    check(markerVertices(hidden) == 0 && trailVertices(hidden) == 0, "Client toggle must hide guides while keeping the real model");
                    check(hidden.values().stream().mapToInt(c -> c.vertices).sum() > 0, "Disabling guides must still render the physical ball");
                    phase = 2;
                } else {
                    screenshot(mc, "ball-flight-guides-disabled.png");
                    GolfClientConfig.FLIGHT_GUIDES.set(true);
                    Files.writeString(Path.of("ball-flight-result.txt"), "PASS");
                    GolfMod.LOGGER.info("BALL_FLIGHT_CLIENT_RESULT PASS");
                    mc.stop();
                }
            }
        } catch (Throwable error) {
            GolfMod.LOGGER.error("BALL_FLIGHT_CLIENT_RESULT ERROR", error);
            try { Files.writeString(Path.of("ball-flight-result.txt"), "ERROR"); }
            catch (Exception reportError) { GolfMod.LOGGER.error("Could not write flight test result", reportError); }
            mc.stop();
        }
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of(name)); }
    }
    private static void check(boolean passed, String message) { if (!passed) { throw new IllegalStateException(message); } }
    private static Map<RenderType, Capture> capture(Minecraft mc, GolfBallEntity ball) {
        var captures = new LinkedHashMap<RenderType, Capture>();
        mc.getEntityRenderDispatcher().getRenderer(ball).render(ball, 0, 0.5f, new PoseStack(),
            type -> captures.computeIfAbsent(type, unused -> new Capture()), 0xF000F0);
        return captures;
    }
    private static int markerVertices(Map<RenderType, Capture> captures) {
        var marker = captures.get(RenderType.debugStructureQuads());
        return marker == null ? 0 : marker.vertices;
    }
    private static int trailVertices(Map<RenderType, Capture> captures) {
        return captures.entrySet().stream().filter(entry -> entry.getKey().toString().contains("ball_trail"))
            .mapToInt(entry -> entry.getValue().vertices).sum();
    }
    private static final class Capture implements VertexConsumer {
        int vertices;
        final List<Integer> colors = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { colors.add(r << 16 | g << 8 | b); return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
