package com.ziggleflig.golf.entity;

import java.util.UUID;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.GolfWind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class GolfBallEntity extends Entity implements ItemSupplier {

    // Hole detection tuning
    private static final double HOLE_ROLLING_SPEED_THRESHOLD_SQR = 0.16D;
    private static final double HOLE_AIR_SPEED_THRESHOLD_SQR = 0.5D;
    private static final double HOLE_STOPPED_SPEED_THRESHOLD_SQR = 0.0001D;
    private static final double HOLE_RADIUS = 2.5D / 16.0D;
    private static final double HOLE_RADIUS_SQR = HOLE_RADIUS * HOLE_RADIUS;
    private static final double HOLE_STOPPED_RADIUS = 1.0D;
    private static final double HOLE_STOPPED_RADIUS_SQR = HOLE_STOPPED_RADIUS * HOLE_STOPPED_RADIUS;
    private static final double WIND_INFLUENCE = 0.025D;
    private static final double GLOW_CHECK_SPEED_SQR = 0.0025D;

    private int strokes;
    private UUID lastHitter;
    private boolean onTee;
    private boolean isDrivingRangeBall;
    private int drivingRangeLifetime;

    // Driving range stats
    private double startX, startY, startZ;
    private double maxHeight;
    private boolean hasBouncedOnce;
    private double carryDistance;
    private boolean statsRecorded;
    private float lastShotErrorPercent;
    private float spin;
    private boolean glowUntilNearby;
    private int glowTimeoutTicks;

    // Client interpolation
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

    public GolfBallEntity(EntityType<? extends GolfBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(false);
    }
    
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = steps;
    }

    public void registerStroke(Player player) {
        this.strokes++;
        this.lastHitter = player.getUUID();

        if (this.isDrivingRangeBall && this.drivingRangeLifetime == 0) {
            this.drivingRangeLifetime = 600;
            this.startX = this.getX();
            this.startY = this.getY();
            this.startZ = this.getZ();
            this.maxHeight = this.startY;
            this.hasBouncedOnce = false;
            this.statsRecorded = false;
        }
    }

    public UUID getLastHitter() {
        return this.lastHitter;
    }
    
    public void setLastHitter(UUID uuid) {
        this.lastHitter = uuid;
    }

    public int getStrokes() {
        return this.strokes;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    public void setOnTee(boolean onTee) {
        this.onTee = onTee;
    }
    
    public boolean isOnTee() {
        return this.onTee;
    }
    
    public void clearTee() {
        this.onTee = false;
    }
    
    public void setDrivingRangeBall(boolean isDrivingRangeBall) {
        this.isDrivingRangeBall = isDrivingRangeBall;
    }
    
    public boolean isDrivingRangeBall() {
        return this.isDrivingRangeBall;
    }

    public void setLastShotErrorPercent(float lastShotErrorPercent) {
        this.lastShotErrorPercent = lastShotErrorPercent;
    }

    public float getLastShotErrorPercent() {
        return this.lastShotErrorPercent;
    }

    public void setSpin(float spin) {
        this.spin = spin;
    }

    public float getSpin() {
        return this.spin;
    }

    public void startGlowUntilNearby() {
        this.glowUntilNearby = true;
        this.glowTimeoutTicks = 6000;
        this.setGlowingTag(true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.strokes = tag.getInt("Strokes");
        if (tag.hasUUID("LastHitter")) {
            this.lastHitter = tag.getUUID("LastHitter");
        }
        this.onTee = tag.getBoolean("OnTee");
        this.isDrivingRangeBall = tag.getBoolean("DrivingRangeBall");
        this.drivingRangeLifetime = tag.getInt("DrivingRangeLifetime");
        this.startX = tag.getDouble("StartX");
        this.startY = tag.getDouble("StartY");
        this.startZ = tag.getDouble("StartZ");
        this.maxHeight = tag.getDouble("MaxHeight");
        this.hasBouncedOnce = tag.getBoolean("HasBouncedOnce");
        this.carryDistance = tag.getDouble("CarryDistance");
        this.statsRecorded = tag.getBoolean("StatsRecorded");
        this.lastShotErrorPercent = tag.getFloat("LastShotErrorPercent");
        this.spin = tag.getFloat("Spin");
        this.glowUntilNearby = tag.getBoolean("GlowUntilNearby");
        this.glowTimeoutTicks = tag.getInt("GlowTicks");
        if (this.glowUntilNearby && this.glowTimeoutTicks <= 0) {
            this.glowTimeoutTicks = 6000;
        }
        if (this.glowUntilNearby) {
            this.setGlowingTag(true);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Strokes", this.strokes);
        if (this.lastHitter != null) {
            tag.putUUID("LastHitter", this.lastHitter);
        }
        tag.putBoolean("OnTee", this.onTee);
        tag.putBoolean("DrivingRangeBall", this.isDrivingRangeBall);
        tag.putInt("DrivingRangeLifetime", this.drivingRangeLifetime);
        tag.putDouble("StartX", this.startX);
        tag.putDouble("StartY", this.startY);
        tag.putDouble("StartZ", this.startZ);
        tag.putDouble("MaxHeight", this.maxHeight);
        tag.putBoolean("HasBouncedOnce", this.hasBouncedOnce);
        tag.putDouble("CarryDistance", this.carryDistance);
        tag.putBoolean("StatsRecorded", this.statsRecorded);
        tag.putFloat("LastShotErrorPercent", this.lastShotErrorPercent);
        tag.putFloat("Spin", this.spin);
        tag.putBoolean("GlowUntilNearby", this.glowUntilNearby);
        tag.putInt("GlowTicks", this.glowTimeoutTicks);
    }

    @Override
    public void tick() {
        super.tick();
        handleClientLerp();
        spawnTrailParticles();

        if (this.level().isClientSide) {
            return;
        }

        if (updateDrivingRangeLifetime()) {
            return;
        }

        updateGlowUntilNearby();

        Vec3 motion = this.getDeltaMovement();
        motion = applyGravity(motion);
        motion = applyAirDrag(motion);
        motion = applyMagnusEffect(motion);
        motion = applyWind(motion);

        Vec3 posBefore = this.position();
        Vec3 velocityBeforeMove = motion;

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        motion = handleWallCollisions(posBefore, motion);

        if (this.onGround()) {
            handleGroundInteraction(velocityBeforeMove);
        }
    }

    private void handleClientLerp() {
        if (!this.level().isClientSide || this.lerpSteps <= 0) {
            return;
        }

        double newX = this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps;
        double newY = this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps;
        double newZ = this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps;
        this.setPos(newX, newY, newZ);
        this.lerpSteps--;
    }

    private void spawnTrailParticles() {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.lengthSqr();
        if (speed > 0.01D) {
            this.level().addParticle(
                ParticleTypes.CLOUD,
                this.getX(),
                this.getY(),
                this.getZ(),
                0.0D, 0.0D, 0.0D
            );
        }
    }

    private boolean updateDrivingRangeLifetime() {
        if (this.isDrivingRangeBall && this.drivingRangeLifetime > 0) {
            if (this.getY() > this.maxHeight) {
                this.maxHeight = this.getY();
            }
            this.drivingRangeLifetime--;
            if (this.drivingRangeLifetime <= 0) {
                this.discard();
                return true;
            }
        }
        return false;
    }

    private Vec3 applyGravity(Vec3 motion) {
        if (this.isNoGravity()) {
            return motion;
        }
        return motion.add(0.0D, -0.035D, 0.0D);
    }

    private Vec3 applyAirDrag(Vec3 motion) {
        if (this.onGround()) {
            return motion;
        }
        return motion.scale(0.97D);
    }

    private Vec3 applyMagnusEffect(Vec3 motion) {
        if (this.onGround() || Math.abs(this.spin) < 0.001F) {
            return motion;
        }

        double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontalSpeed < 0.01D) {
            return motion;
        }

        Vec3 sideways = new Vec3(-motion.z, 0.0D, motion.x).normalize();
        double curveStrength = this.spin * horizontalSpeed * 0.04D;
        return motion.add(sideways.scale(curveStrength));
    }

    private Vec3 applyWind(Vec3 motion) {
        if (this.onGround()) {
            return motion;
        }

        int groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.getBlockX(), this.getBlockZ());
        if (this.getY() <= groundY + 3.0D) {
            return motion;
        }

        Vec3 windVelocity = GolfWind.getWindVelocity(this.level());
        if (windVelocity.lengthSqr() < 1.0E-6D) {
            return motion;
        }

        return motion.add(windVelocity.scale(WIND_INFLUENCE));
    }

    private void updateGlowUntilNearby() {
        if (!this.glowUntilNearby) {
            return;
        }

        this.setGlowingTag(true);
        Vec3 motion = this.getDeltaMovement();
        if (!this.onGround() || motion.lengthSqr() > GLOW_CHECK_SPEED_SQR) {
            if (this.glowTimeoutTicks > 0) {
                this.glowTimeoutTicks--;
            }
            if (this.glowTimeoutTicks <= 0) {
                this.glowUntilNearby = false;
                this.setGlowingTag(false);
            }
            return;
        }

        Player hitter = getLastHitterPlayer();
        if (hitter != null && hitter.distanceToSqr(this) <= 9.0D) {
            this.glowUntilNearby = false;
            this.glowTimeoutTicks = 0;
            this.setGlowingTag(false);
            return;
        }

        if (this.glowTimeoutTicks > 0) {
            this.glowTimeoutTicks--;
        }
        if (this.glowTimeoutTicks <= 0) {
            this.glowUntilNearby = false;
            this.setGlowingTag(false);
        }
    }

    private Vec3 handleWallCollisions(Vec3 posBefore, Vec3 motion) {
        Vec3 posAfter = this.position();
        Vec3 actualMovement = posAfter.subtract(posBefore);
        Vec3 intendedMovement = motion;

        BlockPos collisionPos = this.blockPosition();
        boolean hitFlagPole = this.level().getBlockState(collisionPos).is(GolfMod.GOLF_FLAG_BLOCK.get());
        double bounceMultiplier = hitFlagPole ? 0.15D : 0.6D;

        boolean hitWall = false;
        Vec3 reflectedMotion = motion;

        if (Math.abs(intendedMovement.x) > 0.001 && Math.abs(actualMovement.x) < Math.abs(intendedMovement.x) * 0.5) {
            reflectedMotion = new Vec3(-motion.x * bounceMultiplier, motion.y, motion.z);
            hitWall = true;
        }

        if (Math.abs(intendedMovement.z) > 0.001 && Math.abs(actualMovement.z) < Math.abs(intendedMovement.z) * 0.5) {
            reflectedMotion = new Vec3(reflectedMotion.x, motion.y, -motion.z * bounceMultiplier);
            hitWall = true;
        }

        if (hitWall) {
            this.setDeltaMovement(reflectedMotion);
            return reflectedMotion;
        }

        return motion;
    }

    private void handleGroundInteraction(Vec3 velocityBeforeMove) {
        BlockPos belowPos = this.getBlockPosBelowThatAffectsMyMovement();
        BlockState belowState = this.level().getBlockState(belowPos);

        Vec3 motion = this.getDeltaMovement();
        double friction = getGroundFriction(belowState);
        double bounce = getBounceMultiplier(belowState);

        if (velocityBeforeMove.y < -0.05D) {
            motion = new Vec3(motion.x * friction, -velocityBeforeMove.y * bounce, motion.z * friction);
            recordCarryDistance();
        } else {
            motion = new Vec3(motion.x * friction, motion.y * 0.1D, motion.z * friction);
        }

        if (motion.horizontalDistanceSqr() < 0.0001D && Math.abs(motion.y) < 0.005D) {
            motion = Vec3.ZERO;
        }

        this.setDeltaMovement(motion);

        maybeDisplayDrivingRangeStats();
        checkForHole();
    }

    private void recordCarryDistance() {
        if (this.isDrivingRangeBall && !this.hasBouncedOnce && this.drivingRangeLifetime > 0) {
            this.hasBouncedOnce = true;
            double dx = this.getX() - this.startX;
            double dz = this.getZ() - this.startZ;
            this.carryDistance = Math.sqrt(dx * dx + dz * dz);
        }
    }

    private void maybeDisplayDrivingRangeStats() {
        if (this.isDrivingRangeBall && !this.statsRecorded && this.drivingRangeLifetime > 0) {
            Vec3 currentMotion = this.getDeltaMovement();
            double currentSpeed = currentMotion.lengthSqr();
            if (currentSpeed < 0.001D && this.onGround()) {
                this.statsRecorded = true;
                displayDrivingRangeStats();
            }
        }
    }

    private double getGroundFriction(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key.getPath();

        if (path.contains("putting_green")) {
            return 0.95D;
        }

        if (path.contains("fairway")) {
            return 0.9D;
        }

        if (path.contains("rough")) {
            return 0.7D;
        }

        if (path.contains("sand") || path.contains("gravel")) {
            return 0.5D;
        }

        if (path.contains("ice")) {
            return 0.99D;
        }

        return 0.9D;
    }

    private double getBounceMultiplier(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = key.getPath();

        if (path.contains("putting_green")) {
            return 0.55D;
        }

        if (path.contains("fairway")) {
            return 0.55D;
        }

        if (path.contains("rough")) {
            return 0.4D;
        }

        if (path.contains("sand")) {
            return 0.0D;
        }

        if (path.contains("dirt") || path.contains("grass") || path.contains("soul")) {
            return 0.3D;
        }

        if (path.contains("concrete")) {
            return 0.9D;
        }

        if (path.contains("stone") || path.contains("brick")) {
            return 0.8D;
        }

        if (path.contains("wood") || path.contains("plank")) {
            return 0.8D;
        }

        return 0.7D;
    }

    private void checkForHole() {
        BlockPos pos = this.blockPosition();
        BlockState state = this.level().getBlockState(pos);

        if (!state.is(GolfMod.GOLF_FLAG_BLOCK.get())) {
            return;
        }

        Vec3 holeCenter = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        double horizontalDistSqr = Math.pow(this.getX() - holeCenter.x, 2) + Math.pow(this.getZ() - holeCenter.z, 2);
        
        Vec3 motion = this.getDeltaMovement();
        double speedSqr = motion.lengthSqr();
        boolean isInAir = !this.onGround();

        if (speedSqr <= HOLE_STOPPED_SPEED_THRESHOLD_SQR && horizontalDistSqr <= HOLE_STOPPED_RADIUS_SQR) {
            if (this.getY() <= pos.getY() + 0.8D) {
                onScored();
            }
            return;
        }

        if (horizontalDistSqr > HOLE_RADIUS_SQR) {
            return;
        }

        if (isInAir) {
            if (speedSqr > HOLE_AIR_SPEED_THRESHOLD_SQR) {
                return;
            }
        } else {
            if (speedSqr > HOLE_ROLLING_SPEED_THRESHOLD_SQR) {
                return;
            }
        }

        if (this.getY() <= pos.getY() + 0.8D) {
            onScored();
        }
    }

    private void onScored() {
        if (!this.level().isClientSide) {
            Player player = getLastHitterPlayer();
            if (player != null) {
                int hits = Math.max(this.strokes, 1);
                player.displayClientMessage(
                        Component.literal("Ball in the hole in " + hits + " hits."),
                        false
                );
            }
        }

        this.discard();
    }

    private Player getLastHitterPlayer() {
        if (this.lastHitter == null || !(this.level() instanceof Level level)) {
            return null;
        }
        return level.getPlayerByUUID(this.lastHitter);
    }
    
    private void displayDrivingRangeStats() {
        Player player = getLastHitterPlayer();
        if (player == null) {
            return;
        }
        
        double dx = this.getX() - this.startX;
        double dz = this.getZ() - this.startZ;
        double totalDistance = Math.sqrt(dx * dx + dz * dz);

        double heightGained = this.maxHeight - this.startY;

        player.displayClientMessage(
            Component.literal("§6=== Driving Range Stats ===")
                .append(Component.literal("\n§eError: §f" + String.format("%.0f", this.lastShotErrorPercent) + "%"))
                .append(Component.literal("\n§eMax Height: §f" + String.format("%.1f", heightGained) + "m"))
                .append(Component.literal("\n§eCarry Distance: §f" + String.format("%.1f", this.carryDistance) + "m"))
                .append(Component.literal("\n§eTotal Distance: §f" + String.format("%.1f", totalDistance) + "m")),
            false
        );
    }

    @Override
    public ItemStack getItem() {
        return GolfMod.GOLF_BALL.get().getDefaultInstance();
    }
}
