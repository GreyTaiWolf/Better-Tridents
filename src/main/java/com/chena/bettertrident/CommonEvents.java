package com.chena.bettertrident;

import com.chena.bettertrident.entity.SpiritTridentEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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

        ItemStack stack = trident.getPickupItemStackOrigin();
        if (!hasSpiritBinding(player.level(), stack)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null || SpiritTridentState.hasActiveTrident(server, player.getUUID())) {
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
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!SpiritTridentEntity.isValidSpiritTarget(target, player)) {
            return;
        }

        SpiritTridentState.setTarget(player.getUUID(), target.getUUID());
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

    private static boolean hasSpiritBinding(Level level, ItemStack stack) {
        return level.registryAccess()
                .lookup(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.get(BetterTridentEnchantments.SPIRIT_BINDING))
                .map(enchantment -> stack.getEnchantmentLevel((Holder<Enchantment>)enchantment) > 0)
                .orElse(false);
    }
}
