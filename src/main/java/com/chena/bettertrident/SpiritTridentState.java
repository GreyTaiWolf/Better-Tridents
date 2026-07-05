package com.chena.bettertrident;

import com.chena.bettertrident.entity.SpiritTridentEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class SpiritTridentState {
    private static final double ACTIVE_TRIDENT_FALLBACK_RANGE = 128.0D;
    private static final Map<UUID, UUID> ACTIVE_TRIDENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TARGETS = new ConcurrentHashMap<>();

    private SpiritTridentState() {
    }

    public static void setTarget(UUID ownerId, UUID targetId) {
        TARGETS.put(ownerId, targetId);
    }

    public static void clearTarget(UUID ownerId, UUID targetId) {
        TARGETS.remove(ownerId, targetId);
    }

    public static void clearTarget(UUID ownerId) {
        TARGETS.remove(ownerId);
    }

    @Nullable
    public static LivingEntity getTarget(ServerLevel level, UUID ownerId) {
        UUID targetId = TARGETS.get(ownerId);
        if (targetId == null) {
            return null;
        }

        Entity entity = level.getEntity(targetId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }

        TARGETS.remove(ownerId, targetId);
        return null;
    }

    public static void setActive(UUID ownerId, UUID tridentId) {
        ACTIVE_TRIDENTS.put(ownerId, tridentId);
    }

    public static void clearActive(UUID ownerId, UUID tridentId) {
        ACTIVE_TRIDENTS.remove(ownerId, tridentId);
        TARGETS.remove(ownerId);
    }

    @Nullable
    public static SpiritTridentEntity getActiveTrident(MinecraftServer server, UUID ownerId) {
        UUID tridentId = ACTIVE_TRIDENTS.get(ownerId);
        if (tridentId == null) {
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tridentId);
            if (entity instanceof SpiritTridentEntity trident && trident.isAlive()) {
                return trident;
            }
        }

        ACTIVE_TRIDENTS.remove(ownerId, tridentId);
        TARGETS.remove(ownerId);
        return null;
    }

    @Nullable
    public static SpiritTridentEntity getActiveTrident(ServerPlayer player) {
        SpiritTridentEntity active = getActiveTrident(player.getServer(), player.getUUID());
        if (active != null && active.isOwnedBy(player)) {
            return active;
        }

        ServerLevel level = player.serverLevel();
        for (SpiritTridentEntity trident : level.getEntitiesOfClass(
                SpiritTridentEntity.class,
                player.getBoundingBox().inflate(ACTIVE_TRIDENT_FALLBACK_RANGE),
                trident -> trident.isAlive() && trident.isOwnedBy(player)
        )) {
            setActive(player.getUUID(), trident.getUUID());
            return trident;
        }

        return null;
    }

    public static boolean hasActiveTrident(MinecraftServer server, UUID ownerId) {
        return getActiveTrident(server, ownerId) != null;
    }
}
