package com.chena.bettertrident.client;

import com.chena.bettertrident.BetterTrident;
import com.chena.bettertrident.client.renderer.SpiritTridentRenderer;
import com.chena.bettertrident.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = BetterTrident.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SPIRIT_TRIDENT.get(), SpiritTridentRenderer::new);
    }
}
