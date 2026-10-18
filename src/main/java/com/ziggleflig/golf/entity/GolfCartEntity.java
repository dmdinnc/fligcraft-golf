package com.ziggleflig.golf.entity;

import com.ziggleflig.golf.GolfMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GolfCartEntity extends VehicleEntity {
    private static final double MAX_SPEED = 0.6D;
    private static final double ACCELERATION = 0.023D;
    private static final double FRICTION = 0.8D;
    private static final double LATERAL_FRICTION = 0.25D;
    private static final double GRAVITY = 0.08D;
    private static final double MAX_GROUND_MOVE = 0.25D;
    private static final double SEAT_HEIGHT = 0.5625D;
    private static final double SEAT_OFFSET = 0.35D;

    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private float lerpXRot;
    private int lerpSteps;
    private ServerEntity movementTracker;
    private boolean pendingControlSync;

    public GolfCartEntity(EntityType<? extends GolfCartEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected Item getDropItem() {
        return GolfMod.GOLF_CART_ITEM.get();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity tracker) {
        this.movementTracker = tracker;
        return super.getAddEntityPacket(tracker);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        LivingEntity previousDriver = this.getControllingPassenger();
        super.addPassenger(passenger);
        if (previousDriver != this.getControllingPassenger()) { this.pendingControlSync = true; }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        LivingEntity previousDriver = this.getControllingPassenger();
        super.removePassenger(passenger);
        if (previousDriver != this.getControllingPassenger()) { this.pendingControlSync = true; }
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        int index = Math.max(this.getPassengers().indexOf(passenger), 0);
        double offset = index == 0 ? SEAT_OFFSET : -SEAT_OFFSET;
        return new Vec3(offset, SEAT_HEIGHT, 0.0D)
            .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    // Rotation-only packets use these accessors to retain an unfinished position update.
    @Override public double lerpTargetX() { return this.lerpSteps > 0 ? this.lerpX : this.getX(); }
    @Override public double lerpTargetY() { return this.lerpSteps > 0 ? this.lerpY : this.getY(); }
    @Override public double lerpTargetZ() { return this.lerpSteps > 0 ? this.lerpZ : this.getZ(); }
    @Override public float lerpTargetYRot() { return this.lerpSteps > 0 ? this.lerpYRot : this.getYRot(); }
    @Override public float lerpTargetXRot() { return this.lerpSteps > 0 ? this.lerpXRot : this.getXRot(); }

    @Override
    public void tick() {
        super.tick();

        handleClientLerp();
        if (!this.isControlledByLocalInstance()) {
            // The driver sends vanilla vehicle movement packets. A second server-side
            // simulation advances the collision box beyond the position the driver sees.
            this.setDeltaMovement(Vec3.ZERO);
            syncControlHandoff();
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        motion = applyGravity(motion);

        Entity controller = this.getControllingPassenger();
        if (controller instanceof Player player) {
            motion = applyPlayerControl(motion, player);
        } else {
            motion = applyIdleFriction(motion);
        }

        this.setDeltaMovement(motion);
        moveCart(motion);
        syncControlHandoff();
    }

    private void syncControlHandoff() {
        if (!this.pendingControlSync || this.movementTracker == null || !(this.level() instanceof ServerLevel level)) { return; }
        this.pendingControlSync = false;
        // Wait until the dismount is complete. Flush passengers and the tracker's relative
        // position base before the absolute snapshot, or the next delta would be counted twice.
        this.movementTracker.sendChanges();
        level.getChunkSource().broadcastAndSend(this, new ClientboundTeleportEntityPacket(this));
        if (this.getControllingPassenger() instanceof ServerPlayer driver) {
            // The new driver ignores ordinary entity interpolation packets while in control.
            driver.connection.send(new ClientboundMoveVehiclePacket(this));
        }
    }

    private Vec3 applyGravity(Vec3 motion) {
        if (this.onGround()) {
            if (motion.y <= 0.0D) {
                // A downward contact keeps vanilla's grounded/step-up state valid every tick.
                return new Vec3(motion.x, -GRAVITY, motion.z);
            }
            return motion;
        }
        return motion.add(0.0D, -GRAVITY, 0.0D);
    }

    private void moveCart(Vec3 motion) {
        if (!this.onGround() || this.noPhysics) {
            this.move(MoverType.SELF, motion);
            return;
        }
        int steps = Math.max(1, Mth.ceil(motion.horizontalDistance() / MAX_GROUND_MOVE));
        int axes = Math.abs(motion.x) > 0.00001 && Math.abs(motion.z) > 0.00001 ? 2 : 1;
        if (steps == 1 && axes == 1) {
            this.move(MoverType.SELF, motion);
            return;
        }
        // At full speed one move can cross two risers. Vanilla accepts the first partial
        // step, then zeroes the clipped velocity. Short moves finish each riser; separate
        // axes also finish both rises at a diagonal corner instead of accepting half a step.
        boolean zFirst = Math.abs(motion.z) > Math.abs(motion.x);
        double horizontalTime = 1.0D / steps, verticalTime = horizontalTime / axes;
        boolean hitWall = false;
        Vec3 velocity = motion;
        for (int step = 0; step < steps; step++) {
            for (int axis = 0; axis < axes; axis++) {
                boolean alongZ = axis == 0 ? zFirst : !zFirst;
                Vec3 movement = axes == 1 ? velocity.scale(horizontalTime)
                    : new Vec3(alongZ ? 0 : velocity.x * horizontalTime, velocity.y * verticalTime,
                        alongZ ? velocity.z * horizontalTime : 0);
                this.setDeltaMovement(velocity);
                this.move(MoverType.SELF, movement);
                hitWall |= this.horizontalCollision;
                if (step + 1 < steps || axis + 1 < axes) {
                    Vec3 after = this.getDeltaMovement();
                    // Ground contact must persist between moves. Preserve upward block impulses.
                    double y = after.y == 0 && velocity.y < 0 ? velocity.y : after.y;
                    // Apply the block speed factor once per tick, keeping collision clipping
                    // and other block effects from each move.
                    double factor = this.getBlockSpeedFactor();
                    velocity = factor > 0 ? new Vec3(after.x / factor, y, after.z / factor) : new Vec3(after.x, y, after.z);
                }
            }
        }
        this.horizontalCollision = hitWall;
    }

    private Vec3 applyPlayerControl(Vec3 motion, Player player) {
        this.setYRot(player.getYRot());
        this.yRotO = this.getYRot();
        this.setXRot(0.0F);

        float forward = player.zza;
        float strafe = player.xxa;
        Vec3 input = new Vec3(strafe, 0.0D, forward);

        if (input.lengthSqr() > 1.0E-3D) {
            Vec3 desired = input.normalize().scale(MAX_SPEED)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
            Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
            Vec3 delta = desired.subtract(horizontal);
            double accel = ACCELERATION;
            if (delta.lengthSqr() > accel * accel) {
                delta = delta.normalize().scale(accel);
            }
            horizontal = horizontal.add(delta);
            Vec3 forwardDir = new Vec3(0.0D, 0.0D, 1.0D)
                .yRot(-this.getYRot() * Mth.DEG_TO_RAD);
            double forwardSpeed = horizontal.dot(forwardDir);
            Vec3 forwardVel = forwardDir.scale(forwardSpeed);
            Vec3 sidewaysVel = horizontal.subtract(forwardVel).scale(LATERAL_FRICTION);
            horizontal = forwardVel.add(sidewaysVel);
            motion = new Vec3(horizontal.x, motion.y, horizontal.z);
            return clampHorizontalSpeed(motion, MAX_SPEED);
        }

        return applyIdleFriction(motion);
    }

    private Vec3 applyIdleFriction(Vec3 motion) {
        return motion.multiply(FRICTION, 1.0D, FRICTION);
    }

    private Vec3 clampHorizontalSpeed(Vec3 motion, double maxSpeed) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal > maxSpeed) {
            double scale = maxSpeed / horizontal;
            return new Vec3(motion.x * scale, motion.y, motion.z * scale);
        }
        return motion;
    }

    private void handleClientLerp() {
        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            // Relative updates are ignored while driving. Keep their base current so
            // the first packet after dismount is not decoded from the pre-mount position.
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return entity.canBeCollidedWith() || entity.isPushable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public float getPickRadius() {
        return 0.45F;
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose);
    }
}
