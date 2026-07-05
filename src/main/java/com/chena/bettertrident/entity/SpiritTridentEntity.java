package com.chena.bettertrident.entity;

import com.chena.bettertrident.SpiritTridentState;
import com.chena.bettertrident.registry.ModEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class SpiritTridentEntity extends ThrownTrident {
    private static final EntityDataAccessor<Byte> DATA_SPIRIT_MODE = SynchedEntityData.defineId(
            SpiritTridentEntity.class,
            EntityDataSerializers.BYTE
    );

    private static final double ORBIT_RADIUS = 2.0D;
    private static final double ORBIT_VERTICAL_OFFSET = -0.2D;
    private static final double ORBIT_BOB_AMPLITUDE = 0.12D;
    private static final double ORBIT_LOCK_DISTANCE = 0.35D;
    private static final double OWNER_ABSORB_DISTANCE = 1.25D;
    private static final double MAX_TARGET_DISTANCE = 32.0D;
    private static final double MAX_OWNER_DISTANCE = 96.0D;
    private static final double ORBIT_SPEED = 0.075D;
    private static final double ORBIT_APPROACH_SPEED = 0.55D;
    private static final float ORBIT_PICK_RADIUS = 0.85F;
    private static final float ORBIT_YAW_STEP = 8.0F;
    private static final double TRIDENT_ATTACK_SPEED = 2.5D;
    private static final double INERTIAL_TURN_DEGREES_PER_TICK = 18.0D;
    private static final double CHARGED_SHOT_HOMING_DEGREES_PER_TICK = 10.0D;
    private static final double FLYOUT_DISTANCE_FACTOR = 0.35D;
    private static final double MIN_FLYOUT_DISTANCE = 3.0D;
    private static final double MAX_FLYOUT_DISTANCE = 8.0D;
    private static final double WAITING_GRAVITY = 0.05D;
    private static final int HIT_COOLDOWN_TICKS = 12;
    private static final int WAITING_ABSORB_COOLDOWN_TICKS = 12;
    private static final int CHARGE_POSE_TICKS = 60;
    private static final int CHARGE_LEVEL_1_TICKS = 100;
    private static final int CHARGE_LEVEL_2_TICKS = 160;
    private static final int CHARGE_LEVEL_3_TICKS = 220;
    private static final int CHARGE_TARGET_GLOW_TICKS = 12;
    private static final double CHARGE_SLOT_FORWARD = 1.15D;
    private static final double CHARGE_SLOT_SIDE = 0.85D;
    private static final double CHARGE_SLOT_VERTICAL = -0.2D;
    private static final double CHARGED_SHOT_SPEED = 2.5D;
    private static final double[] CHARGED_SHOT_RANGES = {0.0D, 100.0D, 200.0D, 300.0D};
    private static final DustParticleOptions SPIRIT_TRAIL_PARTICLE = new DustParticleOptions(new Vector3f(0.78F, 0.93F, 1.0F), 0.65F);
    private static final DustParticleOptions CHARGE_RING_PARTICLE = new DustParticleOptions(new Vector3f(0.45F, 0.85F, 1.0F), 0.9F);

    private int attackCooldown;
    private AttackPhase attackPhase = AttackPhase.CHASE;
    private int attackPhaseTicks;
    private Vec3 inertialDirection = Vec3.ZERO;
    private double inertialDistanceTraveled;
    private double inertialFlyoutDistance = MIN_FLYOUT_DISTANCE;
    private double attackApproachDistance;
    @Nullable
    private Vec3 previousAttackPosition;
    private boolean chargeInputActive;
    private int chargeTicks;
    private int chargeLevel;
    private Vec3 chargedShotDirection = Vec3.ZERO;
    private double chargedShotDistance;
    private double chargedShotMaxDistance;
    @Nullable
    private UUID chargeAimTargetUuid;
    @Nullable
    private UUID chargedShotTargetUuid;
    @Nullable
    private Vec3 previousChargedShotPosition;
    private int failedOwnerTicks;
    private int waitingAbsorbCooldown;

    public SpiritTridentEntity(EntityType<? extends SpiritTridentEntity> type, Level level) {
        super(type, level);
    }

    public SpiritTridentEntity(Level level, LivingEntity owner, ItemStack stack) {
        this(ModEntities.SPIRIT_TRIDENT.get(), level);
        this.setOwner(owner);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        this.setPickupItemStack(stack.copyWithCount(1));
        this.pickup = owner instanceof Player player && player.hasInfiniteMaterials()
                ? AbstractArrow.Pickup.CREATIVE_ONLY
                : AbstractArrow.Pickup.ALLOWED;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPIRIT_MODE, SpiritMode.FLYING.dataValue);
    }

    public boolean isOwnedBy(Player player) {
        return this.ownedBy(player);
    }

    public boolean isOrbiting() {
        return getSpiritMode() == SpiritMode.ORBIT;
    }

    public static boolean isValidSpiritTarget(@Nullable Entity entity, @Nullable Entity owner) {
        if (!(entity instanceof LivingEntity livingTarget) || !livingTarget.isAlive() || entity instanceof Player) {
            return false;
        }

        return owner == null || (entity != owner && !entity.getUUID().equals(owner.getUUID()));
    }

    public boolean canRecallBy(Player player) {
        return getSpiritMode() == SpiritMode.ORBIT
                && this.ownedBy(player)
                && player.getMainHandItem().isEmpty();
    }

    public boolean recallTo(Player player) {
        if (this.level().isClientSide || !canRecallBy(player)) {
            return false;
        }

        boolean shouldReturnItem = this.pickup != AbstractArrow.Pickup.CREATIVE_ONLY || !player.hasInfiniteMaterials();
        ItemStack recalled = recallItem();
        if (shouldReturnItem && !player.getInventory().add(recalled)) {
            player.drop(recalled, false);
        }

        return true;
    }

    public boolean canStartChargedShot(Player player) {
        return !this.level().isClientSide
                && this.ownedBy(player)
                && player.isAlive()
                && player.getMainHandItem().isEmpty()
                && getSpiritMode() == SpiritMode.ORBIT;
    }

    public void startCharging(Player player) {
        if (!canStartChargedShot(player) && !chargeInputActive) {
            return;
        }

        chargeInputActive = true;
        if (chargeTicks <= 0) {
            chargeTicks = 0;
            chargeLevel = 0;
            resetAttackLoop();
            SpiritTridentState.clearTarget(player.getUUID());
        }
    }

    public void setChargeAimTarget(Player player, int targetId) {
        if (!canUpdateChargeAimTarget(player)) {
            clearChargeAimTarget();
            return;
        }

        if (targetId < 0 || !(this.level() instanceof ServerLevel serverLevel)) {
            clearChargeAimTarget();
            return;
        }

        Entity entity = serverLevel.getEntity(targetId);
        if (isValidChargeAimTarget(player, entity)) {
            chargeAimTargetUuid = entity.getUUID();
            refreshChargeAimGlow((LivingEntity)entity);
        } else {
            clearChargeAimTarget();
        }
    }

    public void cancelCharging() {
        if (!chargeInputActive && getSpiritMode() != SpiritMode.CHARGING) {
            return;
        }

        resetChargeState();
        if (getSpiritMode() == SpiritMode.CHARGING) {
            enterOrbit();
        }
    }

    public void releaseChargedShot(Player player) {
        if (!this.ownedBy(player) || !chargeInputActive) {
            return;
        }

        if (chargeLevel <= 0) {
            cancelCharging();
            return;
        }

        beginChargedShot(player, chargeLevel);
    }

    @Override
    public void tick() {
        Entity owner = this.getOwner();
        if (!this.level().isClientSide && owner instanceof LivingEntity livingOwner) {
            SpiritTridentState.setActive(livingOwner.getUUID(), this.getUUID());
        }

        SpiritMode currentMode = getSpiritMode();
        if (currentMode == SpiritMode.FLYING) {
            super.tick();
            if (!this.isRemoved() && getSpiritMode() == SpiritMode.FLYING) {
                spawnFlightTrailParticles();
            }
            return;
        }

        this.baseTick();

        if (!(owner instanceof LivingEntity livingOwner) || !livingOwner.isAlive() || owner.level().dimension() != this.level().dimension()) {
            handleMissingOwner();
            return;
        }

        failedOwnerTicks = 0;
        if (currentMode == SpiritMode.WAITING_FOR_OWNER) {
            tickWaitingForOwner(livingOwner);
            return;
        }

        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(true);

        if (!this.level().isClientSide) {
            if (currentMode != SpiritMode.CHARGED_SHOT && this.distanceToSqr(owner) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
                dropAndDiscard();
                return;
            }

            tickServerSpirit((LivingEntity)owner);
        } else {
            tickClientSpirit(livingOwner);
        }
    }

    private void tickServerSpirit(LivingEntity owner) {
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (chargeInputActive || getSpiritMode() == SpiritMode.CHARGING) {
            tickCharging(owner);
            return;
        }

        if (getSpiritMode() == SpiritMode.CHARGED_SHOT) {
            tickChargedShot(owner);
            return;
        }

        LivingEntity target = null;
        if (this.level() instanceof ServerLevel serverLevel) {
            target = SpiritTridentState.getTarget(serverLevel, owner.getUUID());
        }

        if (isValidSpiritTarget(target, owner) && target.distanceToSqr(owner) <= MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE) {
            setSpiritMode(SpiritMode.ATTACKING);
            tickAttackTarget(target, owner);
            return;
        }

        boolean wasAttacking = getSpiritMode() == SpiritMode.ATTACKING;
        resetAttackLoop();
        if (wasAttacking) {
            SpiritTridentState.clearTarget(owner.getUUID());
        }

        setSpiritMode(SpiritMode.ORBIT);
        moveToOrbitSlot(owner);
        spawnOrbitParticles();
    }

    private void tickClientSpirit(LivingEntity owner) {
        SpiritMode mode = getSpiritMode();
        if (mode == SpiritMode.ORBIT) {
            moveToOrbitSlot(owner);
        } else if (mode == SpiritMode.CHARGING && owner instanceof Player player) {
            moveToChargeSlot(player);
        } else if (mode == SpiritMode.CHARGED_SHOT || mode == SpiritMode.ATTACKING) {
            Vec3 motion = this.getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-4D) {
                this.move(MoverType.SELF, motion);
            }
            pointAlongMotion();
        } else {
            pointAlongMotion();
        }
    }

    private void tickAttackTarget(LivingEntity target, LivingEntity owner) {
        if (attackPhase == AttackPhase.INERTIAL_TURN) {
            tickInertialTurn(target);
            return;
        }

        if (attackPhase == AttackPhase.RETURN) {
            tickInertialReturn(target, owner);
            return;
        }

        if (attackPhase != AttackPhase.CHASE) {
            setAttackPhase(AttackPhase.CHASE);
        }
        if (attackApproachDistance <= 0.0D) {
            attackApproachDistance = this.distanceTo(target);
        }
        flyToward(target.getEyePosition(), TRIDENT_ATTACK_SPEED);
        pointAlongMotion();
        spawnFlightTrailParticles();
        tryHitTarget(target, owner);
    }

    private void tickInertialTurn(LivingEntity target) {
        attackPhaseTicks++;

        Vec3 direction = getValidInertialDirection();
        if (inertialDistanceTraveled >= inertialFlyoutDistance) {
            Vec3 toTarget = target.getEyePosition().subtract(this.position());
            direction = turnDirectionToward(direction, toTarget);
            if (isFacingTarget(direction, toTarget)) {
                setAttackPhase(AttackPhase.RETURN);
            }
        }

        moveWithInertia(direction);
    }

    private void tickInertialReturn(LivingEntity target, LivingEntity owner) {
        attackPhaseTicks++;
        Vec3 direction = turnDirectionToward(getValidInertialDirection(), target.getEyePosition().subtract(this.position()));
        moveWithInertia(direction);
        tryHitTarget(target, owner);
    }

    private void moveWithInertia(Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() > 1.0E-4D ? direction.normalize() : getValidInertialDirection();
        Vec3 motion = normalized.scale(TRIDENT_ATTACK_SPEED);
        previousAttackPosition = this.position();
        moveSmoothly(motion);
        inertialDirection = normalized;
        inertialDistanceTraveled += motion.length();
        pointAlongMotion();
        spawnFlightTrailParticles();
    }

    private void tickCharging(LivingEntity owner) {
        if (!(owner instanceof Player player) || !player.isAlive() || !chargeInputActive || !player.getMainHandItem().isEmpty()) {
            cancelCharging();
            return;
        }

        chargeTicks++;
        if (chargeTicks < CHARGE_POSE_TICKS) {
            setSpiritMode(SpiritMode.ORBIT);
            moveToOrbitSlot(owner);
            spawnOrbitParticles();
            return;
        }

        setSpiritMode(SpiritMode.CHARGING);
        moveToChargeSlot(player);
        int newLevel = getChargeLevelForTicks(chargeTicks);
        if (newLevel > chargeLevel) {
            chargeLevel = newLevel;
            playChargeLevelEffect(player, newLevel);
        }
        refreshChargeAimTarget(player);
    }

    private boolean canUpdateChargeAimTarget(Player player) {
        return !this.level().isClientSide
                && this.ownedBy(player)
                && chargeInputActive
                && player.isAlive()
                && player.getMainHandItem().isEmpty()
                && chargeTicks >= CHARGE_LEVEL_1_TICKS;
    }

    private boolean isValidChargeAimTarget(Player player, @Nullable Entity entity) {
        int currentLevel = Math.max(chargeLevel, getChargeLevelForTicks(chargeTicks));
        if (currentLevel <= 0 || !isValidSpiritTarget(entity, player)) {
            return false;
        }

        double range = CHARGED_SHOT_RANGES[Mth.clamp(currentLevel, 1, 3)];
        return player.distanceToSqr(entity) <= range * range;
    }

    @Nullable
    private LivingEntity getChargeAimTarget(Player player) {
        if (chargeAimTargetUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(chargeAimTargetUuid);
        if (isValidChargeAimTarget(player, entity)) {
            return (LivingEntity)entity;
        }

        clearChargeAimTarget();
        return null;
    }

    private void refreshChargeAimTarget(Player player) {
        LivingEntity target = getChargeAimTarget(player);
        if (target != null) {
            refreshChargeAimGlow(target);
        }
    }

    private void refreshChargeAimGlow(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CHARGE_TARGET_GLOW_TICKS, 0, false, false, true));
    }

    private void clearChargeAimTarget() {
        chargeAimTargetUuid = null;
    }

    private void moveToChargeSlot(Player player) {
        Vec3 look = getValidLookDirection(player);
        Vec3 side = getSideDirection(look);
        Vec3 target = player.getEyePosition()
                .add(look.scale(CHARGE_SLOT_FORWARD))
                .add(side.scale(CHARGE_SLOT_SIDE))
                .add(0.0D, CHARGE_SLOT_VERTICAL, 0.0D);
        Vec3 toTarget = target.subtract(this.position());
        double distance = toTarget.length();
        Vec3 motion = distance <= ORBIT_LOCK_DISTANCE
                ? toTarget
                : toTarget.normalize().scale(Math.min(ORBIT_APPROACH_SPEED, distance * 0.4D));

        moveSmoothly(motion);
        pointAlongDirection(look);
    }

    private int getChargeLevelForTicks(int ticks) {
        if (ticks >= CHARGE_LEVEL_3_TICKS) {
            return 3;
        }
        if (ticks >= CHARGE_LEVEL_2_TICKS) {
            return 2;
        }
        if (ticks >= CHARGE_LEVEL_1_TICKS) {
            return 1;
        }

        return 0;
    }

    private void playChargeLevelEffect(Player player, int level) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 look = getValidLookDirection(player);
        Vec3 tip = this.position().add(look.scale(0.9D));
        serverLevel.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.TRIDENT_RIPTIDE_1.value(),
                SoundSource.PLAYERS,
                0.8F + level * 0.15F,
                0.85F + level * 0.12F
        );
        spawnChargeRingParticles(serverLevel, tip, look, level);
    }

    private void spawnChargeRingParticles(ServerLevel serverLevel, Vec3 center, Vec3 look, int level) {
        Vec3 side = getSideDirection(look);
        Vec3 up = side.cross(look).normalize();
        int points = 28;
        double radius = 0.35D + level * 0.18D;
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = radius + ring * 0.22D;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2.0D * i) / points;
                Vec3 offset = side.scale(Math.cos(angle) * ringRadius).add(up.scale(Math.sin(angle) * ringRadius));
                Vec3 particlePos = center.add(offset);
                serverLevel.sendParticles(
                        CHARGE_RING_PARTICLE,
                        particlePos.x,
                        particlePos.y,
                        particlePos.z,
                        1,
                        0.015D,
                        0.015D,
                        0.015D,
                        0.0D
                );
            }
        }
    }

    private void beginChargedShot(Player player, int level) {
        LivingEntity lockedTarget = getChargeAimTarget(player);
        chargedShotTargetUuid = lockedTarget == null ? null : lockedTarget.getUUID();
        chargedShotDirection = getChargedShotStartDirection(player, lockedTarget);
        chargedShotDistance = 0.0D;
        chargedShotMaxDistance = CHARGED_SHOT_RANGES[Mth.clamp(level, 1, 3)];
        previousChargedShotPosition = this.position();
        resetAttackLoop();
        resetChargeState();
        setSpiritMode(SpiritMode.CHARGED_SHOT);
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(true);
        this.setDeltaMovement(chargedShotDirection.scale(CHARGED_SHOT_SPEED));
        pointAlongDirection(chargedShotDirection);
        this.level().playSound(null, this, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private Vec3 getChargedShotStartDirection(Player player, @Nullable LivingEntity lockedTarget) {
        if (lockedTarget != null) {
            Vec3 toTarget = lockedTarget.getEyePosition().subtract(this.position());
            if (toTarget.lengthSqr() > 1.0E-4D) {
                return toTarget.normalize();
            }
        }

        return getValidLookDirection(player);
    }

    private void tickChargedShot(LivingEntity owner) {
        Vec3 direction = getValidChargedShotDirection(owner);
        double remaining = chargedShotMaxDistance - chargedShotDistance;
        if (remaining <= 0.0D) {
            enterPostHitState(owner);
            return;
        }

        double step = Math.min(CHARGED_SHOT_SPEED, remaining);
        Vec3 start = this.position();
        Vec3 motion = direction.scale(step);
        Vec3 end = start.add(motion);
        previousChargedShotPosition = start;

        HitResult hitResult = findChargedShotHit(start, end, motion, owner);
        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            moveSmoothly(hitResult.getLocation().subtract(this.position()));
            chargedShotDistance += start.distanceTo(hitResult.getLocation());
            handleChargedShotHit(hitResult, owner);
            return;
        }

        moveSmoothly(motion);
        chargedShotDistance += motion.length();
        pointAlongDirection(direction);
        spawnFlightTrailParticles();

        if (chargedShotDistance >= chargedShotMaxDistance) {
            enterPostHitState(owner);
        }
    }

    @Nullable
    private HitResult findChargedShotHit(Vec3 start, Vec3 end, Vec3 motion, LivingEntity owner) {
        HitResult blockHit = this.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));
        Vec3 traceEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this.level(),
                this,
                start,
                traceEnd,
                this.getBoundingBox().expandTowards(motion).inflate(1.0D),
                entity -> this.canHitEntity(entity) && isValidSpiritTarget(entity, owner)
        );
        if (entityHit != null) {
            return entityHit;
        }

        return blockHit;
    }

    private void handleChargedShotHit(HitResult hitResult, LivingEntity owner) {
        if (hitResult instanceof EntityHitResult entityHit) {
            onSpiritHit(entityHit.getEntity());
            this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
            enterPostHitState(owner);
        } else if (hitResult instanceof BlockHitResult blockHit) {
            onHitBlock(blockHit);
        }
    }

    private Vec3 getValidChargedShotDirection(LivingEntity owner) {
        Vec3 direction = chargedShotDirection.lengthSqr() > 1.0E-4D
                ? chargedShotDirection.normalize()
                : getValidLookDirection(owner);
        LivingEntity target = getChargedShotTarget(owner);
        if (target != null) {
            direction = turnDirectionToward(
                    direction,
                    target.getEyePosition().subtract(this.position()),
                    CHARGED_SHOT_HOMING_DEGREES_PER_TICK
            );
        }

        chargedShotDirection = direction;
        return direction;
    }

    @Nullable
    private LivingEntity getChargedShotTarget(LivingEntity owner) {
        if (chargedShotTargetUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        Entity entity = serverLevel.getEntity(chargedShotTargetUuid);
        if (isValidSpiritTarget(entity, owner)) {
            return (LivingEntity)entity;
        }

        chargedShotTargetUuid = null;
        return null;
    }

    private Vec3 getValidLookDirection(LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        if (look.lengthSqr() > 1.0E-4D) {
            return look.normalize();
        }

        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private Vec3 getSideDirection(Vec3 look) {
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() <= 1.0E-4D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }

        horizontal = horizontal.normalize();
        return new Vec3(-horizontal.z, 0.0D, horizontal.x);
    }

    private void tickWaitingForOwner(LivingEntity owner) {
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(false);

        if (waitingAbsorbCooldown > 0) {
            waitingAbsorbCooldown--;
        }

        if (!this.level().isClientSide) {
            if (this.distanceToSqr(owner) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
                dropAndDiscard();
                return;
            }

            if (waitingAbsorbCooldown <= 0 && isOwnerCloseEnough(owner)) {
                enterOrbit();
                return;
            }
        }

        Vec3 motion = this.getDeltaMovement();
        if (this.onGround()) {
            motion = new Vec3(motion.x * 0.55D, 0.0D, motion.z * 0.55D);
            if (motion.horizontalDistanceSqr() < 0.0025D) {
                motion = Vec3.ZERO;
            }
        } else {
            motion = motion.add(0.0D, -WAITING_GRAVITY, 0.0D).scale(0.98D);
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);
        pointAlongMotion();
    }

    private boolean isOwnerCloseEnough(LivingEntity owner) {
        return this.distanceToSqr(owner) <= OWNER_ABSORB_DISTANCE * OWNER_ABSORB_DISTANCE
                || owner.getBoundingBox().inflate(OWNER_ABSORB_DISTANCE).intersects(this.getBoundingBox());
    }

    private void moveToOrbitSlot(LivingEntity owner) {
        double angle = getOrbitAngle();
        Vec3 target = getOrbitPosition(owner, angle);
        Vec3 toTarget = target.subtract(this.position());
        double distance = toTarget.length();

        Vec3 motion = distance <= 0.03D
                ? toTarget
                : toTarget.normalize().scale(distance <= ORBIT_LOCK_DISTANCE
                        ? distance * 0.45D
                        : Math.min(ORBIT_APPROACH_SPEED, distance * 0.35D));
        moveSmoothly(motion);

        standUpright((float)(angle * 180.0D / Math.PI) + 90.0F);
    }

    private double getOrbitAngle() {
        double offset = Math.floorMod(this.getUUID().getLeastSignificantBits(), 6283L) / 1000.0D;
        return (this.level().getGameTime() * ORBIT_SPEED) + offset;
    }

    private Vec3 getOrbitPosition(LivingEntity owner, double angle) {
        return owner.getEyePosition().add(
                Math.cos(angle) * ORBIT_RADIUS,
                ORBIT_VERTICAL_OFFSET + Math.sin(angle * 0.5D) * ORBIT_BOB_AMPLITUDE,
                Math.sin(angle) * ORBIT_RADIUS
        );
    }

    private void moveSmoothly(Vec3 motion) {
        this.setDeltaMovement(motion);
        if (motion.lengthSqr() > 1.0E-8D) {
            this.move(MoverType.SELF, motion);
        }
    }

    private void spawnFlightTrailParticles() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 direction = motion.normalize();
        for (int i = 0; i < 3; i++) {
            Vec3 particlePos = this.position().subtract(direction.scale(0.25D + i * 0.22D));
            serverLevel.sendParticles(
                    SPIRIT_TRAIL_PARTICLE,
                    particlePos.x,
                    particlePos.y,
                    particlePos.z,
                    1,
                    0.025D,
                    0.025D,
                    0.025D,
                    0.0D
            );
        }
    }

    private void spawnOrbitParticles() {
        if (this.tickCount % 4 != 0 || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(
                SPIRIT_TRAIL_PARTICLE,
                this.getX(),
                this.getY() + 0.15D,
                this.getZ(),
                2,
                0.16D,
                0.22D,
                0.16D,
                0.0D
        );
    }

    private void flyToward(Vec3 target, double speed) {
        Vec3 toTarget = target.subtract(this.position());
        if (toTarget.lengthSqr() < 1.0E-4D) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 motion = toTarget.normalize().scale(Math.min(speed, Math.max(0.15D, toTarget.length() * 0.45D)));
        previousAttackPosition = this.position();
        moveSmoothly(motion);
    }

    private void tryHitTarget(LivingEntity target, LivingEntity owner) {
        if (attackCooldown > 0 || !sweptIntersectsTarget(target)) {
            return;
        }

        onSpiritHit(target);
        attackCooldown = HIT_COOLDOWN_TICKS;
        beginInertialTurn(target, owner);
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    private boolean sweptIntersectsTarget(LivingEntity target) {
        if (this.getBoundingBox().inflate(0.35D).intersects(target.getBoundingBox())) {
            return true;
        }

        Vec3 start = previousAttackPosition == null ? this.position() : previousAttackPosition;
        Vec3 end = this.position();
        return target.getBoundingBox().inflate(0.45D).clip(start, end).isPresent();
    }

    private void onSpiritHit(Entity target) {
        float damage = 8.0F;
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);
        ItemStack weapon = this.getWeaponItem();
        if (this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(serverLevel, weapon, target, source, damage);
        }

        if (target.hurt(source, damage)) {
            if (this.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, weapon);
                triggerSpiritChanneling(serverLevel, target, weapon);
            }

            if (target instanceof LivingEntity livingTarget) {
                this.doKnockback(livingTarget, source);
                this.doPostHurtEffects(livingTarget);
            }
        }
    }

    private void triggerSpiritChanneling(ServerLevel serverLevel, Entity target, ItemStack weapon) {
        var enchantments = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (weapon.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.CHANNELING)) <= 0) {
            return;
        }

        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning == null) {
            return;
        }

        lightning.moveTo(target.getX(), target.getY(), target.getZ());
        if (this.getOwner() instanceof ServerPlayer serverPlayer) {
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

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        if (getSpiritMode() != SpiritMode.FLYING) {
            return null;
        }

        return ProjectileUtil.getEntityHitResult(
                this.level(),
                this,
                start,
                end,
                this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.0D),
                this::canHitEntity
        );
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (getSpiritMode() == SpiritMode.FLYING) {
            Entity hitEntity = result.getEntity();
            onSpiritHit(hitEntity);
            this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

            if (hitEntity instanceof LivingEntity target
                    && this.getOwner() instanceof LivingEntity owner
                    && isValidSpiritTarget(target, owner)) {
                beginAttackLoop(target, owner);
                return;
            }

            enterPostHitState(this.getOwner());
        }
    }

    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult result) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.hitBlockEnchantmentEffects(serverLevel, result, this.getWeaponItem());
        }

        this.playSound(this.getDefaultHitGroundSoundEvent(), 1.0F, 1.0F);
        enterPostHitState(this.getOwner());
    }

    private void enterPostHitState(@Nullable Entity owner) {
        resetAttackLoop();
        resetChargeState();
        resetChargedShotState();
        if (this.level().isClientSide || hasLoyaltyReturn()) {
            enterOrbit();
            return;
        }

        if (owner != null) {
            SpiritTridentState.clearTarget(owner.getUUID());
        }

        enterWaitingForOwner();
    }

    private void beginAttackLoop(LivingEntity target, LivingEntity owner) {
        SpiritTridentState.setTarget(owner.getUUID(), target.getUUID());
        setSpiritMode(SpiritMode.ATTACKING);
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(true);
        attackCooldown = HIT_COOLDOWN_TICKS;
        beginInertialTurn(target, owner);
    }

    private void beginInertialTurn(LivingEntity target, LivingEntity owner) {
        attackPhase = AttackPhase.INERTIAL_TURN;
        attackPhaseTicks = 0;
        inertialDistanceTraveled = 0.0D;
        inertialDirection = resolveInertialDirection(target, owner);

        double attackDistance = attackApproachDistance > 0.0D ? attackApproachDistance : owner.distanceTo(target);
        inertialFlyoutDistance = Mth.clamp(
                attackDistance * FLYOUT_DISTANCE_FACTOR,
                MIN_FLYOUT_DISTANCE,
                MAX_FLYOUT_DISTANCE
        );
        attackApproachDistance = 0.0D;
        previousAttackPosition = null;
    }

    private Vec3 resolveInertialDirection(LivingEntity target, LivingEntity owner) {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-4D) {
            return motion.normalize();
        }

        Vec3 awayFromOwner = target.getEyePosition().subtract(owner.getEyePosition());
        if (awayFromOwner.lengthSqr() > 1.0E-4D) {
            return awayFromOwner.normalize();
        }

        Vec3 awayFromTarget = this.position().subtract(target.position());
        if (awayFromTarget.lengthSqr() > 1.0E-4D) {
            return awayFromTarget.normalize();
        }

        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private Vec3 getValidInertialDirection() {
        if (inertialDirection.lengthSqr() > 1.0E-4D) {
            return inertialDirection.normalize();
        }

        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-4D) {
            inertialDirection = motion.normalize();
            return inertialDirection;
        }

        inertialDirection = new Vec3(0.0D, 0.0D, 1.0D);
        return inertialDirection;
    }

    private Vec3 turnDirectionToward(Vec3 currentDirection, Vec3 targetVector) {
        return turnDirectionToward(currentDirection, targetVector, INERTIAL_TURN_DEGREES_PER_TICK);
    }

    private Vec3 turnDirectionToward(Vec3 currentDirection, Vec3 targetVector, double maxDegreesPerTick) {
        if (targetVector.lengthSqr() <= 1.0E-4D) {
            return getValidInertialDirection();
        }

        Vec3 current = currentDirection.lengthSqr() > 1.0E-4D ? currentDirection.normalize() : getValidInertialDirection();
        Vec3 desired = targetVector.normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, current.dot(desired)));
        double angle = Math.acos(dot);
        double maxAngle = Math.toRadians(maxDegreesPerTick);
        if (angle <= maxAngle || angle <= 1.0E-4D) {
            return desired;
        }

        double t = maxAngle / angle;
        double sinAngle = Math.sin(angle);
        if (Math.abs(sinAngle) <= 1.0E-4D) {
            return current.scale(1.0D - t).add(desired.scale(t)).normalize();
        }

        return current.scale(Math.sin((1.0D - t) * angle) / sinAngle)
                .add(desired.scale(Math.sin(t * angle) / sinAngle))
                .normalize();
    }

    private boolean isFacingTarget(Vec3 direction, Vec3 targetVector) {
        if (targetVector.lengthSqr() <= 1.0E-4D) {
            return true;
        }

        return direction.normalize().dot(targetVector.normalize()) >= 0.72D;
    }

    private void setAttackPhase(AttackPhase phase) {
        if (attackPhase == phase) {
            return;
        }

        attackPhase = phase;
        attackPhaseTicks = 0;
    }

    private void resetAttackLoop() {
        attackPhase = AttackPhase.CHASE;
        attackPhaseTicks = 0;
        inertialDirection = Vec3.ZERO;
        inertialDistanceTraveled = 0.0D;
        inertialFlyoutDistance = MIN_FLYOUT_DISTANCE;
        attackApproachDistance = 0.0D;
        previousAttackPosition = null;
    }

    private void resetChargeState() {
        chargeInputActive = false;
        chargeTicks = 0;
        chargeLevel = 0;
        chargeAimTargetUuid = null;
    }

    private void resetChargedShotState() {
        chargedShotDirection = Vec3.ZERO;
        chargedShotDistance = 0.0D;
        chargedShotMaxDistance = 0.0D;
        chargedShotTargetUuid = null;
        previousChargedShotPosition = null;
    }

    private void enterOrbit() {
        setSpiritMode(SpiritMode.ORBIT);
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(true);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void enterWaitingForOwner() {
        setSpiritMode(SpiritMode.WAITING_FOR_OWNER);
        this.inGround = false;
        this.inGroundTime = 0;
        this.shakeTime = 0;
        this.setNoPhysics(false);
        this.setDeltaMovement(0.0D, -0.08D, 0.0D);
        this.waitingAbsorbCooldown = WAITING_ABSORB_COOLDOWN_TICKS;
    }

    private boolean hasLoyaltyReturn() {
        return this.level() instanceof ServerLevel serverLevel
                && EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, this.getWeaponItem(), this) > 0;
    }

    public ItemStack recallItem() {
        ItemStack stack = this.getPickupItemStackOrigin().copy();
        clearActive();
        this.discard();
        return stack;
    }

    private void handleMissingOwner() {
        if (this.level().isClientSide) {
            return;
        }

        failedOwnerTicks++;
        if (failedOwnerTicks > 20) {
            dropAndDiscard();
        }
    }

    private void dropAndDiscard() {
        if (!this.level().isClientSide && this.pickup == AbstractArrow.Pickup.ALLOWED) {
            this.spawnAtLocation(this.getPickupItemStackOrigin().copy(), 0.1F);
        }

        clearActive();
        this.discard();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearActive();
        super.remove(reason);
    }

    private void clearActive() {
        Entity owner = this.getOwner();
        if (owner != null) {
            SpiritTridentState.clearActive(owner.getUUID(), this.getUUID());
        }
    }

    private void pointAlongMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-4D) {
            pointAlongDirection(motion);
        }
    }

    private void pointAlongDirection(Vec3 direction) {
        if (direction.lengthSqr() <= 1.0E-4D) {
            return;
        }

        Vec3 normalized = direction.normalize();
        double horizontal = normalized.horizontalDistance();
        this.setYRot((float)(Mth.atan2(normalized.x, normalized.z) * 180.0F / Math.PI));
        this.setXRot((float)(Mth.atan2(normalized.y, horizontal) * 180.0F / Math.PI));
    }

    private void standUpright(float targetYaw) {
        this.setYRot(Mth.approachDegrees(this.getYRot(), targetYaw, ORBIT_YAW_STEP));
        this.setXRot(0.0F);
    }

    @Override
    public void playerTouch(Player player) {
        if (getSpiritMode() == SpiritMode.WAITING_FOR_OWNER && this.ownedBy(player)) {
            if (!this.level().isClientSide) {
                enterOrbit();
            }
            return;
        }

        if (getSpiritMode() == SpiritMode.FLYING) {
            super.playerTouch(player);
        }
    }

    @Override
    public boolean isPickable() {
        SpiritMode mode = getSpiritMode();
        return (mode == SpiritMode.FLYING || mode == SpiritMode.ORBIT || mode == SpiritMode.WAITING_FOR_OWNER) && super.isPickable();
    }

    @Override
    public float getPickRadius() {
        if (getSpiritMode() == SpiritMode.ORBIT) {
            return ORBIT_PICK_RADIUS;
        }

        return Math.max(super.getPickRadius(), 0.35F);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !canRecallBy(player)) {
            return super.interact(player, hand);
        }

        recallTo(player);

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void tickDespawn() {
        if (getSpiritMode() == SpiritMode.FLYING) {
            super.tickDespawn();
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSpiritMode(SpiritMode.byName(tag.getString("SpiritMode")));
        this.attackCooldown = tag.getInt("SpiritAttackCooldown");
        this.attackPhase = AttackPhase.byName(tag.getString("SpiritAttackPhase"));
        this.attackPhaseTicks = tag.getInt("SpiritAttackPhaseTicks");
        this.inertialDirection = new Vec3(
                tag.getDouble("SpiritInertialDirX"),
                tag.getDouble("SpiritInertialDirY"),
                tag.getDouble("SpiritInertialDirZ")
        );
        if (this.inertialDirection.lengthSqr() <= 1.0E-4D) {
            this.inertialDirection = Vec3.ZERO;
        }
        this.inertialDistanceTraveled = tag.getDouble("SpiritInertialDistance");
        this.inertialFlyoutDistance = Mth.clamp(
                tag.getDouble("SpiritInertialFlyoutDistance"),
                MIN_FLYOUT_DISTANCE,
                MAX_FLYOUT_DISTANCE
        );
        this.chargeInputActive = tag.getBoolean("SpiritChargeInputActive");
        this.chargeTicks = tag.getInt("SpiritChargeTicks");
        this.chargeLevel = tag.getInt("SpiritChargeLevel");
        this.chargedShotDirection = new Vec3(
                tag.getDouble("SpiritChargedShotDirX"),
                tag.getDouble("SpiritChargedShotDirY"),
                tag.getDouble("SpiritChargedShotDirZ")
        );
        if (this.chargedShotDirection.lengthSqr() <= 1.0E-4D) {
            this.chargedShotDirection = Vec3.ZERO;
        }
        this.chargedShotDistance = tag.getDouble("SpiritChargedShotDistance");
        this.chargedShotMaxDistance = tag.getDouble("SpiritChargedShotMaxDistance");
        this.chargeAimTargetUuid = tag.hasUUID("SpiritChargeAimTarget") ? tag.getUUID("SpiritChargeAimTarget") : null;
        this.chargedShotTargetUuid = tag.hasUUID("SpiritChargedShotTarget") ? tag.getUUID("SpiritChargedShotTarget") : null;
        this.waitingAbsorbCooldown = tag.getInt("SpiritWaitingAbsorbCooldown");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SpiritMode", getSpiritMode().serializedName);
        tag.putInt("SpiritAttackCooldown", this.attackCooldown);
        tag.putString("SpiritAttackPhase", this.attackPhase.serializedName);
        tag.putInt("SpiritAttackPhaseTicks", this.attackPhaseTicks);
        tag.putDouble("SpiritInertialDirX", this.inertialDirection.x);
        tag.putDouble("SpiritInertialDirY", this.inertialDirection.y);
        tag.putDouble("SpiritInertialDirZ", this.inertialDirection.z);
        tag.putDouble("SpiritInertialDistance", this.inertialDistanceTraveled);
        tag.putDouble("SpiritInertialFlyoutDistance", this.inertialFlyoutDistance);
        tag.putBoolean("SpiritChargeInputActive", this.chargeInputActive);
        tag.putInt("SpiritChargeTicks", this.chargeTicks);
        tag.putInt("SpiritChargeLevel", this.chargeLevel);
        tag.putDouble("SpiritChargedShotDirX", this.chargedShotDirection.x);
        tag.putDouble("SpiritChargedShotDirY", this.chargedShotDirection.y);
        tag.putDouble("SpiritChargedShotDirZ", this.chargedShotDirection.z);
        tag.putDouble("SpiritChargedShotDistance", this.chargedShotDistance);
        tag.putDouble("SpiritChargedShotMaxDistance", this.chargedShotMaxDistance);
        if (this.chargeAimTargetUuid != null) {
            tag.putUUID("SpiritChargeAimTarget", this.chargeAimTargetUuid);
        }
        if (this.chargedShotTargetUuid != null) {
            tag.putUUID("SpiritChargedShotTarget", this.chargedShotTargetUuid);
        }
        tag.putInt("SpiritWaitingAbsorbCooldown", this.waitingAbsorbCooldown);
    }

    private SpiritMode getSpiritMode() {
        return SpiritMode.byDataValue(this.entityData.get(DATA_SPIRIT_MODE));
    }

    private void setSpiritMode(SpiritMode mode) {
        this.entityData.set(DATA_SPIRIT_MODE, mode.dataValue);
    }

    private enum SpiritMode {
        FLYING("flying", (byte)0),
        ORBIT("orbit", (byte)1),
        ATTACKING("attacking", (byte)2),
        WAITING_FOR_OWNER("waiting_for_owner", (byte)3),
        CHARGING("charging", (byte)4),
        CHARGED_SHOT("charged_shot", (byte)5);

        private final String serializedName;
        private final byte dataValue;

        SpiritMode(String serializedName, byte dataValue) {
            this.serializedName = serializedName;
            this.dataValue = dataValue;
        }

        private static SpiritMode byName(String name) {
            for (SpiritMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }

            return FLYING;
        }

        private static SpiritMode byDataValue(byte dataValue) {
            for (SpiritMode mode : values()) {
                if (mode.dataValue == dataValue) {
                    return mode;
                }
            }

            return FLYING;
        }
    }

    private enum AttackPhase {
        CHASE("chase"),
        INERTIAL_TURN("inertial_turn"),
        RETURN("return");

        private final String serializedName;

        AttackPhase(String serializedName) {
            this.serializedName = serializedName;
        }

        private static AttackPhase byName(String name) {
            for (AttackPhase phase : values()) {
                if (phase.serializedName.equals(name)) {
                    return phase;
                }
            }

            return CHASE;
        }
    }
}
