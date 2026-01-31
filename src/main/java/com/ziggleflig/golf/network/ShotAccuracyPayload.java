package com.ziggleflig.golf.network;

import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.item.GolfClubItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShotAccuracyPayload(long elapsedMs) implements CustomPacketPayload {
    public static final Type<ShotAccuracyPayload> TYPE = new Type<>(GolfMod.id("shot_accuracy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShotAccuracyPayload> STREAM_CODEC =
            StreamCodec.of(ShotAccuracyPayload::encode, ShotAccuracyPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ShotAccuracyPayload payload) {
        buffer.writeLong(payload.elapsedMs);
    }

    private static ShotAccuracyPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ShotAccuracyPayload(buffer.readLong());
    }

    public static void handle(ShotAccuracyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GolfClubItem.executePendingShot(context.player(), payload.elapsedMs()));
    }
}
