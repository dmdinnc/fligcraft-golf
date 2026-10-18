package com.ziggleflig.golf.terrain;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Immutable corner heights in sixteenths; the NW-SE diagonal is shared by rendering and sampling. */
public record SculptedSurface(int northWest, int northEast, int southEast, int southWest) {
    public static final int BLOCK_HEIGHT = 16;
    public static final int MAX_HEIGHT = 32;
    public static final SculptedSurface FULL = new SculptedSurface(16, 16, 16, 16);
    // Wider steps prevent vanilla's step-up solver repeatedly clipping normal walking motion.
    // Ball contacts still use the exact triangles.
    private static final int COLLISION_STEPS = 2;
    private static final LoadingCache<SculptedSurface, VoxelShape> COLLISIONS = CacheBuilder.newBuilder()
        .maximumSize(512).build(new CacheLoader<>() {
            @Override public VoxelShape load(SculptedSurface surface) { return surface.buildCollision(); }
        });
    private record Slice(SculptedSurface surface, boolean cap) { }
    private static final LoadingCache<Slice, VoxelShape> SLICES = CacheBuilder.newBuilder().maximumSize(1024)
        .build(new CacheLoader<>() {
            @Override public VoxelShape load(Slice slice) {
                return Shapes.join(slice.surface.collisionShape().move(0, slice.cap ? -1 : 0, 0), Shapes.block(), BooleanOp.AND);
            }
        });
    private static final LoadingCache<SculptedSurface, List<TerrainTriangle>> TRIANGLES = CacheBuilder.newBuilder().maximumSize(512)
        .build(new CacheLoader<>() {
            @Override public List<TerrainTriangle> load(SculptedSurface surface) { return surface.buildTriangles(); }
        });

    public enum Corner {
        NORTH_WEST(0, 0), NORTH_EAST(1, 0), SOUTH_EAST(1, 1), SOUTH_WEST(0, 1);
        public final int x;
        public final int z;
        Corner(int x, int z) { this.x = x; this.z = z; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Corner nearest(double x, double z) {
            return z < 0.5 ? (x < 0.5 ? NORTH_WEST : NORTH_EAST) : (x < 0.5 ? SOUTH_WEST : SOUTH_EAST);
        }
    }

    public record Sample(double height, Vec3 normal, TerrainMaterial material) { }

    public SculptedSurface {
        northWest = Mth.clamp(northWest, 0, MAX_HEIGHT);
        northEast = Mth.clamp(northEast, 0, MAX_HEIGHT);
        southEast = Mth.clamp(southEast, 0, MAX_HEIGHT);
        southWest = Mth.clamp(southWest, 0, MAX_HEIGHT);
    }

    public static SculptedSurface fromArray(int[] heights) {
        return heights.length == 4 ? new SculptedSurface(heights[0], heights[1], heights[2], heights[3]) : FULL;
    }
    public int[] toArray() { return new int[] {northWest, northEast, southEast, southWest}; }
    public int height(Corner corner) {
        return switch (corner) {
            case NORTH_WEST -> northWest;
            case NORTH_EAST -> northEast;
            case SOUTH_EAST -> southEast;
            case SOUTH_WEST -> southWest;
        };
    }
    public SculptedSurface withCorner(Corner corner, int height) {
        return new SculptedSurface(corner == Corner.NORTH_WEST ? height : northWest,
            corner == Corner.NORTH_EAST ? height : northEast, corner == Corner.SOUTH_EAST ? height : southEast,
            corner == Corner.SOUTH_WEST ? height : southWest);
    }
    public static SculptedSurface flat(int height) { return new SculptedSurface(height, height, height, height); }

    public double heightAt(double x, double z) {
        x = Mth.clamp(x, 0, 1);
        z = Mth.clamp(z, 0, 1);
        return (x >= z ? northWest * (1 - x) + northEast * (x - z) + southEast * z
            : northWest * (1 - z) + southEast * x + southWest * (z - x)) / BLOCK_HEIGHT;
    }

