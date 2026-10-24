package com.ziggleflig.golf.block;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import com.ziggleflig.golf.GolfMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainMaterial;
import com.ziggleflig.golf.entity.GolfBallEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;

public class SculptedGroundBlock extends BaseEntityBlock {
    // Lighting runs without access to block entities, including during chunk decoding.
    public static final BooleanProperty FULL_CELL = BooleanProperty.create("full_cell");
    public static final MapCodec<SculptedGroundBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        TerrainMaterial.CODEC.fieldOf("material").forGetter(SculptedGroundBlock::material),
        Codec.BOOL.optionalFieldOf("cap", false).forGetter(SculptedGroundBlock::isCap), propertiesCodec())
        .apply(instance, SculptedGroundBlock::new));
    private static final VoxelShape ZERO_SELECTION = Shapes.box(0, 0, 0, 1, 1.0 / 64, 1);
    private final TerrainMaterial material;
    private final boolean cap;

    public SculptedGroundBlock(TerrainMaterial material, Properties properties) { this(material, false, properties); }
    public SculptedGroundBlock(TerrainMaterial material, boolean cap, Properties properties) {
        super(properties); this.material = material; this.cap = cap;
        registerDefaultState(stateDefinition.any().setValue(FULL_CELL, !cap));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FULL_CELL); }
    public TerrainMaterial material() { return material; }
    public boolean isCap() { return cap; }
    /** Derived opacity must follow corner edits, including planned fills and reversed undo edits. */
    public static BlockState stateForSurface(BlockState state, SculptedSurface surface) {
        int top = (((SculptedGroundBlock) state.getBlock()).isCap() ? 2 : 1) * SculptedSurface.BLOCK_HEIGHT;
        boolean full = surface.northWest() >= top && surface.northEast() >= top
            && surface.southEast() >= top && surface.southWest() >= top;
        return state.setValue(FULL_CELL, full);
    }
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        var owner = source(level, pos);
        return owner == null ? ItemStack.EMPTY : new ItemStack(owner.getBlockState().getBlock());
    }
    public static void onCapBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof SculptedGroundBlock block) || !block.isCap()
                || !(event.getLevel() instanceof Level level)) { return; }
        var owner = source(level, event.getPos().below());
        if (owner != null && (!level.mayInteract(event.getPlayer(), owner.getBlockPos())
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, owner.getBlockPos(), owner.getBlockState(), event.getPlayer())).isCanceled())) {
            event.setCanceled(true);
        }
    }
    public static SculptedGroundBlockEntity source(BlockGetter level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof SculptedGroundBlock block && block.isCap()) { pos = pos.below(); }
        return level.getBlockState(pos).getBlock() instanceof SculptedGroundBlock block && !block.isCap()
            && level.getBlockEntity(pos) instanceof SculptedGroundBlockEntity ground ? ground : null;
    }
    public static SculptedSurface surface(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SculptedGroundBlockEntity ground ? ground.surface() : SculptedSurface.FULL;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SculptedGroundBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var owner = source(level, pos);
        if (owner == null || !owner.hasFlag() || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) { return InteractionResult.PASS; }
        if (player.isSpectator()) { return InteractionResult.FAIL; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!level.mayInteract(player, owner.getBlockPos()) || !player.mayUseItemAt(owner.getBlockPos(), hit.getDirection(), ItemStack.EMPTY)
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(
                level, owner.getBlockPos(), owner.getBlockState(), player)).isCanceled()) { return InteractionResult.FAIL; }
        if (source(level, pos) != owner || !owner.setFlag(false)) { return InteractionResult.FAIL; }
        ItemStack flag = new ItemStack(GolfMod.GOLF_FLAG_ITEM.get());
        if (!player.getInventory().add(flag)) { player.drop(flag, false); }
        return InteractionResult.CONSUME;
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var drops = new ArrayList<>(super.getDrops(state, params));
        if (!cap && params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SculptedGroundBlockEntity ground
                && ground.hasFlag()) { drops.add(new ItemStack(GolfMod.GOLF_FLAG_ITEM.get())); }
        return drops;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entity) {
            if (entity.getEntity() instanceof GolfBallEntity) { return Shapes.empty(); }
            if (entity.getEntity() instanceof Mob mob) { return surface(level, pos).walkingCollisionSlice(cap, mob.maxUpStep()); }
        }
        return surface(level, pos).collisionSlice(cap);
    }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
    @Override public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        // Caps are terrain, not walk-through air. Empty tiles still allow paths at foundation height.
        return surface(level, pos).collisionSlice(cap).isEmpty() ? PathType.OPEN : PathType.BLOCKED;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = surface(level, pos).collisionSlice(cap);
        return shape.isEmpty() && !cap ? ZERO_SELECTION : shape;
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (cap && direction == Direction.DOWN) {
            SculptedGroundBlockEntity owner = source(level, pos);
            if (owner == null || !owner.surface().hasOverflow()) { return Blocks.AIR.defaultBlockState(); }
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide) {
            if (cap) {
                SculptedGroundBlockEntity owner = source(level, pos.below());
                if (owner != null && owner.surface().hasOverflow()) { owner.setSurface(owner.surface().trimOverflow()); }
            } else if (level.getBlockState(pos.above()).getBlock() instanceof SculptedGroundBlock above && above.isCap()) {
                level.removeBlock(pos.above(), false);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var owner = source(level, pos);
        if (owner != null) { owner.refreshCap(); }
        else if (cap) { level.removeBlock(pos, false); }
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }
    // Unlike air, replacing this block must refresh lighting, even with zero scalar opacity.
    // In particular, upgraded worlds can still have skylight columns cached for the old opaque blocks.
    @Override protected boolean useShapeForLightOcclusion(BlockState state) { return true; }
    @Override protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(FULL_CELL) ? 15 : 0;
    }
}
