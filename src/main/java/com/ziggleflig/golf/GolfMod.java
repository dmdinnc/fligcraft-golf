package com.ziggleflig.golf;

import org.slf4j.Logger;

import com.ziggleflig.golf.block.DrivingRangeBayBlock;
import com.ziggleflig.golf.block.GolfFlagBlock;
import com.ziggleflig.golf.item.GolfFlagItem;
import com.ziggleflig.golf.block.GolfTeeBlock;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.TerrainMaterial;
import com.ziggleflig.golf.client.SculptedGroundGeometry;
import com.ziggleflig.golf.client.CourseFlagRenderer;
import com.ziggleflig.golf.client.TerrainTrowelOverlay;
import com.ziggleflig.golf.item.TerrainTrowelItem;
import com.ziggleflig.golf.item.TerrainGraderItem;
import com.ziggleflig.golf.client.TerrainGraderOverlay;
import com.ziggleflig.golf.client.GolfBagScreen;
import com.ziggleflig.golf.client.GolfBallRenderer;
import com.ziggleflig.golf.client.GolfBallTrackerScreen;
import com.ziggleflig.golf.client.GolfCartRenderer;
import com.ziggleflig.golf.client.GolfClient;
import com.ziggleflig.golf.client.GolfClientConfig;
import com.ziggleflig.golf.command.DeleteBallCommand;
import com.ziggleflig.golf.command.DeleteAllMineCommand;
import com.ziggleflig.golf.command.DeleteAllTrackedCommand;
import com.ziggleflig.golf.command.GolfHelperManageCommand;
import com.ziggleflig.golf.command.GolfClubsCommand;
import com.ziggleflig.golf.command.TerrainGraderCommand;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.entity.GolfCartEntity;
import com.ziggleflig.golf.entity.GolfCartModel;
import com.ziggleflig.golf.inventory.GolfBagMenu;
import com.ziggleflig.golf.inventory.GolfBallTrackerMenu;
import com.ziggleflig.golf.recipe.GolfBallDyeRecipe;
import com.ziggleflig.golf.tracker.GolfBallLocator;
import com.ziggleflig.golf.tracker.GolfBallTracking;
import com.ziggleflig.golf.item.GolfBagItem;
import com.ziggleflig.golf.item.GolfBallItem;
import com.ziggleflig.golf.item.GolfCartItem;
import com.ziggleflig.golf.item.GolfHelperItem;
import com.ziggleflig.golf.item.GolfClubItem;
import com.ziggleflig.golf.item.LawnmowerItem;
import com.ziggleflig.golf.item.RangefinderItem;
import com.ziggleflig.golf.network.GolfNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.world.item.BlockItem;
import com.ziggleflig.golf.item.CourseGroundItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(GolfMod.MODID)
public class GolfMod {
    public static final String MODID = "fligcraft_golf";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<GolfBallDyeRecipe>> GOLF_BALL_DYE_RECIPE =
        RECIPE_SERIALIZERS.register("golf_ball_dye", () -> new SimpleCraftingRecipeSerializer<>(GolfBallDyeRecipe::new));
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> GOLF_TEE_BLOCK = BLOCKS.register("golf_tee", 
            () -> new GolfTeeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.2F).noOcclusion()));
    public static final DeferredBlock<Block> GOLF_FLAG_BLOCK = BLOCKS.register("golf_flag", 
            () -> new GolfFlagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).noOcclusion()));
    public static final DeferredBlock<Block> DRIVING_RANGE_BAY_BLOCK = BLOCKS.register("driving_range_bay",
            () -> new DrivingRangeBayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).noOcclusion()));

    // Keep the shipped block/item IDs and property-free states; old chunks default to full geometry.
    public static final DeferredBlock<SculptedGroundBlock> FAIRWAY_BLOCK = courseGround("fairway", TerrainMaterial.FAIRWAY);
    public static final DeferredBlock<SculptedGroundBlock> ROUGH_BLOCK = courseGround("rough", TerrainMaterial.ROUGH);
    public static final DeferredBlock<SculptedGroundBlock> PUTTING_GREEN_BLOCK = courseGround("putting_green", TerrainMaterial.GREEN);
    public static final DeferredBlock<SculptedGroundBlock> BUNKER_SAND_BLOCK = courseGround("bunker_sand", TerrainMaterial.SAND);
    public static final DeferredBlock<SculptedGroundBlock> FAIRWAY_CAP = courseCap("fairway_cap", TerrainMaterial.FAIRWAY);
    public static final DeferredBlock<SculptedGroundBlock> ROUGH_CAP = courseCap("rough_cap", TerrainMaterial.ROUGH);
    public static final DeferredBlock<SculptedGroundBlock> PUTTING_GREEN_CAP = courseCap("putting_green_cap", TerrainMaterial.GREEN);
    public static final DeferredBlock<SculptedGroundBlock> BUNKER_SAND_CAP = courseCap("bunker_sand_cap", TerrainMaterial.SAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SculptedGroundBlockEntity>> COURSE_GROUND_ENTITY =
        BLOCK_ENTITY_TYPES.register("course_ground", () -> BlockEntityType.Builder.of(SculptedGroundBlockEntity::new,
            FAIRWAY_BLOCK.get(), ROUGH_BLOCK.get(), PUTTING_GREEN_BLOCK.get(), BUNKER_SAND_BLOCK.get(),
            FAIRWAY_CAP.get(), ROUGH_CAP.get(), PUTTING_GREEN_CAP.get(), BUNKER_SAND_CAP.get()).build(null));

    private static DeferredBlock<SculptedGroundBlock> courseCap(String name, TerrainMaterial material) {
        return BLOCKS.register(name, () -> new SculptedGroundBlock(material, true, courseProperties(material)));
    }
    public static SculptedGroundBlock courseCap(TerrainMaterial material) {
        return switch (material) {
            case FAIRWAY -> FAIRWAY_CAP.get();
            case ROUGH -> ROUGH_CAP.get();
            case GREEN -> PUTTING_GREEN_CAP.get();
            case SAND -> BUNKER_SAND_CAP.get();
        };
    }

    private static DeferredBlock<SculptedGroundBlock> courseGround(String name, TerrainMaterial material) {
        return BLOCKS.register(name, () -> new SculptedGroundBlock(material, courseProperties(material)));
    }
    private static BlockBehaviour.Properties courseProperties(TerrainMaterial material) {
        boolean sand = material == TerrainMaterial.SAND;
        return BlockBehaviour.Properties.of().mapColor(sand ? MapColor.SAND : MapColor.GRASS)
            .sound(sand ? SoundType.SAND : SoundType.GRASS).strength(sand ? 0.5f : 0.6f).noOcclusion().dynamicShape();
    }

    // Entities
    public static final DeferredHolder<EntityType<?>, EntityType<GolfBallEntity>> GOLF_BALL_ENTITY = ENTITY_TYPES.register("golf_ball",
            () -> EntityType.Builder.<GolfBallEntity>of(GolfBallEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(GolfBallEntity.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(MODID, "golf_ball").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<GolfCartEntity>> GOLF_CART_ENTITY = ENTITY_TYPES.register("golf_cart",
            () -> EntityType.Builder.<GolfCartEntity>of(GolfCartEntity::new, MobCategory.MISC)
                    .sized(1.5F, 1.2F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(MODID, "golf_cart").toString()));

    // Items
    public static final DeferredItem<BlockItem> GOLF_TEE_ITEM = ITEMS.registerSimpleBlockItem("golf_tee", GOLF_TEE_BLOCK);
    public static final DeferredItem<BlockItem> GOLF_FLAG_ITEM = ITEMS.register("golf_flag", () -> new GolfFlagItem(GOLF_FLAG_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> DRIVING_RANGE_BAY_ITEM = ITEMS.registerSimpleBlockItem("driving_range_bay", DRIVING_RANGE_BAY_BLOCK);
    public static final DeferredItem<BlockItem> FAIRWAY_ITEM = ITEMS.register("fairway", () -> new CourseGroundItem(FAIRWAY_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> ROUGH_ITEM = ITEMS.register("rough", () -> new CourseGroundItem(ROUGH_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> PUTTING_GREEN_ITEM = ITEMS.register("putting_green", () -> new CourseGroundItem(PUTTING_GREEN_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> BUNKER_SAND_ITEM = ITEMS.register("bunker_sand", () -> new CourseGroundItem(BUNKER_SAND_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<Item> TERRAIN_TROWEL = ITEMS.register("terrain_trowel", () -> new TerrainTrowelItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TERRAIN_GRADER = ITEMS.register("terrain_grader", () -> new TerrainGraderItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GOLF_BALL = ITEMS.register("golf_ball",
            () -> new GolfBallItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> GOLF_CART_ITEM = ITEMS.register("golf_cart",
            () -> new GolfCartItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> CLUB_SHAFT = ITEMS.register("club_shaft",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DRIVER = ITEMS.register("driver",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 4.9D, 0.35D));
    public static final DeferredItem<Item> HYBRID = ITEMS.register("hybrid",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 4.15D, 0.45D));
    public static final DeferredItem<Item> IRON = ITEMS.register("iron",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 3.6D, 0.65D));
    public static final DeferredItem<Item> PITCHING_WEDGE = ITEMS.register("pitching_wedge",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 2.95D, 0.80D));
    public static final DeferredItem<Item> SAND_WEDGE = ITEMS.register("sand_wedge",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 4.0D, 1.40D));
    public static final DeferredItem<Item> PUTTER = ITEMS.register("putter",
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 0.7D, 0.02D, true));

    public static final DeferredItem<Item> GOLF_HELPER_ITEM = ITEMS.register("golf_helper",
            () -> new GolfHelperItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> LAWNMOWER = ITEMS.register("lawnmower",
            () -> new LawnmowerItem(new Item.Properties().stacksTo(1).durability(5000)));

    public static final DeferredItem<Item> GOLF_BAG_ITEM = ITEMS.register("golf_bag",
            () -> new GolfBagItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> RANGEFINDER = ITEMS.register("rangefinder",
            () -> new RangefinderItem(new Item.Properties().stacksTo(1)));

    // Menus
    public static final DeferredHolder<MenuType<?>, MenuType<GolfBagMenu>> GOLF_BAG_MENU = MENU_TYPES.register("golf_bag",
            () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                boolean isMainHand = data.readBoolean();
                ItemStack bagStack = isMainHand ? inv.player.getMainHandItem() : inv.player.getOffhandItem();
                return new GolfBagMenu(windowId, inv, bagStack);
            }));
    public static final DeferredHolder<MenuType<?>, MenuType<GolfBallTrackerMenu>> GOLF_BALL_TRACKER_MENU = MENU_TYPES.register("golf_ball_tracker",
        () -> IMenuTypeExtension.create((id, inventory, data) -> new GolfBallTrackerMenu(id, inventory)));

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GOLF_TAB = CREATIVE_MODE_TABS.register("golf_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fligcraft_golf"))
            .icon(() -> GOLF_BALL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(CLUB_SHAFT.get());
                output.accept(DRIVER.get());
                output.accept(HYBRID.get());
                output.accept(IRON.get());
                output.accept(PITCHING_WEDGE.get());
                output.accept(SAND_WEDGE.get());
                output.accept(PUTTER.get());
                for (DyeColor color : DyeColor.values()) {
                    ItemStack ball = GOLF_BALL.get().getDefaultInstance();
                    GolfBallItem.setColor(ball, color);
                    output.accept(ball);
                }
                output.accept(GOLF_CART_ITEM.get());
                output.accept(GOLF_HELPER_ITEM.get());
                output.accept(LAWNMOWER.get());
                output.accept(RANGEFINDER.get());
                output.accept(GOLF_BAG_ITEM.get());
                output.accept(GOLF_TEE_ITEM.get());
                output.accept(GOLF_FLAG_ITEM.get());
                output.accept(DRIVING_RANGE_BAY_ITEM.get());
                output.accept(FAIRWAY_ITEM.get());
                output.accept(ROUGH_ITEM.get());
                output.accept(PUTTING_GREEN_ITEM.get());
                output.accept(BUNKER_SAND_ITEM.get());
                output.accept(TERRAIN_TROWEL.get());
                output.accept(TERRAIN_GRADER.get());
            }).build());

    public GolfMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(GolfNetwork::registerPayloadHandlers);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, GolfClientConfig.SPEC, "fligcraft_golf-client.toml");
            modEventBus.addListener(GolfMod::registerRenderers);
            modEventBus.addListener(GolfMod::registerLayerDefinitions);
            modEventBus.addListener(GolfMod::registerScreens);
            modEventBus.addListener(GolfClient::registerGuiLayers);
            modEventBus.addListener(GolfClient::registerItemColors);
            modEventBus.addListener(GolfMod::registerTerrainModels);
            NeoForge.EVENT_BUS.addListener(TerrainTrowelOverlay::highlight);
            NeoForge.EVENT_BUS.addListener(TerrainGraderOverlay::renderWorld);
            NeoForge.EVENT_BUS.addListener(TerrainGraderOverlay::onLogout);
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(GolfMod::registerCourseAliases);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, GolfModConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(GolfClubConfig::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(GolfClubConfig::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GolfBallLocator::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(GolfBallLocator::onLogout);
        NeoForge.EVENT_BUS.addListener(GolfBallLocator::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GolfBallTracking::onJoin);
        NeoForge.EVENT_BUS.addListener(GolfBallTracking::onLeave);
        NeoForge.EVENT_BUS.addListener(SculptedGroundBlock::onCapBreak);
        NeoForge.EVENT_BUS.addListener(TerrainTrowelItem::onSwapHands);
        NeoForge.EVENT_BUS.addListener(TerrainGraderItem::onSwapHands);

        modEventBus.addListener(this::addCreativeContents);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("GolfMod common setup");
    }

    @SubscribeEvent
    public void registerCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        DeleteBallCommand.register(event.getDispatcher());
        DeleteAllMineCommand.register(event.getDispatcher());
        DeleteAllTrackedCommand.register(event.getDispatcher());
        GolfHelperManageCommand.register(event.getDispatcher());
        GolfClubsCommand.register(event.getDispatcher());
        TerrainGraderCommand.register(event.getDispatcher());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerCourseAliases(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey() == Registries.BLOCK || event.getRegistryKey() == Registries.ITEM) {
            // Removed layers decode as air in chunk palettes, containers, and player inventories.
            // Aliases do not register replacement blocks or expose items in the creative tab.
            event.getRegistry().addAlias(id("fairway_layer"), ResourceLocation.withDefaultNamespace("air"));
            event.getRegistry().addAlias(id("rough_layer"), ResourceLocation.withDefaultNamespace("air"));
            // Retain terrain from earlier Sand prototypes under the new Bunker Sand name.
            event.getRegistry().addAlias(id("sand"), id("bunker_sand"));
            if (event.getRegistryKey() == Registries.BLOCK) { event.getRegistry().addAlias(id("sand_cap"), id("bunker_sand_cap")); }
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(COURSE_GROUND_ENTITY.get(), CourseFlagRenderer::new);
        event.registerEntityRenderer(
                GOLF_BALL_ENTITY.get(),
                GolfBallRenderer::new
        );
        event.registerEntityRenderer(
                GOLF_CART_ENTITY.get(),
                GolfCartRenderer::new
        );
    }

    private static void registerTerrainModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
        event.register(id("course_ground"), new SculptedGroundGeometry.Loader());
    }

    private static void registerLayerDefinitions(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GolfBallRenderer.LAYER_LOCATION, GolfBallRenderer.GolfBallModel::createBodyLayer);
        event.registerLayerDefinition(GolfCartModel.LAYER_LOCATION, GolfCartModel::createBodyLayer);
    }
    
    private static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(GOLF_BAG_MENU.get(), GolfBagScreen::new);
        event.register(GOLF_BALL_TRACKER_MENU.get(), GolfBallTrackerScreen::new);
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CLUB_SHAFT.get());
            event.accept(DRIVER.get());
            event.accept(HYBRID.get());
            event.accept(IRON.get());
            event.accept(PITCHING_WEDGE.get());
            event.accept(SAND_WEDGE.get());
            event.accept(PUTTER.get());
            event.accept(GOLF_BALL.get());
            event.accept(GOLF_CART_ITEM.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("GolfMod: server starting");
    }
}
