package com.ziggleflig.golf.block;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.entity.GolfBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class DrivingRangeBayBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape SHAPE_NORTH = Block.box(2.0D, 0.0D, 8.0D, 14.0D, 9.0D, 12.0D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.0D, 0.0D, 4.0D, 14.0D, 9.0D, 8.0D);
    private static final VoxelShape SHAPE_EAST = Block.box(4.0D, 0.0D, 2.0D, 8.0D, 9.0D, 14.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(8.0D, 0.0D, 2.0D, 12.0D, 9.0D, 14.0D);

    public DrivingRangeBayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    private static VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            if (isPowered) {
                spawnDrivingRangeBalls(level, pos, state);
            }
        }
    }

    private void spawnDrivingRangeBalls(Level level, BlockPos bayPos, BlockState state) {
        Direction facing = state.getValue(FACING);

        BlockPos pos1 = bayPos.relative(facing);
        BlockPos pos2 = bayPos.relative(facing, 2);

        spawnBallAtPosition(level, pos1);
        spawnBallAtPosition(level, pos2);
    }

    private void spawnBallAtPosition(Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        boolean isOnTee = blockState.is(GolfMod.GOLF_TEE_BLOCK.get());

        Vec3 spawnPos;
        if (isOnTee) {
            spawnPos = Vec3.atCenterOf(pos).add(0.0D, 0.625D, 0.0D);
        } else {
            spawnPos = Vec3.atCenterOf(pos).add(0.0D, 0.125D, 0.0D);
        }

        AABB searchBox = new AABB(pos).inflate(0.35D, 1.0D, 0.35D);
        List<GolfBallEntity> existingBalls = level.getEntitiesOfClass(GolfBallEntity.class, searchBox);

        if (!existingBalls.isEmpty()) {
            return;
        }

        GolfBallEntity ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), level);
        ball.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        ball.setDrivingRangeBall(true);

        if (isOnTee) {
            ball.setOnTee(true);
        }
        
        level.addFreshEntity(ball);
    }
}
