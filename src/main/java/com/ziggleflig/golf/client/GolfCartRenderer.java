package com.ziggleflig.golf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfCartEntity;
import com.ziggleflig.golf.entity.GolfCartModel;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class GolfCartRenderer extends EntityRenderer<GolfCartEntity> {
    private static final ResourceLocation TEXTURE = GolfMod.id("textures/block/golf_cart.png");

    private final GolfCartModel model;

    public GolfCartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new GolfCartModel(context.bakeLayer(GolfCartModel.LAYER_LOCATION));
    }

    @Override
    public void render(GolfCartEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 1.5D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GolfCartEntity entity) {
        return TEXTURE;
    }
}
