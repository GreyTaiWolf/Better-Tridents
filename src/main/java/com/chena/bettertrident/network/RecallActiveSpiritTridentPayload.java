package com.chena.bettertrident.network;

import com.chena.bettertrident.BetterTrident;
import com.chena.bettertrident.SpiritTridentState;
import com.chena.bettertrident.entity.SpiritTridentEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RecallActiveSpiritTridentPayload() implements CustomPacketPayload {
    public static final Type<RecallActiveSpiritTridentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterTrident.MOD_ID, "recall_active_spirit_trident")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RecallActiveSpiritTridentPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            },
            buffer -> new RecallActiveSpiritTridentPayload()
    );

    @Override
    public Type<RecallActiveSpiritTridentPayload> type() {
        return TYPE;
    }

    public static void handle(RecallActiveSpiritTridentPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        for (SpiritTridentEntity trident : SpiritTridentState.getActiveTridents(player)) {
            if (trident.canSummonToOrbitBy(player)) {
                trident.summonToOrbit(player);
            }
        }
    }
}
