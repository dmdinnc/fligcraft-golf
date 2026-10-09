package com.ziggleflig.golf.tracker;

import com.ziggleflig.golf.entity.GolfBallEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

public final class GolfBallTracking {
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof GolfBallEntity ball && event.getLevel() instanceof ServerLevel level) {
            GolfBallTrackerData data = GolfBallTrackerData.get(level);
            if (data.isDeleted(ball.getUUID())) {
                // The item was already returned when deletion was queued. Never return a second one.
                event.setCanceled(true);
                ball.discard();
            }
        }
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof GolfBallEntity ball && event.getLevel() instanceof ServerLevel level) {
            Entity.RemovalReason reason = ball.getRemovalReason();
            if (reason != null && (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
                GolfBallTrackerData.get(level).forget(ball.getUUID());
            } else {
                GolfBallTrackerData.get(level).track(ball);
            }
        }
    }
    private GolfBallTracking() { }
}
