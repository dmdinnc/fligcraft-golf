package com.ziggleflig.golf;

import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfCartEntity;
import com.ziggleflig.golf.item.TerrainTrowelItem;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import com.ziggleflig.golf.terrain.TerrainMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class SculptedGroundTests {
    @GameTest(template = "empty")
    public static void craftedSandMatchesNeighborsEditsSharedCornersAndKeepsItsCap(GameTestHelper helper) {
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.SAND)));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Vanilla sand must have a course-sand crafting recipe");
        ItemStack crafted = recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(crafted.is(GolfMod.BUNKER_SAND_ITEM.get()) && crafted.getCount() == 1, "One vanilla sand must craft one editable sand");
        var neighbor = place(helper, 3, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        neighbor.setSurface(new SculptedSurface(18, 8, 8, 18));
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, crafted);
        BlockPos below = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.assertTrue(crafted.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false))).consumesAction(),
            "Course sand must be placeable through its normal item");
        BlockPos pos = below.above();
        var sand = SculptedGroundBlock.source(helper.getLevel(), pos);
        helper.assertTrue(sand != null && sand.surface().northEast() == 18 && sand.surface().southEast() == 18
            && helper.getLevel().getBlockState(pos.above()).is(GolfMod.BUNKER_SAND_CAP.get()), "Placement must recover the adjoining green slope and create a sand extension");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.TERRAIN_TROWEL.get()));
        helper.assertTrue(click(player, sand, 0.98, 0.02).consumesAction() && sand.surface().northEast() == 17 && neighbor.surface().northWest() == 17,
            "The trowel must edit shared corners across sand and grass materials");
        var saved = sand.saveWithFullMetadata(helper.getLevel().registryAccess());
        var loaded = (SculptedGroundBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
            pos, sand.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded != null && loaded.surface().equals(sand.surface()), "Saved sand must retain its geometry");
        var drops = Block.getDrops(sand.getBlockState(), helper.getLevel(), pos, sand);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(GolfMod.BUNKER_SAND_ITEM.get()), "Mining a sand base must return its course block");
        helper.getLevel().removeBlock(below, false);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(GolfMod.BUNKER_SAND_BLOCK.get())
                && helper.getLevel().getBlockState(pos.above()).is(GolfMod.BUNKER_SAND_CAP.get()), "Edited sand must stay in place and keep its cap without a supporting block");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void trowelLowersRaisesAndLevels(GameTestHelper helper) {
        var ground = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        Player player = player(helper);
        ItemStack tool = player.getMainHandItem();
        swap(player, true); // Shared -> one corner.
        for (int i = 0; i < 20; i++) { click(player, ground, 0.02, 0.02); }
        helper.assertTrue(ground.surface().northWest() == 0 && ground.surface().southEast() == 16, "Only selected corner should lower to zero");
        player.setShiftKeyDown(true);
        click(player, ground, 0.02, 0.02);
        helper.assertTrue(ground.surface().northWest() == 1, "Sneak must raise the same corner");
        player.setShiftKeyDown(false);
        swap(player, true); // One corner -> level tile.
        click(player, ground, 0.02, 0.02);
        helper.assertTrue(ground.surface().isEmpty(), "Level mode should flatten to selected corner's new height");
        BlockState state = ground.getBlockState();
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), ground.getBlockPos()).isEmpty(), "Zero-height tile must have zero physical height");
        helper.assertFalse(state.getShape(helper.getLevel(), ground.getBlockPos(), CollisionContext.empty()).isEmpty(), "Zero-height tile must remain selectable");
        player.setShiftKeyDown(true);
        for (int i = 0; i < 40; i++) { click(player, ground, 0.98, 0.98); }
        helper.assertTrue(ground.surface().equals(SculptedSurface.flat(32)), "Raising must clamp to the full upper-cell extension");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swapCyclesTrowelSettingsAndSaves(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack tool = player.getMainHandItem(), offhand = new ItemStack(Items.STONE);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putString("OtherToolSetting", "keep"));
        helper.assertTrue(TerrainTrowelItem.step(tool) == 1 && TerrainTrowelItem.mode(tool) == TerrainTrowelItem.Mode.JOINED,
            "New and existing trowels must default to the one-step shared edit");
        for (int step : new int[] {2, 4, 8, 1}) {
            helper.assertTrue(swap(player, false).isCanceled(), "Swap must change trowel settings instead of exchanging hands");
            helper.assertTrue(TerrainTrowelItem.step(tool) == step && TerrainTrowelItem.mode(tool) == TerrainTrowelItem.Mode.JOINED,
                "Swap must cycle 1, 2, 4, 8 independently of mode");
        }
        for (var mode : new TerrainTrowelItem.Mode[] {TerrainTrowelItem.Mode.CORNER, TerrainTrowelItem.Mode.LEVEL, TerrainTrowelItem.Mode.JOINED}) {
            swap(player, true);
            helper.assertTrue(TerrainTrowelItem.mode(tool) == mode && TerrainTrowelItem.step(tool) == 1,
                "Sneak + Swap must cycle modes independently of the edit step");
        }
        swap(player, false); swap(player, false); swap(player, true); // Step 4, One corner.
        var saved = tool.save(helper.getLevel().registryAccess());
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElseThrow();
        helper.assertTrue(TerrainTrowelItem.step(restored) == 4 && TerrainTrowelItem.mode(restored) == TerrainTrowelItem.Mode.CORNER,
            "Saved trowel must retain both step and mode");
        helper.assertTrue(restored.get(DataComponents.CUSTOM_DATA).copyTag().getString("OtherToolSetting").equals("keep"),
            "Cycling settings must preserve unrelated custom data");
        helper.assertTrue(player.getMainHandItem() == tool && player.getOffhandItem() == offhand,
            "Trowel swap actions must leave both items in their original hands");
        tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(TerrainTrowelItem.step(tool) == 4 && TerrainTrowelItem.mode(tool) == TerrainTrowelItem.Mode.CORNER,
            "Right-clicking air must no longer change settings");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swapSupportsOffhandTrowelAndLeavesOtherItemsAlone(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack main = new ItemStack(Items.STONE), offhand = new ItemStack(Items.STICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        helper.assertFalse(swap(player, false).isCanceled(), "Ordinary items must retain vanilla hand swapping");
        ItemStack tool = new ItemStack(GolfMod.TERRAIN_TROWEL.get());
        player.setItemInHand(InteractionHand.OFF_HAND, tool);
        helper.assertTrue(swap(player, false).isCanceled() && TerrainTrowelItem.step(tool) == 2,
            "Swap must also cycle a trowel held in the offhand");
        swap(player, true);
        helper.assertTrue(TerrainTrowelItem.mode(tool) == TerrainTrowelItem.Mode.CORNER
                && player.getMainHandItem() == main && player.getOffhandItem() == tool,
            "Offhand mode cycling must keep the trowel and main-hand item in place");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largerTrowelStepsEditSharedCornersAndClampSafely(GameTestHelper helper) {
        var nw = place(helper, 1, 1, GolfMod.FAIRWAY_BLOCK.get());
        var ne = place(helper, 2, 1, GolfMod.ROUGH_BLOCK.get());
        var sw = place(helper, 1, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        var se = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        var grounds = List.of(nw, ne, sw, se);
        Corner[] corners = {Corner.SOUTH_EAST, Corner.SOUTH_WEST, Corner.NORTH_EAST, Corner.NORTH_WEST};
        Player player = player(helper);
        for (int step : new int[] {1, 2, 4, 8}) {
            helper.assertTrue(TerrainTrowelItem.step(player.getMainHandItem()) == step, "Unexpected edit step");
            click(player, nw, 0.98, 0.98);
            for (int i = 0; i < grounds.size(); i++) {
                helper.assertTrue(grounds.get(i).surface().height(corners[i]) == 16 - step, "Lowering must apply the selected step to every shared corner");
            }
            player.setShiftKeyDown(true);
            click(player, nw, 0.98, 0.98);
            for (var ground : grounds) { helper.assertTrue(ground.surface().equals(SculptedSurface.FULL), "Raising must restore each shared corner by the same step"); }
            player.setShiftKeyDown(false);
            if (step != 8) { swap(player, false); }
        }
        helper.getLevel().setBlock(ne.getBlockPos().above(), Blocks.STONE.defaultBlockState(), 3);
        player.setShiftKeyDown(true);
        helper.assertTrue(click(player, nw, 0.98, 0.98) == InteractionResult.FAIL, "An occupied upper cell must reject a large shared raise");
        for (var ground : grounds) { helper.assertTrue(ground.surface().equals(SculptedSurface.FULL), "A rejected large step must leave all tiles unchanged"); }
        helper.getLevel().removeBlock(ne.getBlockPos().above(), false);
        click(player, nw, 0.98, 0.98);
        for (int i = 0; i < grounds.size(); i++) {
            helper.assertTrue(grounds.get(i).surface().height(corners[i]) == 24, "An eight-step raise must create caps at 24/16");
        }
        click(player, nw, 0.98, 0.98);
        for (int i = 0; i < grounds.size(); i++) {
            helper.assertTrue(grounds.get(i).surface().height(corners[i]) == 32, "A second large raise must reach the 32/16 cap limit");
        }
        player.setShiftKeyDown(false);
        for (int count = 0; count < 4; count++) { click(player, nw, 0.98, 0.98); }
        for (int i = 0; i < grounds.size(); i++) {
            helper.assertTrue(grounds.get(i).surface().height(corners[i]) == 0 && helper.getLevel().getBlockState(grounds.get(i).getBlockPos().above()).isAir(),
                "Large lowering steps must clamp at ground level and remove caps");
        }
        swap(player, true); // One corner.
        player.setShiftKeyDown(true);
        click(player, nw, 0.98, 0.98);
        helper.assertTrue(nw.surface().southEast() == 8 && ne.surface().southWest() == 0, "One corner mode must also use the selected step");
        swap(player, true); // Level tile.
        player.setShiftKeyDown(false);
        click(player, nw, 0.98, 0.98);
        helper.assertTrue(nw.surface().isEmpty(), "Level tile mode must use the selected step when flattening");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void joinedCornersCrossMaterials(GameTestHelper helper) {
        var nw = place(helper, 1, 1, GolfMod.FAIRWAY_BLOCK.get());
        var ne = place(helper, 2, 1, GolfMod.ROUGH_BLOCK.get());
        var sw = place(helper, 1, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        var se = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        ne.setSurface(ne.surface().withCorner(Corner.SOUTH_WEST, 4)); // Repair an existing seam, rather than applying independent deltas.
        Player player = player(helper);
        click(player, nw, 0.98, 0.98);
        helper.assertTrue(nw.surface().southEast() == 15 && ne.surface().southWest() == 15
            && sw.surface().northEast() == 15 && se.surface().northWest() == 15, "Shared corner must join all four materials");
        helper.assertTrue(ne.surface().northEast() == 16 && sw.surface().southWest() == 16, "Unselected corners must stay unchanged");
        helper.assertTrue(sw.sample(0.5, 0.5).material() == TerrainMaterial.GREEN, "Surface metadata must preserve material");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerCanClimbRamp(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) { helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE); }
        var ground = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        ground.setSurface(new SculptedSurface(16, 0, 0, 16));
        Player player = player(helper);
        BlockPos pos = ground.getBlockPos();
        player.setPos(pos.getX() + 1.35, pos.getY(), pos.getZ() + 0.5);
        player.setOnGround(true);
        for (int i = 0; i < 12; i++) { player.move(MoverType.SELF, new Vec3(-0.125, -0.08, 0)); }
        helper.assertTrue(player.getX() < pos.getX() + 0.1, "Player must be able to move across the ramp");
        helper.assertTrue(player.getY() > pos.getY() + 0.8, "Player must step up onto the ramp's high side");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerWalkingSpeedOnShallowSlope(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) {
            place(helper, x, 2, GolfMod.FAIRWAY_BLOCK.get()).setSurface(SculptedSurface.flat(8));
            place(helper, x, 4, GolfMod.FAIRWAY_BLOCK.get()).setSurface(new SculptedSurface(2 + x * 2, 4 + x * 2, 4 + x * 2, 2 + x * 2));
        }
        double flat = walkDistance(helper, 2, false), uphill = walkDistance(helper, 4, false);
        helper.assertTrue(uphill >= flat * 0.95, "Climbing a shallow sculpted slope must retain ordinary walking speed: flat=" + flat + ", uphill=" + uphill);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerSprintingSpeedOnShallowSlope(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) {
            place(helper, x, 2, GolfMod.FAIRWAY_BLOCK.get()).setSurface(SculptedSurface.flat(8));
            place(helper, x, 4, GolfMod.FAIRWAY_BLOCK.get()).setSurface(new SculptedSurface(2 + x * 2, 4 + x * 2, 4 + x * 2, 2 + x * 2));
        }
        double flat = walkDistance(helper, 2, true), uphill = walkDistance(helper, 4, true);
        helper.assertTrue(uphill >= flat * 0.95, "Climbing a shallow sculpted slope must retain ordinary sprint speed: flat=" + flat + ", uphill=" + uphill);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cartKeepsMovingAcrossShallowSlope(GameTestHelper helper) {
        for (int x = 1; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                place(helper, x, z, GolfMod.FAIRWAY_BLOCK.get()).setSurface(new SculptedSurface(2 + x * 2, 4 + x * 2, 4 + x * 2, 2 + x * 2));
            }
        }
        var cart = new GolfCartEntity(GolfMod.GOLF_CART_ENTITY.get(), helper.getLevel());
        cart.setPos(helper.absoluteVec(new Vec3(2.5, 3, 3.5)));
        cart.move(MoverType.SELF, new Vec3(0, -3, 0));
        double startX = cart.getX(), startY = cart.getY();
        for (int tick = 0; tick < 6; tick++) {
            cart.setDeltaMovement(new Vec3(0.4, -0.08, 0));
            cart.move(MoverType.SELF, cart.getDeltaMovement());
            helper.assertTrue(!cart.horizontalCollision, "A shallow ramp must not repeatedly zero the cart's forward velocity");
        }
        helper.assertTrue(cart.getX() > startX + 2.2 && cart.getY() > startY + 0.2,
            "The cart must travel up the slope at its intended speed");
        helper.succeed();
    }

    private static double walkDistance(GameTestHelper helper, int z, boolean sprint) {
        Player player = player(helper);
        player.setSpeed(sprint ? 0.13f : 0.1f);
        player.setSprinting(sprint);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 3, z + 0.5)));
        player.move(MoverType.SELF, new Vec3(0, -3, 0));
        player.setDeltaMovement(Vec3.ZERO);
        double start = player.getX();
        for (int tick = 0; tick < 12; tick++) { player.travel(new Vec3(1, 0, 0)); }
        return player.getX() - start;
    }

    @GameTest(template = "empty")
    public static void raisingGroundLiftsStandingEntities(GameTestHelper helper) {
        var ground = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        ground.setSurface(SculptedSurface.flat(8));
        var entity = helper.spawn(EntityType.ARMOR_STAND, new Vec3(2.5, 1.5, 2.5));
        entity.setNoGravity(true);
        double originalY = entity.getY();
        ground.setSurface(SculptedSurface.flat(9));
        helper.assertTrue(Math.abs(entity.getY() - originalY - 1.0 / 16) < 0.00001, "Raising a tile under an entity must lift it onto the surface");
        ground.setSurface(SculptedSurface.flat(8));
        helper.assertTrue(Math.abs(entity.getY() - originalY - 1.0 / 16) < 0.00001, "Lowering a tile must leave gravity to settle entities naturally");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terrainCraftingAndDrops(GameTestHelper helper) {
        Block[] terrain = {GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.PUTTING_GREEN_BLOCK.get()};
        for (int i = 0; i < terrain.length; i++) {
            var ground = place(helper, 2 + i, 2, terrain[i]);
            ground.setSurface(SculptedSurface.flat(0));
            var drops = Block.getDrops(ground.getBlockState(), helper.getLevel(), ground.getBlockPos(), ground);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(terrain[i].asItem()), "Even fully lowered tiles must drop one matching block with its original item ID");
        }
        CraftingInput trowelInput = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK), ItemStack.EMPTY));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, trowelInput, helper.getLevel());
        helper.assertTrue(recipe.isPresent() && recipe.orElseThrow().value().assemble(trowelInput, helper.getLevel().registryAccess()).is(GolfMod.TERRAIN_TROWEL.get()), "Trowel must craft in the player's 2-by-2 grid");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectedNeighborCancelsWholeEdit(GameTestHelper helper) {
        var source = place(helper, 1, 1, GolfMod.FAIRWAY_BLOCK.get());
        var neighbor = place(helper, 2, 1, GolfMod.FAIRWAY_BLOCK.get());
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> {
            if (event.getPos().equals(neighbor.getBlockPos())) { event.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            Player player = player(helper);
            InteractionResult result = TerrainTrowelItem.edit(helper.getLevel(), player, player.getMainHandItem(), source.getBlockPos(),
                Direction.UP, Corner.NORTH_EAST, 8);
            helper.assertTrue(result == InteractionResult.FAIL, "Protected neighbor must reject shared edit");
            helper.assertTrue(source.surface().equals(SculptedSurface.FULL) && neighbor.surface().equals(SculptedSurface.FULL), "Rejected edit must leave every tile unchanged");
        } finally { NeoForge.EVENT_BUS.unregister(protect); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void surfaceSaveAndSyncRoundTrip(GameTestHelper helper) {
        var source = place(helper, 2, 2, GolfMod.ROUGH_BLOCK.get());
        SculptedSurface expected = new SculptedSurface(0, 5, 16, 9);
        source.setSurface(expected);
        var registries = helper.getLevel().registryAccess();
        var restored = new SculptedGroundBlockEntity(source.getBlockPos(), source.getBlockState());
        restored.loadWithComponents(source.saveWithFullMetadata(registries), registries);
        helper.assertTrue(restored.surface().equals(expected), "Save/load must preserve every corner");
        var clientCopy = new SculptedGroundBlockEntity(source.getBlockPos(), source.getBlockState());
        clientCopy.loadWithComponents(source.getUpdateTag(registries), registries);
        helper.assertTrue(clientCopy.surface().equals(expected), "Client update must carry all corner heights");
        helper.assertTrue(clientCopy.getModelData().get(SculptedGroundBlockEntity.SURFACE).equals(expected), "Model data must match saved geometry");
        CompoundTag invalid = new CompoundTag();
        invalid.putIntArray("CornerHeights", new int[] {-8, 64, 4, 12});
        restored.loadWithComponents(invalid, registries);
        helper.assertTrue(restored.surface().equals(new SculptedSurface(0, 32, 4, 12)), "Invalid heights must clamp safely");
        restored.loadWithComponents(new CompoundTag(), registries);
        helper.assertTrue(restored.surface().equals(SculptedSurface.FULL), "Missing metadata must default to a full block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void geometrySupportsRampsAndGroundLevel(GameTestHelper helper) {
        SculptedSurface ramp = new SculptedSurface(16, 0, 0, 16);
        helper.assertTrue(Math.abs(ramp.heightAt(0.25, 0.75) - 0.75) < 0.00001, "Ramp height must interpolate along x");
        Vec3 normal = ramp.normalAt(0.25, 0.75);
        helper.assertTrue(normal.x > 0 && normal.y > 0 && Math.abs(normal.z) < 0.00001, "Ramp normal must point up and downhill");
        helper.assertTrue(Math.abs(normal.length() - 1) < 0.00001, "Surface normals must be unit vectors");
        SculptedSurface twisted = new SculptedSurface(4, 16, 8, 0);
        for (double t = 0; t <= 1; t += 0.1) {
            double diagonal = (4 * (1 - t) + 8 * t) / 16;
            helper.assertTrue(Math.abs(twisted.heightAt(t, t) - diagonal) < 0.00001, "Triangle diagonal must stay continuous");
        }
        var ground = place(helper, 2, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        ground.setSurface(ramp);
        helper.assertTrue(ground.getBlockState().getCollisionShape(helper.getLevel(), ground.getBlockPos()).equals(ramp.collisionShape()), "Block must use edited collision geometry");
        helper.assertTrue(SculptedSurface.flat(0).collisionShape().isEmpty(), "Fully lowered surface must have no collision");
        helper.assertTrue(Math.abs(SculptedSurface.flat(8).collisionShape().bounds().maxY - 0.5) < 0.00001, "Flat half-height must collide at half height");
        helper.assertTrue(((SculptedGroundBlock) ground.getBlockState().getBlock()).material().rollingRetention() == 0.93, "Green must retain its material response metadata");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overflowCapFollowsSourceAndCanBeEdited(GameTestHelper helper) {
        var source = place(helper, 2, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        source.setSurface(new SculptedSurface(16, 16, 16, 32));
        BlockPos above = source.getBlockPos().above();
        helper.assertTrue(helper.getLevel().getBlockState(above).is(GolfMod.PUTTING_GREEN_CAP.get()), "Overflow must create a matching upper-cell cap");
        var cap = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(above);
        helper.assertTrue(cap.surface().equals(source.surface()) && SculptedGroundBlock.source(helper.getLevel(), above) == source, "Cap must store and select its owner's exact surface");
        helper.assertTrue(cap.getBlockState().getCollisionShape(helper.getLevel(), above).bounds().maxY == 1, "Upper-cell collision must reach the full 32/16 height");
        helper.assertTrue(source.getBlockState().getCollisionShape(helper.getLevel(), source.getBlockPos()).bounds().maxY == 1, "Base collision must stay within its own cell");
        Player player = player(helper);
        click(player, cap, 0.02, 0.98);
        helper.assertTrue(source.surface().southWest() == 31 && cap.surface().southWest() == 31, "Clicking the cap must edit and synchronize the original corner");
        source.setSurface(SculptedSurface.FULL);
        helper.assertTrue(helper.getLevel().getBlockState(above).isAir(), "Lowering to normal height must remove the cap");
        source.setSurface(SculptedSurface.flat(18));
        helper.getLevel().destroyBlock(above, true);
        helper.assertTrue(source.surface().equals(SculptedSurface.FULL), "Breaking a cap must trim its owner's raised corners");
        source.setSurface(SculptedSurface.flat(18));
        helper.getLevel().destroyBlock(source.getBlockPos(), true);
        helper.assertTrue(helper.getLevel().getBlockState(above).isAir(), "Breaking the owner must remove the cap without leaving an orphan");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overflowRejectsOccupiedAndProtectedUpperCells(GameTestHelper helper) {
        var source = place(helper, 1, 2, GolfMod.FAIRWAY_BLOCK.get());
        var neighbor = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        helper.getLevel().setBlock(neighbor.getBlockPos().above(), Blocks.STONE.defaultBlockState(), 3);
        Player player = player(helper);
        var result = TerrainTrowelItem.edit(helper.getLevel(), player, player.getMainHandItem(), source.getBlockPos(), Direction.UP, Corner.NORTH_EAST, 17);
        helper.assertTrue(result == InteractionResult.FAIL && source.surface().equals(SculptedSurface.FULL) && neighbor.surface().equals(SculptedSurface.FULL), "One occupied upper cell must cancel every shared raise");
        helper.getLevel().removeBlock(neighbor.getBlockPos().above(), false);
        Consumer<BlockEvent.EntityPlaceEvent> protect = event -> { if (event.getPos().equals(neighbor.getBlockPos().above())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            result = TerrainTrowelItem.edit(helper.getLevel(), player, player.getMainHandItem(), source.getBlockPos(), Direction.UP, Corner.NORTH_EAST, 17);
            helper.assertTrue(result == InteractionResult.FAIL && source.surface().equals(SculptedSurface.FULL) && neighbor.surface().equals(SculptedSurface.FULL), "Cap creation must respect upper-cell protection atomically");
        } finally { NeoForge.EVENT_BUS.unregister(protect); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedCornersJoinDifferentBlockLevels(GameTestHelper helper) {
        var lower = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        helper.setBlock(new BlockPos(3, 2, 2), GolfMod.PUTTING_GREEN_BLOCK.get());
        var higher = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 2)));
        higher.setSurface(new SculptedSurface(0, 8, 8, 0));
        Player player = player(helper);
        player.setShiftKeyDown(true);
        click(player, lower, 0.98, 0.02);
        helper.assertTrue(lower.surface().northEast() == 17 && higher.surface().northWest() == 1,
            "Shared corners must match world height across neighboring Y levels");
        helper.assertTrue(lower.getBlockPos().getY() + lower.sample(1, 0).height() == higher.getBlockPos().getY() + higher.sample(0, 0).height(), "Joined world heights must be exactly equal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedCornerSkipsBuriedFoundationForExposedUpperTile(GameTestHelper helper) {
        var lower = place(helper, 2, 2, GolfMod.FAIRWAY_BLOCK.get());
        lower.setSurface(SculptedSurface.flat(20));
        var buried = place(helper, 3, 2, GolfMod.FAIRWAY_BLOCK.get());
        helper.setBlock(new BlockPos(3, 2, 2), GolfMod.PUTTING_GREEN_BLOCK.get());
        var upper = SculptedGroundBlock.source(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 2)));
        upper.setSurface(SculptedSurface.flat(4));
        Player player = player(helper);
        var result = click(player, lower, 0.98, 0.02);
        helper.assertTrue(result.consumesAction() && lower.surface().northEast() == 19
                && upper.surface().northWest() == 3 && buried.surface().equals(SculptedSurface.FULL),
            "Shared edits must join the exposed tile at the matching world height without editing its buried foundation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingCapRespectsOwnerProtection(GameTestHelper helper) {
        var source = place(helper, 2, 2, GolfMod.ROUGH_BLOCK.get());
        source.setSurface(SculptedSurface.flat(18));
        BlockPos upper = source.getBlockPos().above();
        Consumer<BlockEvent.BreakEvent> protect = event -> { if (event.getPos().equals(source.getBlockPos())) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(protect);
        try {
            var result = NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), upper, helper.getLevel().getBlockState(upper), player(helper)));
            helper.assertTrue(result.isCanceled(), "Mining the cap must not bypass its owner's block protection");
        } finally { NeoForge.EVENT_BUS.unregister(protect); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copiedSurfaceDataRepairsCap(GameTestHelper helper) {
        var source = place(helper, 2, 2, GolfMod.PUTTING_GREEN_BLOCK.get());
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("CornerHeights", new int[] {17, 18, 19, 20});
        source.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.runAfterDelay(3, () -> {
            BlockPos upper = source.getBlockPos().above();
            helper.assertTrue(helper.getLevel().getBlockState(upper).is(GolfMod.PUTTING_GREEN_CAP.get()), "Loading copied height data must create the upper-cell cap");
            var cap = (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(upper);
            helper.assertTrue(cap.surface().equals(source.surface()), "Repaired cap must preserve all saved heights");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void protectionHookChangingShapeCancelsSharedEdit(GameTestHelper helper) {
        var source = place(helper, 1, 1, GolfMod.FAIRWAY_BLOCK.get());
        var neighbor = place(helper, 2, 1, GolfMod.FAIRWAY_BLOCK.get());
        Consumer<BlockEvent.EntityPlaceEvent> change = event -> {
            if (event.getPos().equals(neighbor.getBlockPos())) { neighbor.setSurface(SculptedSurface.flat(12)); }
        };
        NeoForge.EVENT_BUS.addListener(change);
        var player = player(helper);
        try { helper.assertTrue(TerrainTrowelItem.edit(helper.getLevel(), player, player.getMainHandItem(), source.getBlockPos(),
            Direction.UP, Corner.NORTH_EAST, 8) == InteractionResult.FAIL, "A changed shape must invalidate the entire shared edit"); }
        finally { NeoForge.EVENT_BUS.unregister(change); }
        helper.assertTrue(source.surface().equals(SculptedSurface.FULL) && neighbor.surface().equals(SculptedSurface.flat(12)),
            "A stale edit must preserve both the untouched source and the callback's changes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void protectionHookReplacingEntityCancelsSharedEdit(GameTestHelper helper) {
        var source = place(helper, 1, 1, GolfMod.FAIRWAY_BLOCK.get());
        var neighbor = place(helper, 2, 1, GolfMod.FAIRWAY_BLOCK.get());
        Consumer<BlockEvent.EntityPlaceEvent> change = event -> {
            if (event.getPos().equals(neighbor.getBlockPos())) {
                helper.getLevel().removeBlockEntity(neighbor.getBlockPos());
                helper.getLevel().setBlockEntity(new SculptedGroundBlockEntity(neighbor.getBlockPos(), neighbor.getBlockState()));
            }
        };
        NeoForge.EVENT_BUS.addListener(change);
        var player = player(helper);
        try { helper.assertTrue(TerrainTrowelItem.edit(helper.getLevel(), player, player.getMainHandItem(), source.getBlockPos(),
            Direction.UP, Corner.NORTH_EAST, 8) == InteractionResult.FAIL, "A replacement entity must invalidate the edit even with identical state and corners"); }
        finally { NeoForge.EVENT_BUS.unregister(change); }
        helper.assertTrue(source.surface().equals(SculptedSurface.FULL)
            && SculptedGroundBlock.source(helper.getLevel(), neighbor.getBlockPos()).surface().equals(SculptedSurface.FULL),
            "A stale edit must not partially modify its remaining targets or a detached entity");
        helper.succeed();
    }

    private static SculptedGroundBlockEntity place(GameTestHelper helper, int x, int z, Block block) {
        BlockPos pos = new BlockPos(x, 1, z);
        helper.setBlock(pos, block);
        return (SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
    private static Player player(GameTestHelper helper) {
        Player player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "terrain-test"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GolfMod.TERRAIN_TROWEL.get()));
        return player;
    }
    private static net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands swap(Player player, boolean shift) {
        boolean previous = player.isShiftKeyDown();
        player.setShiftKeyDown(shift);
        var event = CommonHooks.onLivingSwapHandItems(player);
        player.setShiftKeyDown(previous);
        return event;
    }
    private static InteractionResult click(Player player, SculptedGroundBlockEntity ground, double x, double z) {
        BlockPos pos = ground.getBlockPos();
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(new Vec3(pos.getX() + x, pos.getY() + 1, pos.getZ() + z), Direction.UP, pos, false)));
    }
}
