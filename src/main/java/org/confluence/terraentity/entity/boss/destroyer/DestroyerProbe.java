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
import org.confluence.terraentity.init.entity.TEBossEntities;
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

public class DestroyerProbe extends AbstractTerraBossBase implements GeoEntity, RangedAttackMob, Boss {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 本体引用
    private Destroyer head;
    // 同步数据：本体UUID
    public static final EntityDataAccessor<Optional<UUID>> DATA_HEAD_UUID = SynchedEntityData.defineId(DestroyerProbe.class, EntityDataSerializers.OPTIONAL_UUID);

    public DestroyerProbe(EntityType<? extends Monster> entityType, Level level) {
        this(entityType, level, null);
    }

    public DestroyerProbe(EntityType<? extends Monster> entityType, Level level, @Nullable Destroyer head) {
        super(entityType, level);
        this.moveControl = new ProbeMoveControl(this);
        this.xpReward = 5;
        this.noPhysics = true; // 飞行单位无物理碰撞

        // 初始化本体
        setHead(head);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEAD_UUID, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // 客户端监听到UUID变更时，尝试重新获取实体引用
        if (key == DATA_HEAD_UUID) {
            setHead(null);
        }
    }

    // 设置本体，处理UUID同步和实体引用查找
    public void setHead(@Nullable Destroyer newHead) {
        if (newHead == null) {
            // 尝试通过UUID查找实体（主要用于客户端或加载后）
            if (getEntityData().get(DATA_HEAD_UUID).isPresent()) {
                UUID uuid = getEntityData().get(DATA_HEAD_UUID).get();
                if (level() instanceof ServerLevel serverLevel) {
                    Entity entity = serverLevel.getEntity(uuid);
                    if (entity instanceof Destroyer) {
                        newHead = (Destroyer) entity;
                    }
                }
            }
        } else {
            // 设置新本体并同步UUID
            if (!level().isClientSide) {
                getEntityData().set(DATA_HEAD_UUID, Optional.of(newHead.getUUID()));
            }
        }
        this.head = newHead;
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端/服务端：如果引用丢失（如跨区块加载），尝试恢复
        if (this.head == null && tickCount % 20 == 0) {
            setHead(null);
        }

        // 目标同步逻辑
        if (this.head != null && this.head.isAlive()) {
            LivingEntity headTarget = this.head.getTarget();
            // 如果本体有目标，且探测器当前没有目标或目标不一致，则同步
            if (headTarget != null && headTarget.isAlive() && this.getTarget() != headTarget) {
                this.setTarget(headTarget);
            }
        }
    }

