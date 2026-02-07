package com.ziggleflig.golf.item;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ziggleflig.golf.GolfWind;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.network.ShotAccuracyPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class GolfClubItem extends Item {

    private final double basePower;
    private final double loft;
    private final boolean isPutter;
    private static final float CHIP_CHARGE_CUTOFF = 0.35F;
    
    public static final class ShotData {
        private final float charge;
        private final float yaw;
        private final GolfBallEntity targetBall;
        private final long startTime;

        ShotData(float charge, float yaw, GolfBallEntity targetBall) {
            this.charge = charge;
            this.yaw = yaw;
            this.targetBall = targetBall;
            this.startTime = System.currentTimeMillis();
        }

        public float charge() {
            return this.charge;
        }

        public float yaw() {
            return this.yaw;
        }

        public GolfBallEntity targetBall() {
            return this.targetBall;
        }

        public long startTime() {
            return this.startTime;
        }
    }
    
    private static final Map<UUID, ShotData> pendingShotsClient = new HashMap<>();
    private static final Map<UUID, ShotData> pendingShotsServer = new HashMap<>();

    public GolfClubItem(Properties properties, double basePower, double loft) {
        this(properties, basePower, loft, false);
    }

    public GolfClubItem(Properties properties, double basePower, double loft, boolean isPutter) {
        super(properties);
        this.basePower = basePower;
        this.loft = loft;
        this.isPutter = isPutter;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }

        int used = this.getUseDuration(stack, player) - timeLeft;
        if (used < 3) {
            return;
        }

        float charge = Math.min(1.0F, used / 60.0F);

        GolfBallEntity target = findNearestBall(level, player);
        if (target == null) {
            return;
        }

        getPendingShots(level).put(player.getUUID(), new ShotData(charge, player.getYRot(), target));

        if (!level.isClientSide) {
            player.displayClientMessage(
                Component.literal("Power set! Left-click to shoot."),
                true
            );
        }
    }
    
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        ShotData shotData = getPendingShots(player.level()).get(player.getUUID());
        if (shotData != null) {
            if (player.level().isClientSide) {
                sendPendingShot(player, shotData);
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, 
                            net.minecraft.core.BlockPos pos, LivingEntity entity) {
        if (entity instanceof Player player) {
            ShotData shotData = getPendingShots(level).get(player.getUUID());
            if (shotData != null) {
                if (level.isClientSide) {
                    sendPendingShot(player, shotData);
                }
                return true;
            }
        }
        return super.mineBlock(stack, level, state, pos, entity);
    }
    
    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, 
                                 net.minecraft.core.BlockPos pos, Player player) {
        ShotData shotData = getPendingShots(level).get(player.getUUID());
        return shotData == null;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(livingEntity instanceof Player player)) {
            return;
        }

        if (level.getGameTime() % 10 != 0) {
            return;
        }

        if (findNearestBall(level, player) == null) {
            return;
        }

        double windSpeed = GolfWind.getWindSpeedMph(level);
        String windDirection = GolfWind.getWindDirectionText(level);
        player.displayClientMessage(
            Component.literal(String.format("Wind: %.1f mph %s", windSpeed, windDirection)),
            true
        );
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player) || !isSelected) {
            return;
        }

        ShotData shotData = getPendingShots(level).get(player.getUUID());
        if (shotData == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - shotData.startTime();
        if (level.isClientSide) {
            if (elapsed > 6000) {
                pendingShotsClient.remove(player.getUUID());
            }
            return;
        }

        if (elapsed <= 6000 && level.getGameTime() % 10 == 0) {
            double windSpeed = GolfWind.getWindSpeedMph(level);
            String windDirection = GolfWind.getWindDirectionText(level);
            player.displayClientMessage(
                Component.literal(String.format("Wind: %.1f mph %s | Left-click to shoot!", windSpeed, windDirection)),
                true
            );
        }

        if (elapsed > 6000) {
            pendingShotsServer.remove(player.getUUID());
            if (shotData.targetBall() != null && shotData.targetBall().isAlive()) {
                executeShotWithAccuracy(player, shotData, elapsed);
            }
        }
    }

    private GolfBallEntity findNearestBall(Level level, Player player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(look.scale(4.0D));

        AABB searchBox = new AABB(eye, reach).inflate(0.75D);
        List<GolfBallEntity> balls = level.getEntitiesOfClass(GolfBallEntity.class, searchBox, Entity::isAlive);

        if (balls.isEmpty()) {
            return null;
        }

        UUID playerId = player.getUUID();
        List<GolfBallEntity> ownedBalls = balls.stream()
                .filter(ball -> playerId.equals(ball.getLastHitter()))
                .toList();

        List<GolfBallEntity> candidates = ownedBalls.isEmpty() ? balls : ownedBalls;

        return candidates.stream()
                .min(Comparator.comparingDouble(ball -> ball.distanceToSqr(player)))
                .orElse(null);
    }
    
    private void executeShotWithAccuracy(Player player, ShotData shotData, long elapsedMs) {
        GolfBallEntity target = shotData.targetBall();

        target.registerStroke(player);
        
        float accuracy = calculateAccuracy(elapsedMs);

        float yaw = shotData.yaw();
        
        float sliderPos = calculateSliderPosition(elapsedMs);
        float errorPercent = (1.0f - accuracy) * 100.0f;

        float errorScale;
        if (errorPercent <= 50.0f) {
            errorScale = 0.1f * (errorPercent / 50.0f);
        } else {
            errorScale = 0.1f + (0.9f * ((errorPercent - 50.0f) / 50.0f));
        }

        float maxErrorDegrees = 15.0f;
        float errorDegrees = sliderPos * maxErrorDegrees * errorScale;
        float adjustedYaw = yaw + errorDegrees;
        
        double yawRad = Math.toRadians(adjustedYaw);
        Vec3 horizontalDir = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        
        float charge = shotData.charge();
        float weightedCharge = applyWeightedCharge(charge);
        double basePowerCalc = this.basePower * (0.2D + 0.8D * weightedCharge);
        
        double powerMultiplier = 0.7D + (0.3D * accuracy);
        double power = basePowerCalc * powerMultiplier;
        
        if (target.isOnTee()) {
            power *= 1.10D;
            target.clearTee();
            player.displayClientMessage(
                Component.literal("Tee shot! +10% distance"),
                true
            );
        }

        double horizontalPower = power * Math.cos(this.loft);
        double verticalPower = power * Math.sin(this.loft);

        Vec3 velocity = new Vec3(
            horizontalDir.x * horizontalPower,
            verticalPower,
            horizontalDir.z * horizontalPower
        );

        target.startGlowUntilNearby();
        float spinStrength = Math.min(1.0f, errorPercent / 100.0f);
        float maxSpin = 1.0f;
        target.setSpin(sliderPos * spinStrength * maxSpin);
        target.setLastShotErrorPercent(errorPercent);
        target.setPuttShot(this.isPutter);

        target.setDeltaMovement(velocity);

        String accuracyText = String.format("Accuracy: %.0f%%", accuracy * 100);
        player.displayClientMessage(
            Component.literal(accuracyText),
            true
        );
    }
    
    private float calculateSliderPosition(long elapsedMs) {
        float cycleTime = (elapsedMs % 2000) / 2000.0f;
        if (cycleTime < 0.5f) {
            return (cycleTime * 4.0f) - 1.0f;
        } else {
            return 3.0f - (cycleTime * 4.0f);
        }
    }
    
    private float calculateAccuracy(long elapsedMs) {
        if (elapsedMs > 6000) {
            return 0.1f;
        }
        float sliderPos = calculateSliderPosition(elapsedMs);
        float distanceFromCenter = Math.abs(sliderPos);
        return 1.0f - distanceFromCenter;
    }

    private float applyWeightedCharge(float charge) {
        if (charge <= 0.0f) {
            return 0.0f;
        }
        if (charge >= 1.0f) {
            return 1.0f;
        }
        if (charge <= CHIP_CHARGE_CUTOFF) {
            float t = charge / CHIP_CHARGE_CUTOFF;
            return CHIP_CHARGE_CUTOFF * t * t;
        }
        return charge;
    }
    
    public static ShotData getPendingShot(Player player) {
        return getPendingShots(player.level()).get(player.getUUID());
    }
    
    public static void clearPendingShot(UUID playerId) {
        pendingShotsClient.remove(playerId);
        pendingShotsServer.remove(playerId);
    }
    
    public static void executePendingShot(Player player) {
        ShotData shotData = pendingShotsServer.remove(player.getUUID());
        if (shotData != null && shotData.targetBall() != null && shotData.targetBall().isAlive()) {
            long elapsed = System.currentTimeMillis() - shotData.startTime();
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof GolfClubItem club) {
                club.executeShotWithAccuracy(player, shotData, elapsed);
            }
        }
    }

    public static void executePendingShot(Player player, long elapsedMs) {
        ShotData shotData = pendingShotsServer.remove(player.getUUID());
        if (shotData != null && shotData.targetBall() != null && shotData.targetBall().isAlive()) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof GolfClubItem club) {
                club.executeShotWithAccuracy(player, shotData, Math.max(0L, elapsedMs));
            }
        }
    }

    public static void handleClientLeftClick(Player player) {
        ShotData shotData = pendingShotsClient.get(player.getUUID());
        if (shotData != null) {
            sendPendingShot(player, shotData);
        }
    }

    private static void sendPendingShot(Player player, ShotData shotData) {
        long elapsed = System.currentTimeMillis() - shotData.startTime();
        PacketDistributor.sendToServer(new ShotAccuracyPayload(Math.max(0L, elapsed)));
        pendingShotsClient.remove(player.getUUID());
    }

    private static Map<UUID, ShotData> getPendingShots(Level level) {
        return level.isClientSide ? pendingShotsClient : pendingShotsServer;
    }
}
