package com.ziggleflig.golf.block.entity;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Non-ticking, persistent geometry; material comes from the registered block, not mutable NBT. */
public class SculptedGroundBlockEntity extends BlockEntity {
    public static final ModelProperty<SculptedSurface> SURFACE = new ModelProperty<>();
    public static final ModelProperty<Boolean> HAS_FLAG = new ModelProperty<>();
    private SculptedSurface surface = SculptedSurface.FULL;
    private boolean hasFlag;
    private ModelData modelData = createModelData();

    public SculptedGroundBlockEntity(BlockPos pos, BlockState state) { super(GolfMod.COURSE_GROUND_ENTITY.get(), pos, state); }
    public SculptedSurface surface() { return surface; }
    public boolean hasFlag() { return hasFlag && !isCap(); }
    private ModelData createModelData() {
        return ModelData.builder().with(SURFACE, surface).with(HAS_FLAG, hasFlag).build();
    }
    public boolean setFlag(boolean present) {
        if (isCap() || hasFlag == present) { return false; }
        hasFlag = present;
        modelData = createModelData();
        setChanged();
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }
    public SculptedSurface.Sample sample(double localX, double localZ) {
        return new SculptedSurface.Sample(surface.heightAt(localX, localZ) - (isCap() ? 1 : 0), surface.normalAt(localX, localZ),
            ((SculptedGroundBlock) getBlockState().getBlock()).material());
    }

    public boolean setSurface(SculptedSurface next) {
        if (surface.equals(next)) { return false; }
        if (!canAccommodate(next)) { return false; }
        SculptedSurface previous = surface;
        surface = next;
        modelData = createModelData();
        setChanged();
        if (level != null) {
            if (!level.isClientSide && !isCap()) { updateCap(next); }
            requestModelDataUpdate();
            refreshLight();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (!level.isClientSide && !isCap()) { liftEntities(previous, next); }
        }
        return true;
    }

    private void refreshLight() {
        // Clients use the server's light packets. Re-solving light while decoding block entity
        // tags races the chunk's initial lighting packet and its not-yet-loaded neighbors.
        if (level.isClientSide) { return; }
        BlockState state = getBlockState();
        BlockState next = SculptedGroundBlock.stateForSurface(state, surface);
        if (state != next) {
            // Keep opacity in the saved palette and initial chunk packet, where the lighting
            // worker can read it without asking the main thread for corner metadata.
            level.setBlock(worldPosition, next, 3);
        }
        // Also repair source/light data saved by an older build, even if opacity is unchanged.
        // The source table takes chunk-local X/Z. Its BlockGetter must be that chunk,
        // or it reads an unrelated world column near (0, 0) and dims the real surface.
        var chunk = level.getChunkAt(worldPosition);
        chunk.getSkyLightSources().update(chunk,
            worldPosition.getX() & 15, worldPosition.getY(), worldPosition.getZ() & 15);
        level.getLightEngine().checkBlock(worldPosition);
    }

    public boolean isCap() { return ((SculptedGroundBlock) getBlockState().getBlock()).isCap(); }
    public boolean canAccommodate(SculptedSurface next) {
        if (level == null || isCap() || !next.hasOverflow()) { return true; }
        BlockPos above = worldPosition.above();
        if (above.getY() >= level.getMaxBuildHeight()) { return false; }
        BlockState existing = level.getBlockState(above);
        return existing.isAir() || existing.getBlock() instanceof SculptedGroundBlock block && block.isCap();
    }
    private void updateCap(SculptedSurface next) {
        BlockPos above = worldPosition.above();
        if (next.hasOverflow()) {
            var cap = GolfMod.courseCap(((SculptedGroundBlock) getBlockState().getBlock()).material());
            if (!level.getBlockState(above).is(cap)) { level.setBlock(above, cap.defaultBlockState(), 3); }
            if (level.getBlockEntity(above) instanceof SculptedGroundBlockEntity upper) { upper.setSurface(next); }
        } else if (level.getBlockState(above).getBlock() instanceof SculptedGroundBlock block && block.isCap()) {
            level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
        }
    }
    public void refreshCap() {
        if (level != null && !level.isClientSide && !isCap()) {
            if (canAccommodate(surface)) { updateCap(surface); }
            else { setSurface(surface.trimOverflow()); }
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        // Repair light data saved by an older version as soon as the tile is loaded.
        if (level != null && !level.isClientSide) { refreshLight(); }
        if (level != null && !level.isClientSide && (isCap() || surface.hasOverflow())) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /** Balls use the exact edited surface; other entities use vanilla-style conservative push-up. */
    private void liftEntities(SculptedSurface previous, SculptedSurface next) {
        if (next.northWest() <= previous.northWest() && next.northEast() <= previous.northEast()
                && next.southEast() <= previous.southEast() && next.southWest() <= previous.southWest()) { return; }
        // A small corner edit can change the real surface without changing any of the coarse boxes.
        for (var ball : level.getEntitiesOfClass(GolfBallEntity.class,
                new AABB(worldPosition).expandTowards(0, 0.25, 0).inflate(0.125))) {
            double height = worldPosition.getY() + next.ballFeetHeight(ball.getX() - worldPosition.getX(),
                ball.getZ() - worldPosition.getZ(), ball.getBbWidth() / 2.0);
            if (ball.getY() < height && ball.getBoundingBox().maxY > worldPosition.getY()) {
                ball.teleportRelative(0, height + 0.00001 - ball.getY(), 0);
            }
        }
        var added = Shapes.joinUnoptimized(previous.collisionShape(), next.collisionShape(), BooleanOp.ONLY_SECOND)
            .move(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (!added.isEmpty()) {
            for (var entity : level.getEntities(null, added.bounds())) {
                if (entity instanceof GolfBallEntity) { continue; }
                double down = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0, 1, 0), List.of(added), -1);
                if (1 + down > 0.000001) { entity.teleportRelative(0, 1 + down, 0); }
            }
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SurfaceVersion", 2);
        tag.putIntArray("CornerHeights", surface.toArray());
        tag.putBoolean("GolfFlag", hasFlag());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        surface = SculptedSurface.fromArray(tag.getIntArray("CornerHeights"));
        hasFlag = !isCap() && tag.getBoolean("GolfFlag");
        modelData = createModelData();
        if (level != null) { refreshLight(); }
        if (level != null && !level.isClientSide && !isCap()) {
            // Restore/repair caps after chunk loading, copied block data, or a live /data edit.
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override public ModelData getModelData() { return modelData; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
}
