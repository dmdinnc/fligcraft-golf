package com.ziggleflig.golf.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ziggleflig.golf.entity.GolfBallEntity;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class GolfBallFlightGuide {
    private static RenderType trailType(String name, double width) {
        return RenderType.create("fligcraft_golf:" + name, DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 1536, RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    }
    private static final RenderType TRAIL_OUTLINE = trailType("ball_trail_outline", 5);
    private static final RenderType TRAIL_BORDER = trailType("ball_trail_border", 3.5);
    private static final RenderType TRAIL_COLOR = trailType("ball_trail_color", 2);

    public static void render(GolfBallEntity ball, float partialTick, PoseStack poses, MultiBufferSource buffers) {
        if (!GolfClientConfig.FLIGHT_GUIDES.get()) { return; }
        float time = ball.tickCount + partialTick;
        float alpha = ball.flightTrail().opacity(time);
        if (alpha <= 0) { return; }
        Vec3 head = new Vec3(Mth.lerp(partialTick, ball.xo, ball.getX()), Mth.lerp(partialTick, ball.yo, ball.getY()),
            Mth.lerp(partialTick, ball.zo, ball.getZ()));
        int color = ball.getColor().getTextureDiffuseColor();
        drawTrail(ball, time, head, poses, buffers.getBuffer(TRAIL_OUTLINE), 0x101010, alpha * 0.5f);
        drawTrail(ball, time, head, poses, buffers.getBuffer(TRAIL_BORDER), 0xFFFFFF, alpha * 0.7f);
        drawTrail(ball, time, head, poses, buffers.getBuffer(TRAIL_COLOR), color, alpha);

        var minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        double distance = head.distanceTo(dispatcher.camera.getPosition());
        float markerAlpha = alpha * (float) Math.clamp((distance - 24) / 16, 0, 1);
        if (markerAlpha <= 0) { return; }
        poses.pushPose();
        poses.translate(0, ball.getBbWidth() / 2.0, 0);
        poses.mulPose(dispatcher.cameraOrientation());
        // This is a small screen-facing guide; the physical model and hitbox retain their real size.
        poses.translate(0, 0, ball.getBbWidth() / 2.0 + 0.01);
        // About seven screen pixels across at the configured FOV, including on smaller windows.
        float radius = (float) (distance * 7 * Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2)
            / Math.max(1, minecraft.getWindow().getHeight()));
        VertexConsumer marker = buffers.getBuffer(RenderType.debugStructureQuads());
        diamond(poses.last(), marker, radius, 0x101010, markerAlpha);
        diamond(poses.last(), marker, radius * 0.72f, 0xFFFFFF, markerAlpha);
        diamond(poses.last(), marker, radius * 0.42f, color, markerAlpha);
        poses.popPose();
    }
    private static void drawTrail(GolfBallEntity ball, float time, Vec3 head, PoseStack poses, VertexConsumer lines, int color, float opacity) {
        Vec3 previous = null;
        float previousAlpha = 0;
        for (var sample : ball.flightTrail().samples()) {
            if (sample.tick() >= ball.tickCount) { break; } // The head follows the same interpolation as the ball model.
            float alpha = opacity * Math.clamp(1 - (time - sample.tick()) / 20, 0, 1);
            Vec3 point = sample.position().subtract(head).add(0, ball.getBbWidth() / 2.0, 0);
            if (previous != null) { line(poses.last(), lines, previous, point, color, previousAlpha, alpha); }
            previous = point;
            previousAlpha = alpha;
        }
        if (previous != null) { line(poses.last(), lines, previous, new Vec3(0, ball.getBbWidth() / 2.0, 0), color, previousAlpha, opacity); }
    }
    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vec3 a, Vec3 b, int color, float aAlpha, float bAlpha) {
        Vec3 direction = b.subtract(a).normalize();
        if (direction.lengthSqr() == 0) { return; }
        vertex(pose, lines, a, direction, color, aAlpha);
        vertex(pose, lines, b, direction, color, bAlpha);
    }
    private static void vertex(PoseStack.Pose pose, VertexConsumer lines, Vec3 point, Vec3 normal, int color, float alpha) {
        lines.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
            .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, (int) (alpha * 255))
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
    private static void diamond(PoseStack.Pose pose, VertexConsumer quads, float radius, int color, float alpha) {
        for (float[] point : new float[][] {{0, radius}, {-radius, 0}, {0, -radius}, {radius, 0}}) {
            quads.addVertex(pose, point[0], point[1], 0).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, (int) (alpha * 255));
        }
    }
    private GolfBallFlightGuide() { }
}
