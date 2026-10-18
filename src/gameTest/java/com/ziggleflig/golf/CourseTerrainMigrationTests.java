package com.ziggleflig.golf;

import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class CourseTerrainMigrationTests {
    @GameTest(template = "empty")
    public static void shippedPaletteWithoutBlockEntitiesUpgradesInPlace(GameTestHelper helper) {
        String[] names = {"fairway", "rough", "putting_green"};
        for (int i = 0; i < names.length; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(2 + i, 1, 3));
            CompoundTag savedState = new CompoundTag();
            savedState.putString("Name", GolfMod.MODID + ":" + names[i]);
            var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedState);
            var chunk = helper.getLevel().getChunkAt(pos);
            // Simulate a shipped chunk palette: its blocks have no saved block-entity entries.
            chunk.removeBlockEntity(pos);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
            helper.assertTrue(chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null,
                "The migration fixture must not already contain terrain metadata");
            var ground = SculptedGroundBlock.source(helper.getLevel(), pos);
            helper.assertTrue(ground != null && ground.surface().equals(SculptedSurface.FULL),
                "An old " + names[i] + " must acquire editable geometry while keeping its full-block height");
            var inventoryItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GolfMod.MODID, names[i]));
            helper.assertTrue(inventoryItem == state.getBlock().asItem(), "Existing inventory IDs must still place the same terrain block");
            var shape = new SculptedSurface(8, 12, 16, 10);
            helper.assertTrue(ground.setSurface(shape), "An upgraded block must immediately support editing");
            var restored = BlockEntity.loadStatic(pos, state, ground.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(restored instanceof SculptedGroundBlockEntity terrain && terrain.surface().equals(shape),
                "Upgraded terrain must save and reload its edited shape");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingTerrainRecipesProduceEditableBlocks(GameTestHelper helper) {
        var additions = List.of(Items.BONE_MEAL, Items.WHEAT_SEEDS, Items.LIME_DYE);
        var blocks = List.of(GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.PUTTING_GREEN_BLOCK.get());
        for (int i = 0; i < blocks.size(); i++) {
            var input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.GRASS_BLOCK), new ItemStack(additions.get(i))));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
            helper.assertTrue(recipe.isPresent() && recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess()).is(blocks.get(i).asItem()),
                "The original terrain recipes must craft the new editable blocks directly");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingFlagStillScoresOnUpgradedGreen(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.PUTTING_GREEN_BLOCK.get());
        BlockPos groundPos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().removeBlockEntity(groundPos);
        helper.setBlock(new BlockPos(3, 2, 3), GolfMod.GOLF_FLAG_BLOCK.get());
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        ball.setPos(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        ball.setDeltaMovement(new Vec3(0.02, 0, 0));
        ball.setOnGround(true);
        ball.tick();
        helper.assertTrue(ball.isRemoved() && helper.getLevel().getBlockState(groundPos.above()).is(GolfMod.GOLF_FLAG_BLOCK.get()),
            "A shipped flag must keep its scoring functionality and remain placed above the upgraded green");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedLayersDisappearFromSavedPalettesAndItems(GameTestHelper helper) {
        for (String name : List.of("fairway_layer", "rough_layer")) {
            var id = ResourceLocation.fromNamespaceAndPath(GolfMod.MODID, name);
            helper.assertTrue(!BuiltInRegistries.BLOCK.keySet().contains(id) && !BuiltInRegistries.ITEM.keySet().contains(id),
                "Removed layers must not remain registered as hidden blocks or items");
            helper.assertTrue(BuiltInRegistries.BLOCK.get(id) == Blocks.AIR && BuiltInRegistries.ITEM.get(id) == Items.AIR,
                "Removed IDs must resolve explicitly to air");
            for (int height = 1; height <= 8; height++) {
                CompoundTag savedState = new CompoundTag();
                savedState.putString("Name", id.toString());
                CompoundTag properties = new CompoundTag();
                properties.putString("layers", Integer.toString(height));
                savedState.put("Properties", properties);
                var loaded = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedState);
                helper.assertTrue(loaded.isAir() && loaded.getCollisionShape(helper.getLevel(), BlockPos.ZERO).isEmpty(),
                    "Every saved layer height must load as empty space without residual collision");
                helper.assertTrue(NbtUtils.writeBlockState(loaded).getString("Name").equals("minecraft:air"),
                    "Saving again must discard the removed layer ID");
            }
            CompoundTag savedItem = new CompoundTag();
            savedItem.putString("id", id.toString());
            savedItem.putInt("count", 32);
            helper.assertTrue(ItemStack.parseOptional(helper.getLevel().registryAccess(), savedItem).isEmpty(),
                "Layer stacks from saved inventories and containers must disappear too");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void renamedBunkerSandPreservesPrototypeBlocksCapsAndItems(GameTestHelper helper) {
        for (String name : List.of("sand", "sand_cap")) {
            CompoundTag savedState = new CompoundTag();
            savedState.putString("Name", GolfMod.MODID + ":" + name);
            var loaded = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), savedState);
            var expected = name.equals("sand") ? GolfMod.BUNKER_SAND_BLOCK.get() : GolfMod.BUNKER_SAND_CAP.get();
            helper.assertTrue(loaded.is(expected), "Prototype sand and its extension must survive the rename");
            helper.assertTrue(NbtUtils.writeBlockState(loaded).getString("Name").equals(GolfMod.MODID + ":bunker_" + name),
                "Subsequent saves must use the Bunker Sand ID");
        }
        CompoundTag savedItem = new CompoundTag();
        savedItem.putString("id", GolfMod.MODID + ":sand");
        savedItem.putInt("count", 32);
        var loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), savedItem);
        helper.assertTrue(loaded.is(GolfMod.BUNKER_SAND_ITEM.get()) && loaded.getCount() == 32,
            "Renaming sand must preserve existing inventory stacks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedChunkSectionRemovesOnlyLayersAndWritesAir(GameTestHelper helper) {
        // Use the same palette codec as ChunkSerializer, including old state properties and packed indices.
        var codec = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        for (String name : List.of("fairway_layer", "rough_layer")) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", GolfMod.MODID + ":" + name);
            CompoundTag properties = new CompoundTag();
            properties.putString("layers", "8");
            state.put("Properties", properties);
            palette.add(state);
        }
        palette.add(NbtUtils.writeBlockState(GolfMod.PUTTING_GREEN_BLOCK.get().defaultBlockState()));
        CompoundTag section = new CompoundTag();
        section.put("palette", palette);
        long[] data = new long[256];
        data[0] = 0x3210; // Stone, fairway layer, rough layer, green; the remaining cells are stone.
        section.putLongArray("data", data);
        var loaded = codec.parse(NbtOps.INSTANCE, section).getOrThrow();
        helper.assertTrue(loaded.get(0, 0, 0).is(Blocks.STONE) && loaded.get(1, 0, 0).isAir()
            && loaded.get(2, 0, 0).isAir() && loaded.get(3, 0, 0).is(GolfMod.PUTTING_GREEN_BLOCK.get()),
            "Loading a saved chunk section must erase the layers while preserving neighboring blocks");
        var rewritten = codec.encodeStart(NbtOps.INSTANCE, loaded).getOrThrow();
        helper.assertTrue(!rewritten.toString().contains("fairway_layer") && !rewritten.toString().contains("rough_layer"),
            "The next chunk save must write canonical air rather than retain removed IDs");
        var reloaded = codec.parse(NbtOps.INSTANCE, rewritten).getOrThrow();
        helper.assertTrue(reloaded.get(1, 0, 0).isAir() && reloaded.get(2, 0, 0).isAir(),
            "Removed terrain must remain absent after another save/load cycle");
        helper.succeed();
    }
}
