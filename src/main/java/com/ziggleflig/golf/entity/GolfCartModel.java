package com.ziggleflig.golf.entity;

import com.ziggleflig.golf.GolfMod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class GolfCartModel extends EntityModel<GolfCartEntity> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(GolfMod.id("golf_cart"), "main");
	private final ModelPart base;
	private final ModelPart wheels;
	private final ModelPart front;
	private final ModelPart roofassembly;

	public GolfCartModel(ModelPart root) {
		this.base = root.getChild("base");
		this.wheels = root.getChild("wheels");
		this.front = root.getChild("front");
		this.roofassembly = root.getChild("roofassembly");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition base = partdefinition.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-20.0F, -4.0F, -12.0F, 40.0F, 1.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(6, 31).addBox(-4.0F, -9.0F, -12.0F, 14.0F, 5.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(5, 84).addBox(10.0F, -22.0F, -12.0F, 2.0F, 18.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition wheels = partdefinition.addOrReplaceChild("wheels", CubeListBuilder.create().texOffs(0, 60).addBox(-18.0F, -6.0F, 12.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(14, 60).addBox(-18.0F, -6.0F, -14.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(28, 60).addBox(13.0F, -6.0F, -14.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(42, 60).addBox(13.0F, -6.0F, 12.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition front = partdefinition.addOrReplaceChild("front", CubeListBuilder.create().texOffs(47, 68).addBox(-16.0F, -16.0F, -12.0F, 8.0F, 12.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition slant_r1 = front.addOrReplaceChild("slant_r1", CubeListBuilder.create().texOffs(65, 78).addBox(0.0F, -13.0F, -1.0F, 1.0F, 13.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-20.0F, -4.0F, -11.0F, 0.0F, 0.0F, 0.3054F));

		PartDefinition roofassembly = partdefinition.addOrReplaceChild("roofassembly", CubeListBuilder.create().texOffs(2, 0).addBox(-6.0F, -38.0F, -12.0F, 23.0F, 2.0F, 24.0F, new CubeDeformation(0.0F))
		.texOffs(74, 0).addBox(14.0F, -37.0F, -11.0F, 2.0F, 33.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(74, 0).addBox(14.0F, -37.0F, 9.0F, 2.0F, 33.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition FLpillar_r1 = roofassembly.addOrReplaceChild("FLpillar_r1", CubeListBuilder.create().texOffs(74, 0).addBox(-1.0F, -24.0F, -1.0F, 2.0F, 24.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(74, 0).addBox(-1.0F, -24.0F, 19.0F, 2.0F, 24.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-12.0F, -14.0F, -10.0F, 0.0F, 0.0F, 0.3054F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(GolfCartEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		base.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		wheels.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		front.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		roofassembly.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}