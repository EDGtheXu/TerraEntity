package org.confluence.terraentity.entity.boss.destroyer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.proj.LineProj;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEProjectileEntities;

/**
 * 毁灭者体节
 * 核心逻辑：
 * 1. 物理位置跟随前一节。
 * 2. 旋转角度(Roll)滞后跟随前一节（DNA螺旋关键）。
 * 3. 侧翼排气动画同步。
 */
public class DestroyerSegment extends AbstractTerraBossBase implements Boss.BossPart {

    private Destroyer head;
    private LivingEntity prevSegment; // 前一个节点 (Destroyer 或 DestroyerSegment)
    private boolean isTail = false;

    // --- 同步数据 ---
    // 探测器是否释放
    private static final EntityDataAccessor<Boolean> DATA_PROBE_RELEASED = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);
    // 侧翼是否展开
    public static final EntityDataAccessor<Boolean> DATA_FLAPS_OPEN = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);
    // 当前体节的 Roll 角度
    public static final EntityDataAccessor<Float> DATA_SEGMENT_ROLL = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.FLOAT);
    // 是否为尾部 (用于渲染器决定模型)
    public static final EntityDataAccessor<Boolean> DATA_IS_TAIL = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);

    public DestroyerSegment(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true; // 体节始终无物理碰撞移动，完全由代码设置位置
    }

    public DestroyerSegment(Destroyer head, Level level) {
        this(TEBossEntities.DESTROYER_SEGMENT.get(), level);
        this.head = head;
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROBE_RELEASED, false);
        builder.define(DATA_FLAPS_OPEN, false);
        builder.define(DATA_SEGMENT_ROLL, 0f);
        builder.define(DATA_IS_TAIL, false);
    }

    // --- Setter ---
    public void setHead(Destroyer head) {
        this.head = head;
    }

    public void setPrevSegment(LivingEntity entity) {
        this.prevSegment = entity;
    }

    public void setTail(boolean tail) {
        this.isTail = tail;
        this.entityData.set(DATA_IS_TAIL, tail);
    }

    public float getSegmentRoll() { return entityData.get(DATA_SEGMENT_ROLL); }
    public void setSegmentRoll(float r) { entityData.set(DATA_SEGMENT_ROLL, r); }
    public boolean isTail() { return entityData.get(DATA_IS_TAIL); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // 生存检查
        if (head == null || !head.isAlive() || prevSegment == null || !prevSegment.isAlive()) {
            this.discard();
            return;
        }

        // 1. 位置跟随算法
        tickMovementFollow();

        // 2. 旋转角度(Roll)跟随算法 (核心需求)
        tickRollFollow();

        // 3. 状态同步 (侧翼展开)
        tickStateSync();

        // 4. 天空模式下的接触伤害 (蓝色火焰)
        if (head.getPhase() == Destroyer.Phase.SKY) {
            // 对周围实体造成火焰伤害
            level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.5),
                            e -> e != this && e != head && !(e instanceof DestroyerSegment))
                    .forEach(e -> {
                        // TODO：平衡数值，debuff等
                        e.hurt(damageSources().mobAttack(this), 10.0f);
                    });
        }
    }

    private void tickMovementFollow() {
        double distSq = distanceToSqr(prevSegment);
        float interval = 3.5f; // 理想间距

        // 如果距离过远（如传送），直接瞬移
        if (distSq > 6400) { // 80格
            this.setPos(prevSegment.position());
            return;
        }

        // 计算目标位置：前一节的位置 + 反向向量 * 间距
        // 这里使用简单的插值追踪，模拟“拖拽”感
        if (distSq > 0.5) {
            Vec3 dirToPrev = prevSegment.position().subtract(this.position()).normalize();

            // 目标位置是 前一节位置 减去 (方向 * 间距)
            // 但为了平滑，我们看向前一节，然后设置速度

            // 实际位置更新：直接数学计算位置，不使用setVelocity，因为 noPhysics = true
            // 这种算法能保证体节紧跟
            double currentDist = Math.sqrt(distSq);
            double moveDist = currentDist - interval;

            if (moveDist > 0) {
                Vec3 moveVec = dirToPrev.scale(moveDist);
                this.setPos(this.position().add(moveVec));
            }

            // 更新朝向 LookAt
            this.lookAt(prevSegment, 180, 180);
        }
    }

    /**
     * 实现需求：
     * - 差值 >= 10度 -> 强制跟随缩小到10度内
     * - 差值 < 10度 -> 缓慢旋转一致
     */
    private void tickRollFollow() {
        float targetRoll = 0;

        // 获取前一节的 Roll
        if (prevSegment instanceof Destroyer) {
            targetRoll = ((Destroyer) prevSegment).getBodyRoll();
        } else if (prevSegment instanceof DestroyerSegment) {
            targetRoll = ((DestroyerSegment) prevSegment).getSegmentRoll();
        }

        float currentRoll = getSegmentRoll();

        // 计算角度差 (-180 到 180)
        float diff = Mth.degreesDifference(currentRoll, targetRoll);

        if (Math.abs(diff) >= 10.0f) {
            // 差距过大，强制拉回。
            // 这里的逻辑是：让 currentRoll 变成 (targetRoll ± 9.9)，保留一点滞后感
            float adjustment = diff > 0 ? (diff - 9.0f) : (diff + 9.0f);
            currentRoll += adjustment;
        } else {
            // 差距小，缓慢插值
            currentRoll += diff * 0.15f;
        }

        setSegmentRoll(Mth.wrapDegrees(currentRoll));
    }

    private void tickStateSync() {
        boolean shouldOpenFlaps = false;

        if (getTarget() != null) {
            // 天空模式：全程打开
            if (head.getPhase() == Destroyer.Phase.SKY) {
                shouldOpenFlaps = true;
            }
            // 地面、地下模式：离开方块且看到玩家时打开
            else {
                boolean inSolid = level().getBlockState(blockPosition()).isSolid();
                shouldOpenFlaps = !inSolid && hasLineOfSight(getTarget());
            }
        }

        // 仅在状态改变时同步，节省带宽
        if (entityData.get(DATA_FLAPS_OPEN) != shouldOpenFlaps) {
            entityData.set(DATA_FLAPS_OPEN, shouldOpenFlaps);
            if (shouldOpenFlaps) {
                // 可以在这里播放机械音效等
            }
        }
    }

    // 由头部调用
    public void tryShootLaser(LivingEntity target) {
        if (target == null) return;

        // 只有侧翼展开且尚未脱出时发射
        if (!entityData.get(DATA_FLAPS_OPEN)) return;
        if (!entityData.get(DATA_PROBE_RELEASED)) return;

        // 生成激光实体
        LineProj laser = TEProjectileEntities.DESTROYER_LASER_PROJ.get().create(level());
        if (laser != null) {
            laser.setOwner(this);
            // 从体节中心稍微向上的位置发射
            Vec3 shootPos = this.position().add(0, 0.5, 0);
            laser.setPos(shootPos);
            laser.setDamage(20.0f);

            laser.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), 1.5F, 1);
            level().addFreshEntity(laser);

            this.playSound(net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.5f, 2.0f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 将伤害传递给头部 (共享血量机制)
        if (head != null && head.isAlive()) {
            boolean hurtResult = head.hurt(source, amount);
            // 仅在服务端运行，且确实造成了伤害
            if (!this.level().isClientSide && hurtResult) {
                // 检查：尚未释放探测器 且 随机判定
                if (this.random.nextFloat() < 0.2f) {
                    tryReleaseProbe();
                }
            }
            return hurtResult;
        }
        return false;
    }

    private void tryReleaseProbe() {
        if (this.level() instanceof ServerLevel serverLevel && !entityData.get(DATA_PROBE_RELEASED)) {
            // 标记为已释放
            entityData.set(DATA_PROBE_RELEASED, true);

            // 播放音效 (可选)
            this.playSound(SoundEvents.PISTON_EXTEND, 1.0f, 1.0f);

            // 生成探测器实体
            Entity probe = TEBossEntities.DESTROYER_PROBE.get().create(serverLevel);

            if (probe != null) {
                probe.moveTo(this.getX(), this.getY() + 0.5, this.getZ(), this.getYRot(), 0);

                // 如果探测器是 Mob，初始化它
                if (probe instanceof Mob mobProbe) {
                    mobProbe.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(this.blockPosition()), MobSpawnType.TRIGGERED, null);
                    // 让探测器立刻攻击伤害体节的来源（通常是玩家）
                    if (this.lastHurtByPlayer != null) {
                        mobProbe.setTarget(this.lastHurtByPlayer);
                    }
                }

                serverLevel.addFreshEntity(probe);
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        // 防止攻击自己人
        return !(entity instanceof Destroyer || entity instanceof DestroyerSegment || entity instanceof DestroyerProbe);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!CommonHooks.onLivingDeath(this, damageSource)) {
            if (!this.isRemoved() && !this.dead) {
                this.dead = true;
                this.getCombatTracker().recheckStatus();
                if (this.level() instanceof ServerLevel serverLevel) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    // 不掉落物品，掉落物由头统一管理
                    // 但可以生成爆炸粒子
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
                    this.level().broadcastEntityEvent(this, (byte)3);
                }
                this.setPose(Pose.DYING);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("ProbeReleased", entityData.get(DATA_PROBE_RELEASED));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ProbeReleased")) {
            entityData.set(DATA_PROBE_RELEASED, compound.getBoolean("ProbeReleased"));
        }
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldShowBossBar() { return false; }

    @Override
    public void addSkills() {}

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL);
    }
}
