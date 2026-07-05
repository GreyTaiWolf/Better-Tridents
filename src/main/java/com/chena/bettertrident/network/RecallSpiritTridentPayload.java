package com.chena.bettertrident.network;

import com.chena.bettertrident.BetterTrident;
import com.chena.bettertrident.entity.SpiritTridentEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RecallSpiritTridentPayload(int entityId) implements CustomPacketPayload {
    private static final double MAX_RECALL_DISTANCE = 8.0D;

    public static final Type<RecallSpiritTridentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterTrident.MOD_ID, "recall_spirit_trident")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RecallSpiritTridentPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.entityId()),
            buffer -> new RecallSpiritTridentPayload(buffer.readVarInt())
    );

    @Override
    public Type<RecallSpiritTridentPayload> type() {
        return TYPE;
    }

    public static void handle(RecallSpiritTridentPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        Entity entity = player.serverLevel().getEntity(payload.entityId());
        if (entity instanceof SpiritTridentEntity trident
                && player.distanceToSqr(trident) <= MAX_RECALL_DISTANCE * MAX_RECALL_DISTANCE
                && trident.canRecallBy(player)) {
            trident.recallTo(player);
        }
    }
}
