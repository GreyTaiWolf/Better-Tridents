package com.chena.bettertrident.registry;

import com.chena.bettertrident.BetterTrident;
import com.chena.bettertrident.entity.SpiritTridentEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BetterTrident.MOD_ID);

    public static final Supplier<EntityType<SpiritTridentEntity>> SPIRIT_TRIDENT = ENTITY_TYPES.register(
            "spirit_trident",
            () -> EntityType.Builder.<SpiritTridentEntity>of(SpiritTridentEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("spirit_trident")
    );

    private ModEntities() {
    }
}
