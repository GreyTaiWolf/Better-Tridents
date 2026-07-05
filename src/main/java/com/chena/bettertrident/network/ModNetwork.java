package com.chena.bettertrident.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String NETWORK_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(
                RecallSpiritTridentPayload.TYPE,
                RecallSpiritTridentPayload.STREAM_CODEC,
                RecallSpiritTridentPayload::handle
        );
        registrar.playToServer(
                ChargeSpiritTridentPayload.TYPE,
                ChargeSpiritTridentPayload.STREAM_CODEC,
                ChargeSpiritTridentPayload::handle
        );
    }
}
