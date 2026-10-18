package com.ziggleflig.golf.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GolfClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FLIGHT_GUIDES;
    static {
        var builder = new ModConfigSpec.Builder();
        FLIGHT_GUIDES = builder.comment("Show a short colored trail and a distant marker for moving shots. Does not affect ball physics.")
            .define("flightGuides", true);
        SPEC = builder.build();
    }
    private GolfClientConfig() { }
}
