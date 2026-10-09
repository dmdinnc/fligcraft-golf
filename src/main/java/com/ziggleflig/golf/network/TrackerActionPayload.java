package com.ziggleflig.golf.network;

import java.util.UUID;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.inventory.GolfBallTrackerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrackerActionPayload(int containerId, UUID ballId, boolean delete) implements CustomPacketPayload {
    public static final Type<TrackerActionPayload> TYPE = new Type<>(GolfMod.id("tracker_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackerActionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> { buffer.writeVarInt(payload.containerId); buffer.writeUUID(payload.ballId); buffer.writeBoolean(payload.delete); },
        buffer -> new TrackerActionPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TrackerActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof GolfBallTrackerMenu menu
                    && menu.containerId == payload.containerId) {
                menu.act(player, payload.ballId, payload.delete);
            }
        });
    }
}
