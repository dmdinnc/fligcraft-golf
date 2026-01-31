package com.ziggleflig.golf.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class GolfNetwork {
    private static final String VERSION = "1";

    private GolfNetwork() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ShotAccuracyPayload.TYPE, ShotAccuracyPayload.STREAM_CODEC, ShotAccuracyPayload::handle);
    }
}
