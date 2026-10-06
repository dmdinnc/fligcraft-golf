package com.ziggleflig.golf.entity;

import com.ziggleflig.golf.GolfMod;

import net.minecraft.nbt.CompoundTag;
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
    private static final double SEAT_HEIGHT = 0.5625D;
    private static final double SEAT_OFFSET = 0.35D;

    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

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
        this.lerpSteps = steps;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide && !this.isControlledByLocalInstance()) {
            handleClientLerp();
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
        this.move(MoverType.SELF, motion);
    }

    private Vec3 applyGravity(Vec3 motion) {
        if (this.onGround()) {
            if (motion.y < 0.0D) {
                return new Vec3(motion.x, 0.0D, motion.z);
            }
            return motion;
        }
        return motion.add(0.0D, -0.08D, 0.0D);
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
        if (this.lerpSteps <= 0) {
            return;
        }
        double newX = this.getX() + (this.lerpX - this.getX()) / (double)this.lerpSteps;
        double newY = this.getY() + (this.lerpY - this.getY()) / (double)this.lerpSteps;
        double newZ = this.getZ() + (this.lerpZ - this.getZ()) / (double)this.lerpSteps;
        this.setPos(newX, newY, newZ);
        this.lerpSteps--;
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
