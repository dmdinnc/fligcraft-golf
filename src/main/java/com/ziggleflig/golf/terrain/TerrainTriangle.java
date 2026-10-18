package com.ziggleflig.golf.terrain;

import net.minecraft.world.phys.Vec3;

/** A finite, outward-facing triangle. Sweeps test its face, edge capsules, and vertex spheres. */
public record TerrainTriangle(Vec3 a, Vec3 b, Vec3 c, Vec3 normal, boolean top) {
    private static final double EPSILON = 1.0e-8;
    public record Hit(double time, Vec3 normal, boolean top) { }

    public TerrainTriangle(Vec3 a, Vec3 b, Vec3 c, boolean top) {
        this(a, b, c, b.subtract(a).cross(c.subtract(a)).normalize(), top);
    }
    public TerrainTriangle move(Vec3 offset) { return new TerrainTriangle(a.add(offset), b.add(offset), c.add(offset), normal, top); }

    public Hit sweep(Vec3 center, Vec3 movement, double radius) {
        if (normal.lengthSqr() < 0.5) { return null; }
        Hit best = null;
        double approach = movement.dot(normal);
        double distance = center.subtract(a).dot(normal) - radius;
        if (approach < -EPSILON && distance >= -0.0001) {
            double time = Math.max(0, distance / -approach);
            Vec3 contact = center.add(movement.scale(time)).subtract(normal.scale(radius));
            if (time <= 1 && contains(contact)) { best = new Hit(time, normal, top); }
        }
        Vec3[] vertices = {a, b, c};
        for (int i = 0; i < 3; i++) {
            Vec3 start = vertices[i], end = vertices[(i + 1) % 3];
            Vec3 edge = end.subtract(start);
            double lengthSqr = edge.lengthSqr();
            if (lengthSqr > EPSILON) {
                Vec3 relative = center.subtract(start);
                Vec3 perpendicular = relative.subtract(edge.scale(relative.dot(edge) / lengthSqr));
                Vec3 speed = movement.subtract(edge.scale(movement.dot(edge) / lengthSqr));
                double time = firstRoot(speed.lengthSqr(), 2 * perpendicular.dot(speed), perpendicular.lengthSqr() - radius * radius);
                if (time >= 0 && time <= 1) {
                    Vec3 impact = center.add(movement.scale(time));
                    double along = impact.subtract(start).dot(edge) / lengthSqr;
                    if (along >= 0 && along <= 1) {
                        best = nearer(best, edgeHit(time, impact.subtract(start.add(edge.scale(along))).normalize(), movement));
                    }
                }
            }
            Vec3 relative = center.subtract(start);
            double time = firstRoot(movement.lengthSqr(), 2 * relative.dot(movement), relative.lengthSqr() - radius * radius);
            if (time >= 0 && time <= 1) {
                best = nearer(best, edgeHit(time, center.add(movement.scale(time)).subtract(start).normalize(), movement));
            }
        }
        return best;
    }

    private Hit edgeHit(double time, Vec3 hitNormal, Vec3 movement) {
        // Back-facing edges are inside the terrain volume; grazing a joined edge is not an impact.
        return hitNormal.dot(normal) >= -EPSILON && movement.dot(hitNormal) < -EPSILON ? new Hit(time, hitNormal, top) : null;
    }
    private static Hit nearer(Hit first, Hit second) {
        return second != null && (first == null || second.time < first.time - EPSILON) ? second : first;
    }
    private static double firstRoot(double a, double b, double c) {
        if (a < EPSILON || c < -0.0001) { return -1; }
        double discriminant = b * b - 4 * a * c;
        if (discriminant < -EPSILON) { return -1; }
        return (-b - Math.sqrt(Math.max(0, discriminant))) / (2 * a);
    }
    private boolean contains(Vec3 point) {
        return b.subtract(a).cross(point.subtract(a)).dot(normal) >= -EPSILON
            && c.subtract(b).cross(point.subtract(b)).dot(normal) >= -EPSILON
            && a.subtract(c).cross(point.subtract(c)).dot(normal) >= -EPSILON;
    }
}
