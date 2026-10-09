package com.ziggleflig.golf.tracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.ziggleflig.golf.entity.GolfBallEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3f;

public final class GolfBallLocator {
    public static final double MAX_DISTANCE = 128.0;
    private record Path(ServerPlayer player, GolfBallEntity ball, int expires) { }
    private static final Map<UUID, Path> PATHS = new ConcurrentHashMap<>();

    public static void stop(ServerPlayer player) { PATHS.remove(player.getUUID()); }

    public static void start(ServerPlayer player, GolfBallEntity ball) {
        stop(player);
        if (ball.level() != player.level()) {
            player.displayClientMessage(Component.translatable("gui.fligcraft_golf.tracker.other_dimension"), true);
        } else if (ball.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) {
            player.displayClientMessage(Component.translatable("gui.fligcraft_golf.tracker.too_far", (int) MAX_DISTANCE), true);
        } else {
            PATHS.put(player.getUUID(), new Path(player, ball, player.tickCount + 100));
            draw(player, ball);
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        Path path = PATHS.get(player.getUUID());
        if (path == null) { return; }
        GolfBallEntity ball = path.ball();
        if (player.tickCount >= path.expires() || !ball.isAlive() || ball.level() != player.level()
                || !player.getUUID().equals(ball.getLastHitter()) || ball.distanceToSqr(player) > MAX_DISTANCE * MAX_DISTANCE) {
            PATHS.remove(player.getUUID());
        } else if (player.tickCount % 10 == 0) {
            draw(player, ball);
        }
    }

    private static void draw(ServerPlayer player, GolfBallEntity ball) {
        Vec3 start = player.getEyePosition().add(0, -0.3, 0);
        Vec3 end = ball.position().add(0, 0.15, 0);
        int points = Math.min(256, Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2)));
        int rgb = ball.getColor().getTextureDiffuseColor();
        DustParticleOptions particle = new DustParticleOptions(new Vector3f(
            ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), 1.0f);
        for (int i = 0; i <= points; i++) {
            Vec3 pos = start.lerp(end, i / (double) points);
            player.serverLevel().sendParticles(player, particle, true, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { PATHS.remove(event.getEntity().getUUID()); }
    public static void onServerStopped(ServerStoppedEvent event) { PATHS.values().removeIf(path -> path.player().server == event.getServer()); }
    private GolfBallLocator() { }
}
