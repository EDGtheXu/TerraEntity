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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.confluence.lib.mixed.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.proj.LineProj;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEProjectileEntities;

/**
 * 毁灭者体节
 */
public class DestroyerSegment extends AbstractTerraBossBase implements Boss.BossPart {

    private Destroyer head;
    private LivingEntity prevSegment;
    private boolean isTail = false;

    // 渲染平滑插值字段
    public float prevSegmentRoll;

    // --- 同步数据 ---
    // 探测器是否已释放 (true = 红灯灭; false = 红灯亮)
    private static final EntityDataAccessor<Boolean> DATA_PROBE_RELEASED = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);
    // 侧翼是否展开
    public static final EntityDataAccessor<Boolean> DATA_FLAPS_OPEN = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);
    // 当前体节 Roll
    public static final EntityDataAccessor<Float> DATA_SEGMENT_ROLL = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.FLOAT);
    // 是否为尾部
    public static final EntityDataAccessor<Boolean> DATA_IS_TAIL = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);
    // 是否为探针体节
    public static final EntityDataAccessor<Boolean> DATA_IS_PROBE_SEGMENT = SynchedEntityData.defineId(DestroyerSegment.class, EntityDataSerializers.BOOLEAN);

    public DestroyerSegment(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
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
        builder.define(DATA_IS_PROBE_SEGMENT, false);
    }

    // --- Setter/Getter ---
    public void setHead(Destroyer head) { this.head = head; }
    public void setPrevSegment(LivingEntity entity) { this.prevSegment = entity; }
    public void setTail(boolean tail) {
        this.isTail = tail;
        this.entityData.set(DATA_IS_TAIL, tail);
    }
    public void setProbeSegment(boolean isProbe) {
        this.entityData.set(DATA_IS_PROBE_SEGMENT, isProbe);
    }

    public float getSegmentRoll() { return entityData.get(DATA_SEGMENT_ROLL); }
    public void setSegmentRoll(float r) { entityData.set(DATA_SEGMENT_ROLL, r); }
    public boolean isTail() { return entityData.get(DATA_IS_TAIL); }
    public boolean isProbeSegment() { return entityData.get(DATA_IS_PROBE_SEGMENT); }
    public boolean hasReleasedProbe() { return entityData.get(DATA_PROBE_RELEASED); }

    @Override
    public void tick() {
        // 记录上一帧的 Roll
        this.prevSegmentRoll = this.getSegmentRoll();

        super.tick();

        // 客户端逻辑：侧翼排气粒子
        if (level().isClientSide) {
            if (entityData.get(DATA_FLAPS_OPEN)) {
                // 仅演示，实际位置需根据旋转矩阵计算
                if (random.nextFloat() < 0.1) {
                    level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0.1, 0);
                }
            }
            return;
        }

        // 服务端逻辑
        if (head == null || !head.isAlive() || prevSegment == null || !prevSegment.isAlive()) {
            this.discard();
            return;
        }

        tickMovementFollow();
        tickRollFollow();
        tickStateSync();

        // 天空模式：蓝色火焰伤害
        if (head.getPhase() == Destroyer.Phase.SKY) {
            level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.5),
                            e -> e != this && e != head && !(e instanceof DestroyerSegment))
                    .forEach(e -> {
                        // TODO: 点燃或其他命中效果
                        e.hurt(damageSources().mobAttack(this), 10.0f);
                    });
        }
    }

    private void tickMovementFollow() {
        double distSq = distanceToSqr(prevSegment);
        float interval = 3.5f;

        if (distSq > 6400) { this.setPos(prevSegment.position()); return; }

        if (distSq > 0.5) {
            Vec3 dirToPrev = prevSegment.position().subtract(this.position()).normalize();
            double currentDist = Math.sqrt(distSq);
            double moveDist = currentDist - interval;

            if (moveDist > 0) {
                Vec3 moveVec = dirToPrev.scale(moveDist);
                this.setPos(this.position().add(moveVec));
            }
            this.lookAt(prevSegment, 180, 180);
        }
    }

    // DNA 螺旋逻辑
    private void tickRollFollow() {
        float targetRoll = 0;
        if (prevSegment instanceof Destroyer) targetRoll = ((Destroyer) prevSegment).getBodyRoll();
        else if (prevSegment instanceof DestroyerSegment) targetRoll = ((DestroyerSegment) prevSegment).getSegmentRoll();

        float currentRoll = getSegmentRoll();
        float diff = Mth.degreesDifference(currentRoll, targetRoll);

        if (Math.abs(diff) >= 10.0f) {
            // 差距大 -> 机械性拉回
            currentRoll += (diff > 0 ? (diff - 9.0f) : (diff + 9.0f));
        } else {
            // 差距小 -> 平滑跟随
            currentRoll += diff * 0.15f;
        }
        setSegmentRoll(Mth.wrapDegrees(currentRoll));
    }

    private void tickStateSync() {
        boolean shouldOpenFlaps = false;

        if (head.getPhase() == Destroyer.Phase.UNDERGROUND) {
            shouldOpenFlaps = false;
        } else if (head.getPhase() == Destroyer.Phase.SKY) {
            shouldOpenFlaps = true;
        } else {
            // 地面模式：不在方块内即展开 (自然形成顺序效果)
            shouldOpenFlaps = !level().getBlockState(blockPosition()).isSolid();
        }

        if (entityData.get(DATA_FLAPS_OPEN) != shouldOpenFlaps) {
            entityData.set(DATA_FLAPS_OPEN, shouldOpenFlaps);
            if (shouldOpenFlaps) this.playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.3f, 1.5f);
        }
    }

    public void tryShootLaser(LivingEntity target) {
        if (target == null) return;

        // 核心修正：
        // 1. 必须是 Probe Segment
        // 2. 必须未释放 (亮红灯)
        // 3. 必须侧翼展开
        // 4. 不在墙里
        if (!isProbeSegment()) return;
        if (hasReleasedProbe()) return;
        if (!entityData.get(DATA_FLAPS_OPEN)) return;
        if (level().getBlockState(blockPosition()).isSolid()) return;

        LineProj laser = TEProjectileEntities.DESTROYER_LASER_PROJ.get().create(level());
        if (laser != null) {
            laser.setOwner(this);
            laser.setPos(this.position().add(0, 0.5, 0));
            laser.setDamage(20.0f);
            laser.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), 1.5F, 1.0F);
            level().addFreshEntity(laser);
            this.playSound(SoundEvents.BEACON_ACTIVATE, 0.5f, 2.0f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (head != null && head.isAlive()) {
            boolean hurtResult = head.hurt(source, amount);

            // 受伤释放探针逻辑
            if (!this.level().isClientSide && hurtResult) {
                // 仅探针体节、未释放、且不在地下
                if (isProbeSegment() && !hasReleasedProbe() && head.getPhase() != Destroyer.Phase.UNDERGROUND) {
                    // 20% 概率释放
                    if (this.random.nextFloat() < 0.2f) {
                        tryReleaseProbe();
                    }
                }
            }
            return hurtResult;
        }
        return false;
    }

    private void tryReleaseProbe() {
        if (this.level() instanceof ServerLevel serverLevel && !hasReleasedProbe()) {
            // 标记已释放 (红灯灭)
            entityData.set(DATA_PROBE_RELEASED, true);
            this.playSound(SoundEvents.PISTON_EXTEND, 1.0f, 1.0f);

            DestroyerProbe probe = TEBossEntities.DESTROYER_PROBE.get().create(serverLevel);
            if (probe != null) {
                probe.moveTo(this.getX(), this.getY() + 1.5, this.getZ(), this.getYRot(), 0);
                probe.setHead(this.head); // 设置本体，同步仇恨
                probe.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(this.blockPosition()), MobSpawnType.TRIGGERED, null);
                serverLevel.addFreshEntity(probe);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("ProbeReleased", hasReleasedProbe());
        compound.putBoolean("IsProbeSegment", isProbeSegment());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ProbeReleased")) entityData.set(DATA_PROBE_RELEASED, compound.getBoolean("ProbeReleased"));
        if (compound.contains("IsProbeSegment")) setProbeSegment(compound.getBoolean("IsProbeSegment"));
    }

    // --- 基础重写 ---
    @Override public boolean canAttack(LivingEntity entity) { return !(entity instanceof Destroyer || entity instanceof DestroyerSegment || entity instanceof DestroyerProbe); }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean shouldShowBossBar() { return false; }
    @Override public void addSkills() {}
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override public boolean isInvulnerableTo(DamageSource s) { return super.isInvulnerableTo(s) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.FALL); }
    @Override public void die(DamageSource damageSource) {
        if (!CommonHooks.onLivingDeath(this, damageSource)) {
            if (!this.isRemoved() && !this.dead) {
                this.dead = true;
                if (this.level() instanceof ServerLevel serverLevel) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
                    this.level().broadcastEntityEvent(this, (byte)3);
                }
                this.setPose(Pose.DYING);
            }
        }
    }
}
