package com.ziggleflig.golf.item;

import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.TerrainPlacement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CourseGroundItem extends BlockItem {
    public CourseGroundItem(Block block, Properties properties) { super(block, properties); }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        var data = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        // Pick-block copies with saved corner data keep their explicit shape.
        SculptedSurface surface = data.contains("CornerHeights", Tag.TAG_INT_ARRAY)
            ? SculptedSurface.fromArray(data.getIntArray("CornerHeights")) : TerrainPlacement.fit(level, pos);
        if (!TerrainPlacement.canFit(level, pos, surface)) {
            if (player != null) { player.displayClientMessage(Component.translatable("message.fligcraft_golf.terrain.match_blocked"), true); }
            return false;
        }
        if (surface.hasOverflow() && player != null && (!level.mayInteract(player, pos.above())
                || !player.mayUseItemAt(pos.above(), context.getClickedFace(), stack))) { return false; }
        // Vanilla/NeoForge owns placement, consumption, and protection-event rollback for both blocks.
        if (!super.placeBlock(context, state)) { return false; }
        if (level.getBlockEntity(pos) instanceof SculptedGroundBlockEntity ground) { ground.setSurface(surface); }
        return true;
    }
}
