package com.chena.bettertrident;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

public final class BetterTridentEnchantments {
    public static final ResourceKey<Enchantment> SPIRIT_BINDING = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(BetterTrident.MOD_ID, "spirit_binding")
    );

    private BetterTridentEnchantments() {
    }
}
