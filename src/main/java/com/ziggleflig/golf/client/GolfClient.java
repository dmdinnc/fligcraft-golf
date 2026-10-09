package com.ziggleflig.golf.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public class GolfClient {
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0
            ? 0xFF000000 | com.ziggleflig.golf.item.GolfBallItem.getColor(stack).getTextureDiffuseColor() : -1,
            com.ziggleflig.golf.GolfMod.GOLF_BALL.get());
    }
    
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, 
            com.ziggleflig.golf.GolfMod.id("charge_bar"), 
            new GolfChargeOverlay());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, 
            com.ziggleflig.golf.GolfMod.id("accuracy_slider"), 
            new GolfAccuracyOverlay());
    }
}
