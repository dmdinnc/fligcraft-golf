package com.ziggleflig.golf.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ziggleflig.golf.item.GolfClubItem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GolfAccuracyOverlay implements LayeredDraw.Layer {

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        
        if (player == null) {
            return;
        }
        
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof GolfClubItem)) {
            return;
        }

        GolfClubItem.ShotData shotData = GolfClubItem.getPendingShot(player);
        if (shotData == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long startTime = shotData.startTime();
        long elapsed = currentTime - startTime;

        float sliderPos = calculateSliderPosition(elapsed);

        renderAccuracySlider(guiGraphics, mc, sliderPos, elapsed);
    }
    
    private float calculateSliderPosition(long elapsedMs) {
        float cycleTime = (elapsedMs % 2000) / 2000.0f;
        if (cycleTime < 0.5f) {
            return (cycleTime * 4.0f) - 1.0f;
        } else {
            return 3.0f - (cycleTime * 4.0f);
        }
    }
    
    private void renderAccuracySlider(GuiGraphics guiGraphics, Minecraft mc, float sliderPos, long elapsed) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 + 60;

        int sliderWidth = 200;
        int sliderHeight = 10;
        int sliderX = centerX - sliderWidth / 2;
        int sliderY = centerY;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        guiGraphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderHeight, 0x80000000);

        int centerLineX = centerX - 1;
        guiGraphics.fill(centerLineX, sliderY - 5, centerLineX + 2, sliderY + sliderHeight + 5, 0xFF00FF00);

        int indicatorX = centerX + (int)(sliderPos * (sliderWidth / 2));
        int indicatorSize = 6;

        float distanceFromCenter = Math.abs(sliderPos);
        int color;
        if (elapsed > 6000) {
            color = 0xFFFF0000;
        } else if (distanceFromCenter < 0.2f) {
            color = 0xFF00FF00;
        } else if (distanceFromCenter < 0.5f) {
            color = 0xFFFFFF00;
        } else {
            color = 0xFFFF0000;
        }
        
        guiGraphics.fill(
            indicatorX - indicatorSize / 2,
            sliderY - 2,
            indicatorX + indicatorSize / 2,
            sliderY + sliderHeight + 2,
            color
        );
        
        String text = elapsed > 6000 ? "TOO LATE!" : "Left-click to shoot!";
        int textColor = elapsed > 6000 ? 0xFFFF0000 : 0xFFFFFFFF;
        guiGraphics.drawCenteredString(
            mc.font,
            text,
            centerX,
            sliderY - 15,
            textColor
        );
        
        RenderSystem.disableBlend();
    }
}
