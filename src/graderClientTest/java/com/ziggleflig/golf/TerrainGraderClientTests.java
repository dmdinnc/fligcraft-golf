package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.client.TerrainGraderOverlay;
import com.ziggleflig.golf.item.TerrainGraderItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainGrading;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = GolfMod.MODID, value = Dist.CLIENT)
public final class TerrainGraderClientTests {
    private static final long STARTED_AT = System.nanoTime();
    private static final BlockPos GAP = new BlockPos(-33, -61, 34);
    private static final BlockPos[] SOURCES = {new BlockPos(-34, -61, 33), new BlockPos(-32, -61, 33), new BlockPos(-32, -61, 35), new BlockPos(-34, -61, 35)};
    private static boolean started, prepared;
    private static int phase, ticks;

    private static ItemStack tool() {
        var stack = new ItemStack(GolfMod.TERRAIN_GRADER.get());
        int[] points = new int[20];
        for (int i = 0; i < 4; i++) {
            var pos = SOURCES[i];
            points[i * 5] = pos.getX(); points[i * 5 + 1] = pos.getY(); points[i * 5 + 2] = pos.getZ();
            points[i * 5 + 3] = i; points[i * 5 + 4] = pos.getY() * 16 + 8;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putIntArray("TerrainGraderPoints", points);
            tag.putString("TerrainGraderDimension", "minecraft:overworld");
            tag.putUUID("TerrainGraderSelection", UUID.randomUUID());
        });
        return stack;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() - STARTED_AT > 180_000_000_000L) { throw new IllegalStateException("Grader client test timed out"); }
            if (!started && mc.screen instanceof AccessibilityOnboardingScreen && mc.getOverlay() == null) { mc.screen.onClose(); return; }
            if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                started = true; mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4);
                var settings = new LevelSettings("Grader preview client test", GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("grader-preview-" + System.currentTimeMillis(), settings,
                    new WorldOptions(0, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
                return;
            }
            if (!started || mc.level == null || mc.player == null) { return; }
            mc.player.setYRot(-90); mc.player.setXRot(40);
            if (!prepared) {
                prepared = true;
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var level = server.overworld();
                    level.setDayTime(6000); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.connection.teleport(-36.5, -59, 34.5, -90, 40);
                    for (int x = -35; x <= -30; x++) for (int z = 32; z <= 37; z++) { level.setBlock(new BlockPos(x, -61, z), GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3); }
                    for (var pos : SOURCES) { SculptedGroundBlock.source(level, pos).setSurface(SculptedSurface.flat(8)); }
                    level.setBlock(GAP, Blocks.AIR.defaultBlockState(), 3);
                    player.getInventory().setItem(0, tool());
                    player.getInventory().setItem(1, new ItemStack(GolfMod.FAIRWAY_ITEM.get(), 64));
                });
            }
            if (phase == 0) {
                if (TerrainGraderItem.anchors(mc.player.getInventory().getItem(0)).size() != 4 || !mc.level.hasChunkAt(GAP)
                        || SculptedGroundBlock.source(mc.level, SOURCES[0]) == null) { return; }
                carry(mc, 0);
                check(!TerrainGraderOverlay.activeTool(mc).isEmpty(), "Equipping the grader must activate its preview");
                var plan = TerrainGrading.plan(mc.level, TerrainGraderItem.anchors(mc.player.getMainHandItem()), TerrainGraderItem.mode(mc.player.getMainHandItem()));
                check(!plan.valid(), "The gap must initially be highlighted");
                carry(mc, 1);
                phase = 1; ticks = 0;
                return;
            }
            if (++ticks < 10) { return; } ticks = 0;
            if (phase == 1) {
                check(mc.player.getMainHandItem().is(GolfMod.FAIRWAY_ITEM.get()) && !TerrainGraderOverlay.activeTool(mc).isEmpty(), "Switching to repair blocks must retain the grader preview");
                screenshot(mc, "grader-preview-with-blocks.png");
                mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    level.setBlock(GAP, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3);
                    SculptedGroundBlock.source(level, GAP).setSurface(SculptedSurface.flat(8));
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var replacement = player.getInventory().getItem(0).copy();
                    CustomData.update(DataComponents.CUSTOM_DATA, replacement, tag -> tag.putBoolean("StackRefresh", true));
                    player.getInventory().setItem(0, replacement);
                });
                phase = 2;
            } else if (phase == 2) {
                var selected = TerrainGraderOverlay.activeTool(mc);
                if (SculptedGroundBlock.source(mc.level, GAP) == null || selected.isEmpty()
                        || !selected.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean("StackRefresh")) { return; }
                var plan = TerrainGrading.plan(mc.level, TerrainGraderItem.anchors(selected), TerrainGraderItem.mode(selected));
                check(plan.valid(), "The retained preview must see repaired terrain and follow server stack replacements");
                screenshot(mc, "grader-preview-repaired.png");
                mc.getSingleplayerServer().execute(() -> TerrainGraderItem.clear(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getInventory().getItem(0)));
                phase = 3;
            } else if (phase == 3) {
                if (!TerrainGraderItem.anchors(mc.player.getInventory().getItem(0)).isEmpty()) { return; }
                check(TerrainGraderOverlay.activeTool(mc).isEmpty(), "Clearing the selected grader must remove the pinned preview");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getInventory().setItem(0, tool()));
                phase = 4;
            } else {
                if (TerrainGraderItem.anchors(mc.player.getInventory().getItem(0)).size() != 4) { return; }
                carry(mc, 0); check(!TerrainGraderOverlay.activeTool(mc).isEmpty(), "A fresh selection must activate");
                carry(mc, 1);
                Vec3 position = mc.player.position();
                mc.player.setPos(position.add(300, 0, 0));
                check(TerrainGraderOverlay.activeTool(mc).isEmpty(), "Leaving the reasonable range must dismiss the pinned preview");
                mc.player.setPos(position);
                check(TerrainGraderOverlay.activeTool(mc).isEmpty(), "Returning with another item must not restore a dismissed preview");
                carry(mc, 0); check(!TerrainGraderOverlay.activeTool(mc).isEmpty(), "Re-equipping the selected grader may reopen its preview");
                carry(mc, 1); mc.player.getInventory().setItem(0, ItemStack.EMPTY);
                check(TerrainGraderOverlay.activeTool(mc).isEmpty(), "Removing the selected tool from inventory must clear its preview");
                Files.writeString(Path.of("grader-preview-result.txt"), "PASS");
                GolfMod.LOGGER.info("GRADER_PREVIEW_CLIENT_RESULT PASS: item switching, live repairs, stack sync, clearing, range, tool removal");
                mc.stop();
            }
        } catch (Throwable error) {
            GolfMod.LOGGER.error("GRADER_PREVIEW_CLIENT_RESULT ERROR", error);
            try { Files.writeString(Path.of("grader-preview-result.txt"), "ERROR"); }
            catch (Exception reportError) { GolfMod.LOGGER.error("Could not write grader test result", reportError); }
            mc.stop();
        }
    }
    private static void carry(Minecraft mc, int slot) {
        mc.player.getInventory().selected = slot;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of(name)); }
    }
    private static void check(boolean passed, String message) { if (!passed) { throw new IllegalStateException(message); } }
}
