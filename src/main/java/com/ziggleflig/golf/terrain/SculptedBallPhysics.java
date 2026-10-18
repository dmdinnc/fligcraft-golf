package com.ziggleflig.golf.terrain;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.GolfFlagBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server-only swept sphere contacts; Minecraft still moves the ball against ordinary block obstacles. */
public final class SculptedBallPhysics {
    private static final double EPSILON = 0.00001;
    private static final double BOUNCE_SPEED = 0.05;
    // At a half-block rise per block, the bank is steep enough to use the full surface normal.
    private static final double FULL_BOUNCE_SLOPE = 0.5;
    // Capture slow supported balls without applying this threshold to airborne impacts or full-speed putts.
    private static final double STATIC_STOP_SPEED = 0.04;
    private record Face(TerrainTriangle triangle, TerrainMaterial material, BlockPos source, boolean flag) { }
    public record Contact(double time, Vec3 normal, boolean top, TerrainMaterial material, BlockPos source, boolean flag) { }
    public record Response(double retention, double restitution, double resistance, double impactRetention) {
        public Response(double retention, double restitution, double resistance) { this(retention, restitution, resistance, retention); }
    }
    public record Result(boolean touchedGround, boolean bounced) { }
    @FunctionalInterface
    public interface MoveObserver {
        void afterMove(Vec3 previousPosition, Vec3 approachVelocity, double elapsedTicks);
    }
    private record OrdinaryMove(Vec3 velocity, boolean blocked, boolean grounded, boolean bounced) { }

    public static boolean hasTerrainOnPath(Level level, Vec3 feet, Vec3 movement, double radius) {
        return !sources(level, feet, movement, radius, true).isEmpty();
    }

    /** Short downward sphere cast also finds support at a triangle edge, a seam, or an overflow cap. */
    public static Contact support(Level level, Vec3 feet, double radius, double drop) {
        Vec3 start = feet.add(0, 0.004, 0), movement = new Vec3(0, -drop - 0.004, 0);
        return sweep(level, start, movement, radius, true);
    }

    public static Contact sweep(Level level, Vec3 feet, Vec3 movement, double radius, boolean topsOnly) {
        Vec3 center = feet.add(0, radius, 0);
        Contact best = null;
        for (Face face : faces(level, feet, movement, radius)) {
            if (topsOnly && !face.triangle.top()) { continue; }
            var hit = face.triangle.sweep(center, movement, radius);
            if (hit != null && (!topsOnly || hit.normal().y > 0.1)
                    && (best == null || hit.time() < best.time - 1.0e-8)) {
                best = new Contact(hit.time(), hit.normal(), hit.top(), face.material, face.source, face.flag);
            }
        }
        return best;
    }

    public static Vec3 reflect(Vec3 velocity, Vec3 normal, double retention, double restitution) {
        double inward = velocity.dot(normal);
        return tangent(velocity, normal).scale(retention).subtract(normal.scale(inward * restitution));
    }
    public static Vec3 tangent(Vec3 velocity, Vec3 normal) { return velocity.subtract(normal.scale(velocity.dot(normal))); }
    private static Vec3 groundBounce(Vec3 velocity, Vec3 normal, TerrainMaterial material) {
        // Gentle turf should not redirect a descending shot as strongly as a rigid ramp.
        // Bias only its impact normal upward; steep banks and rolling keep the real normal.
        double slope = normal.horizontalDistance() / normal.y;
        double tilt = Math.clamp(slope / FULL_BOUNCE_SLOPE, 0, 1);
        Vec3 impactNormal = new Vec3(normal.x * tilt, normal.y, normal.z * tilt).normalize();
        Vec3 bounced = reflect(velocity, impactNormal, material.impactRetention(), material.restitution());
        // A glancing skip can already be pointing along the incline. Never send it inside the actual surface.
        return bounced.dot(normal) < 0 ? tangent(bounced, normal) : bounced;
    }
    public static Vec3 roll(Vec3 velocity, Vec3 normal, Response response, double time) {
        Vec3 along = tangent(velocity, normal).scale(Math.pow(response.retention, time));
        double speed = along.length();
        double resistance = response.resistance * time;
        return speed <= Math.max(resistance, 0.000001) ? Vec3.ZERO : along.scale((speed - resistance) / speed);
    }
    private static Response response(TerrainMaterial material) {
        return new Response(material.rollingRetention(), material.restitution(), material.rollingResistance(), material.impactRetention());
    }

    private static boolean canRest(Vec3 velocity, Vec3 normal, TerrainMaterial material, double gravity) {
        if (tangent(velocity, normal).lengthSqr() > STATIC_STOP_SPEED * STATIC_STOP_SPEED) { return false; }
        Vec3 weight = new Vec3(0, -gravity, 0);
        double downhillForce = tangent(weight, normal).length();
        double normalForce = Math.max(0, -weight.dot(normal));
        return downhillForce <= material.staticFriction() * normalForce + 1.0e-9;
    }

