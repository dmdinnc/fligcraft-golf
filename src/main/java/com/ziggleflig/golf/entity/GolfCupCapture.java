package com.ziggleflig.golf.entity;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A moving ball needs time to fall into the cup, rather than scoring on proximity alone. */
final class GolfCupCapture {
    private static final double RADIUS = 0.20;
    private static final double STOPPED_SPEED_SQR = 0.04 * 0.04;
    private static final double HEIGHT_TOLERANCE = 0.15;
    private static final double GRAVITY = 0.035;
    private BlockPos activeCup;
    private double timeOverCup;

    private record Cup(BlockPos key, BlockPos ground, SculptedSurface surface, double stoppedRadius) {
        double feetHeight(Vec3 point, double ballRadius) {
            return surface == null ? key.getY() : ground.getY()
                + surface.ballFeetHeight(point.x - ground.getX(), point.z - ground.getZ(), ballRadius);
        }
        double distanceSqr(Vec3 point) {
            double x = point.x - key.getX() - 0.5, z = point.z - key.getZ() - 0.5;
            return x * x + z * z;
        }
    }
    private record Interval(double enter, double exit) { }

    boolean update(GolfBallEntity ball, Vec3 previous, Vec3 approachVelocity, double elapsedTicks) {
        Vec3 current = ball.position(), movement = current.subtract(previous);
        double ballRadius = ball.getBbWidth() / 2.0;
        double speedSqr = Math.max(approachVelocity.lengthSqr(), ball.getDeltaMovement().lengthSqr());
        BlockPos min = BlockPos.containing(Math.min(previous.x, current.x) - 1,
            Math.min(previous.y, current.y) - 2, Math.min(previous.z, current.z) - 1);
        BlockPos max = BlockPos.containing(Math.max(previous.x, current.x) + 1,
            Math.max(previous.y, current.y) + 1, Math.max(previous.z, current.z) + 1);
        Cup selected = null;
        Interval selectedInterval = null;
        double longestTime = -1;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!ball.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { continue; }
            Cup cup;
            if (ball.level().getBlockState(pos).is(GolfMod.GOLF_FLAG_BLOCK.get())) {
                var sculpted = SculptedGroundBlock.source(ball.level(), pos.below());
                cup = new Cup(pos.immutable(), sculpted == null ? null : sculpted.getBlockPos(),
                    sculpted == null ? null : sculpted.surface(), 1.0);
            } else if (ball.level().getBlockEntity(pos) instanceof SculptedGroundBlockEntity ground && ground.hasFlag()) {
                cup = new Cup(pos.immutable(), ground.getBlockPos(), ground.surface(), 0.5);
            } else { continue; }

            // Preserve stopped-ball assistance, but a collision/drag after a fast approach cannot grant it.
            if (speedSqr <= STOPPED_SPEED_SQR && cup.distanceSqr(current) <= cup.stoppedRadius * cup.stoppedRadius + 1.0e-9
                    && Math.abs(current.y - cup.feetHeight(current, ballRadius)) <= HEIGHT_TOLERANCE) { return true; }

            Interval interval = overCup(cup, previous, movement, ballRadius);
            if (interval == null) { continue; }
            double time = (interval.exit - interval.enter) * elapsedTicks;
            if (time > longestTime) {
                selected = cup;
                selectedInterval = interval;
                longestTime = time;
            }
        }
        if (selected == null) { clear(); return false; }
        if (!selected.key.equals(activeCup) || selectedInterval.enter > 1.0e-7) { timeOverCup = 0; }
        activeCup = selected.key;
        timeOverCup += longestTime;
        // Falling one ball radius takes about 2.7 ticks. Physics substeps contribute their actual
        // elapsed time only; neither extra collision callbacks nor temporarily losing onGround help.
        boolean captured = timeOverCup >= Math.sqrt(2 * ballRadius / GRAVITY);
        if (selectedInterval.exit < 1 - 1.0e-7) { clear(); }
        return captured;
    }

    private static Interval overCup(Cup cup, Vec3 previous, Vec3 movement, double ballRadius) {
        double x = previous.x - cup.key.getX() - 0.5, z = previous.z - cup.key.getZ() - 0.5;
        double a = movement.horizontalDistanceSqr(), enter = 0, exit = 1;
        if (a < 1.0e-16) {
            if (x * x + z * z > RADIUS * RADIUS + 1.0e-9) { return null; }
        } else {
            double b = x * movement.x + z * movement.z;
            double discriminant = b * b - a * (x * x + z * z - RADIUS * RADIUS);
            if (discriminant < 0) { return null; }
            double root = Math.sqrt(discriminant);
            enter = Math.max(0, (-b - root) / a);
            exit = Math.min(1, (-b + root) / a);
            if (exit < enter) { return null; }
        }
        // Clip to the actual surface height during this movement, including lowered/raised cups.
        Vec3 start = previous.add(movement.scale(enter)), end = previous.add(movement.scale(exit));
        double gap = start.y - cup.feetHeight(start, ballRadius);
        double gapChange = end.y - cup.feetHeight(end, ballRadius) - gap;
        if (Math.abs(gapChange) < 1.0e-12) {
            return Math.abs(gap) <= HEIGHT_TOLERANCE ? new Interval(enter, exit) : null;
        }
        double lower = (-HEIGHT_TOLERANCE - gap) / gapChange, upper = (HEIGHT_TOLERANCE - gap) / gapChange;
        double from = Math.max(0, Math.min(lower, upper)), to = Math.min(1, Math.max(lower, upper));
        return to < from ? null : new Interval(enter + (exit - enter) * from, enter + (exit - enter) * to);
    }

    private void clear() { activeCup = null; timeOverCup = 0; }
}
