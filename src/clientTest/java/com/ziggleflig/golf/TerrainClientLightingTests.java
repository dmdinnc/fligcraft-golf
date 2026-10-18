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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL15;

@EventBusSubscriber(modid = GolfMod.MODID, value = Dist.CLIENT)
public final class TerrainClientLightingTests {
    private static final long STARTED_AT = System.nanoTime();
    private static boolean started, prepared, patchPrepared;
    private static int tick, phase;
    private static BlockPos target;
    private static boolean failed;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (System.nanoTime() - STARTED_AT > 180_000_000_000L) {
            GolfMod.LOGGER.error("TERRAIN_CLIENT_RESULT TIMEOUT");
            try { Files.writeString(Path.of("terrain-client-lighting-result.txt"), "TIMEOUT"); }
            catch (Exception error) { GolfMod.LOGGER.error("Could not write client test result", error); }
            mc.stop();
            return;
        }
        if (!started && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) {
            mc.screen.onClose();
            return;
        }
        if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            var settings = new LevelSettings("Terrain client lighting test", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("terrain-lighting-" + System.currentTimeMillis(), settings,
                new WorldOptions(0, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            return;
        }
        if (!started || mc.level == null || mc.player == null || mc.gameMode == null) { return; }
        try {
            if (!prepared) {
                prepared = true;
                // Always outside the origin chunk, regardless of randomized initial spawn.
                target = new BlockPos(-33, -61, 34);
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var level = server.overworld();
                    level.setDayTime(6000);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(-36.5, -60, 34.5, -90, 40);
                });
            }
            // Edit a loaded, watched patch, like a player placing terrain in the world.
            // Teleporting and editing previously unwatched chunks in one tick can precede their initial light packets.
            if (!patchPrepared) {
                if (mc.player.position().distanceToSqr(Vec3.atCenterOf(target)) > 100
                        || !mc.levelRenderer.isSectionCompiled(target.offset(-2, 0, -2))
                        || !mc.levelRenderer.isSectionCompiled(target.offset(2, 0, 2))) { return; }
                patchPrepared = true;
                mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                        var pos = target.offset(x, 0, z);
                        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
                        level.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3);
                        SculptedGroundBlock.source(level, pos).setSurface(new SculptedSurface(12 - x, 11 - x, 11 - x, 12 - x));
                    }
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                        .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.FAIRWAY_ITEM.get(), 64));
                });
            }
            mc.player.setYRot(phase >= 21 ? -49 : -90); mc.player.setXRot(phase >= 21 ? 10 : 40);
            if (phase == 0 && (SculptedGroundBlock.source(mc.level, target) == null || !mc.levelRenderer.isSectionCompiled(target))) { return; }
            if (++tick % 50 != 0) { return; }
            if (phase == 16) {
                mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                        var pos = target.offset(x, 0, z);
                        level.setBlock(pos, GolfMod.BUNKER_SAND_BLOCK.get().defaultBlockState(), 3);
                        SculptedGroundBlock.source(level, pos).setSurface(new SculptedSurface(12 - x, 11 - x, 11 - x, 12 - x));
                    }
                    SculptedGroundBlock.source(level, target).setSurface(new SculptedSurface(32, 11, 11, 12));
                    mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()
                        .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.BUNKER_SAND_ITEM.get(), 64));
                });
            } else if (phase == 17) {
                record(mc, "sand-initial");
                sandTexture(mc, target);
                sandTexture(mc, target.above());
                screenshot(mc, "sand-raised.png");
            } else if (phase == 18) {
                mc.gameMode.startDestroyBlock(target, Direction.UP);
            } else if (phase == 19) {
                if (!mc.level.getBlockState(target).isAir() || !mc.level.getBlockState(target.above()).isAir()
                        || mc.level.getBrightness(LightLayer.SKY, target) != 15) { failed = true; }
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target.below()).add(0, .5, 0), Direction.UP, target.below(), false));
            } else if (phase == 20) {
                record(mc, "sand-replaced"); sandTexture(mc, target);
                screenshot(mc, "sand-replaced.png");
            } else if (phase >= 21) {
                if (phase <= 27 && phase % 2 == 1) {
                    int height = new int[] {8, 16, 20, 32}[(phase - 21) / 2];
                    mc.getSingleplayerServer().execute(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                            var pos = target.offset(x, 0, z);
                            level.setBlock(pos, GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState(), 3);
                            var ground = SculptedGroundBlock.source(level, pos);
                            ground.setSurface(SculptedSurface.flat(height));
                            ground.setFlag(x == 0 && z == 0);
                        }
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                        player.connection.teleport(-36.5, -58, 31, -49, 10);
                    });
                } else if (phase <= 28) {
                    flagLighting(mc, "flag-" + new int[] {8, 16, 20, 32}[(phase - 22) / 2], true);
                    screenshot(mc, "flag-" + phase + ".png");
                } else if (phase == 29) {
                    mc.getSingleplayerServer().execute(() -> SculptedGroundBlock.source(
                        mc.getSingleplayerServer().overworld(), target).setSurface(new SculptedSurface(20, 32, 28, 18)));
                } else if (phase == 30) {
                    flagLighting(mc, "flag-raised-slope", true);
                    screenshot(mc, "flag-raised-slope.png");
                } else if (phase == 31) {
                    mc.getSingleplayerServer().execute(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                            level.setBlock(target.offset(x, 5, z), Blocks.STONE.defaultBlockState(), 3);
                        }
                    });
                } else if (phase == 32) {
                    flagLighting(mc, "flag-shaded", false);
                    screenshot(mc, "flag-shaded.png");
                    mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setBlock(
                        target.above(3).west(), Blocks.GLOWSTONE.defaultBlockState(), 3));
                } else if (phase == 33) {
                    var capture = flagLighting(mc, "flag-near-light-source", false);
                    if (capture.flagBlock.stream().noneMatch(light -> light > 0)) {
                        throw new IllegalStateException("Flag must respond to nearby block light");
                    }
                    mc.getSingleplayerServer().execute(() -> SculptedGroundBlock.source(
                        mc.getSingleplayerServer().overworld(), target).setFlag(false));
                } else if (phase == 34) {
                    var ground = SculptedGroundBlock.source(mc.level, target);
                    var capture = new Capture();
                    mc.getBlockEntityRenderDispatcher().render(ground, 0, new PoseStack(), type -> capture);
                    if (ground.hasFlag() || !capture.flag.isEmpty()) { throw new IllegalStateException("Retrieved flag still renders"); }
                }
            } else if (phase == 0) { record(mc, "initial"); }
            else if (phase % 5 == 1) {
                mc.gameMode.startDestroyBlock(target, Direction.UP);
            } else if (phase % 5 == 2) {
                if (!mc.level.getBlockState(target).isAir() || mc.level.getBrightness(LightLayer.SKY, target) != 15) { failed = true; }
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(target.below()).add(0, .5, 0), Direction.UP, target.below(), false));
            } else if (phase % 5 == 3) {
                record(mc, "replaced-" + phase / 5);
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setBlock(target.above(), Blocks.STONE.defaultBlockState(), 3));
            } else if (phase % 5 == 4) {
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().removeBlock(target.above(), false));
            } else {
                record(mc, "stone-removed-" + phase / 5);
            }
            if (++phase >= 35) {
                GolfMod.LOGGER.info("TERRAIN_CLIENT_RESULT {}", failed ? "FAIL" : "PASS");
                Files.writeString(Path.of("terrain-client-lighting-result.txt"), failed ? "FAIL" : "PASS");
                mc.stop();
            }
        } catch (Throwable error) {
            GolfMod.LOGGER.error("TERRAIN_CLIENT_RESULT ERROR", error);
            try { Files.writeString(Path.of("terrain-client-lighting-result.txt"), "ERROR"); }
            catch (Exception reportError) { GolfMod.LOGGER.error("Could not write client test result", reportError); }
            mc.stop();
        }
    }

    private static Capture flagLighting(Minecraft mc, String label, boolean daylight) {
        var ground = SculptedGroundBlock.source(mc.level, target);
        if (ground == null || !ground.hasFlag()) { throw new IllegalStateException("Planted flag did not synchronize"); }
        var state = ground.getBlockState();
        var model = mc.getBlockRenderer().getBlockModel(state);
        var capture = new Capture();
        mc.getBlockRenderer().getModelRenderer().tesselateWithoutAO(mc.level, model, state, target,
            new PoseStack(), capture, false, RandomSource.create(0), 0, 0,
            mc.level.getModelDataManager().getAt(target), RenderType.solid());
        if (!capture.flag.isEmpty()) { throw new IllegalStateException("Flag must not also appear in the solid terrain mesh"); }
        mc.getBlockEntityRenderDispatcher().render(ground, 0, new PoseStack(), type -> capture);
        var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(ground);
        var bounds = renderer.getRenderBoundingBox(ground);
        double cupY = target.getY() + ground.surface().heightAt(0.5, 0.5);
        if (bounds.minY > cupY || bounds.maxY < cupY + 2) { throw new IllegalStateException("Flag render bounds omit the raised pole"); }
        GolfMod.LOGGER.info("TERRAIN_CLIENT_FLAG {} surface={} poleAndClothSky={} cupSky={} blockLight={}",
            label, ground.surface(), capture.flag, capture.cup, capture.flagBlock);
        if (capture.flag.isEmpty() || capture.cup.isEmpty()
                || capture.flag.stream().anyMatch(light -> daylight ? light != 15 : light >= 15)
                || capture.cup.stream().anyMatch(light -> daylight ? light != 15 : light >= 15)) {
            throw new IllegalStateException("Flag must use " + (daylight ? "exposed daylight" : "shaded light") + " at its visible position: " + capture.flag);
        }
        return capture;
    }

    private static void sandTexture(Minecraft mc, BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof SculptedGroundBlock block) || block.material() != com.ziggleflig.golf.terrain.TerrainMaterial.SAND) {
            throw new IllegalStateException("Sand geometry did not arrive at " + pos);
        }
        var model = mc.getBlockRenderer().getBlockModel(state);
        var quads = model.getQuads(state, null, RandomSource.create(0), mc.level.getModelDataManager().getAt(pos), RenderType.solid());
        var bunkerSand = GolfMod.id("block/bunker_sand");
        if (quads.isEmpty() || quads.stream().anyMatch(quad -> !quad.getSprite().contents().name().equals(bunkerSand))) {
            throw new IllegalStateException("Sand base/cap must render with the editable Bunker Sand sprite at " + pos);
        }
        GolfMod.LOGGER.info("TERRAIN_CLIENT_SAND_TEXTURE pos={} quads={} sprite={}", pos, quads.size(), bunkerSand);
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of(name)); }
    }

    private static void record(Minecraft mc, String label) throws Exception {
        var state = mc.level.getBlockState(target);
        var ground = SculptedGroundBlock.source(mc.level, target);
        int own = mc.level.getBrightness(LightLayer.SKY, target), above = mc.level.getBrightness(LightLayer.SKY, target.above());
        var model = mc.getBlockRenderer().getBlockModel(state);
        var data = mc.level.getModelDataManager().getAt(target);
        var capture = new Capture();
        mc.getBlockRenderer().getModelRenderer().tesselateWithoutAO(mc.level, model, state, target,
            new PoseStack(), capture, false, RandomSource.create(0), 0, 0, data, RenderType.solid());
        var gpu = gpuLights(mc);
        GolfMod.LOGGER.info("TERRAIN_CLIENT {} pos={} surface={} sky={}/{} renderedTop={} gpuTop={}",
            label, target, ground == null ? null : ground.surface(), own, above, capture.top, gpu);
        if (ground == null || own != 15 || above != 15 || capture.top.isEmpty() || gpu.isEmpty()
                || capture.top.stream().anyMatch(light -> light < 15) || gpu.stream().anyMatch(light -> light < 15)) { failed = true; }
    }

    private static List<Integer> gpuLights(Minecraft mc) throws Exception {
        var field = mc.levelRenderer.getClass().getDeclaredField("viewArea"); field.setAccessible(true);
        var view = field.get(mc.levelRenderer);
        var method = view.getClass().getDeclaredMethod("getRenderSectionAt", BlockPos.class); method.setAccessible(true);
        var section = method.invoke(view, target);
        var buffer = (com.mojang.blaze3d.vertex.VertexBuffer) section.getClass().getMethod("getBuffer", RenderType.class).invoke(section, RenderType.solid());
        int previousBuffer = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        var bufferField = buffer.getClass().getDeclaredField("vertexBufferId"); bufferField.setAccessible(true);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferField.getInt(buffer));
        int size = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
        ByteBuffer vertices = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        var result = new ArrayList<Integer>();
        int x = target.getX() & 15, y = target.getY() & 15, z = target.getZ() & 15;
        for (int i = 0; i + 32 <= size; i += 32) {
            float vx = vertices.getFloat(i), vy = vertices.getFloat(i + 4), vz = vertices.getFloat(i + 8);
            if (vx >= x - .00001 && vx <= x + 1.00001 && vz >= z - .00001 && vz <= z + 1.00001
                    && vy > y + .01 && vy < y + 1 && vertices.get(i + 29) > 0) {
                result.add((vertices.getShort(i + 26) & 65535) / 16);
            }
        }
        return result;
    }

    private static final class Capture implements VertexConsumer {
        final List<Integer> top = new ArrayList<>();
        final List<Integer> flag = new ArrayList<>();
        final List<Integer> flagBlock = new ArrayList<>();
        final List<Integer> cup = new ArrayList<>();
        @Override public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] colors, float r, float g, float b,
                float a, int[] light, int overlay, boolean alpha) {
            if (quad.getDirection() == Direction.UP) { for (int packed : light) { top.add(packed >> 20 & 15); } }
            var sprite = quad.getSprite().contents().name();
            if (sprite.equals(GolfMod.id("block/pole")) || sprite.equals(GolfMod.id("block/flag"))) {
                for (int packed : light) { flag.add(packed >> 20 & 15); flagBlock.add(packed >> 4 & 15); }
            }
            if (sprite.equals(GolfMod.id("block/hole"))) { for (int packed : light) { cup.add(packed >> 20 & 15); } }
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
