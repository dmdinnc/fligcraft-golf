package com.ziggleflig.golf;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GolfWind {
    private static final double MAX_WIND_MPH = 12.0D;
    private static final double MPH_TO_BLOCKS_PER_TICK = 0.44704D / 20.0D;
    private static final double SPEED_CYCLE_TICKS = 9000.0D;
    private static final double DIRECTION_CYCLE_TICKS = 16000.0D;
    private static final double DIRECTION_WOBBLE_TICKS = 5000.0D;
    private static final double DIRECTION_WOBBLE_RADIANS = Math.toRadians(25.0D);

    private GolfWind() {
    }

    public static double getWindSpeedMph(Level level) {
        double time = getSampledTime(level);
        double phase = getPhase(level);
        double cycle = (time / SPEED_CYCLE_TICKS) * (Math.PI * 2.0D);
        double speed = (MAX_WIND_MPH / 2.0D) * (1.0D + Math.sin(cycle + phase));
        return Math.max(0.0D, Math.min(MAX_WIND_MPH, speed));
    }

    public static Vec3 getWindDirection(Level level) {
        double time = getSampledTime(level);
        double phase = getPhase(level);
        double base = (time / DIRECTION_CYCLE_TICKS) * (Math.PI * 2.0D);
        double wobble = Math.sin((time / DIRECTION_WOBBLE_TICKS) * (Math.PI * 2.0D) + phase * 0.7D) * DIRECTION_WOBBLE_RADIANS;
        double angle = base + wobble + phase;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).normalize();
    }

    public static Vec3 getWindVelocity(Level level) {
        double speedBlocksPerTick = getWindSpeedMph(level) * MPH_TO_BLOCKS_PER_TICK;
        return getWindDirection(level).scale(speedBlocksPerTick);
    }

    public static String getWindDirectionText(Level level) {
        Vec3 direction = getWindDirection(level);
        return getCardinalDirection(direction.x, direction.z);
    }

    public static String getCardinalDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D;
        if (angle < 0.0D) {
            angle += 360.0D;
        }

        if (angle >= 337.5D || angle < 22.5D) return "South";
        if (angle >= 22.5D && angle < 67.5D) return "Southwest";
        if (angle >= 67.5D && angle < 112.5D) return "West";
        if (angle >= 112.5D && angle < 157.5D) return "Northwest";
        if (angle >= 157.5D && angle < 202.5D) return "North";
        if (angle >= 202.5D && angle < 247.5D) return "Northeast";
        if (angle >= 247.5D && angle < 292.5D) return "East";
        if (angle >= 292.5D && angle < 337.5D) return "Southeast";
        return "Unknown";
    }

    private static double getPhase(Level level) {
        int hash = level.dimension().location().hashCode();
        return (hash % 360) * (Math.PI / 180.0D);
    }

    private static double getSampledTime(Level level) {
        long time = level.getGameTime();
        long updateTicks = Math.max(1L, 20L * GolfModConfig.COMMON.windUpdateSeconds.get());
        long snapped = (time / updateTicks) * updateTicks;
        return (double) snapped;
    }
}
