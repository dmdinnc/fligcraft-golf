package com.ziggleflig.golf.terrain;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Fits only the new tile. Existing geometry, including intentionally separate corners, is left alone. */
public final class TerrainPlacement {
    private TerrainPlacement() { }

    public static SculptedSurface fit(Level level, BlockPos pos) {
        SculptedSurface result = SculptedSurface.FULL;
        for (var corner : SculptedSurface.Corner.values()) {
            int vertexX = pos.getX() + corner.x, vertexZ = pos.getZ() + corner.z;
            int[] votes = new int[SculptedSurface.MAX_HEIGHT + 1];
            int bestLevel = Integer.MAX_VALUE;
            var seen = new HashSet<BlockPos>();
            for (int x = vertexX - 1; x <= vertexX; x++) for (int z = vertexZ - 1; z <= vertexZ; z++) {
                if (x == pos.getX() && z == pos.getZ() || !level.hasChunk(x >> 4, z >> 4)) { continue; }
                var shared = SculptedSurface.Corner.nearest(vertexX - x, vertexZ - z);
                for (int dy : new int[] {0, -1, 1}) {
                    BlockPos candidate = new BlockPos(x, pos.getY() + dy, z);
                    if (level.isOutsideBuildHeight(candidate)) { continue; }
                    var neighbor = SculptedGroundBlock.source(level, candidate);
                    if (neighbor == null || !seen.add(neighbor.getBlockPos())) { continue; }
                    int distance = Math.abs(neighbor.getBlockPos().getY() - pos.getY());
                    int height = (neighbor.getBlockPos().getY() - pos.getY()) * SculptedSurface.BLOCK_HEIGHT
                        + neighbor.surface().height(shared);
                    // A different course level can join only where its world height fits this tile.
                    if (distance > 1 || height < 0 || height > SculptedSurface.MAX_HEIGHT || distance > bestLevel) { continue; }
                    if (distance < bestLevel) { java.util.Arrays.fill(votes, 0); bestLevel = distance; }
                    votes[height]++;
                }
            }
            int selected = SculptedSurface.BLOCK_HEIGHT;
            for (int height = 0; height < votes.length; height++) {
                // Prefer the most common existing height, then the one nearest full height.
                // This resolves pre-existing seams consistently without inventing an averaged height.
                if (votes[height] > votes[selected] || votes[height] > 0 && votes[height] == votes[selected]
                        && Math.abs(height - SculptedSurface.BLOCK_HEIGHT) < Math.abs(selected - SculptedSurface.BLOCK_HEIGHT)) {
                    selected = height;
                }
            }
            result = result.withCorner(corner, selected);
        }
        return result;
    }

    public static boolean canFit(Level level, BlockPos pos, SculptedSurface surface) {
        if (!surface.hasOverflow()) { return true; }
        BlockPos above = pos.above();
        if (level.isOutsideBuildHeight(above)) { return false; }
        BlockState existing = level.getBlockState(above);
        return existing.isAir() || existing.getBlock() instanceof SculptedGroundBlock block && block.isCap();
    }
}
