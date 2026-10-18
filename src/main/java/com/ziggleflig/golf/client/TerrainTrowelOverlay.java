package com.ziggleflig.golf.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.item.TerrainTrowelItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

public class TerrainTrowelOverlay implements LayeredDraw.Layer {
    // Lowering previews lie inside the existing solid terrain; draw them through the tile without writing depth.
    private static final RenderType PREVIEW_LINES = RenderType.create("fligcraft_golf:trowel_preview",
        DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
        RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
            .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));

    private static ItemStack tool(Minecraft minecraft) {
        return TerrainTrowelItem.heldTool(minecraft.player);
    }

    @Override public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack tool = tool(minecraft);
        if (tool.isEmpty() || minecraft.level == null || minecraft.options.hideGui) { return; }
        Component label = Component.translatable("message.fligcraft_golf.trowel.settings",
            TerrainTrowelItem.modeName(TerrainTrowelItem.mode(tool)), TerrainTrowelItem.step(tool));
        if (minecraft.hitResult instanceof BlockHitResult hit && SculptedGroundBlock.source(minecraft.level, hit.getBlockPos()) != null) {
            var ground = SculptedGroundBlock.source(minecraft.level, hit.getBlockPos());
            Corner corner = Corner.nearest(hit.getLocation().x - hit.getBlockPos().getX(), hit.getLocation().z - hit.getBlockPos().getZ());
            label = Component.translatable("hud.fligcraft_golf.trowel.target", TerrainTrowelItem.modeName(TerrainTrowelItem.mode(tool)),
                TerrainTrowelItem.step(tool),
                Component.translatable("corner.fligcraft_golf." + corner.key()), ground.surface().height(corner),
                TerrainTrowelItem.targetHeight(tool, ground.surface(), corner, minecraft.player.isShiftKeyDown()));
        }
        int x = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 + 30;
        graphics.drawCenteredString(minecraft.font, label, x, y, 0xFFFFFF);
        Component swap = minecraft.options.keySwapOffhand.getTranslatedKeyMessage();
        Component sneak = minecraft.options.keyShift.getTranslatedKeyMessage();
        Component use = minecraft.options.keyUse.getTranslatedKeyMessage();
        graphics.drawCenteredString(minecraft.font, Component.translatable("hud.fligcraft_golf.trowel.edit", use, sneak, use), x, y + 12, 0xDDDDDD);
        graphics.drawCenteredString(minecraft.font, Component.translatable("hud.fligcraft_golf.trowel.controls", swap, sneak, swap), x, y + 24, 0xDDDDDD);
    }

    public static void highlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack tool = tool(minecraft);
        var hit = event.getTarget();
        if (tool.isEmpty() || minecraft.level == null) { return; }
        var ground = SculptedGroundBlock.source(minecraft.level, hit.getBlockPos());
        if (ground == null) { return; }
        event.setCanceled(true);
        Corner selected = Corner.nearest(hit.getLocation().x - hit.getBlockPos().getX(), hit.getLocation().z - hit.getBlockPos().getZ());
        int nextHeight = TerrainTrowelItem.targetHeight(tool, ground.surface(), selected, minecraft.player.isShiftKeyDown());
        Vec3 camera = event.getCamera().getPosition();
        TerrainTrowelItem.Mode mode = TerrainTrowelItem.mode(tool);
        var plan = TerrainTrowelItem.plan(minecraft.level, tool, ground.getBlockPos(), selected, nextHeight);
        for (var edit : plan.edits()) {
            highlightTile(event, edit.ground(), edit.corner(), edit.surface().height(edit.corner()), mode, camera);
        }
    }

    private static void highlightTile(RenderHighlightEvent.Block event, SculptedGroundBlockEntity ground, Corner selected,
            int nextHeight, TerrainTrowelItem.Mode mode, Vec3 camera) {
        var poses = event.getPoseStack();
        BlockPos pos = ground.getBlockPos();
        poses.pushPose();
        poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(PREVIEW_LINES);
        SculptedSurface surface = ground.surface();
        SculptedSurface preview = mode == TerrainTrowelItem.Mode.LEVEL ? SculptedSurface.flat(nextHeight) : surface.withCorner(selected, nextHeight);
        Corner[] corners = Corner.values();
        for (int i = 0; i < corners.length; i++) {
            Corner a = corners[i], b = corners[(i + 1) % 4];
            line(poses.last(), lines, top(surface, a), top(surface, b), 1, 1, 1);
            if (!preview.equals(surface)) {
                line(poses.last(), lines, top(preview, a), top(preview, b), 0.2f, 1, 0.5f);
            }
            if (a == selected || mode == TerrainTrowelItem.Mode.LEVEL) {
                double current = surface.height(a) / 16.0;
                LevelRenderer.renderLineBox(poses, lines, new AABB(a.x - 0.035, current - 0.025, a.z - 0.035,
                    a.x + 0.035, current + 0.025, a.z + 0.035), 1, 0.85f, 0.1f, 1);
                double next = nextHeight / 16.0;
                LevelRenderer.renderLineBox(poses, lines, new AABB(a.x - 0.045, next - 0.02, a.z - 0.045,
                    a.x + 0.045, next + 0.02, a.z + 0.045), 0.2f, 1, 0.5f, 1);
            }
        }
        // Make the fixed triangulation visible when a tile has a crease, including in the preview.
        line(poses.last(), lines, top(surface, Corner.NORTH_WEST), top(surface, Corner.SOUTH_EAST), 0.65f, 0.65f, 0.65f);
        if (!preview.equals(surface)) {
            line(poses.last(), lines, top(preview, Corner.NORTH_WEST), top(preview, Corner.SOUTH_EAST), 0.2f, 1, 0.5f);
        }
        poses.popPose();
    }

    private static Vec3 top(SculptedSurface surface, Corner corner) {
        return new Vec3(corner.x, surface.height(corner) / 16.0 + 0.004, corner.z);
    }

    private static void line(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer consumer, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 normal = b.subtract(a).normalize();
        consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, blue, 1)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, blue, 1)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
