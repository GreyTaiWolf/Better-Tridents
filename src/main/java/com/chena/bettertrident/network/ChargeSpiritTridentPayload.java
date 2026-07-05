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

public record ChargeSpiritTridentPayload(Action action, int targetId) implements CustomPacketPayload {
    public static final Type<ChargeSpiritTridentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterTrident.MOD_ID, "charge_spirit_trident")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargeSpiritTridentPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeEnum(payload.action());
                buffer.writeVarInt(payload.targetId());
            },
            buffer -> new ChargeSpiritTridentPayload(buffer.readEnum(Action.class), buffer.readVarInt())
    );

    public ChargeSpiritTridentPayload(Action action) {
        this(action, -1);
    }

    @Override
    public Type<ChargeSpiritTridentPayload> type() {
        return TYPE;
    }

    public static void handle(ChargeSpiritTridentPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        SpiritTridentEntity trident = SpiritTridentState.getActiveTrident(player);
        if (trident == null || !trident.isOwnedBy(player)) {
            return;
        }

        switch (payload.action()) {
            case START -> {
                if (player.getMainHandItem().isEmpty() && trident.canStartChargedShot(player)) {
                    trident.startCharging(player);
                }
            }
            case RELEASE -> trident.releaseChargedShot(player);
            case CANCEL -> trident.cancelCharging();
            case AIM_TARGET -> trident.setChargeAimTarget(player, payload.targetId());
        }
    }

    public enum Action {
        START,
        RELEASE,
        CANCEL,
        AIM_TARGET
    }
}
