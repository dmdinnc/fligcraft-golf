package com.ziggleflig.golf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL15;

/** Uses only a fresh fixture world; never opens or edits an existing player world. */
@EventBusSubscriber(modid = GolfMod.MODID, value = Dist.CLIENT)
public final class TerrainReloadClientTests {
    private static final long STARTED_AT = System.nanoTime();
    private static final BlockPos CENTER = new BlockPos(-33, -61, 34);
    private static final List<BlockPos> PROBES = List.of(CENTER.offset(-3, 0, 0), CENTER.west(), CENTER.east(), CENTER.offset(3, 0, 0), CENTER.offset(4, 0, 0));
    private static String worldName;
    private static int stage, ticks, round;
    private static volatile boolean patchReady;
    private static List<List<Integer>> reference;
    private static boolean stopped;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (stopped) { return; }
        try {
            if (System.nanoTime() - STARTED_AT > 240_000_000_000L) { throw new IllegalStateException("Terrain reload fixture timed out"); }
            if (stage == 0 && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) { mc.screen.onClose(); return; }
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                stage = 1;
                worldName = "terrain-reload-" + System.currentTimeMillis();
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                var settings = new LevelSettings("Terrain canopy reload test", GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(0, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), mc.screen);
                return;
            }
            if (stage == 4) {
                if (mc.level == null && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    stage = 5; ticks = 0;
                    GolfMod.LOGGER.info("TERRAIN_RELOAD reopening {} round={}", worldName, round);
                    mc.createWorldOpenFlows().openWorld(worldName, () -> finish(mc, "OPEN_FAILED"));
                }
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) { return; }
            if (stage == 1) {
                stage = 2;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    server.overworld().setDayTime(6000);
                    server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    server.getPlayerList().getPlayers().getFirst().connection.teleport(-40.5, -60, 28.5, -55, 10);
                });
                return;
            }
            mc.player.setYRot(-55); mc.player.setXRot(10);
            if (stage == 2) {
                if (mc.player.position().distanceToSqr(Vec3.atCenterOf(CENTER)) > 150
                        || !mc.levelRenderer.isSectionCompiled(CENTER.offset(-5, 0, -5))
                        || !mc.levelRenderer.isSectionCompiled(CENTER.offset(5, 0, 5))) { return; }
                stage = 3; ticks = 0;
                mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                        var pos = CENTER.offset(x, 0, z);
                        var block = x < -2 ? GolfMod.PUTTING_GREEN_BLOCK.get() : x < 0 ? GolfMod.ROUGH_BLOCK.get()
                            : x < 2 ? GolfMod.FAIRWAY_BLOCK.get() : x < 4 ? GolfMod.BUNKER_SAND_BLOCK.get() : GolfMod.PUTTING_GREEN_BLOCK.get();
                        level.setBlock(pos, block.defaultBlockState(), 3);
                        int height = x < -2 ? 8 : x < 0 ? 16 : x < 2 ? 20 : x < 4 ? 32 : 10;
                        SculptedGroundBlock.source(level, pos).setSurface(x >= 4 ? new SculptedSurface(12, 8, 8, 12) : SculptedSurface.flat(height));
                    }
                    for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
                        level.setBlock(CENTER.offset(x, 5, z), Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 3);
                    }
                    for (int y = 2; y <= 5; y++) { level.setBlock(CENTER.offset(0, y, 3), Blocks.BIRCH_LOG.defaultBlockState(), 3); }
                    patchReady = true;
                });
                return;
            }
            if ((stage != 3 && stage != 5) || !patchReady || !loaded(mc) || ++ticks < 80) { return; }
            var current = record(mc);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(Path.of("canopy-round-" + round + ".png"));
            }
            if (reference == null) { reference = current; }
            else if (!reference.equals(current)) { throw new IllegalStateException("Reload changed shaded mesh lighting: before=" + reference + " after=" + current); }
            if (round++ >= 2) { finish(mc, "PASS"); return; }
            stage = 4;
            GolfMod.LOGGER.info("TERRAIN_RELOAD saving {} round={}", worldName, round);
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } catch (Throwable error) {
            GolfMod.LOGGER.error("TERRAIN_RELOAD_RESULT ERROR", error);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of("canopy-failure.png")); }
            catch (Exception ignored) { }
            finish(mc, "FAIL");
        }
    }

    private static boolean loaded(Minecraft mc) {
        for (var pos : PROBES) {
            var ground = SculptedGroundBlock.source(mc.level, pos);
            if (ground == null || !mc.levelRenderer.isSectionCompiled(pos) || ground.surface().equals(SculptedSurface.FULL) && pos.getX() != CENTER.getX() - 1) { return false; }
        }
        return true;
    }

    private static List<List<Integer>> record(Minecraft mc) throws Exception {
        var result = new ArrayList<List<Integer>>();
        boolean dark = false;
        for (var base : PROBES) {
            var ground = SculptedGroundBlock.source(mc.level, base);
            var pos = ground.surface().heightAt(0.5, 0.5) > 1 ? base.above() : base;
            var state = mc.level.getBlockState(pos);
            var capture = new Capture();
            mc.getBlockRenderer().getModelRenderer().tesselateWithoutAO(mc.level, mc.getBlockRenderer().getBlockModel(state),
                state, pos, new PoseStack(), capture, false, RandomSource.create(0), 0, 0,
                mc.level.getModelDataManager().getAt(pos), RenderType.solid());
            var gpu = gpuLights(mc, pos);
            var serverLight = mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().overworld().getBrightness(LightLayer.SKY, pos)).join();
            int clientLight = mc.level.getBrightness(LightLayer.SKY, pos);
            GolfMod.LOGGER.info("TERRAIN_RELOAD round={} pos={} surface={} sky client/server={}/{} baked={} gpu={}",
                round, pos, ground.surface(), clientLight, serverLight, capture.top, gpu);
            dark |= capture.top.isEmpty() || gpu.isEmpty() || capture.top.stream().anyMatch(light -> light == 0)
                || gpu.stream().anyMatch(light -> light == 0) || capture.top.stream().noneMatch(light -> light < 15)
                || clientLight != serverLight;
            result.add(gpu.stream().sorted().toList());
        }
        if (dark) { throw new IllegalStateException("Course terrain beneath leaves must have normal shade, without zero-lit mesh faces"); }
        return result;
    }

    private static List<Integer> gpuLights(Minecraft mc, BlockPos pos) throws Exception {
        var field = mc.levelRenderer.getClass().getDeclaredField("viewArea"); field.setAccessible(true);
        var view = field.get(mc.levelRenderer);
        var method = view.getClass().getDeclaredMethod("getRenderSectionAt", BlockPos.class); method.setAccessible(true);
        var section = method.invoke(view, pos);
        var buffer = (com.mojang.blaze3d.vertex.VertexBuffer) section.getClass().getMethod("getBuffer", RenderType.class).invoke(section, RenderType.solid());
        int previous = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        var bufferField = buffer.getClass().getDeclaredField("vertexBufferId"); bufferField.setAccessible(true);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferField.getInt(buffer));
        int size = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
        ByteBuffer vertices = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        var lights = new ArrayList<Integer>();
        int x = pos.getX() & 15, y = pos.getY() & 15, z = pos.getZ() & 15;
        for (int i = 0; i + 32 <= size; i += 32) {
            float vx = vertices.getFloat(i), vy = vertices.getFloat(i + 4), vz = vertices.getFloat(i + 8);
            if (vx >= x - .00001 && vx <= x + 1.00001 && vz >= z - .00001 && vz <= z + 1.00001
                    && vy > y + .01 && vy <= y + 1.00001 && vertices.get(i + 29) > 0) {
                lights.add((vertices.getShort(i + 26) & 65535) / 16);
            }
        }
        return lights;
    }

    private static void finish(Minecraft mc, String result) {
        stopped = true;
        GolfMod.LOGGER.info("TERRAIN_RELOAD_RESULT {}", result);
        try { Files.writeString(Path.of("terrain-reload-result.txt"), result); }
        catch (Exception error) { GolfMod.LOGGER.error("Could not save reload fixture result", error); }
        mc.stop();
    }

    private static final class Capture implements VertexConsumer {
        final List<Integer> top = new ArrayList<>();
        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] colors, float r, float g, float b,
                float a, int[] light, int overlay, boolean alpha) {
            if (quad.getDirection() == Direction.UP) { for (int packed : light) { top.add(packed >> 20 & 15); } }
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
