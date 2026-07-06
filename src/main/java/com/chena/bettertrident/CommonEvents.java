package com.chena.bettertrident;

import com.chena.bettertrident.entity.SpiritTridentEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof ThrownTrident trident) || entity instanceof SpiritTridentEntity) {
            return;
        }

        Entity owner = trident.getOwner();
        if (!(owner instanceof Player player)) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && SpiritTridentState.isReentryCoolingDown(serverPlayer)) {
            return;
        }

        ItemStack stack = trident.getPickupItemStackOrigin();
        if (!hasSpiritBinding(player.level(), stack)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null || !SpiritTridentState.canAddActiveTrident(server, player.getUUID())) {
            return;
        }

        SpiritTridentEntity spirit = new SpiritTridentEntity(event.getLevel(), player, stack);
        spirit.moveTo(trident.getX(), trident.getY(), trident.getZ(), trident.getYRot(), trident.getXRot());
        spirit.setDeltaMovement(trident.getDeltaMovement());
        spirit.pickup = trident.pickup;
        if (event.getLevel().addFreshEntity(spirit)) {
            SpiritTridentState.setActive(player.getUUID(), spirit.getUUID());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        triggerUnrestrictedChanneling(event);

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!SpiritTridentEntity.isValidSpiritTarget(target, player)) {
            return;
        }

        SpiritTridentState.assignAttackTargets(player, target);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }

        if (!(event.getTarget() instanceof SpiritTridentEntity trident) || !trident.canRecallBy(player)) {
            return;
        }

        if (!trident.recallTo(player)) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        if (!event.isTargetting(Enchantments.RIPTIDE)) {
            return;
        }

        var riptide = event.getHolder(Enchantments.RIPTIDE);
        var spiritBinding = event.getHolder(BetterTridentEnchantments.SPIRIT_BINDING);
        if (riptide.isEmpty() || spiritBinding.isEmpty()) {
            return;
        }

        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        if (hasRiptideAndSpiritBinding(enchantments, riptide.get(), spiritBinding.get())) {
            enchantments.set(riptide.get(), 0);
        }
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!event.getRight().isEmpty() && wouldCreateSpiritRiptide(event.getPlayer().level(), event.getLeft(), event.getRight())) {
            event.setCanceled(true);
        }
    }

    private static boolean hasSpiritBinding(Level level, ItemStack stack) {
        return level.registryAccess()
                .lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(BetterTridentEnchantments.SPIRIT_BINDING))
                .map(enchantment -> EnchantmentHelper.getTagEnchantmentLevel((Holder<Enchantment>)enchantment, stack) > 0)
                .orElse(false);
    }

    private static boolean hasRiptideAndSpiritBinding(
            ItemEnchantments.Mutable enchantments,
            Holder<Enchantment> riptide,
            Holder<Enchantment> spiritBinding
    ) {
        return enchantments.getLevel(riptide) > 0
                && enchantments.getLevel(spiritBinding) > 0;
    }

    private static boolean wouldCreateSpiritRiptide(Level level, ItemStack left, ItemStack right) {
        return level.registryAccess()
                .lookup(Registries.ENCHANTMENT)
                .map(registry -> {
                    var riptide = registry.get(Enchantments.RIPTIDE);
                    var spiritBinding = registry.get(BetterTridentEnchantments.SPIRIT_BINDING);
                    if (riptide.isEmpty() || spiritBinding.isEmpty()) {
                        return false;
                    }

                    ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
                    for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
                        merged.upgrade(entry.getKey(), entry.getIntValue());
                    }

                    return hasRiptideAndSpiritBinding(merged, riptide.get(), spiritBinding.get());
                })
                .orElse(false);
    }

    private static void triggerUnrestrictedChanneling(LivingDamageEvent.Post event) {
        Entity directEntity = event.getSource().getDirectEntity();
        if (!(directEntity instanceof ThrownTrident trident) || directEntity instanceof SpiritTridentEntity) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack weapon = trident.getPickupItemStackOrigin();
        var enchantments = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int channelingLevel = weapon.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.CHANNELING));
        if (channelingLevel < 2 || canTriggerVanillaChanneling(serverLevel, target)) {
            return;
        }

        summonChannelingLightning(serverLevel, target, trident.getOwner());
    }

    private static boolean canTriggerVanillaChanneling(ServerLevel serverLevel, Entity target) {
        return serverLevel.isThundering() && serverLevel.canSeeSky(target.blockPosition());
    }

    private static void summonChannelingLightning(ServerLevel serverLevel, Entity target, Entity owner) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning == null) {
            return;
        }

        lightning.moveTo(target.getX(), target.getY(), target.getZ());
        if (owner instanceof ServerPlayer serverPlayer) {
            lightning.setCause(serverPlayer);
        }

        serverLevel.addFreshEntity(lightning);
        serverLevel.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.TRIDENT_THUNDER,
                SoundSource.WEATHER,
                5.0F,
                1.0F
        );
    }

}
