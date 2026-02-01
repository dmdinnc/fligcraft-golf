package com.ziggleflig.golf;

import org.slf4j.Logger;

import com.ziggleflig.golf.block.DrivingRangeBayBlock;
import com.ziggleflig.golf.block.FairwayBlock;
import com.ziggleflig.golf.block.FairwayLayerBlock;
import com.ziggleflig.golf.block.GolfFlagBlock;
import com.ziggleflig.golf.block.GolfTeeBlock;
import com.ziggleflig.golf.block.PuttingGreenBlock;
import com.ziggleflig.golf.block.RoughBlock;
import com.ziggleflig.golf.block.RoughLayerBlock;
import com.ziggleflig.golf.client.GolfBagScreen;
import com.ziggleflig.golf.client.GolfBallRenderer;
import com.ziggleflig.golf.client.GolfClient;
import com.ziggleflig.golf.command.DeleteBallCommand;
import com.ziggleflig.golf.command.DeleteAllMineCommand;
import com.ziggleflig.golf.command.DeleteAllTrackedCommand;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.inventory.GolfBagMenu;
import com.ziggleflig.golf.item.GolfBagItem;
import com.ziggleflig.golf.item.GolfBallItem;
import com.ziggleflig.golf.item.GolfBallTrackerItem;
import com.ziggleflig.golf.item.GolfClubItem;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> GOLF_TEE_BLOCK = BLOCKS.register("golf_tee", 
            () -> new GolfTeeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.2F).noOcclusion()));
    public static final DeferredBlock<Block> GOLF_FLAG_BLOCK = BLOCKS.register("golf_flag", 
            () -> new GolfFlagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).noOcclusion()));
    public static final DeferredBlock<Block> DRIVING_RANGE_BAY_BLOCK = BLOCKS.register("driving_range_bay",
            () -> new DrivingRangeBayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).noOcclusion()));

    // Blocks
    public static final DeferredBlock<Block> FAIRWAY_BLOCK = BLOCKS.register("fairway",
            () -> new FairwayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F)));
    public static final DeferredBlock<Block> ROUGH_BLOCK = BLOCKS.register("rough",
            () -> new RoughBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F)));
    public static final DeferredBlock<Block> PUTTING_GREEN_BLOCK = BLOCKS.register("putting_green",
            () -> new PuttingGreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F)));
    public static final DeferredBlock<Block> FAIRWAY_LAYER_BLOCK = BLOCKS.register("fairway_layer",
            () -> new FairwayLayerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.1F).noOcclusion()));
    public static final DeferredBlock<Block> ROUGH_LAYER_BLOCK = BLOCKS.register("rough_layer",
            () -> new RoughLayerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.1F).noOcclusion()));

    // Entities
    public static final DeferredHolder<EntityType<?>, EntityType<GolfBallEntity>> GOLF_BALL_ENTITY = ENTITY_TYPES.register("golf_ball",
            () -> EntityType.Builder.<GolfBallEntity>of(GolfBallEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(ResourceLocation.fromNamespaceAndPath(MODID, "golf_ball").toString()));

    // Items
    public static final DeferredItem<BlockItem> GOLF_TEE_ITEM = ITEMS.registerSimpleBlockItem("golf_tee", GOLF_TEE_BLOCK);
    public static final DeferredItem<BlockItem> GOLF_FLAG_ITEM = ITEMS.registerSimpleBlockItem("golf_flag", GOLF_FLAG_BLOCK);
    public static final DeferredItem<BlockItem> DRIVING_RANGE_BAY_ITEM = ITEMS.registerSimpleBlockItem("driving_range_bay", DRIVING_RANGE_BAY_BLOCK);
    public static final DeferredItem<BlockItem> FAIRWAY_ITEM = ITEMS.registerSimpleBlockItem("fairway", FAIRWAY_BLOCK);
    public static final DeferredItem<BlockItem> ROUGH_ITEM = ITEMS.registerSimpleBlockItem("rough", ROUGH_BLOCK);
    public static final DeferredItem<BlockItem> PUTTING_GREEN_ITEM = ITEMS.registerSimpleBlockItem("putting_green", PUTTING_GREEN_BLOCK);
    public static final DeferredItem<BlockItem> FAIRWAY_LAYER_ITEM = ITEMS.registerSimpleBlockItem("fairway_layer", FAIRWAY_LAYER_BLOCK);
    public static final DeferredItem<BlockItem> ROUGH_LAYER_ITEM = ITEMS.registerSimpleBlockItem("rough_layer", ROUGH_LAYER_BLOCK);
    public static final DeferredItem<Item> GOLF_BALL = ITEMS.register("golf_ball",
            () -> new GolfBallItem(new Item.Properties().stacksTo(16)));

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
            () -> new GolfClubItem(new Item.Properties().stacksTo(1), 0.7D, 0.02D));

    public static final DeferredItem<Item> GOLF_BALL_TRACKER_ITEM = ITEMS.register("golf_ball_tracker",
            () -> new GolfBallTrackerItem(new Item.Properties().stacksTo(1)));

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

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GOLF_TAB = CREATIVE_MODE_TABS.register("golf_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.fligcraft_golf"))
            .icon(() -> GOLF_BALL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(DRIVER.get());
                output.accept(HYBRID.get());
                output.accept(IRON.get());
                output.accept(PITCHING_WEDGE.get());
                output.accept(SAND_WEDGE.get());
                output.accept(PUTTER.get());
                output.accept(GOLF_BALL.get());
                output.accept(GOLF_BALL_TRACKER_ITEM.get());
                output.accept(RANGEFINDER.get());
                output.accept(GOLF_BAG_ITEM.get());
                output.accept(GOLF_TEE_ITEM.get());
                output.accept(GOLF_FLAG_ITEM.get());
                output.accept(DRIVING_RANGE_BAY_ITEM.get());
                output.accept(FAIRWAY_ITEM.get());
                output.accept(FAIRWAY_LAYER_ITEM.get());
                output.accept(ROUGH_ITEM.get());
                output.accept(ROUGH_LAYER_ITEM.get());
                output.accept(PUTTING_GREEN_ITEM.get());
            }).build());

    public GolfMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(GolfNetwork::registerPayloadHandlers);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(GolfMod::registerRenderers);
            modEventBus.addListener(GolfMod::registerLayerDefinitions);
            modEventBus.addListener(GolfMod::registerScreens);
            modEventBus.addListener(GolfClient::registerGuiLayers);
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, GolfModConfig.SPEC);

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
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                GOLF_BALL_ENTITY.get(),
                GolfBallRenderer::new
        );
    }

    private static void registerLayerDefinitions(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GolfBallRenderer.LAYER_LOCATION, GolfBallRenderer.GolfBallModel::createBodyLayer);
    }
    
    private static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(GOLF_BAG_MENU.get(), GolfBagScreen::new);
    }

    private void addCreativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DRIVER.get());
            event.accept(HYBRID.get());
            event.accept(IRON.get());
            event.accept(PITCHING_WEDGE.get());
            event.accept(SAND_WEDGE.get());
            event.accept(PUTTER.get());
            event.accept(GOLF_BALL.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("GolfMod: server starting");
    }
}
