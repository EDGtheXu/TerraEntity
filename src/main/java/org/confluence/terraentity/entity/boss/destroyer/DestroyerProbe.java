package org.confluence.terraentity.entity.boss.destroyer;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.proj.LineProj;
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * 毁灭者探针 (Minion)
 */
public class DestroyerProbe extends AbstractTerraBossBase implements GeoEntity, RangedAttackMob, Boss {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Destroyer head;
    public static final EntityDataAccessor<Optional<UUID>> DATA_HEAD_UUID = SynchedEntityData.defineId(DestroyerProbe.class, EntityDataSerializers.OPTIONAL_UUID);

    public DestroyerProbe(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new ProbeMoveControl(this);
        this.xpReward = 5;
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEAD_UUID, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_HEAD_UUID) setHead(null);
    }

    public void setHead(@Nullable Destroyer newHead) {
        if (newHead == null) {
            // 通过UUID查找
            if (getEntityData().get(DATA_HEAD_UUID).isPresent()) {
                UUID uuid = getEntityData().get(DATA_HEAD_UUID).get();
                if (level() instanceof ServerLevel serverLevel) {
                    Entity entity = serverLevel.getEntity(uuid);
                    if (entity instanceof Destroyer) newHead = (Destroyer) entity;
                }
            }
        } else {
            if (!level().isClientSide) {
                getEntityData().set(DATA_HEAD_UUID, Optional.of(newHead.getUUID()));
            }
        }
        this.head = newHead;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.head == null && tickCount % 20 == 0) setHead(null);

        // 同步本体目标
        if (this.head != null && this.head.isAlive()) {
            LivingEntity headTarget = this.head.getTarget();
            if (headTarget != null && headTarget.isAlive() && this.getTarget() != headTarget) {
                this.setTarget(headTarget);
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.goalSelector.addGoal(1, new ProbeAttackGoal(this));
        this.goalSelector.addGoal(2, new ProbeRandomFlyGoal(this));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) return;
        LineProj laser = TEProjectileEntities.VILE_SPIT_PROJ.get().create(level());
        if (laser != null) {
            laser.setOwner(this);
            laser.setPos(this.getX(), this.getY() + 0.5, this.getZ());
            laser.setDamage(20.0f);
            laser.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), 1.5F, 1.0F);
            this.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);
            this.level().addFreshEntity(laser);
        }
    }

    // --- Save/Load ---
    @Override public void addAdditionalSaveData(CompoundTag compound) { super.addAdditionalSaveData(compound); if (head != null) compound.putUUID("HeadUUID", head.getUUID()); }
    @Override public void readAdditionalSaveData(@NotNull CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains("HeadUUID")) getEntityData().set(DATA_HEAD_UUID, Optional.of(tag.getUUID("HeadUUID"))); }

    // --- Boilerplate ---
    @Override public boolean isNoGravity() { return true; }
    @Override protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}
    @Override public boolean shouldShowBossBar() { return false; }
    @Override public boolean isMainBody() { return false; }
    @Override protected BossEvent.BossBarColor getBossBarColor() { return BossEvent.BossBarColor.RED; }
    @Override public void addSkills() {}
    @Override public boolean isPushable() { return true; }
    @Override protected SoundEvent getHurtSound(@NotNull DamageSource s) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GENERIC_EXPLODE.value(); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) { c.add(new AnimationController<GeoAnimatable>(this, "controller", 0, e -> PlayState.CONTINUE)); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // --- Inner Classes ---
    class ProbeMoveControl extends MoveControl {
        public ProbeMoveControl(DestroyerProbe probe) { super(probe); }
        @Override public void tick() {
            if (this.operation == Operation.MOVE_TO) {
                Vec3 vec3 = new Vec3(this.wantedX - mob.getX(), this.wantedY - mob.getY(), this.wantedZ - mob.getZ());
                double d0 = vec3.length();
                if (d0 < mob.getBoundingBox().getSize()) {
                    this.operation = Operation.WAIT;
                    mob.setDeltaMovement(mob.getDeltaMovement().scale(0.5D));
                } else {
                    mob.setDeltaMovement(mob.getDeltaMovement().add(vec3.scale(this.speedModifier * 0.05D / d0)));
                    Vec3 look = mob.getDeltaMovement();
                    if (mob.getTarget() != null) {
                        look = mob.getTarget().position().subtract(mob.position());
                    }
                    mob.setYRot(-((float)Mth.atan2(look.x, look.z)) * (180F / (float)Math.PI));
                    mob.yBodyRot = mob.getYRot();
                }
            }
        }
    }

    class ProbeRandomFlyGoal extends Goal {
        private final DestroyerProbe probe;
        public ProbeRandomFlyGoal(DestroyerProbe probe) { this.probe = probe; this.setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return !this.probe.getMoveControl().hasWanted() && this.probe.getRandom().nextInt(7) == 0; }
        @Override public void tick() {
            if (probe.head != null && probe.head.isAlive() && probe.distanceToSqr(probe.head) > 64 * 64) {
                Vec3 headPos = probe.head.position();
                this.probe.moveControl.setWantedPosition(headPos.x + (random.nextDouble()-0.5)*10, headPos.y+5, headPos.z+(random.nextDouble()-0.5)*10, 1.0);
                return;
            }
            BlockPos bp = this.probe.blockPosition().offset(random.nextInt(15)-7, random.nextInt(11)-5, random.nextInt(15)-7);
            if (probe.level().isEmptyBlock(bp)) probe.moveControl.setWantedPosition(bp.getX()+0.5, bp.getY()+0.5, bp.getZ()+0.5, 0.25);
        }
    }

    class ProbeAttackGoal extends Goal {
        private final DestroyerProbe probe;
        private int attackTime;
        public ProbeAttackGoal(DestroyerProbe probe) { this.probe = probe; this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return probe.getTarget() != null && probe.getTarget().isAlive(); }
        @Override public void start() { attackTime = 0; }
        @Override public void tick() {
            LivingEntity t = probe.getTarget();
            if (t != null) {
                double dist = probe.distanceToSqr(t);
                if (dist < 100) { // 太近后退
                    Vec3 dir = probe.position().subtract(t.position()).normalize();
                    probe.moveControl.setWantedPosition(probe.getX()+dir.x*5, probe.getY()+2, probe.getZ()+dir.z*5, 1.0);
                } else if (dist > 256) { // 太远靠近
                    probe.moveControl.setWantedPosition(t.getX(), t.getY()+3, t.getZ(), 1.0);
                }
                probe.getLookControl().setLookAt(t, 30, 30);
                if (--attackTime <= 0) {
                    attackTime = 60;
                    probe.performRangedAttack(t, 1.0f);
                }
            }
        }
    }
}
