package com.ziggleflig.golf.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GolfFlagBlock extends Block {

    // Flag pole shape
    private static final VoxelShape SHAPE = Block.box(7.5, 0.0, 7.5, 8.5, 16.0, 8.5);

    public GolfFlagBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