    public Vec3 normalAt(double x, double z) {
        double dx = x >= z ? northEast - northWest : southEast - southWest;
        double dz = x >= z ? southEast - northEast : southWest - northWest;
        return new Vec3(-dx / BLOCK_HEIGHT, 1, -dz / BLOCK_HEIGHT).normalize();
    }

    public boolean isFlat() { return northWest == northEast && northWest == southEast && northWest == southWest; }
    public boolean isEmpty() { return isFlat() && northWest == 0; }
    public boolean hasOverflow() { return northWest > 16 || northEast > 16 || southEast > 16 || southWest > 16; }
    public SculptedSurface trimOverflow() { return new SculptedSurface(Math.min(16, northWest), Math.min(16, northEast), Math.min(16, southEast), Math.min(16, southWest)); }
    public VoxelShape collisionShape() { return COLLISIONS.getUnchecked(this); }
    public VoxelShape collisionSlice(boolean cap) {
        return hasOverflow() ? SLICES.getUnchecked(new Slice(this, cap))
            : cap ? Shapes.empty() : collisionShape();
    }

    /** Both visual cells and ball contacts use this single closed surface, without a face at the cap boundary. */
    public List<TerrainTriangle> triangles() { return TRIANGLES.getUnchecked(this); }
    private List<TerrainTriangle> buildTriangles() {
        Vec3 nw = new Vec3(0, northWest / 16.0, 0), ne = new Vec3(1, northEast / 16.0, 0);
        Vec3 se = new Vec3(1, southEast / 16.0, 1), sw = new Vec3(0, southWest / 16.0, 1);
        List<TerrainTriangle> result = new ArrayList<>(12);
        result.add(new TerrainTriangle(nw, sw, se, true));
        result.add(new TerrainTriangle(nw, se, ne, true));
        if (!isEmpty()) {
            Vec3[] corners = {nw, ne, se, sw};
            for (int i = 0; i < 4; i++) {
                Vec3 a = corners[i], b = corners[(i + 1) % 4];
                Vec3 bottomA = new Vec3(a.x, 0, a.z), bottomB = new Vec3(b.x, 0, b.z);
                result.add(new TerrainTriangle(bottomA, a, b, false));
                result.add(new TerrainTriangle(bottomA, b, bottomB, false));
            }
            result.add(new TerrainTriangle(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(1, 0, 1), false));
            result.add(new TerrainTriangle(Vec3.ZERO, new Vec3(1, 0, 1), new Vec3(0, 0, 1), false));
        }
        return List.copyOf(result);
    }

    public double ballFeetHeight(double x, double z, double radius) {
        Vec3 center = new Vec3(x, 2 + radius, z), down = new Vec3(0, -3, 0);
        double earliest = 1;
        for (TerrainTriangle triangle : triangles()) {
            if (!triangle.top()) { continue; }
            var hit = triangle.sweep(center, down, radius);
            if (hit != null) { earliest = Math.min(earliest, hit.time()); }
        }
        return 2 - 3 * earliest;
    }

    private VoxelShape buildCollision() {
        if (isEmpty()) { return Shapes.empty(); }
        if (isFlat()) { return Shapes.box(0, 0, 0, 1, northWest / 16.0, 1); }
        VoxelShape shape = Shapes.empty();
        for (int x = 0; x < COLLISION_STEPS; x++) {
            for (int z = 0; z < COLLISION_STEPS; z++) {
                double x0 = x / (double) COLLISION_STEPS, x1 = (x + 1) / (double) COLLISION_STEPS;
                double z0 = z / (double) COLLISION_STEPS, z1 = (z + 1) / (double) COLLISION_STEPS;
                // Conservatively support players/carts just above the visible plane, never inside it.
                double height = Math.max(Math.max(heightAt(x0, z0), heightAt(x0, z1)), Math.max(heightAt(x1, z0), heightAt(x1, z1)));
                // Optimize once after combining the cells, rather than after each of the 64 unions.
                if (height > 0) { shape = Shapes.joinUnoptimized(shape, Shapes.box(x0, 0, z0, x1, height, z1), BooleanOp.OR); }
            }
        }
        return shape.optimize();
    }
}
