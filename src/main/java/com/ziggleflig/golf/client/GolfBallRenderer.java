package com.ziggleflig.golf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class GolfBallRenderer extends EntityRenderer<GolfBallEntity> {
    
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            GolfMod.id("golf_ball"), "main");
    
    private static final ResourceLocation TEXTURE = GolfMod.id("textures/entity/golf_ball.png");
    
    private final GolfBallModel model;

    public GolfBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new GolfBallModel(context.bakeLayer(LAYER_LOCATION));
    }

    @Override
    public void render(GolfBallEntity entity, float entityYaw, float partialTicks, 
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        poseStack.scale(0.25F, 0.25F, 0.25F);

        double speed = entity.getDeltaMovement().length();
        if (speed > 0.01) {
            float rotation = (entity.tickCount + partialTicks) * (float)speed * 50.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(rotation));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 0.7F));
        }
        
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, 
                                  OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GolfBallEntity entity) {
        return TEXTURE;
    }

    public static class GolfBallModel extends EntityModel<GolfBallEntity> {
        private final ModelPart ball;

        public GolfBallModel(ModelPart root) {
            this.ball = root.getChild("ball");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition meshDefinition = new MeshDefinition();
            PartDefinition partDefinition = meshDefinition.getRoot();

            partDefinition.addOrReplaceChild("ball", 
                CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);

            return LayerDefinition.create(meshDefinition, 32, 32);
        }

        @Override
        public void setupAnim(GolfBallEntity entity, float limbSwing, float limbSwingAmount, 
                             float ageInTicks, float netHeadYaw, float headPitch) {
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, 
                                   int packedOverlay, int color) {
            ball.render(poseStack, buffer, packedLight, packedOverlay, color);
        }
    }
}
