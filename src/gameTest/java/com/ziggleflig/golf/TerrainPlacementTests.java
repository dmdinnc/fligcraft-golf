package com.ziggleflig.golf;

import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class TerrainPlacementTests {
    @GameTest(template = "empty")
    public static void replacingMissingTileRestoresMixedMaterialSlope(GameTestHelper helper) {
        slope(helper);
        BlockPos center = new BlockPos(3, 1, 3);
        helper.getLevel().destroyBlock(helper.absolutePos(center), false);
        helper.setBlock(center.below(), Blocks.STONE);
        var player = player(helper, new ItemStack(GolfMod.PUTTING_GREEN_ITEM.get(), 2));
        helper.assertTrue(place(player, helper.absolutePos(center)).consumesAction(), "The missing tile must be placeable");
        helper.assertTrue(ground(helper, center).surface().equals(slopeAt(3, 3)), "Replacing the tile must recover all four original corner heights");
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            helper.assertTrue(ground(helper, new BlockPos(x, 1, z)).surface().equals(slopeAt(x, z)), "Placement must leave the surrounding slope unchanged");
        }
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Successful placement must consume one block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mowingReplacementDirtRestoresSlope(GameTestHelper helper) {
        slope(helper);
        BlockPos center = new BlockPos(3, 1, 3);
        helper.setBlock(center, Blocks.DIRT);
        var player = player(helper, new ItemStack(GolfMod.LAWNMOWER.get()));
        player.setShiftKeyDown(true);
        use(player, helper.absolutePos(center));
        helper.assertTrue(ground(helper, center).surface().equals(slopeAt(3, 3)), "Mowing replacement dirt must reconstruct the slope");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "Restoring one tile must cost one durability point");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void diagonalNeighborsMatchOnlyTheirSharedCorner(GameTestHelper helper) {
        terrain(helper, new BlockPos(2, 1, 2), GolfMod.ROUGH_BLOCK.get(), new SculptedSurface(2, 3, 7, 4));
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        place(player(helper, new ItemStack(GolfMod.FAIRWAY_ITEM.get())), helper.absolutePos(target));
        helper.assertTrue(ground(helper, target).surface().equals(new SculptedSurface(7, 16, 16, 16)),
            "A diagonal tile must supply its shared corner; unanchored corners must stay full height");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disagreeingNeighborsChooseExistingMajorityWithoutReshapingThem(GameTestHelper helper) {
        var diagonal = terrain(helper, new BlockPos(2, 1, 2), GolfMod.ROUGH_BLOCK.get(), SculptedSurface.flat(6));
        var west = terrain(helper, new BlockPos(2, 1, 3), GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.flat(12));
        var north = terrain(helper, new BlockPos(3, 1, 2), GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(12));
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        place(player(helper, new ItemStack(GolfMod.FAIRWAY_ITEM.get())), helper.absolutePos(target));
        helper.assertTrue(ground(helper, target).surface().northWest() == 12,
            "A pre-existing seam must resolve to the most common actual height rather than an average");
        helper.assertTrue(diagonal.surface().equals(SculptedSurface.flat(6)) && west.surface().equals(SculptedSurface.flat(12))
            && north.surface().equals(SculptedSurface.flat(12)), "Automatic fitting must not repair or move existing seams");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void isolatedPlacementStaysFullHeight(GameTestHelper helper) {
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        place(player(helper, new ItemStack(GolfMod.ROUGH_ITEM.get())), helper.absolutePos(target));
        helper.assertTrue(ground(helper, target).surface().equals(SculptedSurface.FULL), "An isolated tile must retain the full-block default");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neighboringLevelsMatchWorldHeightsAndCreateCap(GameTestHelper helper) {
        terrain(helper, new BlockPos(2, 1, 3), GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.flat(20));
        terrain(helper, new BlockPos(4, 3, 3), GolfMod.PUTTING_GREEN_BLOCK.get(), SculptedSurface.flat(2));
        BlockPos target = new BlockPos(3, 2, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        place(player(helper, new ItemStack(GolfMod.ROUGH_ITEM.get())), helper.absolutePos(target));
        helper.assertTrue(ground(helper, target).surface().equals(new SculptedSurface(4, 18, 18, 4)),
            "Corners must match in world coordinates across a one-block change of course level");
        helper.assertBlockPresent(GolfMod.ROUGH_CAP.get(), target.above());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedExtensionRejectsPlacementWithoutConsumingItem(GameTestHelper helper) {
        var neighbor = terrain(helper, new BlockPos(2, 1, 3), GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.flat(18));
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        helper.setBlock(target.above(), Blocks.STONE);
        var player = player(helper, new ItemStack(GolfMod.ROUGH_ITEM.get(), 2));
        helper.assertTrue(place(player, helper.absolutePos(target)) == InteractionResult.FAIL, "Placement must explain a blocked extension instead of making a seam");
        helper.assertBlockPresent(Blocks.AIR, target);
        helper.assertBlockPresent(Blocks.STONE, target.above());
        helper.assertTrue(player.getMainHandItem().getCount() == 2 && neighbor.surface().equals(SculptedSurface.flat(18)),
            "Failed fitting must not consume the item or reshape the neighbor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bulkMowingKeepsAllSharedCornersConsistent(GameTestHelper helper) {
        for (int z = 2; z <= 4; z++) {
            terrain(helper, new BlockPos(1, 1, z), GolfMod.ROUGH_BLOCK.get(), new SculptedSurface(4 + z, 8 + z, 9 + z, 5 + z));
        }
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); }
        use(player(helper, new ItemStack(GolfMod.LAWNMOWER.get())), helper.absolutePos(new BlockPos(3, 1, 3)));
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            var surface = ground(helper, new BlockPos(x, 1, z)).surface();
            if (x == 2) {
                helper.assertTrue(surface.northWest() == 8 + z && surface.southWest() == 9 + z,
                    "Every boundary tile must inherit the old course edge");
            }
            if (x < 4) {
                var east = ground(helper, new BlockPos(x + 1, 1, z)).surface();
                helper.assertTrue(surface.northEast() == east.northWest() && surface.southEast() == east.southWest(), "Mowed east/west edges must have no seams");
            }
            if (z < 4) {
                var south = ground(helper, new BlockPos(x, 1, z + 1)).surface();
                helper.assertTrue(surface.southWest() == south.northWest() && surface.southEast() == south.northEast(), "Mowed north/south edges must have no seams");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedExtensionCancelsEntireMowingPatch(GameTestHelper helper) {
        terrain(helper, new BlockPos(1, 1, 3), GolfMod.ROUGH_BLOCK.get(), SculptedSurface.flat(18));
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT); }
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
        var player = player(helper, new ItemStack(GolfMod.LAWNMOWER.get()));
        helper.assertTrue(use(player, helper.absolutePos(new BlockPos(3, 1, 3))) == InteractionResult.FAIL, "One blocked cap must reject the complete mowing operation");
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) { helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, 1, z)); }
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "A rejected patch must not charge durability");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copiedCornerDataAndReloadedTerrainKeepTheirShape(GameTestHelper helper) {
        terrain(helper, new BlockPos(2, 1, 2), GolfMod.ROUGH_BLOCK.get(), SculptedSurface.flat(6));
        BlockPos target = new BlockPos(3, 1, 3);
        helper.setBlock(target.below(), Blocks.STONE);
        var stack = new ItemStack(GolfMod.FAIRWAY_ITEM.get());
        CompoundTag data = new CompoundTag();
        data.putString("id", GolfMod.MODID + ":course_ground");
        data.putIntArray("CornerHeights", SculptedSurface.flat(12).toArray());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        place(player(helper, stack), helper.absolutePos(target));
        var ground = ground(helper, target);
        helper.assertTrue(ground.surface().equals(SculptedSurface.flat(12)), "Explicitly copied shape data must take precedence over automatic fitting");
        var restored = BlockEntity.loadStatic(ground.getBlockPos(), ground.getBlockState(),
            ground.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.getLevel().setBlockEntity(restored);
        helper.assertTrue(ground(helper, target).surface().equals(SculptedSurface.flat(12)), "Loading existing metadata must not refit a saved tile");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedAutomaticCapRollsBackWholePlacement(GameTestHelper helper) {
        terrain(helper, new BlockPos(2, 1, 3), GolfMod.FAIRWAY_BLOCK.get(), SculptedSurface.flat(18));
        BlockPos target = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        var player = player(helper, new ItemStack(GolfMod.ROUGH_ITEM.get(), 2));
        Consumer<BlockEvent.EntityMultiPlaceEvent> protection = event -> {
            if (event.getEntity() == player && event.getReplacedBlockSnapshots().stream().anyMatch(snapshot -> snapshot.getPos().equals(target.above()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(protection);
        try {
            helper.assertTrue(place(player, target) == InteractionResult.FAIL, "Protection mods must see and reject an automatically placed cap");
        } finally { NeoForge.EVENT_BUS.unregister(protection); }
        helper.assertTrue(helper.getLevel().getBlockState(target).isAir() && helper.getLevel().getBlockState(target.above()).isAir(),
            "Protection rejection must roll back both parts of the placed tile");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "Protection rejection must restore the block stack");
        helper.succeed();
    }

    private static SculptedSurface slopeAt(int x, int z) { return new SculptedSurface(4 + x + z, 5 + x + z, 6 + x + z, 5 + x + z); }
    private static void slope(GameTestHelper helper) {
        Block[] materials = {GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.PUTTING_GREEN_BLOCK.get()};
        for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
            terrain(helper, new BlockPos(x, 1, z), materials[(x + z) % materials.length], slopeAt(x, z));
        }
    }
    private static SculptedGroundBlockEntity terrain(GameTestHelper helper, BlockPos pos, Block block, SculptedSurface surface) {
        helper.setBlock(pos, block);
        var ground = ground(helper, pos);
        ground.setSurface(surface);
        return ground;
    }
    private static SculptedGroundBlockEntity ground(GameTestHelper helper, BlockPos pos) {
        return SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(pos));
    }
    private static FakePlayer player(GameTestHelper helper, ItemStack stack) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "placement-test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }
    private static InteractionResult place(FakePlayer player, BlockPos target) { return use(player, target.below()); }
    private static InteractionResult use(FakePlayer player, BlockPos clicked) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false)));
    }
}
