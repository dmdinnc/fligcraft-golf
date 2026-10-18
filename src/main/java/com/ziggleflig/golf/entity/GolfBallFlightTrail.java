package com.ziggleflig.golf.entity;

import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Client-observed positions only: bounded history, with no predicted trajectory or physics changes. */
public final class GolfBallFlightTrail {
    public static final int LIFETIME_TICKS = 20;
    public record Sample(int tick, Vec3 position) { }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private Vec3 previous;
    private int shot;
    private int lastMovementTick = Integer.MIN_VALUE;

    public void update(int tick, Vec3 position, int shot, boolean putt) {
        if (this.shot != shot || putt || shot == 0 || previous == null || previous.distanceToSqr(position) > 16 * 16) {
            samples.clear();
            lastMovementTick = Integer.MIN_VALUE;
            this.shot = shot;
            previous = position;
            return;
        }
        while (!samples.isEmpty() && samples.getFirst().tick() <= tick - LIFETIME_TICKS) { samples.removeFirst(); }
        if (previous.distanceToSqr(position) > 0.0001) {
            if (samples.isEmpty()) { samples.addLast(new Sample(tick - 1, previous)); }
            samples.addLast(new Sample(tick, position));
            lastMovementTick = tick;
        }
        previous = position;
    }
    public List<Sample> samples() { return List.copyOf(samples); }
    public float opacity(float tick) {
        if (lastMovementTick == Integer.MIN_VALUE) { return 0; }
        return Math.clamp(1 - (tick - lastMovementTick) / LIFETIME_TICKS, 0, 1);
    }
}
