package org.confluence.terraentity.entity.boss.destroyer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.confluence.lib.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.utils.CameraShakeData;
import org.confluence.terraentity.utils.CameraShakeManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 毁灭者 (The Destroyer) - 头部
 */
public class Destroyer extends AbstractTerraBossBase implements Boss {

    // --- 同步数据 ---
    // 0: Underground, 1: Ground, 2: Sky
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.INT);
    // 0: 白漆, 1: 神圣金属
    public static final EntityDataAccessor<Integer> DATA_TEXTURE_VARIANT = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.INT);
    // 身体滚转角 (Z轴)
    public static final EntityDataAccessor<Float> DATA_BODY_ROLL = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.FLOAT);
    // 头甲状态
    public static final EntityDataAccessor<Boolean> DATA_HEAD_SHELL_OPEN = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.BOOLEAN);

    public enum Phase {
        UNDERGROUND, GROUND, SKY
    }

    // --- 配置 ---
    private final int segmentCount = 80;
    private final float segmentInterval = 3.5f;
    private final float turnSpeedBase = 3.0f;
    private final float moveSpeedBase = 0.6f;

    // 高度阈值
//    private static final int Y_LEVEL_DEEP = 50;
//    private static final int Y_LEVEL_SKY = 120;
    private static final int Y_LEVEL_DEEP = 0;
    private static final int Y_LEVEL_SKY = 10;

    // --- 运行时 ---
    public List<DestroyerSegment> segments = new CopyOnWriteArrayList<>();
    private boolean genSegments = true;
    private boolean isInsideBlock = false;
    private int phaseTimer = 0;

    // 渲染平滑插值字段
    public float prevBodyRoll;

    // 地面攻击状态机: 0=追踪, 1=蓄力, 2=冲刺(重力开启), 3=冷却
    private int groundAttackState = 0;

    // 激光控制
    private int laserSequenceIndex = -1;
    private int laserTick = 0;
    private int volleyCooldown = 0; // 齐射冷却

    public Destroyer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 500;
        this.setHealth(this.getMaxHealth());
    }

    public Destroyer(Level level) {
        this(TEBossEntities.DESTROYER.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_TEXTURE_VARIANT, 0);
        builder.define(DATA_BODY_ROLL, 0f);
        builder.define(DATA_HEAD_SHELL_OPEN, false);
    }

    // --- Getters / Setters ---
    public Phase getPhase() {
        return Phase.values()[Mth.clamp(entityData.get(DATA_PHASE), 0, 2)];
    }

    public void setPhase(Phase phase) {
        entityData.set(DATA_PHASE, phase.ordinal());
        if (phase == Phase.GROUND) {
            this.noPhysics = false; // 允许物理计算
        } else {
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        // 动画重置
        if (phase == Phase.UNDERGROUND) setHeadShellOpen(false);
        else if (phase == Phase.SKY) setHeadShellOpen(true);
    }

    public float getBodyRoll() { return entityData.get(DATA_BODY_ROLL); }
    public void setBodyRoll(float roll) { entityData.set(DATA_BODY_ROLL, roll); }
    public void setHeadShellOpen(boolean open) { entityData.set(DATA_HEAD_SHELL_OPEN, open); }

    // --- 核心逻辑 ---

    private void genSegments() {
        if (!level().isClientSide) {
            segments.clear();
            Vec3 dir = this.getForward().normalize().scale(-segmentInterval);
            Vec3 lastPos = position();
            LivingEntity lastEntity = this;

            for (int i = 0; i < segmentCount; i++) {
                lastPos = lastPos.add(dir);
                DestroyerSegment seg = new DestroyerSegment(this, level());
                seg.setPos(lastPos);
                seg.setHead(this);
                seg.setPrevSegment(lastEntity);

                // 设置尾部
                if (i == segmentCount - 1) seg.setTail(true);

                // 每隔 3 节设置为探针体节 (Probe Segment)
                // 这些体节有红灯，能射激光，能放探针
                if (i % 3 == 0) {
                    seg.setProbeSegment(true);
                }

                level().addFreshEntity(seg);
                segments.add(seg);
                lastEntity = seg;
            }
            Boss.sendBossSpawnMessage(this);
        }
    }

    @Override
    public void tick() {
        // 记录上一帧的 Roll，用于渲染器插值
        this.prevBodyRoll = this.getBodyRoll();
        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (genSegments) {
            genSegments();
            genSegments = false;
            if (!level().isClientSide) {
                final TargetingConditions attackTargeting = TargetingConditions.forNonCombat().range(128.0);
                level().getNearbyPlayers(attackTargeting, this, this.getBoundingBox().inflate(200))
                        .forEach(p -> bossEvent.addPlayer((ServerPlayer) p));
                bossEvent.setProgress(1.0f);
            }
        }

        if (level().isClientSide) return;

        tickPhaseLogic();
        checkBlockCollisionEffects();
        tickLaserControl();

        // 阶段转换检测 (血量 < 50%)
        if (this.getHealth() / this.getMaxHealth() < 0.5f) {
            if (entityData.get(DATA_TEXTURE_VARIANT) == 0) {
                entityData.set(DATA_TEXTURE_VARIANT, 1);
                CameraShakeManager.addCameraShake(new CameraShakeData(40, this.position(), 50));
                this.playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 2.0f, 0.5f); // 破甲音效
            }
        }

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    private void tickPhaseLogic() {
        LivingEntity target = getTarget();
        phaseTimer++;

        // 默认逻辑
        if (target == null) {
            if (getPhase() != Phase.GROUND) setPhase(Phase.GROUND);
            tickGroundMode(null);
            return;
        }

        double targetY = target.getY();
        Phase currentPhase = getPhase();
        Phase targetPhase;

        // 根据高度决定目标阶段
        if (targetY < Y_LEVEL_DEEP) targetPhase = Phase.UNDERGROUND;
        else if (targetY > Y_LEVEL_SKY) targetPhase = Phase.SKY;
        else targetPhase = Phase.GROUND;

        // 阶段切换处理
        if (currentPhase != targetPhase) {
            setPhase(targetPhase);
            phaseTimer = 0;
            groundAttackState = 0;
        }

        switch (currentPhase) {
            case UNDERGROUND -> {
                // 钻头旋转，冲撞
                float currentRoll = getBodyRoll();
                setBodyRoll((currentRoll + 15.0f) % 360.0f);
                if (target != null) {
                    this.lookAt(target, turnSpeedBase, 80);
                    this.move(MoverType.SELF, this.getForward().scale(moveSpeedBase * 1.2));
                }
            }
            case GROUND -> {
                // 平滑归正
                smoothResetRoll();
                tickGroundMode(target);
            }
            case SKY -> {
                // 飞行，缓慢旋转
                setBodyRoll(getBodyRoll() + 2.0f);
                if (target != null) {
                    Vec3 targetPos = target.position().add(0, 25, 0);
                    // 使用原版 LookAt
                    this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetPos);
                    this.move(MoverType.SELF, this.getForward().scale(moveSpeedBase * 1.5));
                }
            }
        }
    }

    private void tickGroundMode(LivingEntity target) {
        if (target == null) return;

        switch (groundAttackState) {
            case 0 -> { // 潜伏
                this.noPhysics = true;
                this.setNoGravity(true);
                double targetY = Math.min(target.getY() - 15, level().getMinBuildHeight() + 20);
                Vec3 dest = new Vec3(target.getX(), targetY, target.getZ());

                Vec3 dir = dest.subtract(this.position()).normalize();
                this.move(MoverType.SELF, dir.scale(moveSpeedBase * 2.0));
                this.lookAt(target, 20, 20);

                if (distanceToSqr(dest) < 100) {
                    groundAttackState = 1;
                    phaseTimer = 0;
                }
            }
            case 1 -> { // 蓄力
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTimer++ > 30) {
                    groundAttackState = 2;
                    // 开启物理重力
                    this.noPhysics = false;
                    this.setNoGravity(false);

                    Vec3 horiz = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                    Vec3 jump = horiz.scale(1.5).add(0, 2.5, 0);
                    this.setDeltaMovement(jump);
                    setBodyRoll(0);
                    this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 0.5f);
                }
            }
            case 2 -> { // 腾空 (抛物线)
                if (this.getDeltaMovement().y > 0) setBodyRoll(getBodyRoll() + 15f);
                else smoothResetRoll();

                if (this.onGround() && this.getDeltaMovement().y <= 0) {
                    groundAttackState = 3;
                    phaseTimer = 0;
                    CameraShakeManager.addCameraShake(new CameraShakeData(30, this.position(), 40));
                    this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 1.0f);
                }
            }
            case 3 -> { // 冷却
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTimer++ > 60) groundAttackState = 0;
            }
        }
    }

    private void tickLaserControl() {
        if (getPhase() == Phase.UNDERGROUND) return;

        LivingEntity target = getTarget();
        if (target == null) return;

        // --- 齐射模式 (天空模式专属) ---
        if (getPhase() == Phase.SKY) {
            if (volleyCooldown > 0) volleyCooldown--;

            // 随机触发齐射 (5%概率，且冷却完毕)
            if (volleyCooldown <= 0 && random.nextInt(100) < 5) {
                // 所有探针体节尝试发射
                for (DestroyerSegment seg : segments) {
                    if (seg.isAlive()) seg.tryShootLaser(target);
                }
                this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0f, 1.0f); // 轰鸣声
                volleyCooldown = 100; // 5秒冷却
                return; // 齐射回合不进行顺序发射
            }
        }

        // --- 顺序发射模式 ---
        if (laserSequenceIndex >= 0) {
            if (laserTick++ % 2 == 0) {
                if (laserSequenceIndex < segments.size()) {
                    DestroyerSegment seg = segments.get(laserSequenceIndex);
                    if (seg != null && seg.isAlive()) {
                        seg.tryShootLaser(target);
                    }
                    laserSequenceIndex++;
                } else {
                    laserSequenceIndex = -1;
                    laserTick = 0;
                }
            }
        } else {
            // 随机开始新一轮顺序射击
            if (random.nextInt(100) == 0) {
                laserSequenceIndex = 0;
            }
        }
    }

    private void checkBlockCollisionEffects() {
        BlockPos pos = this.blockPosition();
        BlockState state = level().getBlockState(pos);
        boolean currentlyInBlock = !state.isAir() && state.isRedstoneConductor(level(), pos);

        if (currentlyInBlock != isInsideBlock) {
            float radius = currentlyInBlock ? 7.0f : 14.0f;
            int duration = currentlyInBlock ? 10 : 20;
            CameraShakeManager.addCameraShake(new CameraShakeData(duration, this.position(), radius));
            float volume = currentlyInBlock ? 0.5f : 1.5f;
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), volume, 1.0f);
            isInsideBlock = currentlyInBlock;
        }

        if (currentlyInBlock && level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    this.getX(), this.getY(), this.getZ(),
                    5, 1.0, 1.0, 1.0, 0.1);
        }
    }

    private void smoothResetRoll() {
        float r = Mth.wrapDegrees(getBodyRoll());
        if (Math.abs(r) > 2) setBodyRoll(r * 0.8f);
        else setBodyRoll(0);
    }

    @Override
    public boolean isNoGravity() {
        if (getPhase() == Phase.GROUND && groundAttackState == 2) return false;
        return true;
    }

    // --- 基础重写 ---
    @Override public boolean shouldShowBossBar() { return true; }
    @Override protected BossEvent.BossBarColor getBossBarColor() { return BossEvent.BossBarColor.RED; }
    @Override public boolean isInvulnerableTo(DamageSource s) { return super.isInvulnerableTo(s) || s.is(DamageTypes.IN_WALL) || s.is(DamageTypes.FALL); }
    @Override public void addSkills() {}
    @Override public void die(DamageSource damageSource) {
        if (!CommonHooks.onLivingDeath(this, damageSource)) {
            for (DestroyerSegment seg : segments) if (seg != null) seg.discard();
            this.bossEvent.removeAllPlayers();
            super.die(damageSource);
        }
    }
    @Override public void onRemovedFromLevel() {
        this.bossEvent.removeAllPlayers();
        for (DestroyerSegment seg : segments) if (seg != null) seg.discard();
        super.onRemovedFromLevel();
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); this.bossEvent.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); this.bossEvent.removePlayer(p); }
}
