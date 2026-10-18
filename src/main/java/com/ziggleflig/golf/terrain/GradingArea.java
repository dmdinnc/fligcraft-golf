package com.ziggleflig.golf.terrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.ziggleflig.golf.terrain.TerrainGrading.Anchor;
import com.ziggleflig.golf.terrain.TerrainGrading.Mode;

/** The area enclosed by four reference points, independent of alignment and click order. */
public final class GradingArea {
    private static final double EPSILON = 1.0e-8;
    private final List<Anchor> anchors;
    private final List<Anchor> boundary;
    private final Anchor interior;
    private final int minX, minZ, maxX, maxZ;
    private final double centerX, centerZ, meanHeight, slopeX, slopeZ;

    private GradingArea(List<Anchor> anchors, List<Anchor> boundary) {
        this.anchors = List.copyOf(anchors);
        this.boundary = List.copyOf(boundary);
        interior = anchors.stream().filter(a -> !boundary.contains(a)).findFirst().orElse(null);
        minX = anchors.stream().mapToInt(Anchor::x).min().orElseThrow();
        maxX = anchors.stream().mapToInt(Anchor::x).max().orElseThrow();
        minZ = anchors.stream().mapToInt(Anchor::z).min().orElseThrow();
        maxZ = anchors.stream().mapToInt(Anchor::z).max().orElseThrow();
        centerX = anchors.stream().mapToDouble(Anchor::x).average().orElseThrow();
        centerZ = anchors.stream().mapToDouble(Anchor::z).average().orElseThrow();
        meanHeight = anchors.stream().mapToDouble(Anchor::height).average().orElseThrow();
        // Center the fit so it stays stable at large positive or negative world coordinates.
        double xx = 0, xz = 0, zz = 0, xh = 0, zh = 0;
        for (Anchor anchor : anchors) {
            double x = anchor.x() - centerX, z = anchor.z() - centerZ, h = anchor.height() - meanHeight;
            xx += x * x; xz += x * z; zz += z * z; xh += x * h; zh += z * h;
        }
        double determinant = xx * zz - xz * xz;
        slopeX = (xh * zz - zh * xz) / determinant;
        slopeZ = (zh * xx - xh * xz) / determinant;
    }

    public static GradingArea create(List<Anchor> anchors) {
        if (anchors.size() != 4) { return null; }
        List<Anchor> sorted = anchors.stream().sorted(Comparator.comparingInt(Anchor::x).thenComparingInt(Anchor::z)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).x() == sorted.get(i - 1).x() && sorted.get(i).z() == sorted.get(i - 1).z()) { return null; }
        }
        // Keep references along a straight boundary edge too: their individual heights still matter.
        List<Anchor> lower = new ArrayList<>(), upper = new ArrayList<>();
        for (Anchor anchor : sorted) { addHullPoint(lower, anchor); }
        for (int i = sorted.size() - 1; i >= 0; i--) { addHullPoint(upper, sorted.get(i)); }
        lower.removeLast(); upper.removeLast(); lower.addAll(upper);
        if (lower.size() < 3 || lower.stream().allMatch(a -> Math.abs(cross(sorted.getFirst(), sorted.getLast(), a.x(), a.z())) < EPSILON)) { return null; }
        return new GradingArea(anchors, lower);
    }
    private static void addHullPoint(List<Anchor> hull, Anchor point) {
        while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.getLast(), point.x(), point.z()) < 0) { hull.removeLast(); }
        hull.add(point);
    }
    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
    public int width() { return maxX - minX; }
    public int depth() { return maxZ - minZ; }
    public List<Anchor> boundary() { return boundary; }
    public boolean contains(double x, double z) {
        for (int i = 0; i < boundary.size(); i++) {
            if (cross(boundary.get(i), boundary.get((i + 1) % boundary.size()), x, z) < -EPSILON) { return false; }
        }
        return true;
    }
    public int height(Mode mode, double x, double z) {
        return (int) Math.round(mode == Mode.PLANE ? meanHeight + slopeX * (x - centerX) + slopeZ * (z - centerZ) : blend(x, z));
    }
    public int maxAnchorAdjustment(Mode mode) {
        return anchors.stream().mapToInt(a -> Math.abs(height(mode, a.x(), a.z()) - a.height())).max().orElseThrow();
    }
    private double blend(double x, double z) {
        // An interior reference divides a triangular hull into three patches so all four points remain exact.
        if (interior != null) {
            for (int i = 0; i < boundary.size(); i++) {
                Anchor a = boundary.get(i), b = boundary.get((i + 1) % boundary.size());
                double total = cross(a, b, interior.x(), interior.z());
                double wa = cross(b, interior, x, z) / total;
                double wb = cross(interior, a, x, z) / total;
                double wi = cross(a, b, x, z) / total;
                if (wa >= -EPSILON && wb >= -EPSILON && wi >= -EPSILON) {
                    return wa * a.height() + wb * b.height() + wi * interior.height();
                }
            }
        }
        // Mean-value coordinates smoothly blend a skewed or rotated polygon without a fixed diagonal.
        int count = boundary.size();
        double[] distances = new double[count], tangents = new double[count];
        for (int i = 0; i < count; i++) {
            Anchor a = boundary.get(i), b = boundary.get((i + 1) % count);
            double ax = a.x() - x, az = a.z() - z, bx = b.x() - x, bz = b.z() - z;
            double da = Math.hypot(ax, az), db = Math.hypot(bx, bz);
            if (da < EPSILON) { return a.height(); }
            if (db < EPSILON) { return b.height(); }
            if (Math.abs(ax * bz - az * bx) < EPSILON && ax * bx + az * bz <= 0) {
                return (a.height() * db + b.height() * da) / (da + db);
            }
            distances[i] = da;
            tangents[i] = Math.tan(Math.atan2(ax * bz - az * bx, ax * bx + az * bz) / 2);
        }
        double sum = 0, weighted = 0;
        for (int i = 0; i < count; i++) {
            double weight = (tangents[(i + count - 1) % count] + tangents[i]) / distances[i];
            sum += weight; weighted += weight * boundary.get(i).height();
        }
        return weighted / sum;
    }
    private static double cross(Anchor a, Anchor b, double x, double z) {
        return (b.x() - (double) a.x()) * (z - a.z()) - (b.z() - (double) a.z()) * (x - a.x());
    }
}
