package com.chena.bettertrident;

import com.chena.bettertrident.network.ModNetwork;
import com.chena.bettertrident.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(BetterTrident.MOD_ID)
public class BetterTrident {
    public static final String MOD_ID = "better_trident";

    public BetterTrident(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
    }
}
