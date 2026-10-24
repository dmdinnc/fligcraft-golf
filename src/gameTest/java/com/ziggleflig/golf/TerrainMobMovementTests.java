package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.terrain.SculptedSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class TerrainMobMovementTests {
    @GameTest(template = "empty")
    public static void villagerDoesNotJumpAtHigherCornerBehindItsRoute(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState());
        SculptedGroundBlock.source(helper.getLevel(), pos).setSurface(new SculptedSurface(4, 8, 8, 4));
        var villager = new ObservedVillager(helper.getLevel());
        // Only the lower half of this tile intersects the villager's feet; walking along Z is level.
        villager.setPos(pos.getX() + 0.15, pos.getY() + 2, pos.getZ() + 0.35);
        villager.move(MoverType.SELF, new Vec3(0, -3, 0));
        villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ() + 0.2, 1);
        villager.getMoveControl().tick();
        villager.getJumpControl().tick();
        helper.assertTrue(!villager.jumpRequested,
            "Walking along the lower tread must not trigger a jump at a higher corner elsewhere in the same block");
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerNavigatesGentleCourseWithoutRepeatedJumps(GameTestHelper helper) {
        lane(helper, 2);
        navigate(helper, 2.5, 20.5, 4);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerDescendsMixedTerrainWithoutRepeatedJumps(GameTestHelper helper) {
        lane(helper, 2);
        navigate(helper, 20.5, 2.5, 2);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerWalksQuarterBlockGradesAcrossCapTransitions(GameTestHelper helper) {
        lane(helper, 4);
        navigate(helper, 2.5, 20.5, 7);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerWalksAtHalfBlockReliefLimit(GameTestHelper helper) {
        lane(helper, 8);
        navigate(helper, 2.5, 20.5, 12);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerNavigatesFullHeightCaps(GameTestHelper helper) {
        flatLane(helper, 32);
        navigate(helper, 2.5, 20.5, 3);
        helper.succeed();
    }

    @GameTest(template = "bounce_course", batch = "terrainMobMovement")
    public static void villagerNavigatesFullyLoweredTerrain(GameTestHelper helper) {
        flatLane(helper, 0);
        navigate(helper, 2.5, 20.5, 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void steepTerrainAndPlayerCollisionKeepTheirExistingShape(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState());
        var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
        var villager = new ObservedVillager(helper.getLevel());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var gentle = new SculptedSurface(4, 8, 8, 4);
        ground.setSurface(gentle);
        helper.assertTrue(ground.getBlockState().getCollisionShape(helper.getLevel(), pos, CollisionContext.of(player))
                .equals(gentle.collisionSlice(false)), "Players must retain the existing stepped terrain collision");
        var steep = new SculptedSurface(0, 16, 16, 0);
        ground.setSurface(steep);
        helper.assertTrue(ground.getBlockState().getCollisionShape(helper.getLevel(), pos, CollisionContext.of(villager))
                .equals(steep.collisionSlice(false)), "Steep terrain must retain its steps instead of suspending mobs above its lower side");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villagerStillCollidesWithAndJumpsOntoOrdinaryObstacles(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) { helper.setBlock(new BlockPos(x, 1, 3), GolfMod.FAIRWAY_BLOCK.get()); }
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        var villager = new ObservedVillager(helper.getLevel());
        villager.setPos(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        villager.setOnGround(true);
        villager.move(MoverType.SELF, new Vec3(0.5, -0.08, 0));
        helper.assertTrue(villager.horizontalCollision && villager.getBoundingBox().maxX <= helper.absolutePos(new BlockPos(4, 2, 3)).getX(),
            "A full-height obstacle must still block walking");
        villager.getMoveControl().setWantedPosition(villager.getX() + 0.5, villager.getY() + 1, villager.getZ(), 1);
        villager.getMoveControl().tick();
        villager.getJumpControl().tick();
        helper.assertTrue(villager.jumpRequested, "Normal obstacle jumps must remain enabled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loweringASteepTileLiftsMobsOntoItsNewLevelSupport(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, GolfMod.FAIRWAY_BLOCK.get().defaultBlockState());
        var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
        ground.setSurface(new SculptedSurface(0, 10, 10, 0));
        var villager = new ObservedVillager(helper.getLevel());
        villager.setPos(pos.getX() + 0.15, pos.getY() + 2, pos.getZ() + 0.5);
        villager.move(MoverType.SELF, new Vec3(0, -3, 0));
        helper.getLevel().addFreshEntity(villager);
        try {
            ground.setSurface(new SculptedSurface(0, 8, 8, 0));
            helper.assertTrue(Math.abs(villager.getY() - pos.getY() - 0.5) < 0.00001,
                "Changing to level mob support must lift an existing mob out of it even when only lowering terrain corners");
            ground.setSurface(new SculptedSurface(14, 18, 18, 14));
            helper.assertTrue(Math.abs(villager.getY() - pos.getY() - 18 / 16.0) < 0.00001,
                "Raising a gentle tile into a cap must lift the mob to its new support height");
            ground.setSurface(SculptedSurface.flat(0));
            villager.move(MoverType.SELF, new Vec3(0, -3, 0));
            ground.setSurface(SculptedSurface.flat(32));
            helper.assertTrue(Math.abs(villager.getY() - pos.getY() - 2) < 0.00001,
                "A grading edit may lift mobs through the full supported two-block terrain height");
        } finally { villager.discard(); }
        helper.succeed();
    }

    private static void lane(GameTestHelper helper, int rise) {
        for (int x = 1; x <= 22; x++) {
            int height = 4 + x * rise;
            int y = 1 + (height - 1) / 16;
            int local = height - (y - 1) * 16;
            for (int z = 2; z <= 4; z++) {
                for (int foundation = 0; foundation < y; foundation++) { helper.setBlock(new BlockPos(x, foundation, z), Blocks.STONE); }
                BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                helper.getLevel().setBlockAndUpdate(pos, material(x).defaultBlockState());
                SculptedGroundBlock.source(helper.getLevel(), pos).setSurface(new SculptedSurface(local, local + rise, local + rise, local));
            }
        }
    }

    private static void flatLane(GameTestHelper helper, int height) {
        for (int x = 1; x <= 22; x++) for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
            helper.getLevel().setBlockAndUpdate(pos, material(x).defaultBlockState());
            SculptedGroundBlock.source(helper.getLevel(), pos).setSurface(SculptedSurface.flat(height));
        }
    }

    private static SculptedGroundBlock material(int x) {
        return switch (x % 4) {
            case 0 -> GolfMod.FAIRWAY_BLOCK.get();
            case 1 -> GolfMod.ROUGH_BLOCK.get();
            case 2 -> GolfMod.PUTTING_GREEN_BLOCK.get();
            default -> GolfMod.BUNKER_SAND_BLOCK.get();
        };
    }

    private static void navigate(GameTestHelper helper, double fromX, double toX, double targetY) {
        var villager = new ObservedVillager(helper.getLevel());
        villager.setPos(helper.absoluteVec(new Vec3(fromX, 12, 3.5)));
        villager.move(MoverType.SELF, new Vec3(0, -15, 0));
        Vec3 target = helper.absoluteVec(new Vec3(toX, targetY, 3.5));
        helper.assertTrue(villager.getNavigation().moveTo(target.x, target.y, target.z, 1), "The villager must find a route over the course");
        for (int tick = 0; tick < 220; tick++) { villager.tick(); }
        GolfMod.LOGGER.info("TERRAIN_MOB position={} target={} jumpRequests={}", villager.position(), target, villager.jumpRequests);
        helper.assertTrue(Math.abs(villager.getX() - target.x) < 1, "The villager must reach the end of the gentle slope");
        helper.assertTrue(villager.jumpRequests == 0, "Traversing a gentle sculpted slope must not require obstacle jumps: " + villager.jumpRequests);
    }

    private static class ObservedVillager extends Villager {
        private boolean jumpRequested;
        private int jumpRequests;

        private ObservedVillager(Level level) { super(EntityType.VILLAGER, level); }

        @Override public void setJumping(boolean jumping) {
            super.setJumping(jumping);
            jumpRequested |= jumping;
            if (jumping) { jumpRequests++; }
        }

        // Give the test a deterministic destination while retaining vanilla navigation and movement controls.
        @Override protected void customServerAiStep() { }
    }
}
