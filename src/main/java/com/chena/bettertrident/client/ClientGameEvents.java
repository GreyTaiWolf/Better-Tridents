package com.chena.bettertrident.client;

import com.chena.bettertrident.BetterTrident;
import com.chena.bettertrident.entity.SpiritTridentEntity;
import com.chena.bettertrident.network.ChargeSpiritTridentPayload;
import com.chena.bettertrident.network.RecallActiveSpiritTridentPayload;
import com.chena.bettertrident.network.RecallSpiritTridentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = BetterTrident.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static final double SPIRIT_RECALL_REACH = 6.0D;
    private static final double SPIRIT_RECALL_PICK_RADIUS = 1.25D;
    private static final double CHARGE_AIM_PICK_RADIUS = 1.25D;
    private static final int CHARGE_LEVEL_1_TICKS = 100;
    private static final int CHARGE_LEVEL_2_TICKS = 160;
    private static final int CHARGE_LEVEL_3_TICKS = 220;
    private static final double[] CHARGED_SHOT_RANGES = {0.0D, 100.0D, 200.0D, 300.0D};
    private static boolean chargingAttackDown;
    private static boolean recallUseDown;
    private static int localChargeTicks;
    private static int lastAimTargetId = -1;

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            cancelChargeIfNeeded();
            recallUseDown = false;
            return;
        }

        boolean canCharge = minecraft.screen == null
                && minecraft.player.isAlive()
                && minecraft.player.getMainHandItem().isEmpty();

        handleRecallInput(minecraft, canCharge);

        boolean attackDown = canCharge && minecraft.options.keyAttack.isDown();

        if (attackDown && !chargingAttackDown) {
            chargingAttackDown = true;
            localChargeTicks = 0;
            lastAimTargetId = -1;
            PacketDistributor.sendToServer(new ChargeSpiritTridentPayload(ChargeSpiritTridentPayload.Action.START));
        }

        if (attackDown) {
            localChargeTicks++;
            handleChargeAimInput(minecraft);
        } else if (!attackDown && chargingAttackDown) {
            chargingAttackDown = false;
            localChargeTicks = 0;
            lastAimTargetId = -1;
            PacketDistributor.sendToServer(new ChargeSpiritTridentPayload(canCharge
                    ? ChargeSpiritTridentPayload.Action.RELEASE
                    : ChargeSpiritTridentPayload.Action.CANCEL
            ));
        }
    }

    private static void handleRecallInput(Minecraft minecraft, boolean canRecall) {
        boolean useDown = canRecall && minecraft.options.keyUse.isDown();
        if (useDown && !recallUseDown) {
            if (minecraft.player != null && minecraft.player.isShiftKeyDown()) {
                PacketDistributor.sendToServer(new RecallActiveSpiritTridentPayload());
            } else {
                SpiritTridentEntity trident = findLookedAtSpiritTrident(minecraft);
                if (trident != null) {
                    PacketDistributor.sendToServer(new RecallSpiritTridentPayload(trident.getId()));
                }
            }
        }

        recallUseDown = useDown;
    }

    private static void handleChargeAimInput(Minecraft minecraft) {
        int level = getLocalChargeLevel();
        if (level <= 0) {
            if (lastAimTargetId != -1) {
                lastAimTargetId = -1;
                PacketDistributor.sendToServer(new ChargeSpiritTridentPayload(ChargeSpiritTridentPayload.Action.AIM_TARGET, -1));
            }
            return;
        }

        LivingEntity target = findLookedAtChargeTarget(minecraft, CHARGED_SHOT_RANGES[level]);
        int targetId = target == null ? -1 : target.getId();
        if (targetId != lastAimTargetId || localChargeTicks % 5 == 0) {
            lastAimTargetId = targetId;
            PacketDistributor.sendToServer(new ChargeSpiritTridentPayload(ChargeSpiritTridentPayload.Action.AIM_TARGET, targetId));
        }
    }

    private static int getLocalChargeLevel() {
        if (localChargeTicks >= CHARGE_LEVEL_3_TICKS) {
            return 3;
        }
        if (localChargeTicks >= CHARGE_LEVEL_2_TICKS) {
            return 2;
        }
        if (localChargeTicks >= CHARGE_LEVEL_1_TICKS) {
            return 1;
        }

        return 0;
    }

    private static SpiritTridentEntity findLookedAtSpiritTrident(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        if (minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof SpiritTridentEntity trident
                && trident.isOwnedBy(minecraft.player)
                && trident.isOrbiting()) {
            return trident;
        }

        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return null;
        }

        Vec3 start = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(SPIRIT_RECALL_REACH));
        EntityHitResult result = ProjectileUtil.getEntityHitResult(
                minecraft.level,
                camera,
                start,
                end,
                camera.getBoundingBox().expandTowards(look.scale(SPIRIT_RECALL_REACH)).inflate(SPIRIT_RECALL_PICK_RADIUS),
                entity -> entity instanceof SpiritTridentEntity candidate
                        && candidate.isOwnedBy(minecraft.player)
                        && candidate.isOrbiting()
        );

        return result != null && result.getEntity() instanceof SpiritTridentEntity trident ? trident : null;
    }

    private static LivingEntity findLookedAtChargeTarget(Minecraft minecraft, double reach) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }

        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return null;
        }

        Vec3 start = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(reach));
        HitResult blockHit = minecraft.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera
        ));
        Vec3 traceEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult result = ProjectileUtil.getEntityHitResult(
                minecraft.level,
                camera,
                start,
                traceEnd,
                camera.getBoundingBox().expandTowards(look.scale(start.distanceTo(traceEnd))).inflate(CHARGE_AIM_PICK_RADIUS),
                entity -> isValidLocalChargeTarget(entity, minecraft.player)
        );

        return result != null && result.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isValidLocalChargeTarget(Entity entity, Player player) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !(entity instanceof Player)
                && entity != player;
    }

    private static void cancelChargeIfNeeded() {
        if (!chargingAttackDown) {
            return;
        }

        chargingAttackDown = false;
        localChargeTicks = 0;
        lastAimTargetId = -1;
        PacketDistributor.sendToServer(new ChargeSpiritTridentPayload(ChargeSpiritTridentPayload.Action.CANCEL));
    }
}
