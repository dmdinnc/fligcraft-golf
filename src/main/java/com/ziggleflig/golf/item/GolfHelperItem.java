package com.ziggleflig.golf.item;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.GolfWind;
import com.ziggleflig.golf.entity.GolfBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class GolfHelperItem extends Item {
    private static final double BALL_SEARCH_RADIUS = 1000.0D;
    private static final int FLAG_SEARCH_RADIUS = 200;
    private static final int FLAG_SEARCH_RADIUS_SQR = FLAG_SEARCH_RADIUS * FLAG_SEARCH_RADIUS;

    public GolfHelperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        List<GolfBallEntity> playerBalls = getPlayerBalls(level, player);

        if (player.isShiftKeyDown()) {
            if (playerBalls.isEmpty()) {
                showNoBallsMessage(player);
            } else {
                showBallList(player, playerBalls, level);
            }
        } else {
            showSummary(player, playerBalls, level);
        }

        return InteractionResultHolder.success(stack);
    }

    public static List<GolfBallEntity> getPlayerBalls(Level level, Player player) {
        return level.getEntitiesOfClass(
            GolfBallEntity.class,
            player.getBoundingBox().inflate(BALL_SEARCH_RADIUS),
            ball -> ball.getLastHitter() != null && ball.getLastHitter().equals(player.getUUID())
        );
    }

    public static void showBallList(Player player, List<GolfBallEntity> balls, Level level) {
        player.displayClientMessage(
            Component.literal("=== Golf Helper: Tracked Balls ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            false
        );

        List<GolfBallEntity> sortedBalls = balls.stream()
                .sorted(Comparator.comparingDouble(b -> b.distanceToSqr(player)))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedBalls.size(); i++) {
            GolfBallEntity ball = sortedBalls.get(i);
            Vec3 pos = ball.position();
            double distance = Math.sqrt(ball.distanceToSqr(player));
            boolean inWater = ball.isInWaterOrBubble();

            MutableComponent message = Component.literal(String.format("#%d: ", i + 1))
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(String.format("(%.1f, %.1f, %.1f) ", pos.x, pos.y, pos.z))
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format("%.1fm away ", distance))
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(inWater ? "[WATER] " : "[LAND] ")
                            .withStyle(inWater ? ChatFormatting.AQUA : ChatFormatting.GREEN))
                    .append(Component.literal("[DELETE]")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                            "/golf_delete_ball " + ball.getId()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                            Component.literal("Click to delete this ball")))));

            player.displayClientMessage(message, false);
        }

        player.displayClientMessage(
            Component.literal(String.format("Total: %d ball(s)", balls.size())).withStyle(ChatFormatting.AQUA),
            false
        );
        
        MutableComponent deleteAllButton = Component.literal("[DELETE ALL MY BALLS]")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/golf_delete_all_mine"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                Component.literal("Click to delete all your golf balls"))));
        
        player.displayClientMessage(deleteAllButton, false);
        
        if (player.hasPermissions(2)) {
            int totalBalls = level.getEntitiesOfClass(GolfBallEntity.class, 
                    player.getBoundingBox().inflate(1000.0D)).size();
            
            MutableComponent deleteAllTrackedButton = Component.literal("[DELETE ALL TRACKED BALLS (" + totalBalls + " total)]")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/golf_delete_all_tracked"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                                    Component.literal("OP ONLY: Delete ALL golf balls in range"))));
            
            player.displayClientMessage(deleteAllTrackedButton, false);
        }
    }

    private void showSummary(Player player, List<GolfBallEntity> balls, Level level) {
        player.displayClientMessage(
            Component.literal("=== Golf Helper ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            false
        );

        player.displayClientMessage(
            Component.literal(String.format("Tracked balls: %d", balls.size())).withStyle(ChatFormatting.AQUA),
            false
        );

        if (balls.isEmpty()) {
            showNoBallsMessage(player);
        } else {
            showDirectionsToNearest(player, balls);
        }

        double windSpeed = GolfWind.getWindSpeedMph(level);
        String windDirection = GolfWind.getWindDirectionText(level);
        player.displayClientMessage(
            Component.literal(String.format("Wind: %.1f mph %s", windSpeed, windDirection))
                .withStyle(ChatFormatting.BLUE),
            false
        );

        double flagDistance = findNearestFlagDistance(level, player);
        if (flagDistance < 0.0D) {
            player.displayClientMessage(
                Component.literal("No flag within 200m.").withStyle(ChatFormatting.GRAY),
                false
            );
        } else {
            player.displayClientMessage(
                Component.literal(String.format("Nearest flag: %.0fm", flagDistance)).withStyle(ChatFormatting.GREEN),
                false
            );
        }

        if (!balls.isEmpty()) {
            MutableComponent manageButton = Component.literal("[MANAGE BALLS]")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/golf_helper_manage"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to manage tracked balls"))));
            player.displayClientMessage(manageButton, false);
        }
    }

    public static void showNoBallsMessage(Player player) {
        player.displayClientMessage(
            Component.literal("No golf balls found that you've hit.").withStyle(ChatFormatting.RED),
            false
        );
    }

    private void showDirectionsToNearest(Player player, List<GolfBallEntity> balls) {
        GolfBallEntity nearest = balls.stream()
                .min(Comparator.comparingDouble(b -> b.distanceToSqr(player)))
                .orElse(null);

        if (nearest == null) {
            return;
        }

        Vec3 playerPos = player.position();
        Vec3 ballPos = nearest.position();
        Vec3 direction = ballPos.subtract(playerPos);
        double distance = Math.sqrt(nearest.distanceToSqr(player));

        double dx = direction.x;
        double dz = direction.z;
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        if (angle < 0) angle += 360;

        String cardinalDirection = getCardinalDirection(angle);
        
        String verticalDir = "";
        if (direction.y > 2.0) {
            verticalDir = " (above you)";
        } else if (direction.y < -2.0) {
            verticalDir = " (below you)";
        }

        player.displayClientMessage(
            Component.literal("Nearest Ball: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(String.format("%.1fm %s%s", distance, cardinalDirection, verticalDir))
                            .withStyle(ChatFormatting.WHITE)),
            false
        );

        player.displayClientMessage(
            Component.literal(String.format("Coordinates: %.1f, %.1f, %.1f", ballPos.x, ballPos.y, ballPos.z))
                    .withStyle(ChatFormatting.GRAY),
            false
        );
    }

    private String getCardinalDirection(double angle) {
        if (angle >= 337.5 || angle < 22.5) return "South";
        if (angle >= 22.5 && angle < 67.5) return "Southwest";
        if (angle >= 67.5 && angle < 112.5) return "West";
        if (angle >= 112.5 && angle < 157.5) return "Northwest";
        if (angle >= 157.5 && angle < 202.5) return "North";
        if (angle >= 202.5 && angle < 247.5) return "Northeast";
        if (angle >= 247.5 && angle < 292.5) return "East";
        if (angle >= 292.5 && angle < 337.5) return "Southeast";
        return "Unknown";
    }

    private double findNearestFlagDistance(Level level, Player player) {
        BlockPos origin = player.blockPosition();
        Vec3 playerPos = player.position();
        double bestDistSqr = Double.MAX_VALUE;

        for (int dx = -FLAG_SEARCH_RADIUS; dx <= FLAG_SEARCH_RADIUS; dx++) {
            int x = origin.getX() + dx;
            for (int dz = -FLAG_SEARCH_RADIUS; dz <= FLAG_SEARCH_RADIUS; dz++) {
                int distSqr = dx * dx + dz * dz;
                if (distSqr > FLAG_SEARCH_RADIUS_SQR) {
                    continue;
                }

                BlockPos chunkCheck = new BlockPos(x, origin.getY(), origin.getZ() + dz);
                if (!level.isLoaded(chunkCheck)) {
                    continue;
                }

                int z = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int offset = -1; offset <= 1; offset++) {
                    BlockPos flagPos = new BlockPos(x, y + offset, z);
                    if (isFlagAt(level, flagPos)) {
                        double distanceSqr = playerPos.distanceToSqr(
                            flagPos.getX() + 0.5D,
                            flagPos.getY() + 0.5D,
                            flagPos.getZ() + 0.5D
                        );
                        if (distanceSqr < bestDistSqr) {
                            bestDistSqr = distanceSqr;
                        }
                        break;
                    }
                }
            }
        }

        if (bestDistSqr == Double.MAX_VALUE) {
            return -1.0D;
        }

        return Math.sqrt(bestDistSqr);
    }

    private boolean isFlagAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(GolfMod.GOLF_FLAG_BLOCK.get());
    }
}
