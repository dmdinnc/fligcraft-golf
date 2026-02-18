package com.ziggleflig.golf;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GolfModConfig {
    public static final ModConfigSpec SPEC;
    public static final Common COMMON;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        COMMON = new Common(builder);
        SPEC = builder.build();
    }

    public static final class Common {
        public final ModConfigSpec.IntValue windUpdateSeconds;
        public final ModConfigSpec.BooleanValue returnBallFromWater;
        public final ModConfigSpec.IntValue accuracyMeterCycleMs;

        private Common(ModConfigSpec.Builder builder) {
            builder.push("wind");
            windUpdateSeconds = builder
                .comment("How often wind recalculates, in seconds.")
                .defineInRange("windUpdateSeconds", 15, 1, 300);
            builder.pop();

            builder.push("ball");
            returnBallFromWater = builder
                .comment("Return the ball to its last hit position after it stops in water.")
                .define("returnBallFromWater", true);
            builder.pop();

            builder.push("accuracy");
            accuracyMeterCycleMs = builder
                .comment("Milliseconds for a full accuracy meter cycle (lower = faster).")
                .defineInRange("accuracyMeterCycleMs", 1800, 500, 6000);
            builder.pop();
        }
    }

    private GolfModConfig() {
    }
}
