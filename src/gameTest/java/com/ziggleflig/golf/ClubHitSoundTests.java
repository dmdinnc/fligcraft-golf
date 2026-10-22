package com.ziggleflig.golf;

import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfClubItem;
import com.ziggleflig.golf.terrain.SculptedBallPhysics;
import com.ziggleflig.golf.terrain.SculptedSurface;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class ClubHitSoundTests {
    @GameTest(template = "empty")
    public static void clubsHaveDistinctHitsAndOnlySoundOnceAtLaunch(GameTestHelper helper) {
        var signatures = new HashSet<String>();
        float previousPitch = 0;
        for (Item item : List.of(GolfMod.DRIVER.get(), GolfMod.HYBRID.get(), GolfMod.IRON.get(),
                GolfMod.PITCHING_WEDGE.get(), GolfMod.SAND_WEDGE.get(), GolfMod.PUTTER.get())) {
            var sounds = strike(helper, item, GolfMod.FAIRWAY_BLOCK.get(), null, false, false, 60, 450);
            helper.assertTrue(sounds.size() == (item == GolfMod.PUTTER.get() ? 1 : 2),
                "One impact and at most one swing must play per strike");
            helper.assertTrue(sounds.getFirst().getSound().value() == SoundEvents.CHAIN_HIT,
                "Every club must share the pitching wedge's metallic impact character");
            helper.assertTrue(sounds.getFirst().getOriginalPitch() < 1.0F,
                "Every club impact must be pitched down");
            if (item != GolfMod.PUTTER.get()) {
                float pitch = sounds.getFirst().getOriginalPitch();
                helper.assertTrue(pitch > previousPitch, "Shorter full-shot clubs must have progressively higher tones");
                previousPitch = pitch;
            }
            signatures.add(sounds.getFirst().getSound().value().getLocation() + ":" + sounds.getFirst().getOriginalPitch());
        }
        helper.assertTrue(signatures.size() == 6, "All six clubs must have distinct impact signatures");
        var full = strike(helper, GolfMod.DRIVER.get(), Blocks.STONE, null, false, false, 60, 450);
        var chip = strike(helper, GolfMod.DRIVER.get(), Blocks.STONE, null, false, false, 12, 450);
        helper.assertTrue(chip.getFirst().getOriginalVolume() < full.getFirst().getOriginalVolume(),
            "Chips must be quieter than full shots");
        var timeout = strike(helper, GolfMod.IRON.get(), Blocks.STONE, null, false, false, 60, 6001);
        helper.assertTrue(timeout.size() == 2, "A timed-out accuracy shot must also have hit audio");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sandLayerUsesActualSupportIncludingSlopesAndCaps(GameTestHelper helper) {
        for (Item item : List.of(GolfMod.DRIVER.get(), GolfMod.HYBRID.get(), GolfMod.IRON.get(),
                GolfMod.PITCHING_WEDGE.get(), GolfMod.SAND_WEDGE.get(), GolfMod.PUTTER.get())) {
            helper.assertTrue(hasSand(strike(helper, item, GolfMod.BUNKER_SAND_BLOCK.get(),
                new SculptedSurface(20, 24, 24, 20), false, false, 60, 450)),
                "Every club must add sand audio on sculpted bunker extensions");
        }
        for (Block sand : List.of(Blocks.SAND, Blocks.RED_SAND)) {
            helper.assertTrue(hasSand(strike(helper, GolfMod.IRON.get(), sand, null, false, false, 60, 450)),
                "Vanilla sand and red sand must add sand audio");
        }
        helper.assertFalse(hasSand(strike(helper, GolfMod.SAND_WEDGE.get(), GolfMod.FAIRWAY_BLOCK.get(),
            new SculptedSurface(4, 8, 8, 4), false, false, 60, 450)), "A sand wedge on lowered turf is not a sand hit");
        helper.assertFalse(hasSand(strike(helper, GolfMod.IRON.get(), Blocks.SANDSTONE,
            null, false, false, 60, 450)), "Sandstone is not loose sand");
        helper.assertFalse(hasSand(strike(helper, GolfMod.IRON.get(), Blocks.SAND,
            null, true, false, 60, 450)), "A tee isolates the hit from sand beneath it");
        helper.assertFalse(hasSand(strike(helper, GolfMod.IRON.get(), Blocks.SAND,
            null, false, true, 60, 450)), "A submerged ball must not sound like a dry bunker shot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingOrRemovedBallMakesNoHitSound(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "silent-shot"));
        player.setPos(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        var stack = new ItemStack(GolfMod.DRIVER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var sounds = new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> { sounds.add(event); };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var club = (GolfClubItem) stack.getItem();
            club.releaseUsing(stack, helper.getLevel(), player, club.getUseDuration(stack, player) - 60);
            GolfClubItem.executePendingShot(player, 450);
            helper.assertTrue(sounds.isEmpty(), "A missing ball must not make a hit sound");
            var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
            ball.setPos(player.getEyePosition().add(player.getLookAngle()));
            helper.getLevel().addFreshEntity(ball);
            club.releaseUsing(stack, helper.getLevel(), player, club.getUseDuration(stack, player) - 60);
            helper.assertTrue(GolfClubItem.getPendingShot(player) != null, "Test must arm a shot before removing its ball");
            ball.discard();
            GolfClubItem.executePendingShot(player, 450);
            helper.assertTrue(sounds.isEmpty(), "A removed target must not make a hit sound");
        } finally {
            GolfClubItem.clearPendingShot(player.getUUID());
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static boolean hasSand(List<PlayLevelSoundEvent.AtPosition> sounds) {
        return sounds.stream().anyMatch(sound -> sound.getSound().value() == SoundEvents.SAND_BREAK);
    }

    private static List<PlayLevelSoundEvent.AtPosition> strike(GameTestHelper helper, Item item, Block ground,
            SculptedSurface surface, boolean tee, boolean water, int chargeTicks, long elapsed) {
        BlockPos pos = new BlockPos(3, 1, 3), absolute = helper.absolutePos(pos);
        helper.setBlock(pos.above(), Blocks.AIR);
        helper.setBlock(pos, ground);
        if (surface != null) {
            ((SculptedGroundBlockEntity) helper.getLevel().getBlockEntity(absolute)).setSurface(surface);
        }
        var ball = new GolfBallEntity(GolfMod.GOLF_BALL_ENTITY.get(), helper.getLevel());
        Vec3 feet = Vec3.atLowerCornerOf(absolute).add(0.5, 1, 0.5);
        if (surface != null) {
            feet = new Vec3(feet.x, absolute.getY() + surface.ballFeetHeight(0.5, 0.5, ball.getBbWidth() / 2.0), feet.z);
            helper.assertTrue(SculptedBallPhysics.support(helper.getLevel(), feet, ball.getBbWidth() / 2.0, 0.015) != null,
                "Test ball must rest on actual sculpted support");
        }
        if (tee) {
            helper.setBlock(pos.above(), GolfMod.GOLF_TEE_BLOCK.get());
            feet = feet.add(0, 0.5, 0);
            ball.setOnTee(true);
        }
        if (water) { helper.setBlock(pos.above(), Blocks.WATER); }
        ball.setPos(feet);
        helper.getLevel().addFreshEntity(ball);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "hit-sound"));
        player.setPos(ball.getX() - 0.75, ball.getY() - player.getEyeHeight() + 0.125, ball.getZ());
        player.setYRot(-90);
        player.setXRot(0);
        var stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var sounds = new ArrayList<PlayLevelSoundEvent.AtPosition>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == helper.getLevel() && event.getPosition().distanceToSqr(ball.position()) < 0.0001) {
                sounds.add(event);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var club = (GolfClubItem) item;
            club.releaseUsing(stack, helper.getLevel(), player, club.getUseDuration(stack, player) - chargeTicks);
            helper.assertTrue(GolfClubItem.getPendingShot(player) != null, "Test shot must acquire its target");
            helper.assertTrue(sounds.isEmpty(), "Charging and arming must be silent");
            GolfClubItem.executePendingShot(player, elapsed);
            helper.assertTrue(ball.getStrokes() == 1 && !sounds.isEmpty(), "A real strike must emit sound");
            int count = sounds.size();
            GolfClubItem.executePendingShot(player, elapsed);
            helper.assertTrue(sounds.size() == count, "A repeated action must not replay hit audio");
            helper.assertTrue(sounds.stream().allMatch(sound -> sound.getSource() == SoundSource.PLAYERS),
                "All hit layers must use the Players volume slider");
            return sounds;
        } finally {
            GolfClubItem.clearPendingShot(player.getUUID());
            NeoForge.EVENT_BUS.unregister(listener);
            ball.discard();
            helper.setBlock(pos.above(), Blocks.AIR);
            helper.setBlock(pos.above(2), Blocks.AIR);
        }
    }
}