    public static Result move(GolfBallEntity ball, Vec3 velocity, double gravity,
            Function<BlockState, Response> ordinaryResponse, MoveObserver observer) {
        Level level = ball.level();
        double radius = ball.getBbWidth() / 2.0;
        if (!hasTerrainOnPath(level, ball.position(), velocity.add(0, -gravity, 0), radius)) {
            // Airborne shots and ordinary terrain share collision response without triangle substeps.
            Vec3 previous = ball.position(), approach = velocity.add(0, -gravity, 0);
            OrdinaryMove moved = moveOrdinary(ball, approach, 1, ordinaryResponse, ball.onGround());
            ball.setOnGround(moved.grounded);
            ball.setDeltaMovement(moved.velocity);
            observer.afterMove(previous, approach, 1);
            return new Result(moved.grounded || moved.bounced, moved.bounced);
        }
        int steps = Mth.clamp((int) Math.ceil((velocity.length() + gravity) / 0.18), 1, 512);
        double dt = 1.0 / steps;
        boolean touched = false, bounced = false;
        Contact standing = support(level, ball.position(), radius, 0.008);
        boolean rolling = standing != null && velocity.dot(standing.normal) <= EPSILON
            && (ball.onGround() || Math.abs(velocity.dot(standing.normal)) < 0.02);
        for (int step = 0; step < steps && ball.isAlive(); step++) {
            if (rolling && standing != null) {
                // Static friction cancels downhill gravity while held; moving balls retain their rolling drag.
                velocity = canRest(velocity, standing.normal, standing.material, gravity) ? Vec3.ZERO
                    : roll(velocity.add(0, -gravity * dt, 0), standing.normal, response(standing.material), dt);
                touched = true;
            } else { velocity = velocity.add(0, -gravity * dt, 0); }
            double remaining = dt;
            boolean ordinaryGround = false;
            for (int impact = 0; impact < 8 && remaining > 1.0e-7 && ball.isAlive(); impact++) {
                Vec3 movement = velocity.scale(remaining);
                Contact contact = sweep(level, ball.position(), movement, radius, false);
                double fraction = contact == null ? 1 : contact.time;
                // Avoid leaving a loaded course and forcing a new chunk to load for a shot.
                Vec3 end = ball.position().add(movement.scale(fraction));
                if (!level.hasChunk(Mth.floor(end.x) >> 4, Mth.floor(end.z) >> 4)) {
                    velocity = Vec3.ZERO;
                    remaining = 0;
                    break;
                }
                Vec3 previous = ball.position(), approachVelocity = velocity;
                double elapsed = remaining * fraction;
                OrdinaryMove moved = moveOrdinary(ball, velocity, elapsed, ordinaryResponse, rolling);
                velocity = moved.velocity;
                ball.setDeltaMovement(velocity);
                observer.afterMove(previous, approachVelocity, elapsed);
                if (!ball.isAlive()) { break; }
                ordinaryGround |= moved.grounded;
                touched |= moved.grounded || moved.bounced;
                bounced |= moved.bounced;
                remaining *= 1 - fraction;
                if (moved.blocked) {
                    rolling = moved.grounded;
                    if (fraction == 0) { break; }
                    continue;
                }
                if (contact == null) { break; }
                double inward = velocity.dot(contact.normal);
                boolean groundContact = contact.top && contact.normal.y > 0.1;
                touched |= groundContact;
                if ((!rolling || !groundContact) && inward < -BOUNCE_SPEED) {
                    velocity = groundContact && !contact.flag ? groundBounce(velocity, contact.normal, contact.material)
                        : reflect(velocity, contact.normal, contact.flag ? 1 : contact.material.impactRetention(),
                            contact.flag ? 0.15 : contact.material.restitution());
                    rolling = false;
                    bounced = true;
                } else {
                    velocity = tangent(velocity, contact.normal);
                    rolling = groundContact;
                }
                ball.setPos(ball.position().add(contact.normal.scale(EPSILON)));
                ball.setOnGround(rolling);
            }
            standing = support(level, ball.position(), radius, rolling ? 0.04 : 0.008);
            if (standing != null && velocity.dot(standing.normal) <= BOUNCE_SPEED) {
                double height = ball.getY() + 0.004 - (rolling ? 0.044 : 0.012) * standing.time;
                ball.setPos(ball.getX(), height + EPSILON, ball.getZ());
                velocity = tangent(velocity, standing.normal);
                if (canRest(velocity, standing.normal, standing.material, gravity)) { velocity = Vec3.ZERO; }
                rolling = true;
                touched = true;
            } else { rolling = ordinaryGround && velocity.y <= 0; }
            ball.setOnGround(rolling);
            ball.setDeltaMovement(velocity);
        }
        ball.setDeltaMovement(velocity);
        return new Result(touched, bounced);
    }