    // --- 属性设置 ---
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 64.0D); // 增大索敌范围以匹配boss
    }

    // --- 基础配置 ---
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
        // 目标选择器：优先听从本体指挥（在tick中同步），其次反击，最后找玩家
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));

        // 行为目标
        this.goalSelector.addGoal(1, new ProbeAttackGoal(this));
        this.goalSelector.addGoal(2, new ProbeRandomFlyGoal(this));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    // --- NBT保存与读取 ---
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (head != null) {
            compound.putUUID("HeadUUID", head.getUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HeadUUID")) {
            getEntityData().set(DATA_HEAD_UUID, Optional.of(tag.getUUID("HeadUUID")));
        }
    }

    // --- Boss 接口实现 (继承自 AbstractTerraBossBase) ---

    @Override
    public boolean shouldShowBossBar() {
        return false; // 探测器不显示Boss条
    }

    @Override
    public boolean isMainBody() {
        return false;
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.RED;
    }

    // AbstractTerraBossBase 的空实现补充
    @Override
    public void addSkills() {}

    @Override
    public boolean isPushable() {
        return true; // 探测器可以被推开
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        super.doPush(entity);
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.IRON_GOLEM_HURT; // 机械音效
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_EXPLODE.value();
    }

    // --- 攻击逻辑 (实现 RangedAttackMob) ---
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (this.level().isClientSide) return;

        LineProj laser = new LineProj(TEProjectileEntities.VILE_SPIT_PROJ.get(), this.level()); // 使用合适的激光投射物
        laser.setOwner(this);
        laser.setPos(this.getX(), this.getY() + 0.5, this.getZ());
        laser.setDamage(20.0f); // 设置伤害

        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.5D) - laser.getY();
        double d2 = target.getZ() - this.getZ();

        laser.shoot(d0, d1, d2, 1.5F, 1.0F);

        this.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);
        this.level().addFreshEntity(laser);
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        // 防止攻击自己人
        return !(entity instanceof Destroyer || entity instanceof DestroyerSegment || entity instanceof DestroyerProbe);
    }

    // --- GeckoLib 动画 ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GeoAnimatable>(this, "controller", 0, event -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // --- 内部类：移动控制器 ---
    class ProbeMoveControl extends MoveControl {
        public ProbeMoveControl(DestroyerProbe probe) {
            super(probe);
        }

        @Override
        public void tick() {
            if (this.operation == Operation.MOVE_TO) {
                Vec3 vec3 = new Vec3(this.wantedX - DestroyerProbe.this.getX(), this.wantedY - DestroyerProbe.this.getY(), this.wantedZ - DestroyerProbe.this.getZ());
                double d0 = vec3.length();
                if (d0 < DestroyerProbe.this.getBoundingBox().getSize()) {
                    this.operation = Operation.WAIT;
                    DestroyerProbe.this.setDeltaMovement(DestroyerProbe.this.getDeltaMovement().scale(0.5D));
                } else {
                    DestroyerProbe.this.setDeltaMovement(DestroyerProbe.this.getDeltaMovement().add(vec3.scale(this.speedModifier * 0.05D / d0)));
                    if (DestroyerProbe.this.getTarget() == null) {
                        Vec3 vec31 = DestroyerProbe.this.getDeltaMovement();
                        DestroyerProbe.this.setYRot(-((float)Mth.atan2(vec31.x, vec31.z)) * (180F / (float)Math.PI));
                        DestroyerProbe.this.yBodyRot = DestroyerProbe.this.getYRot();
                    } else {
                        double d2 = DestroyerProbe.this.getTarget().getX() - DestroyerProbe.this.getX();
                        double d1 = DestroyerProbe.this.getTarget().getZ() - DestroyerProbe.this.getZ();
                        DestroyerProbe.this.setYRot(-((float)Mth.atan2(d2, d1)) * (180F / (float)Math.PI));
                        DestroyerProbe.this.yBodyRot = DestroyerProbe.this.getYRot();
                    }
                }
            }
        }
    }

    // --- 内部类：随机飞行目标 ---
    class ProbeRandomFlyGoal extends Goal {
        private final DestroyerProbe probe;

        public ProbeRandomFlyGoal(DestroyerProbe probe) {
            this.probe = probe;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.probe.getMoveControl().hasWanted() && this.probe.getRandom().nextInt(7) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            // 如果有本体且本体距离过远，优先飞向本体附近
            if (probe.head != null && probe.head.isAlive() && probe.distanceToSqr(probe.head) > 64 * 64) {
                Vec3 headPos = probe.head.position();
                // 飞到本体附近随机位置
                this.probe.moveControl.setWantedPosition(headPos.x + (random.nextDouble() - 0.5) * 10, headPos.y + 5, headPos.z + (random.nextDouble() - 0.5) * 10, 1.0);
                return;
            }

            BlockPos blockpos = this.probe.blockPosition();
            for(int i = 0; i < 3; ++i) {
                BlockPos blockpos1 = blockpos.offset(this.probe.getRandom().nextInt(15) - 7, this.probe.getRandom().nextInt(11) - 5, this.probe.getRandom().nextInt(15) - 7);
                if (this.probe.level().isEmptyBlock(blockpos1)) {
                    this.probe.moveControl.setWantedPosition((double)blockpos1.getX() + 0.5D, (double)blockpos1.getY() + 0.5D, (double)blockpos1.getZ() + 0.5D, 0.25D);
                    if (this.probe.getTarget() == null) {
                        this.probe.getLookControl().setLookAt((double)blockpos1.getX() + 0.5D, (double)blockpos1.getY() + 0.5D, (double)blockpos1.getZ() + 0.5D, 180.0F, 20.0F);
                    }
                    break;
                }
            }
        }
    }

    // --- 内部类：攻击目标 ---
    class ProbeAttackGoal extends Goal {
        private final DestroyerProbe probe;
        private int attackTime;

        public ProbeAttackGoal(DestroyerProbe probe) {
            this.probe = probe;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.probe.getTarget();
            return target != null && target.isAlive() && this.probe.canAttack(target);
        }

        @Override
        public void start() {
            this.attackTime = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.probe.getTarget();
            if (target != null) {
                double distSq = this.probe.distanceToSqr(target);

                // 保持一定距离并环绕
                if (distSq < 100.0D) {
                    Vec3 retreatDir = this.probe.position().subtract(target.position()).normalize();
                    this.probe.moveControl.setWantedPosition(this.probe.getX() + retreatDir.x * 5, this.probe.getY() + 2, this.probe.getZ() + retreatDir.z * 5, 1.0);
                } else if (distSq > 256.0D) {
                    this.probe.moveControl.setWantedPosition(target.getX(), target.getY() + 3, target.getZ(), 1.0);
                }

                this.probe.getLookControl().setLookAt(target, 30.0F, 30.0F);

                if (--this.attackTime <= 0) {
                    this.attackTime = 60;
                    this.probe.performRangedAttack(target, 1.0f);
                }
            }
        }
    }
}
