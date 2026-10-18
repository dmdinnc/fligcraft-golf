package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.terrain.SculptedSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class TerrainLightingTests {
    @GameTest(template = "empty")
    public static void opacityIsAvailableBeforeCornerMetadataLoads(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3);
        var ground = SculptedGroundBlock.source(level, pos);
        for (int height : new int[] {8, 16, 20, 32}) {
            ground.setSurface(SculptedSurface.flat(height));
            var cells = height > 16 ? java.util.List.of(pos, pos.above()) : java.util.List.of(pos);
            for (var cell : cells) {
                var state = level.getBlockState(cell);
                helper.assertTrue(state.getLightBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, cell)
                        == state.getLightBlock(level, cell),
                    "A chunk palette must provide the same opacity before any corner metadata loads at height " + height);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void loadingOlderCornerDataRepairsTheSavedOpacity(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState(), 3);
        var ground = SculptedGroundBlock.source(level, pos);
        ground.setSurface(SculptedSurface.flat(8));
        var saved = ground.saveWithFullMetadata(level.registryAccess());
        var originalPalette = GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState();
        level.setBlock(pos, originalPalette, 3);
        var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, originalPalette, saved, level.registryAccess());
        level.setBlockEntity(restored);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(SculptedGroundBlock.source(level, pos).surface().equals(SculptedSurface.flat(8)),
                "Older saved corner heights must remain intact");
            helper.assertTrue(level.getBlockState(pos).getLightBlock(level, pos) == 0,
                "Loading older terrain must repair palette opacity without a player editing the tile");
            assertFreshColumn(helper, pos);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lightingWorkerSeesTheSameOpacityAsTheServerThread(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos low = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos high = helper.absolutePos(new BlockPos(3, 2, 1));
        level.setBlock(low, GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState(), 3);
        level.setBlock(high, GolfMod.ROUGH_BLOCK.get().defaultBlockState(), 3);
        SculptedGroundBlock.source(level, low).setSurface(SculptedSurface.flat(8));
        SculptedGroundBlock.source(level, high).setSurface(SculptedSurface.flat(32));
        var positions = java.util.List.of(low, high, high.above());
        var states = positions.stream().map(level::getBlockState).toList();
        var expected = positions.stream().map(pos -> level.getBlockState(pos).getLightBlock(level, pos)).toList();
        var worker = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            var actual = new java.util.ArrayList<Integer>();
            for (int i = 0; i < positions.size(); i++) { actual.add(states.get(i).getLightBlock(level, positions.get(i))); }
            return actual;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(worker.isDone(), "The lighting worker must finish its opacity query");
            helper.assertTrue(worker.join().equals(expected),
                "Lighting must see the same terrain opacity off-thread, without block entity access: main=" + expected + " worker=" + worker.join());
        });
    }

    @GameTest(template = "empty")
    public static void shapeEditsDoNotReadAnUnrelatedColumnNearOrigin(GameTestHelper helper) {
        var level = helper.getLevel();
        // Above/outside the test structures, with an intentionally different block at
        // the chunk-local coordinates interpreted as absolute world coordinates.
        BlockPos pos = helper.absolutePos(new BlockPos(3, 43, 3)).offset(32, 0, 32);
        BlockPos unrelated = new BlockPos(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
        var previous = level.getBlockState(unrelated);
        var previousGround = level.getBlockState(pos);
        var previousFloor = level.getBlockState(pos.below());
        try {
            level.setBlock(unrelated, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3);
            var ground = SculptedGroundBlock.source(level, pos);
            ground.setSurface(new SculptedSurface(12, 8, 8, 12));
            assertFreshColumn(helper, pos);
            var data = ground.saveWithoutMetadata(level.registryAccess());
            ground.setSurface(new SculptedSurface(8, 12, 12, 8));
            ground.loadWithComponents(data, level.registryAccess());
            assertFreshColumn(helper, pos);
        } finally {
            level.setBlock(unrelated, previous, 3);
            level.setBlock(pos, previousGround, 3);
            level.setBlock(pos.below(), previousFloor, 3);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void loadingTerrainRepairsSavedLightingWithoutAnEdit(GameTestHelper helper) {
        BlockPos pos = terrainWithStaleSkyData(helper);
        helper.assertTrue(SculptedGroundBlock.source(helper.getLevel(), pos) != null, "Loading a saved terrain tile must create its metadata");
        helper.runAfterDelay(20, () -> {
            assertFreshColumn(helper, pos);
            helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, pos) < 15,
                "A full-height upgraded tile must block direct sky after its saved light data is repaired");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingUpgradedTerrainRestoresActualSkyBrightness(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, pos) < 15, "The original opaque block must actually darken the test cell");
            var chunk = helper.getLevel().getChunkAt(pos);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15,
                GolfMod.ROUGH_BLOCK.get().defaultBlockState());
            helper.getLevel().destroyBlock(pos, false);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(helper.getLevel().getBrightness(LightLayer.SKY, pos) == 15,
                    "Breaking upgraded terrain must restore daylight without a vanilla block being placed first");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void breakingUpgradedTerrainRefreshesStaleSkylight(GameTestHelper helper) {
        BlockPos pos = terrainWithStaleSkyData(helper);
        helper.getLevel().destroyBlock(pos, false);
        assertFreshColumn(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editingTerrainRefreshesSkylightWithoutReplacingBlock(GameTestHelper helper) {
        BlockPos pos = terrainWithStaleSkyData(helper);
        var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
        ground.setSurface(new SculptedSurface(4, 8, 12, 8));
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(GolfMod.FAIRWAY_BLOCK.get()), "The edit must retain the terrain block state");
        assertFreshColumn(helper, pos);
        helper.assertTrue(helper.getLevel().getBlockState(pos).getLightBlock(helper.getLevel(), pos) == 0,
            "Lowering a corner must reopen the tile to direct skylight");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingRaisedTerrainRemovesCapAndRefreshesBothColumns(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(pos, GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState(), 3);
        SculptedGroundBlock.source(helper.getLevel(), pos).setSurface(SculptedSurface.flat(20));
        helper.assertTrue(helper.getLevel().getBlockState(pos.above()).is(GolfMod.PUTTING_GREEN_CAP.get()), "The fixture must contain an extension");
        helper.getLevel().destroyBlock(pos, false);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() && helper.getLevel().getBlockState(pos.above()).isAir(),
            "Breaking raised terrain must remove both blocks");
        assertFreshColumn(helper, pos);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullTilesBlockSkyWhileLoweredTilesAndCapsAdmitIt(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 43, 3));
        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        int vanillaSource = lowestSkySource(level, pos);
        level.setBlock(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState(), 3);
        var ground = SculptedGroundBlock.source(level, pos);
        helper.assertTrue(lowestSkySource(level, pos) == vanillaSource,
            "A full-height fairway must block direct sky like the original grass block");
        ground.setSurface(SculptedSurface.flat(20));
        helper.assertTrue(level.getBlockState(pos.above()).is(GolfMod.FAIRWAY_CAP.get())
                && lowestSkySource(level, pos) == vanillaSource
                && level.getBlockState(pos.above()).getLightBlock(level, pos.above()) == 0,
            "A raised cap must admit sky while its full base still blocks it");
        ground.setSurface(SculptedSurface.flat(32));
        assertFreshColumn(helper, pos);
        helper.assertTrue(level.getBlockState(pos.above()).getLightBlock(level, pos.above()) == 15
                && lowestSkySource(level, pos) == vanillaSource + 1,
            "A cap filled to 32/16 must block sky through the upper cell");
        ground.setSurface(SculptedSurface.flat(31));
        assertFreshColumn(helper, pos);
        helper.assertTrue(lowestSkySource(level, pos) == vanillaSource,
            "Lowering a full cap by one step must reopen its sky source");
        ground.setSurface(new SculptedSurface(15, 20, 20, 20));
        assertFreshColumn(helper, pos);
        helper.assertTrue(lowestSkySource(level, pos) < vanillaSource,
            "Lowering one corner must reopen the column even when a cap remains");
        ground.setSurface(SculptedSurface.FULL);
        assertFreshColumn(helper, pos);
        helper.assertTrue(lowestSkySource(level, pos) == vanillaSource,
            "Restoring the full tile must close the column and remove the cap");
        helper.succeed();
    }

    private static int lowestSkySource(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var sources = new ChunkSkyLightSources(level);
        sources.fillFrom(level.getChunkAt(pos));
        return sources.getLowestSourceY(pos.getX() & 15, pos.getZ() & 15);
    }

    private static BlockPos terrainWithStaleSkyData(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var chunk = helper.getLevel().getChunkAt(pos);
        // Model stale saved light data from a transparent terrain build. The full tile
        // must repair the open sky column when its block entity is loaded.
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15,
            GolfMod.FAIRWAY_BLOCK.get().defaultBlockState());
        var recomputed = new ChunkSkyLightSources(helper.getLevel());
        recomputed.fillFrom(chunk);
        helper.assertTrue(chunk.getSkyLightSources().getLowestSourceY(pos.getX() & 15, pos.getZ() & 15)
            != recomputed.getLowestSourceY(pos.getX() & 15, pos.getZ() & 15), "The fixture must reproduce stale saved skylight");
        return pos;
    }
    private static void assertFreshColumn(GameTestHelper helper, BlockPos pos) {
        var chunk = helper.getLevel().getChunkAt(pos);
        var recomputed = new ChunkSkyLightSources(helper.getLevel());
        recomputed.fillFrom(chunk);
        helper.assertTrue(chunk.getSkyLightSources().getLowestSourceY(pos.getX() & 15, pos.getZ() & 15)
            == recomputed.getLowestSourceY(pos.getX() & 15, pos.getZ() & 15),
            "Terrain changes must immediately refresh the cached skylight column without placing a vanilla block");
    }
}
