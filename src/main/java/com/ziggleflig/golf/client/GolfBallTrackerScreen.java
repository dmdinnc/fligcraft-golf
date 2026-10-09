package com.ziggleflig.golf.client;

import com.ziggleflig.golf.inventory.GolfBallTrackerMenu;
import com.ziggleflig.golf.network.TrackerActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

public class GolfBallTrackerScreen extends AbstractContainerScreen<GolfBallTrackerMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public GolfBallTrackerScreen(GolfBallTrackerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 133;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 126);
        graphics.blit(BACKGROUND, leftPos, topPos + 126, 0, 215, imageWidth, 7);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && minecraft.gameMode != null) {
            for (Slot slot : menu.slots) {
                if (mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16
                        || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) { continue; }
                var tag = slot.getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.hasUUID(GolfBallTrackerMenu.BALL_ID)) {
                    PacketDistributor.sendToServer(new TrackerActionPayload(menu.containerId,
                        tag.getUUID(GolfBallTrackerMenu.BALL_ID), button == 1));
                } else if (button == 0 && (slot.index == GolfBallTrackerMenu.PREVIOUS
                        || slot.index == GolfBallTrackerMenu.NEXT || slot.index == GolfBallTrackerMenu.REFRESH)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot.index);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