    private static OrdinaryMove moveOrdinary(GolfBallEntity ball, Vec3 velocity, double time,
            Function<BlockState, Response> response, boolean rolling) {
        Vec3 before = ball.position(), intended = velocity.scale(time);
        ball.setDeltaMovement(velocity);
        ball.move(MoverType.SELF, intended);
        Vec3 actual = ball.position().subtract(before);
        boolean blocked = actual.distanceToSqr(intended) > 1.0e-12;
        BlockPos pos = ball.blockPosition();
        boolean flag = ball.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && ball.level().getBlockState(pos).is(GolfMod.GOLF_FLAG_BLOCK.get());
        double wallRetention = flag ? 0.15 : 0.6;
        if (Math.abs(intended.x) > 0.001 && Math.abs(actual.x) < Math.abs(intended.x) * 0.5) { velocity = new Vec3(-velocity.x * wallRetention, velocity.y, velocity.z); }
        if (Math.abs(intended.z) > 0.001 && Math.abs(actual.z) < Math.abs(intended.z) * 0.5) { velocity = new Vec3(velocity.x, velocity.y, -velocity.z * wallRetention); }
        boolean grounded = intended.y < 0 && actual.y > intended.y + 1.0e-7;
        boolean bounced = false;
        if (grounded) {
            BlockPos below = ball.getBlockPosBelowThatAffectsMyMovement();
            Response material = response.apply(ball.level().getBlockState(below));
            if (!rolling && velocity.y < -BOUNCE_SPEED) {
                velocity = reflect(velocity, new Vec3(0, 1, 0), material.impactRetention, material.restitution);
                bounced = true;
                grounded = false;
            } else {
                velocity = roll(velocity, new Vec3(0, 1, 0), material, time);
                if (velocity.horizontalDistanceSqr() < 0.0001) { velocity = Vec3.ZERO; }
            }
        }
        return new OrdinaryMove(velocity, blocked, grounded, bounced);
    }

    private static List<Face> faces(Level level, Vec3 feet, Vec3 movement, double radius) {
        List<Face> result = new ArrayList<>();
        for (BlockPos pos : sources(level, feet, movement, radius, false)) {
            if (level.getBlockEntity(pos) instanceof SculptedGroundBlockEntity ground) {
                TerrainMaterial material = ((SculptedGroundBlock) ground.getBlockState().getBlock()).material();
                Vec3 origin = Vec3.atLowerCornerOf(pos);
                for (TerrainTriangle triangle : ground.surface().triangles()) { result.add(new Face(triangle.move(origin), material, pos, false)); }
                if (ground.hasFlag()) { poleFaces(result, origin, ground.surface().heightAt(0.5, 0.5), material, pos); }
            }
        }
        return result;
    }

    private static void poleFaces(List<Face> faces, Vec3 origin, double height, TerrainMaterial material, BlockPos source) {
        double low = 7.5 / 16, high = 8.5 / 16;
        Vec3 nw = origin.add(low, height, low), ne = origin.add(high, height, low);
        Vec3 se = origin.add(high, height, high), sw = origin.add(low, height, high);
        Vec3[] corners = {nw, ne, se, sw};
        for (int i = 0; i < 4; i++) {
            Vec3 a = corners[i], b = corners[(i + 1) % 4];
            poleFace(faces, a.add(0, GolfFlagBlock.BALL_CLEARANCE, 0), a.add(0, 2, 0), b.add(0, 2, 0),
                b.add(0, GolfFlagBlock.BALL_CLEARANCE, 0), material, source);
        }
        poleFace(faces, nw.add(0, 2, 0), sw.add(0, 2, 0), se.add(0, 2, 0), ne.add(0, 2, 0), material, source);
    }
    private static void poleFace(List<Face> faces, Vec3 a, Vec3 b, Vec3 c, Vec3 d, TerrainMaterial material, BlockPos source) {
        // Pole tops never count as rolling ground or as cup support.
        faces.add(new Face(new TerrainTriangle(a, b, c, false), material, source, true));
        faces.add(new Face(new TerrainTriangle(a, c, d, false), material, source, true));
    }

    private static List<BlockPos> sources(Level level, Vec3 feet, Vec3 movement, double radius, boolean firstOnly) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.max(Math.abs(movement.x), Math.abs(movement.y)), Math.abs(movement.z)) * 2));
        Set<BlockPos> checked = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        for (int i = 0; i <= steps; i++) {
            Vec3 point = feet.add(movement.scale(i / (double) steps));
            for (int x = Mth.floor(point.x - radius); x <= Mth.floor(point.x + radius); x++) {
                for (int z = Mth.floor(point.z - radius); z <= Mth.floor(point.z + radius); z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) { continue; }
                    for (int y = Math.max(level.getMinBuildHeight(), Mth.floor(point.y - SculptedSurface.MAX_HEIGHT / 16.0 - 2 - radius));
                            y <= Math.min(level.getMaxBuildHeight() - 1, Mth.floor(point.y + 2 * radius)); y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (checked.contains(pos)) { continue; }
                        if (level.getBlockState(pos).getBlock() instanceof SculptedGroundBlock ground && !ground.isCap()) {
                            // Only a planted pole extends the contact range two blocks above the terrain.
                            if (point.y - radius > y + SculptedSurface.MAX_HEIGHT / 16.0
                                    && (!(level.getBlockEntity(pos) instanceof SculptedGroundBlockEntity entity) || !entity.hasFlag())) { continue; }
                            found.add(pos);
                            if (firstOnly) { return found; }
                        }
                        checked.add(pos);
                    }
                }
            }
        }
        return found;
    }
    private SculptedBallPhysics() { }
}
