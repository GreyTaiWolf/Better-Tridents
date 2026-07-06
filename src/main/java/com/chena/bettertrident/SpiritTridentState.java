package com.chena.bettertrident;

import com.chena.bettertrident.entity.SpiritTridentEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class SpiritTridentState {
    public static final int MAX_ACTIVE_TRIDENTS = 3;

    private static final double ACTIVE_TRIDENT_FALLBACK_RANGE = 128.0D;
    private static final double ATTACK_TARGET_SPREAD_RADIUS = 10.0D;
    private static final double ATTACK_OWNER_RANGE = 32.0D;
    private static final double CHARGE_TARGET_SPREAD_RADIUS = 12.0D;
    private static final int TARGET_ASSIGN_GLOW_TICKS = 20;
    private static final int REENTRY_COOLDOWN_TICKS = 100;
    private static final Map<UUID, List<UUID>> ACTIVE_TRIDENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TRIDENT_TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REENTRY_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Comparator<SpiritTridentEntity> TRIDENT_ORDER = Comparator
            .comparingInt((SpiritTridentEntity trident) -> trident.getId())
            .thenComparing(trident -> trident.getUUID());

    private SpiritTridentState() {
    }

    public static synchronized boolean setActive(UUID ownerId, UUID tridentId) {
        List<UUID> tridents = ACTIVE_TRIDENTS.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
        if (tridents.contains(tridentId)) {
            return true;
        }

        if (tridents.size() >= MAX_ACTIVE_TRIDENTS) {
            return false;
        }

        tridents.add(tridentId);
        return true;
    }

    public static synchronized boolean canAddActiveTrident(MinecraftServer server, UUID ownerId) {
        return getActiveTridents(server, ownerId).size() < MAX_ACTIVE_TRIDENTS;
    }

    public static synchronized void clearActive(UUID ownerId, UUID tridentId) {
        List<UUID> tridents = ACTIVE_TRIDENTS.get(ownerId);
        if (tridents != null) {
            tridents.remove(tridentId);
            if (tridents.isEmpty()) {
                ACTIVE_TRIDENTS.remove(ownerId);
            }
        }

        TRIDENT_TARGETS.remove(tridentId);
    }

    public static void setTridentTarget(UUID tridentId, UUID targetId) {
        TRIDENT_TARGETS.put(tridentId, targetId);
    }

    public static void clearTridentTarget(UUID tridentId) {
        TRIDENT_TARGETS.remove(tridentId);
    }

    public static void clearTridentTarget(UUID tridentId, UUID targetId) {
        TRIDENT_TARGETS.remove(tridentId, targetId);
    }

    @Nullable
    public static LivingEntity getTridentTarget(ServerLevel level, UUID tridentId) {
        UUID targetId = TRIDENT_TARGETS.get(tridentId);
        if (targetId == null) {
            return null;
        }

        Entity entity = level.getEntity(targetId);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }

        TRIDENT_TARGETS.remove(tridentId, targetId);
        return null;
    }

    @Nullable
    public static SpiritTridentEntity getActiveTrident(MinecraftServer server, UUID ownerId) {
        List<SpiritTridentEntity> tridents = getActiveTridents(server, ownerId);
        return tridents.isEmpty() ? null : tridents.get(0);
    }

    @Nullable
    public static SpiritTridentEntity getActiveTrident(ServerPlayer player) {
        List<SpiritTridentEntity> tridents = getActiveTridents(player);
        return tridents.isEmpty() ? null : tridents.get(0);
    }

    public static synchronized List<SpiritTridentEntity> getActiveTridents(MinecraftServer server, UUID ownerId) {
        List<UUID> tridentIds = ACTIVE_TRIDENTS.get(ownerId);
        if (tridentIds == null || tridentIds.isEmpty()) {
            return List.of();
        }

        List<SpiritTridentEntity> result = new ArrayList<>();
        List<UUID> stale = new ArrayList<>();
        for (UUID tridentId : tridentIds) {
            SpiritTridentEntity trident = findTrident(server, tridentId);
            if (trident != null) {
                result.add(trident);
            } else {
                stale.add(tridentId);
            }
        }

        if (!stale.isEmpty()) {
            tridentIds.removeAll(stale);
            stale.forEach(TRIDENT_TARGETS::remove);
            if (tridentIds.isEmpty()) {
                ACTIVE_TRIDENTS.remove(ownerId);
            }
        }

        result.sort(TRIDENT_ORDER);
        return List.copyOf(result);
    }

    public static List<SpiritTridentEntity> getActiveTridents(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }

        List<SpiritTridentEntity> tridents = new ArrayList<>(getActiveTridents(server, player.getUUID()));
        tridents.removeIf(trident -> !trident.isOwnedBy(player));
        if (tridents.size() >= MAX_ACTIVE_TRIDENTS) {
            return List.copyOf(tridents);
        }

        Set<UUID> known = new HashSet<>();
        for (SpiritTridentEntity trident : tridents) {
            known.add(trident.getUUID());
        }

        List<SpiritTridentEntity> nearby = player.serverLevel().getEntitiesOfClass(
                SpiritTridentEntity.class,
                player.getBoundingBox().inflate(ACTIVE_TRIDENT_FALLBACK_RANGE),
                trident -> trident.isAlive() && trident.isOwnedBy(player) && !known.contains(trident.getUUID())
        );
        nearby.sort(TRIDENT_ORDER);
        for (SpiritTridentEntity trident : nearby) {
            if (tridents.size() >= MAX_ACTIVE_TRIDENTS) {
                break;
            }
            if (setActive(player.getUUID(), trident.getUUID())) {
                tridents.add(trident);
            }
        }

        tridents.sort(TRIDENT_ORDER);
        return List.copyOf(tridents);
    }

    public static boolean hasActiveTrident(MinecraftServer server, UUID ownerId) {
        return !getActiveTridents(server, ownerId).isEmpty();
    }

    public static int getFormationIndex(MinecraftServer server, UUID ownerId, UUID tridentId) {
        List<SpiritTridentEntity> tridents = getActiveTridents(server, ownerId);
        for (int i = 0; i < tridents.size(); i++) {
            if (tridents.get(i).getUUID().equals(tridentId)) {
                return i;
            }
        }

        return 0;
    }

    public static void assignAttackTargets(ServerPlayer player, LivingEntity primaryTarget) {
        if (!(primaryTarget.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        List<SpiritTridentEntity> tridents = getActiveTridents(player).stream()
                .filter(trident -> trident.canReceiveAttackTargetBy(player))
                .toList();
        if (tridents.isEmpty()) {
            return;
        }

        List<LivingEntity> targets = getTargetCandidates(
                serverLevel,
                player,
                primaryTarget,
                ATTACK_TARGET_SPREAD_RADIUS,
                ATTACK_OWNER_RANGE
        );
        assignTargets(tridents, targets);
    }

    public static void assignChargeAimTargets(ServerPlayer player, int primaryTargetId) {
        List<SpiritTridentEntity> tridents = getActiveTridents(player).stream()
                .filter(trident -> trident.canUpdateChargeAimTargetBy(player))
                .toList();
        if (tridents.isEmpty()) {
            return;
        }

        if (primaryTargetId < 0) {
            tridents.forEach(trident -> trident.setChargeAimTarget(player, -1));
            return;
        }

        Entity entity = player.serverLevel().getEntity(primaryTargetId);
        if (!(entity instanceof LivingEntity primaryTarget)) {
            tridents.forEach(trident -> trident.setChargeAimTarget(player, -1));
            return;
        }

        double maxRange = tridents.stream()
                .mapToDouble(SpiritTridentEntity::getChargeAimRange)
                .max()
                .orElse(0.0D);
        List<LivingEntity> targets = getTargetCandidates(
                player.serverLevel(),
                player,
                primaryTarget,
                CHARGE_TARGET_SPREAD_RADIUS,
                maxRange
        );
        if (targets.isEmpty()) {
            tridents.forEach(trident -> trident.setChargeAimTarget(player, -1));
            return;
        }

        for (int i = 0; i < tridents.size(); i++) {
            LivingEntity target = targets.size() == 1 ? targets.get(0) : targets.get(i % targets.size());
            tridents.get(i).setChargeAimTarget(player, target.getId());
        }
        glowTargets(targets);
    }

    public static void startReentryCooldown(ServerPlayer player) {
        REENTRY_COOLDOWNS.put(player.getUUID(), player.serverLevel().getGameTime() + REENTRY_COOLDOWN_TICKS);
    }

    public static boolean isReentryCoolingDown(ServerPlayer player) {
        Long expiresAt = REENTRY_COOLDOWNS.get(player.getUUID());
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt <= player.serverLevel().getGameTime()) {
            REENTRY_COOLDOWNS.remove(player.getUUID(), expiresAt);
            return false;
        }

        return true;
    }

    @Nullable
    private static SpiritTridentEntity findTrident(MinecraftServer server, UUID tridentId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tridentId);
            if (entity instanceof SpiritTridentEntity trident && trident.isAlive()) {
                return trident;
            }
        }

        return null;
    }

    private static List<LivingEntity> getTargetCandidates(
            ServerLevel level,
            Player owner,
            LivingEntity primaryTarget,
            double spreadRadius,
            double ownerRange
    ) {
        if (!primaryTarget.level().dimension().equals(level.dimension())
                || !SpiritTridentEntity.isValidSpiritTarget(primaryTarget, owner)
                || owner.distanceToSqr(primaryTarget) > ownerRange * ownerRange) {
            return List.of();
        }

        List<LivingEntity> targets = new ArrayList<>();
        targets.add(primaryTarget);
        List<LivingEntity> extras = level.getEntitiesOfClass(
                LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(spreadRadius),
                candidate -> candidate != primaryTarget
                        && SpiritTridentEntity.isValidSpiritTarget(candidate, owner)
                        && owner.distanceToSqr(candidate) <= ownerRange * ownerRange
        );
        extras.sort(Comparator
                .comparingDouble((LivingEntity candidate) -> primaryTarget.distanceToSqr(candidate))
                .thenComparing(LivingEntity::getUUID));
        targets.addAll(extras);
        return targets;
    }

    private static void assignTargets(List<SpiritTridentEntity> tridents, List<LivingEntity> targets) {
        if (targets.isEmpty()) {
            tridents.forEach(trident -> clearTridentTarget(trident.getUUID()));
            return;
        }

        Set<LivingEntity> assignedTargets = new HashSet<>();
        for (int i = 0; i < tridents.size(); i++) {
            LivingEntity target = targets.size() == 1 ? targets.get(0) : targets.get(i % targets.size());
            setTridentTarget(tridents.get(i).getUUID(), target.getUUID());
            assignedTargets.add(target);
        }
        glowTargets(assignedTargets);
    }

    private static void glowTargets(Iterable<LivingEntity> targets) {
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, TARGET_ASSIGN_GLOW_TICKS, 0, false, false, true));
        }
    }
}
