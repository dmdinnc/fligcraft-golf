package com.ziggleflig.golf.tracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfBallItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Last known locations and queued deletions, saved separately in each dimension. */
public class GolfBallTrackerData extends SavedData {
    public record Snapshot(UUID id, UUID owner, Vec3 position, DyeColor color, int strokes, String state, String surface) {
        public ItemStack item() {
            ItemStack stack = new ItemStack(GolfMod.GOLF_BALL.get());
            GolfBallItem.setColor(stack, color);
            return stack;
        }
    }
    private static final Factory<GolfBallTrackerData> FACTORY = new Factory<>(GolfBallTrackerData::new, GolfBallTrackerData::load, null);
    private final Map<UUID, Snapshot> balls = new HashMap<>();
    private final Set<UUID> deletions = new HashSet<>();

    public static GolfBallTrackerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "fligcraft_golf_tracked_balls");
    }

    public Collection<Snapshot> snapshots() { return List.copyOf(balls.values()); }
    public Snapshot find(UUID id) { return balls.get(id); }
    public boolean isDeleted(UUID id) { return deletions.contains(id); }

    public void track(GolfBallEntity ball) {
        var reason = ball.getRemovalReason();
        if ((reason != null && (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION))
                || deletions.contains(ball.getUUID())) { return; }
        if (ball.getLastHitter() == null) { forget(ball.getUUID()); return; }
        BlockPos pos = ball.blockPosition();
        Snapshot previous = balls.get(ball.getUUID());
        String surfaceId = previous == null ? "minecraft:air" : previous.surface();
        // Saving an unloading entity must not load its chunk again to inspect the ground.
        if (ball.level().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            var surface = ball.level().getBlockState(pos);
            if (surface.isAir()) { surface = ball.level().getBlockState(pos.below()); }
            surfaceId = BuiltInRegistries.BLOCK.getKey(surface.getBlock()).toString();
        }
        String state = ball.isOnTee() ? "tee" : ball.isInWaterOrBubble() ? "water" : !ball.onGround() ? "air"
            : ball.getDeltaMovement().lengthSqr() > 0.0001 ? "rolling" : "resting";
        Snapshot next = new Snapshot(ball.getUUID(), ball.getLastHitter(), ball.position(), ball.getColor(), ball.getStrokes(),
            state, surfaceId);
        if (!next.equals(balls.put(ball.getUUID(), next))) { setDirty(); }
    }

    public void queueDeletion(UUID id) {
        balls.remove(id);
        deletions.add(id);
        setDirty();
    }

    public void forget(UUID id) {
        // Retain deletion IDs: a chunk can still have an older on-disk entity after a canceled load.
        if (balls.remove(id) != null) { setDirty(); }
    }

    public static GolfBallTrackerData load(CompoundTag tag, HolderLookup.Provider registries) {
        GolfBallTrackerData data = new GolfBallTrackerData();
        ListTag entries = tag.getList("Balls", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner")) { continue; }
            Snapshot snapshot = new Snapshot(entry.getUUID("Id"), entry.getUUID("Owner"),
                new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                DyeColor.byId(entry.getInt("Color")), entry.getInt("Strokes"), entry.getString("State"), entry.getString("Surface"));
            data.balls.put(snapshot.id(), snapshot);
        }
        ListTag pending = tag.getList("Deleted", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.hasUUID("Id")) { data.deletions.add(entry.getUUID("Id")); }
        }
        data.balls.keySet().removeAll(data.deletions);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Snapshot snapshot : balls.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", snapshot.id()); entry.putUUID("Owner", snapshot.owner());
            entry.putDouble("X", snapshot.position().x); entry.putDouble("Y", snapshot.position().y); entry.putDouble("Z", snapshot.position().z);
            entry.putInt("Color", snapshot.color().getId()); entry.putInt("Strokes", snapshot.strokes());
            entry.putString("State", snapshot.state()); entry.putString("Surface", snapshot.surface());
            entries.add(entry);
        }
        tag.put("Balls", entries);
        ListTag pending = new ListTag();
        for (UUID id : deletions) { CompoundTag entry = new CompoundTag(); entry.putUUID("Id", id); pending.add(entry); }
        tag.put("Deleted", pending);
        return tag;
    }
}
