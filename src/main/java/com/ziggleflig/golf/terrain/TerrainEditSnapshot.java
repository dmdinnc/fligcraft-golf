package com.ziggleflig.golf.terrain;

import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Protection callbacks may edit terrain themselves; recheck the entire batch before applying it. */
public record TerrainEditSnapshot(BlockPos pos, BlockState state, BlockState above, BlockEntity entity,
        SculptedSurface surface, boolean flag) {
    public static TerrainEditSnapshot capture(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        var ground = entity instanceof SculptedGroundBlockEntity terrain ? terrain : null;
        return new TerrainEditSnapshot(pos.immutable(), level.getBlockState(pos), level.getBlockState(pos.above()),
            entity, ground == null ? null : ground.surface(), ground != null && ground.hasFlag());
    }

    public boolean matches(Level level) {
        if (!state.equals(level.getBlockState(pos)) || !above.equals(level.getBlockState(pos.above()))
                || entity != level.getBlockEntity(pos)) { return false; }
        return !(entity instanceof SculptedGroundBlockEntity ground)
            || (surface.equals(ground.surface()) && flag == ground.hasFlag());
    }
}
