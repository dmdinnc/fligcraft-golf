package com.ziggleflig.golf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Only the planted flag is drawn here; terrain retains its static chunk mesh. */
public final class CourseFlagRenderer implements BlockEntityRenderer<SculptedGroundBlockEntity> {
    public CourseFlagRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(SculptedGroundBlockEntity ground, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = ground.getLevel();
        if (level == null || !ground.hasFlag()) { return; }
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(ground.getBlockState());
        if (!(model instanceof SculptedGroundGeometry.Model terrain)) { return; }
        var consumer = buffers.getBuffer(RenderType.solid());
        var origin = ground.getBlockPos();
        var sample = new BlockPos.MutableBlockPos();
        int[] lights = new int[4];
        float[] shades = new float[4];
        for (var quad : terrain.flagQuads(ground.surface())) {
            float shade = level.getShade(quad.getDirection(), quad.isShade());
            int[] vertices = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                int offset = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                // Chunk-model lighting samples the owning solid cell, even for geometry several
                // blocks above it. Sample each flag vertex in world space, just above the surface.
                sample.set(origin.getX() + (double) Float.intBitsToFloat(vertices[offset]),
                    origin.getY() + (double) Float.intBitsToFloat(vertices[offset + 1]) + 0.002,
                    origin.getZ() + (double) Float.intBitsToFloat(vertices[offset + 2]));
                lights[i] = LevelRenderer.getLightColor(level, sample);
                shades[i] = shade;
            }
            consumer.putBulkData(poses.last(), quad, shades, 1, 1, 1, 1, lights, packedOverlay, true);
        }
    }

    @Override public boolean shouldRender(SculptedGroundBlockEntity ground, Vec3 camera) {
        return ground.hasFlag() && BlockEntityRenderer.super.shouldRender(ground, camera);
    }

    // The pole can remain visible when its supporting chunk section is outside the view.
    @Override public boolean shouldRenderOffScreen(SculptedGroundBlockEntity ground) { return ground.hasFlag(); }
    @Override public int getViewDistance() { return 512; }
    @Override public AABB getRenderBoundingBox(SculptedGroundBlockEntity ground) {
        double height = ground.surface().heightAt(0.5, 0.5);
        // Include the lower edge of the cup on a slope as well as the tall pole.
        return new AABB(0, 0, 0, 1, height + 2, 1)
            .move(ground.getBlockPos());
    }
}
