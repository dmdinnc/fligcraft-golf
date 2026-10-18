package com.ziggleflig.golf.terrain;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Impact grip, rolling drag, rebound, and static slope grip describe different contact phases. */
public enum TerrainMaterial implements StringRepresentable {
    FAIRWAY("fairway", 0.90, 0.40, 0.002, 0.25, 0.98),
    ROUGH("rough", 0.70, 0.40, 0.006, 0.50, 0.85),
    GREEN("putting_green", 0.93, 0.55, 0.00045, 0.175, 0.98),
    SAND("sand", 0.50, 0.0, 0.008, 0.60, 0.50);

    public static final Codec<TerrainMaterial> CODEC = StringRepresentable.fromEnum(TerrainMaterial::values);
    private final String name;
    private final double rollingRetention;
    private final double restitution;
    private final double rollingResistance;
    private final double staticFriction;
    private final double impactRetention;

    TerrainMaterial(String name, double rollingRetention, double restitution, double rollingResistance, double staticFriction, double impactRetention) {
        this.name = name;
        this.rollingRetention = rollingRetention;
        this.restitution = restitution;
        this.rollingResistance = rollingResistance;
        this.staticFriction = staticFriction;
        this.impactRetention = impactRetention;
    }

    @Override public String getSerializedName() { return name; }
    public double rollingRetention() { return rollingRetention; }
    public double restitution() { return restitution; }
    public double rollingResistance() { return rollingResistance; }
    public double staticFriction() { return staticFriction; }
    public double impactRetention() { return impactRetention; }
    public double shotPower() {
        return switch (this) {
            case ROUGH -> 0.85;
            case SAND -> 0.5;
            default -> 1.0;
        };
    }
}
