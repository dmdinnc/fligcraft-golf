package com.ziggleflig.golf.terrain;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Course materials are authoritative; ordinary blocks retain their existing surface behavior. */
public final class GolfSurfacePhysics {
    public static SculptedBallPhysics.Response response(BlockState state) {
        if (state.getBlock() instanceof SculptedGroundBlock ground) {
            var material = ground.material();
            return new SculptedBallPhysics.Response(material.rollingRetention(), material.restitution(),
                material.rollingResistance(), material.impactRetention());
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        double retention = loose(path) ? 0.5 : path.contains("ice") ? 0.99 : 0.9;
        double restitution;
        if (path.contains("sand")) { restitution = 0; }
        else if (path.contains("dirt") || path.contains("grass") || path.contains("soul")) { restitution = 0.3; }
        else if (path.contains("concrete")) { restitution = 0.9; }
        else if (path.contains("stone") || path.contains("brick") || path.contains("wood") || path.contains("plank")) { restitution = 0.8; }
        else { restitution = 0.7; }
        return new SculptedBallPhysics.Response(retention, restitution, 0, retention);
    }

    public static double shotPower(BlockState state) {
        if (state.getBlock() instanceof SculptedGroundBlock ground) { return ground.material().shotPower(); }
        return loose(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()) ? 0.5 : 0.7;
    }

    private static boolean loose(String path) { return path.contains("sand") || path.contains("gravel"); }
    private GolfSurfacePhysics() { }
}
