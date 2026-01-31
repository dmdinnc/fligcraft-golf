package com.ziggleflig.golf.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public class GolfClient {
    
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
