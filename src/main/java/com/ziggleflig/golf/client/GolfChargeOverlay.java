package com.ziggleflig.golf.client;

import java.util.Comparator;
import java.util.List;

import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfClubItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GolfChargeOverlay implements LayeredDraw.Layer {

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        
        if (player == null || !player.isUsingItem()) {
            return;
        }

        ItemStack activeStack = player.getUseItem();
        if (!(activeStack.getItem() instanceof GolfClubItem)) {
            return;
        }

        Vec3 look = player.getLookAngle().normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(look.scale(4.0D));
        AABB searchBox = new AABB(eye, reach).inflate(0.75D);
        
        List<GolfBallEntity> balls = player.level().getEntitiesOfClass(GolfBallEntity.class, searchBox, Entity::isAlive);
        if (balls.isEmpty()) {
            return;
        }

        GolfBallEntity target = balls.stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(player)))
                .orElse(null);
                
        if (target == null) {
            return;
        }

        int useTime = player.getTicksUsingItem();
        float charge = Math.min(1.0F, useTime / 60.0F);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int barHeight = 100;
        int baseX = screenWidth / 2 + (screenWidth / 4);
        int barY = (screenHeight - barHeight) / 2;
        
        int filledHeight = (int)(barHeight * charge);
        int color = getChargeColor(charge);
        
        for (int y = 0; y < barHeight; y++) {
            float progress = (float)y / barHeight;
            int sliceWidth = (int)(4 + progress * progress * 11);
            double curveAmount = Math.sin(progress * Math.PI);
            int curveOffset = (int)(curveAmount * 15);
            int sliceX = baseX + curveOffset;

            if (y < filledHeight) {
                guiGraphics.fill(sliceX, barY + barHeight - y - 1, sliceX + sliceWidth, barY + barHeight - y, color);
            } else {
                guiGraphics.fill(sliceX, barY + barHeight - y - 1, sliceX + sliceWidth, barY + barHeight - y, 0xFF555555);
            }
        }

        for (int y = 0; y < barHeight; y++) {
            float progress = (float)y / barHeight;
            int sliceWidth = (int)(4 + progress * progress * 11);
            double curveAmount = Math.sin(progress * Math.PI);
            int curveOffset = (int)(curveAmount * 15);
            int sliceX = baseX + curveOffset;

            guiGraphics.fill(sliceX - 1, barY + barHeight - y - 1, sliceX, barY + barHeight - y, 0xFF000000);
            guiGraphics.fill(sliceX + sliceWidth, barY + barHeight - y - 1, sliceX + sliceWidth + 1, barY + barHeight - y, 0xFF000000);
        }

        int bottomWidth = 4;
        guiGraphics.fill(baseX, barY + barHeight - 1, baseX + bottomWidth, barY + barHeight, 0xFF000000);

        float topProgress = (float)(barHeight - 1) / barHeight;
        int topWidth = (int)(4 + topProgress * topProgress * 11);
        double topCurveAmount = Math.sin(topProgress * Math.PI);
        int topCurveOffset = (int)(topCurveAmount * 15);
        int topX = baseX + topCurveOffset;
        guiGraphics.fill(topX, barY, topX + topWidth, barY + 1, 0xFF000000);
    }

    private int getChargeColor(float charge) {
        if (charge < 0.33F) {
            return 0xFF00FF00;
        } else if (charge < 0.66F) {
            return 0xFFFFFF00;
        } else {
            return 0xFFFF0000;
        }
    }
}
