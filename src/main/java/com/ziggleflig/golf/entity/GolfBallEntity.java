package com.ziggleflig.golf.entity;

import java.util.UUID;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.GolfModConfig;
import com.ziggleflig.golf.GolfWind;
import com.ziggleflig.golf.item.GolfBallItem;
import com.ziggleflig.golf.tracker.GolfBallTrackerData;
import com.ziggleflig.golf.terrain.SculptedBallPhysics;
import com.ziggleflig.golf.terrain.GolfSurfacePhysics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class GolfBallEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(
        GolfBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHOT_NUMBER = SynchedEntityData.defineId(
        GolfBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PUTT_SHOT = SynchedEntityData.defineId(
        GolfBallEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int TRACKING_RANGE_CHUNKS = 32;
    public static final double RENDER_DISTANCE = TRACKING_RANGE_CHUNKS * 16.0;
    private final GolfBallFlightTrail flightTrail = new GolfBallFlightTrail();

    private final GolfCupCapture cupCapture = new GolfCupCapture();
    private static final double WIND_INFLUENCE = 0.0125D;
    private static final double GLOW_CHECK_SPEED_SQR = 0.0025D;
    private static final double WATER_STOP_SPEED_SQR = 0.0004D;

    private int strokes;
    private UUID lastHitter;
    private boolean registeredForTracking;
    // Full flight drag ends at the first ground contact; short rebounds should not erase runout.
    private boolean hasLanded;
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
    private boolean hasLastHitPosition;
    private double lastHitX;
    private double lastHitY;
    private double lastHitZ;

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

    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        // Vanilla scales this cutoff with hitbox size, hiding a quarter-block ball at about 16 blocks.
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }
    public GolfBallFlightTrail flightTrail() { return flightTrail; }
    
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = steps;
    }

    public void registerStroke(Player player) {
        this.hasLanded = false;
        this.strokes++;
        this.entityData.set(SHOT_NUMBER, this.strokes);
        this.lastHitter = player.getUUID();
        this.lastHitX = this.getX();
        this.lastHitY = this.getY();
        this.lastHitZ = this.getZ();
        this.hasLastHitPosition = true;

        if (this.isDrivingRangeBall && this.drivingRangeLifetime == 0) {
            this.drivingRangeLifetime = 600;
            this.startX = this.getX();
            this.startY = this.getY();
            this.startZ = this.getZ();
            this.maxHeight = this.startY;
            this.hasBouncedOnce = false;
            this.statsRecorded = false;
        }
        updateTracking();
    }

    public UUID getLastHitter() {
        return this.lastHitter;
    }
    
    public void setLastHitter(UUID uuid) {
        this.lastHitter = uuid;
        updateTracking();
    }

    public int getStrokes() {
        return this.strokes;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, DyeColor.WHITE.getId());
        builder.define(SHOT_NUMBER, 0);
        builder.define(PUTT_SHOT, false);
    }

    public DyeColor getColor() {
        return DyeColor.byId(this.entityData.get(COLOR));
    }

    public void setColor(DyeColor color) {
        this.entityData.set(COLOR, color.getId());
    }

    @Override
    public int getTeamColor() {
        return getColor().getTextColor();
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

    public void setPuttShot(boolean puttShot) {
        this.entityData.set(PUTT_SHOT, puttShot);
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
        setColor(tag.contains("BallColor") ? DyeColor.byId(tag.getInt("BallColor")) : DyeColor.WHITE);
        this.strokes = tag.getInt("Strokes");
        this.entityData.set(SHOT_NUMBER, this.strokes);
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
        this.hasLanded = tag.getBoolean("HasLanded");
        this.carryDistance = tag.getDouble("CarryDistance");
        this.statsRecorded = tag.getBoolean("StatsRecorded");
        this.lastShotErrorPercent = tag.getFloat("LastShotErrorPercent");
        this.spin = tag.getFloat("Spin");
        setPuttShot(tag.getBoolean("PuttShot"));
        this.hasLastHitPosition = tag.getBoolean("HasLastHitPosition");
        this.lastHitX = tag.getDouble("LastHitX");
        this.lastHitY = tag.getDouble("LastHitY");
        this.lastHitZ = tag.getDouble("LastHitZ");
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
        tag.putInt("BallColor", getColor().getId());
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
        tag.putBoolean("HasLanded", this.hasLanded);
        tag.putDouble("CarryDistance", this.carryDistance);
        tag.putBoolean("StatsRecorded", this.statsRecorded);
        tag.putFloat("LastShotErrorPercent", this.lastShotErrorPercent);
        tag.putFloat("Spin", this.spin);
        tag.putBoolean("PuttShot", this.entityData.get(PUTT_SHOT));
        tag.putBoolean("HasLastHitPosition", this.hasLastHitPosition);
        tag.putDouble("LastHitX", this.lastHitX);
        tag.putDouble("LastHitY", this.lastHitY);
        tag.putDouble("LastHitZ", this.lastHitZ);
        tag.putBoolean("GlowUntilNearby", this.glowUntilNearby);
        tag.putInt("GlowTicks", this.glowTimeoutTicks);
        updateTracking();
    }

    @Override
    public void tick() {
        super.tick();
        handleClientLerp();

        if (this.level().isClientSide) {
            flightTrail.update(this.tickCount, this.position(), this.entityData.get(SHOT_NUMBER), this.entityData.get(PUTT_SHOT));
            return;
        }

        // An entity can briefly remain in memory while its chunk becomes inaccessible.
        if (this.level() instanceof ServerLevel serverLevel && GolfBallTrackerData.get(serverLevel).isDeleted(this.getUUID())) {
            this.discard();
            return;
        }

        if (updateDrivingRangeLifetime()) {
            return;
        }

        updateGlowUntilNearby();

        double airRetention = this.onGround() ? 1.0 : this.hasLanded ? 0.995 : 0.97;
        Vec3 motion = applyWind(applyMagnusEffect(this.getDeltaMovement().scale(airRetention)));
        double gravity = this.isNoGravity() ? 0 : 0.035 * airRetention;
        var result = SculptedBallPhysics.move(this, motion, gravity, GolfSurfacePhysics::response, this::onPhysicsMove);
        if (result.touchedGround()) { recordCarryDistance(); maybeDisplayDrivingRangeStats(); }

        maybeReturnFromWater();
        if (this.tickCount % 20 == 0) { updateTracking(); }
    }

    private void updateTracking() {
        if (this.registeredForTracking && this.level() instanceof ServerLevel serverLevel) {
            GolfBallTrackerData.get(serverLevel).track(this);
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        // NeoForge's isAddedToLevel can reset during a temporary chunk visibility change.
        this.registeredForTracking = true;
        updateTracking();
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

    protected Vec3 applyWind(Vec3 motion) {
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

    private void maybeReturnFromWater() {
        if (!GolfModConfig.COMMON.returnBallFromWater.get()) {
            return;
        }

        if (!this.hasLastHitPosition || !this.isInWaterOrBubble()) {
            return;
        }

        if (this.getDeltaMovement().lengthSqr() > WATER_STOP_SPEED_SQR) {
            return;
        }

        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.lastHitX, this.lastHitY, this.lastHitZ);
    }

    private void recordCarryDistance() {
        this.hasLanded = true;
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

    /** Called at each physics movement, including the contact position before its bounce response. */
    protected void onPhysicsMove(Vec3 previousPosition, Vec3 approachVelocity, double elapsedTicks) {
        if (this.isAlive() && cupCapture.update(this, previousPosition, approachVelocity, elapsedTicks)) { onScored(); }
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
                grantGolfBalls(player, 1);
            }
        }

        this.discard();
    }

    private void grantGolfBalls(Player player, int count) {
        if (count <= 0) {
            return;
        }

        ItemStack ballStack = getItem();
        int maxStack = ballStack.getMaxStackSize();
        int remaining = count;

        while (remaining > 0) {
            int stackCount = Math.min(remaining, maxStack);
            ItemStack stack = ballStack.copy();
            stack.setCount(stackCount);
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackCount;
        }
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
        ItemStack stack = GolfMod.GOLF_BALL.get().getDefaultInstance();
        GolfBallItem.setColor(stack, getColor());
        return stack;
    }

    public void returnToPlayer(Player player) {
        ItemStack stack = getItem();
        discard();
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}
