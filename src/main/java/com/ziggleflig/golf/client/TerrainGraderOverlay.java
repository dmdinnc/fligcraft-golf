package com.ziggleflig.golf.client;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.item.TerrainGraderItem;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import com.ziggleflig.golf.terrain.TerrainGrading;
import com.ziggleflig.golf.terrain.TerrainGrading.Anchor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class TerrainGraderOverlay implements LayeredDraw.Layer {
    private static final RenderType LINES = RenderType.create("fligcraft_golf:grader_preview",
        DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
        RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static ClientLevel cachedLevel;
    private static List<Anchor> cachedAnchors = List.of();
    private static TerrainGrading.Mode cachedMode;
    private static TerrainGrading.Plan cachedPlan;
    private static long nextPlanTick;
    private static ClientLevel selectionLevel;
    private static UUID activeSelection;
    public static final double PREVIEW_RANGE = 128;
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        activeSelection = null; selectionLevel = null; cachedLevel = null; cachedPlan = null; cachedAnchors = List.of();
    }

    /** Follow the selected grader in the inventory, including stack replacements from server updates. */
    public static ItemStack activeTool(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || selectionLevel != minecraft.level) {
            activeSelection = null; cachedLevel = null; cachedPlan = null; selectionLevel = minecraft.level;
        }
        if (minecraft.level == null || minecraft.player == null) { return ItemStack.EMPTY; }
        ItemStack held = TerrainGraderItem.heldTool(minecraft.player);
        if (!held.isEmpty() && !TerrainGraderItem.anchors(held).isEmpty()) { activeSelection = TerrainGraderItem.selectionId(held); }
        ItemStack selected = held;
        if (activeSelection != null && !activeSelection.equals(TerrainGraderItem.selectionId(held))) {
            selected = ItemStack.EMPTY;
            for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                var candidate = minecraft.player.getInventory().getItem(slot);
                if (candidate.getItem() instanceof TerrainGraderItem && activeSelection.equals(TerrainGraderItem.selectionId(candidate))) {
                    selected = candidate; break;
                }
            }
        }
        var anchors = TerrainGraderItem.anchors(selected);
        if (selected.isEmpty() || anchors.isEmpty() || !TerrainGraderItem.sameDimension(selected, minecraft.level)
                || !withinRange(anchors, minecraft.player.position())) {
            activeSelection = null; cachedLevel = null; cachedPlan = null;
            return ItemStack.EMPTY;
        }
        return selected;
    }
    private static boolean withinRange(List<Anchor> anchors, Vec3 player) {
        double minX = anchors.stream().mapToInt(Anchor::x).min().orElseThrow(), maxX = anchors.stream().mapToInt(Anchor::x).max().orElseThrow();
        double minZ = anchors.stream().mapToInt(Anchor::z).min().orElseThrow(), maxZ = anchors.stream().mapToInt(Anchor::z).max().orElseThrow();
        double minY = anchors.stream().mapToInt(Anchor::height).min().orElseThrow() / 16.0, maxY = anchors.stream().mapToInt(Anchor::height).max().orElseThrow() / 16.0;
        Vec3 closest = new Vec3(Math.clamp(player.x, minX, maxX), Math.clamp(player.y, minY, maxY), Math.clamp(player.z, minZ, maxZ));
        return player.distanceToSqr(closest) <= PREVIEW_RANGE * PREVIEW_RANGE;
    }

    private static TerrainGrading.Plan preview(Minecraft minecraft, ItemStack tool) {
        var points = TerrainGraderItem.anchors(tool);
        if (minecraft.level == null || !TerrainGraderItem.sameDimension(tool, minecraft.level) || points.size() != 4) {
            cachedLevel = null; cachedPlan = null; return null;
        }
        var mode = TerrainGraderItem.mode(tool);
        long tick = minecraft.level.getGameTime();
        if (cachedLevel != minecraft.level || !points.equals(cachedAnchors) || mode != cachedMode || tick >= nextPlanTick) {
            cachedLevel = minecraft.level; cachedAnchors = points; cachedMode = mode; nextPlanTick = tick + 5;
            cachedPlan = TerrainGrading.plan(minecraft.level, points, mode);
        }
        return cachedPlan;
    }
    @Override public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack tool = activeTool(minecraft);
        if (tool.isEmpty() || minecraft.level == null || minecraft.options.hideGui) { return; }
        var points = TerrainGraderItem.anchors(tool);
        var mode = TerrainGraderItem.mode(tool);
        var plan = preview(minecraft, tool);
        boolean held = TerrainGraderItem.heldTool(minecraft.player) == tool;
        int x = graphics.guiWidth() / 2;
        Component status = Component.translatable("hud.fligcraft_golf.grader.select");
        int color = 0xDDDDDD;
        if (!TerrainGraderItem.sameDimension(tool, minecraft.level)) { status = new TerrainGrading.Problem("dimension", null).message(); color = 0xFF7777; }
        else if (plan != null && !plan.valid()) {
            status = plan.problems().size() == 1 ? plan.problem().message()
                : Component.translatable("hud.fligcraft_golf.grader.problems", plan.problems().size());
            color = 0xFF77FF;
        }
        else if (plan != null) { status = Component.translatable("hud.fligcraft_golf.grader.preview", plan.area().width(),
            plan.area().depth(), plan.edits().size(), plan.area().maxAnchorAdjustment(mode)); color = 0x77FF99; }
        var statusLines = minecraft.font.split(status, graphics.guiWidth() - 24);
        // Keep the pinned preview above item names, action-bar messages, and survival bars.
        int y = held ? graphics.guiHeight() / 2 + 30 : Math.max(8, graphics.guiHeight() - 82 - 12 * (statusLines.size() + 1));
        graphics.drawCenteredString(minecraft.font, held ? Component.translatable("message.fligcraft_golf.grader.settings",
            TerrainGraderItem.modeName(mode), points.size()) : Component.translatable("hud.fligcraft_golf.grader.pinned"), x, y, 0xFFFFFF);
        int textY = y + 12;
        for (var line : statusLines) {
            graphics.drawCenteredString(minecraft.font, line, x, textY, color);
            textY += 12;
        }
        Component swap = minecraft.options.keySwapOffhand.getTranslatedKeyMessage();
        Component sneak = minecraft.options.keyShift.getTranslatedKeyMessage();
        Component use = minecraft.options.keyUse.getTranslatedKeyMessage();
        if (!held) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("hud.fligcraft_golf.grader.pinned_clear", swap), x, textY, 0xDDDDDD);
            return;
        }
        graphics.drawCenteredString(minecraft.font, Component.translatable("hud.fligcraft_golf.grader.edit", use, sneak, use), x, textY, 0xDDDDDD);
        graphics.drawCenteredString(minecraft.font, Component.translatable("hud.fligcraft_golf.grader.controls", swap, sneak, swap), x, textY + 12, 0xDDDDDD);
    }
    /** Persistent preview, including when aiming into the air to apply. */
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) { return; }
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack tool = activeTool(minecraft);
        if (tool.isEmpty() || minecraft.level == null || minecraft.options.hideGui) {
            cachedLevel = null; cachedPlan = null; return;
        }
        if (!TerrainGraderItem.sameDimension(tool, minecraft.level)) { return; }
        var poses = event.getPoseStack();
        var buffer = minecraft.renderBuffers().bufferSource();
        var lines = buffer.getBuffer(LINES);
        Vec3 camera = event.getCamera().getPosition();
        var points = TerrainGraderItem.anchors(tool);
        var mode = TerrainGraderItem.mode(tool);
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            Vec3 relative = new Vec3(point.x(), point.height() / 16.0 + 0.015, point.z()).subtract(camera);
            marker(poses, lines, relative, 1, 0.8f, 0.1f);
        }
        if (TerrainGraderItem.heldTool(minecraft.player) == tool && minecraft.hitResult instanceof BlockHitResult hit) {
            var ground = SculptedGroundBlock.source(minecraft.level, hit.getBlockPos());
            if (ground != null) {
                var pos = ground.getBlockPos();
                var corner = Corner.nearest(hit.getLocation().x - pos.getX(), hit.getLocation().z - pos.getZ());
                marker(poses, lines, new Vec3(pos.getX() + corner.x, pos.getY() + ground.surface().height(corner) / 16.0 + 0.015,
                    pos.getZ() + corner.z).subtract(camera), 1, 1, 1);
            }
        }
        var plan = preview(minecraft, tool);
        if (plan != null && plan.area() != null) {
            var rectangle = plan.area();
            boolean valid = plan.valid();
            for (int x = rectangle.minX(); x <= rectangle.maxX(); x++) {
                for (int z = rectangle.minZ(); z <= rectangle.maxZ(); z++) {
                    if (!rectangle.contains(x, z)) { continue; }
                    Vec3 a = point(rectangle, mode, x, z, camera);
                    if (x < rectangle.maxX() && rectangle.contains(x + 1, z)) { line(poses.last(), lines, a, point(rectangle, mode, x + 1, z, camera), valid ? 0.2f : 1, valid ? 1 : 0.25f, valid ? 0.5f : 0.25f); }
                    if (z < rectangle.maxZ() && rectangle.contains(x, z + 1)) { line(poses.last(), lines, a, point(rectangle, mode, x, z + 1, camera), valid ? 0.2f : 1, valid ? 1 : 0.25f, valid ? 0.5f : 0.25f); }
                    if (x < rectangle.maxX() && z < rectangle.maxZ() && rectangle.contains(x + 1, z + 1)) {
                        line(poses.last(), lines, a, point(rectangle, mode, x + 1, z + 1, camera), valid ? 0.15f : 0.7f, valid ? 0.65f : 0.2f, valid ? 0.35f : 0.2f);
                    }
                }
            }
            var boundary = rectangle.boundary();
            for (int i = 0; i < boundary.size(); i++) {
                var a = boundary.get(i); var b = boundary.get((i + 1) % boundary.size());
                line(poses.last(), lines, point(rectangle, mode, a.x(), a.z(), camera), point(rectangle, mode, b.x(), b.z(), camera),
                    valid ? 0.2f : 1, valid ? 1 : 0.25f, valid ? 0.5f : 0.25f);
            }
            for (var edit : plan.edits()) {
                var pos = edit.pos();
                if (rectangle.contains(pos.getX(), pos.getZ()) && rectangle.contains(pos.getX() + 1, pos.getZ())
                        && rectangle.contains(pos.getX() + 1, pos.getZ() + 1) && rectangle.contains(pos.getX(), pos.getZ() + 1)) { continue; }
                Corner[] corners = Corner.values();
                for (int i = 0; i < 4; i++) {
                    Corner a = corners[i], b = corners[(i + 1) % 4];
                    line(poses.last(), lines, new Vec3(pos.getX() + a.x, pos.getY() + edit.after().height(a) / 16.0 + 0.01, pos.getZ() + a.z).subtract(camera),
                        new Vec3(pos.getX() + b.x, pos.getY() + edit.after().height(b) / 16.0 + 0.01, pos.getZ() + b.z).subtract(camera), 0.3f, 0.65f, 1);
                }
            }
            float pulse = 0.65f + 0.35f * (float) Math.sin(minecraft.level.getGameTime() * 0.3);
            for (var problem : plan.problems()) {
                if (problem.pos() == null) { continue; }
                float r = 1, g = problem.key().equals("blocked") ? 0.4f : 0.05f;
                float b = problem.key().equals("range") ? pulse : 0.05f;
                LevelRenderer.renderLineBox(poses, lines, new AABB(problem.pos()).inflate(0.012).move(camera.scale(-1)), r, g, b, 1);
                if (problem.key().equals("range")) {
                    var ground = SculptedGroundBlock.source(minecraft.level, problem.pos());
                    if (ground != null) {
                        Vec3 nw = new Vec3(problem.pos().getX(), problem.pos().getY() + ground.surface().northWest() / 16.0 + 0.02, problem.pos().getZ()).subtract(camera);
                        Vec3 ne = new Vec3(problem.pos().getX() + 1, problem.pos().getY() + ground.surface().northEast() / 16.0 + 0.02, problem.pos().getZ()).subtract(camera);
                        Vec3 se = new Vec3(problem.pos().getX() + 1, problem.pos().getY() + ground.surface().southEast() / 16.0 + 0.02, problem.pos().getZ() + 1).subtract(camera);
                        Vec3 sw = new Vec3(problem.pos().getX(), problem.pos().getY() + ground.surface().southWest() / 16.0 + 0.02, problem.pos().getZ() + 1).subtract(camera);
                        line(poses.last(), lines, nw, se, r, g, b); line(poses.last(), lines, ne, sw, r, g, b);
                    }
                }
            }
        }
        buffer.endBatch(LINES);
        for (int i = 0; i < points.size(); i++) {
            var anchor = points.get(i);
            poses.pushPose();
            poses.translate(anchor.x() - camera.x, anchor.height() / 16.0 + 0.2 - camera.y, anchor.z() - camera.z);
            poses.mulPose(event.getCamera().rotation());
            poses.scale(-0.025f, -0.025f, 0.025f);
            String label = Integer.toString(i + 1);
            minecraft.font.drawInBatch(label, -minecraft.font.width(label) / 2.0f, 0, 0xFFFFCC55, false, poses.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0, 15728880);
            poses.popPose();
        }
        buffer.endBatch();
    }
    private static Vec3 point(com.ziggleflig.golf.terrain.GradingArea rectangle, TerrainGrading.Mode mode, int x, int z, Vec3 camera) {
        return new Vec3(x, rectangle.height(mode, x, z) / 16.0 + 0.01, z).subtract(camera);
    }
    private static void marker(PoseStack poses, VertexConsumer lines, Vec3 center, float r, float g, float b) {
        LevelRenderer.renderLineBox(poses, lines, new AABB(center.x - 0.06, center.y - 0.04, center.z - 0.06,
            center.x + 0.06, center.y + 0.04, center.z + 0.06), r, g, b, 1);
    }
    private static void line(PoseStack.Pose pose, VertexConsumer consumer, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 normal = b.subtract(a).normalize();
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, blue, 1).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, blue, 1).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
