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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.utils.CameraShakeData;
import org.confluence.terraentity.utils.CameraShakeManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 毁灭者 (The Destroyer)
 * 核心逻辑：头部控制整体AI阶段，负责同步旋转角度(Roll)给体节，并管理激光发射序列。
 */
public class Destroyer extends AbstractTerraBossBase implements Boss {
//    private static final int Y_LEVEL_DEEP = 50;   // 低于50层进入地下模式
//    private static final int Y_LEVEL_SKY = 120;   // 高于120层进入天空模式
    private static final int Y_LEVEL_DEEP = 0;   // 低于50层进入地下模式
    private static final int Y_LEVEL_SKY = 10;   // 高于120层进入天空模式

    // --- 同步数据定义 ---
    // 0: Underground (螺旋冲撞), 1: Ground (抛物线跳跃), 2: Sky (飞行/火焰)
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.INT);
    // 0: 初始白漆, 1: 破损神圣金属
    public static final EntityDataAccessor<Integer> DATA_TEXTURE_VARIANT = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.INT);
    // 身体滚转角 (Z轴旋转)，用于实现DNA螺旋效果
    public static final EntityDataAccessor<Float> DATA_BODY_ROLL = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.FLOAT);
    // 头甲是否打开
    public static final EntityDataAccessor<Boolean> DATA_HEAD_SHELL_OPEN = SynchedEntityData.defineId(Destroyer.class, EntityDataSerializers.BOOLEAN);

    public enum Phase {
        UNDERGROUND, GROUND, SKY
    }

    // --- 配置参数 ---
    private final int segmentCount = 80; // 体节数量
    private final float segmentInterval = 3.5f; // 体节间距
    private final float turnSpeedBase = 3.0f;
    private final float moveSpeedBase = 0.6f;

    // --- 运行时变量 ---
    // 使用 CopyOnWriteArrayList 防止并发修改异常
    public List<DestroyerSegment> segments = new CopyOnWriteArrayList<>();
    private boolean genSegments = true;
    private boolean isInsideBlock = false;
    private int phaseTimer = 0;

    // 地面模式的状态机: 0=追踪, 1=蓄力, 2=冲刺腾空(开启重力), 3=落地冷却
    private int groundAttackState = 0;

    // 激光控制
    private int laserSequenceIndex = -1;
    private int laserTick = 0;
    private int probeSpawnTimer = 0;

    public Destroyer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true; // 默认无重力，Ground模式动态切换
        this.xpReward = 500;
        // 初始属性设置，建议通过 AttributeSupplier 设置，这里作为示例
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

    // --- Getter/Setter ---
    public Phase getPhase() {
        return Phase.values()[Mth.clamp(entityData.get(DATA_PHASE), 0, 2)];
    }

    public void setPhase(Phase phase) {
        entityData.set(DATA_PHASE, phase.ordinal());
        // 阶段切换时的初始化
        if (phase == Phase.GROUND) {
            this.noPhysics = false; // 允许物理计算以便进行抛物线运动
        } else {
            this.noPhysics = true;
            this.setNoGravity(true);
        }

        // 动画状态重置
        if (phase == Phase.UNDERGROUND) {
            setHeadShellOpen(false);
        } else if (phase == Phase.SKY) {
            setHeadShellOpen(true);
        }
    }

    public float getBodyRoll() {
        return entityData.get(DATA_BODY_ROLL);
    }

    public void setBodyRoll(float roll) {
        entityData.set(DATA_BODY_ROLL, roll);
    }

    public void setHeadShellOpen(boolean open) {
        entityData.set(DATA_HEAD_SHELL_OPEN, open);
    }

    // --- 体节生成 ---
    private void genSegments() {
        if (!level().isClientSide) {
            segments.clear();
            Vec3 dir = this.getForward().normalize().scale(-segmentInterval);
            Vec3 lastPos = position();
            LivingEntity lastEntity = this;

            for (int i = 0; i < segmentCount; i++) {
                // 计算生成位置
                lastPos = lastPos.add(dir);

                DestroyerSegment seg = new DestroyerSegment(this, level());
                seg.setPos(lastPos);
                seg.setHead(this);
                seg.setPrevSegment(lastEntity); // 链表式连接

                if (i == segmentCount - 1) {
                    seg.setTail(true);
                }

                level().addFreshEntity(seg);
                segments.add(seg);

                lastEntity = seg;
            }
            Boss.sendBossSpawnMessage(this);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // 1. 初始化逻辑
        if (genSegments) {
            genSegments();
            genSegments = false;
            // 初始化Boss条
            if (!level().isClientSide) {
                final TargetingConditions attackTargeting = TargetingConditions.forNonCombat().range(128.0);
                level().getNearbyPlayers(attackTargeting, this, this.getBoundingBox().inflate(200))
                        .forEach(p -> bossEvent.addPlayer((ServerPlayer) p));
                bossEvent.setProgress(1.0f);
            }
        }

        if (level().isClientSide) return;

        // 2. 状态机逻辑
        tickPhaseLogic();

        // 3. 物理与环境检测 (震屏与破坏方块)
        checkBlockCollisionEffects();

        // 4. 激光控制 (顺序发射逻辑)
        tickLaserControl();

        // 5. 探针生成逻辑
        tickProbeSpawning();

        // 6. 纹理/形态切换
        if (this.getHealth() / this.getMaxHealth() < 0.5f) {
            if (entityData.get(DATA_TEXTURE_VARIANT) == 0) {
                entityData.set(DATA_TEXTURE_VARIANT, 1);
                // 播放一次转换音效或粒子
                CameraShakeManager.addCameraShake(new CameraShakeData(40, this.position(), 50));
            }
        }

        // 更新 Boss条
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    private void tickPhaseLogic() {
        LivingEntity target = getTarget();
        phaseTimer++;

        // 如果没有目标，默认在地面盘旋
        if (target == null) {
            if (getPhase() != Phase.GROUND) setPhase(Phase.GROUND);
            tickGroundMode(null);
            return;
        }

        double targetY = target.getY();
        Phase currentPhase = getPhase();
        Phase targetPhase;

        // 1. 根据玩家高度决定目标阶段
        if (targetY < Y_LEVEL_DEEP) {
            targetPhase = Phase.UNDERGROUND;
        } else if (targetY > Y_LEVEL_SKY) {
            targetPhase = Phase.SKY;
        } else {
            targetPhase = Phase.GROUND;
        }

        // 2. 切换阶段逻辑 (防止每一帧都在切换，加一点缓冲或者直接切换)
        // 这里直接切换，反应最快
        if (currentPhase != targetPhase) {
            setPhase(targetPhase);
            phaseTimer = 0; // 重置计时器，用于新阶段的逻辑
            groundAttackState = 0; // 重置地面攻击状态
            // 重置一些通用状态
            setHeadShellOpen(targetPhase == Phase.SKY);
        }

        // 3. 执行各阶段逻辑
        switch (targetPhase) {
            case UNDERGROUND -> {
                // 地下模式：像钻头一样旋转，尝试从下方突袭
                float currentRoll = getBodyRoll();
                setBodyRoll((currentRoll + 15.0f) % 360.0f); // 转快点
                setHeadShellOpen(false);

                // 简单的追踪逻辑：看向目标
                // 修正：地下模式不需要头部一直盯着玩家，可以像泰拉瑞亚一样尝试穿过玩家
                this.lookAt(target, turnSpeedBase, 80);
                this.move(MoverType.SELF, this.getForward().scale(moveSpeedBase * 1.2));
            }
            case GROUND -> {
                // 地面模式：保持原有的盘旋和冲刺逻辑
                // BodyRoll 已经在 tickGroundMode 里可能被修改，这里为了平滑可以缓慢归零
                float currentRoll = getBodyRoll();
                if (currentRoll > 0) setBodyRoll(currentRoll * 0.9f); // 慢慢恢复水平
                tickGroundMode(target);
            }
            case SKY -> {
                // 天空模式：打开头部外壳，高空飞行
                setHeadShellOpen(true);

                // 缓慢旋转身体
                setBodyRoll(getBodyRoll() + 2.0f);

                // 尝试飞到玩家上方
                Vec3 targetPos = target.getEyePosition();

                // 使用你修正后的渲染相关的 lookAt 逻辑
                this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetPos);

                Vec3 moveDir = this.getForward().scale(moveSpeedBase * 1.5);
                this.move(MoverType.SELF, moveDir);
            }
        }
    }

    private void tickGroundMode(LivingEntity target) {
        if (target == null) return;

        switch (groundAttackState) {
            case 0 -> { // 潜伏追踪
                this.noPhysics = true;
                this.setNoGravity(true);

                // 目标点：玩家正下方深处
                double targetY = Math.min(target.getY() - 15, level().getMinBuildHeight() + 20);
                Vec3 dest = new Vec3(target.getX(), targetY, target.getZ());

                Vec3 dir = dest.subtract(this.position()).normalize();
                this.move(MoverType.SELF, dir.scale(moveSpeedBase * 2.0));

                // 头部看向玩家，准备冲刺
                this.lookAt(target, 20, 20);

                // 归正 Roll
                smoothResetRoll();

                if (distanceToSqr(dest) < 100) {
                    groundAttackState = 1;
                    phaseTimer = 0;
                }
            }
            case 1 -> { // 蓄力
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTimer++ > 30) {
                    groundAttackState = 2;
                    // 冲刺：开启物理，关闭无重力
                    this.noPhysics = false;
                    this.setNoGravity(false);

                    // 计算抛物线初速度
                    Vec3 horizontalDir = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                    // 给予一个巨大的向上冲量 + 水平冲量
                    Vec3 jumpVel = horizontalDir.scale(1.5).add(0, 2.5, 0);
                    this.setDeltaMovement(jumpVel);

                    // 冲出时重置Roll，随后在空中旋转
                    setBodyRoll(0);

                    // 音效
                    this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 0.5f);
                }
            }
            case 2 -> { // 腾空 (抛物线)
                // 原版物理引擎接管运动

                // 在空中根据垂直速度调整 Roll，模拟翻滚
                if (this.getDeltaMovement().y > 0) {
                    setBodyRoll(getBodyRoll() + 15f); // 上升旋转
                } else {
                    smoothResetRoll(); // 下落归正
                }

                // 检测落地
                if (this.onGround() && this.getDeltaMovement().y <= 0) {
                    groundAttackState = 3;
                    phaseTimer = 0;
                    // 落地剧烈震屏
                    CameraShakeManager.addCameraShake(new CameraShakeData(30, this.position(), 40));
                    this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 1.0f);
                }
            }
            case 3 -> { // 落地冷却
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTimer++ > 60) {
                    groundAttackState = 0; // 回到潜伏
                }
            }
        }
    }

    private void smoothResetRoll() {
        float r = getBodyRoll();
        r = Mth.wrapDegrees(r);
        if (Math.abs(r) > 2) {
            setBodyRoll(r * 0.8f);
        } else {
            setBodyRoll(0);
        }
    }

    private void tickLaserControl() {
        // 逻辑：如果处于 sequence 模式，依次命令体节发射
        if (laserSequenceIndex >= 0) {
            if (laserTick++ % 2 == 0) { // 每2tick发射一节
                if (laserSequenceIndex < segments.size()) {
                    DestroyerSegment seg = segments.get(laserSequenceIndex);
                    if (seg != null && seg.isAlive()) {
                        seg.tryShootLaser(getTarget());
                    }
                    laserSequenceIndex++;
                } else {
                    laserSequenceIndex = -1; // 序列结束
                    laserTick = 0;
                }
            }
        } else {
            if (random.nextInt(100) == 0 && getTarget() != null) {
                laserSequenceIndex = 0;
            }
        }
    }

    private void tickProbeSpawning() {
        if (getPhase() == Phase.UNDERGROUND) return;

        probeSpawnTimer++;
        if (probeSpawnTimer > 200 && level() instanceof ServerLevel) { // 每10秒检查
            probeSpawnTimer = 0;
            // 按血量比例生成，血量越低生成越频繁的逻辑可在此处细化
            if (random.nextFloat() > (this.getHealth() / this.getMaxHealth())) {
                // 假设存在 PROBE 实体
                // Entity probe = TEBossEntities.DESTROYER_PROBE.get().create(level());
                // probe.setPos(this.position().add(0, 5, 0));
                // level().addFreshEntity(probe);
            }
        }
    }

    private void checkBlockCollisionEffects() {
        BlockPos pos = this.blockPosition();
        BlockState state = level().getBlockState(pos);
        // 判断当前是否在固体方块内
        boolean currentlyInBlock = !state.isAir() && state.isRedstoneConductor(level(), pos);

        // 状态边缘检测 (Enter <-> Exit)
        if (currentlyInBlock != isInsideBlock) {

            float radius = currentlyInBlock ? 7.0f : 14.0f; // 离开方块(冲出)范围更大
            int duration = currentlyInBlock ? 10 : 20; // 半秒 vs 一秒

            CameraShakeManager.addCameraShake(new CameraShakeData(duration, this.position(), radius));

            // 音效
            float volume = currentlyInBlock ? 0.5f : 1.5f;
            this.playSound(SoundEvents.GENERIC_EXPLODE.value(), volume, 1.0f); // 使用原版音效代替自定义破坏音效

            isInsideBlock = currentlyInBlock;
        }

        // 持续在方块内时产生粒子
        if (currentlyInBlock && level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    this.getX(), this.getY(), this.getZ(),
                    5, 1.0, 1.0, 1.0, 0.1);
        }
    }

    @Override
    public boolean isNoGravity() {
        // 在 Ground Phase 的 Jump 状态下 (State 2)，我们需要重力
        if (getPhase() == Phase.GROUND && groundAttackState == 2) {
            return false;
        }
        return true;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (!CommonHooks.onLivingDeath(this, damageSource)) {
            // 清理所有体节
            for (DestroyerSegment seg : segments) {
                if (seg != null && seg.isAlive()) {
                    // 可以在这里生成额外的掉落物或爆炸粒子
                    seg.discard();
                }
            }
            this.bossEvent.removeAllPlayers();

            // 生成总掉落物
            super.die(damageSource);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        this.bossEvent.removeAllPlayers();
        // 防止体节残留
        for (DestroyerSegment seg : segments) {
            if (seg != null) seg.discard();
        }
        super.onRemovedFromLevel();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean shouldShowBossBar() {
        return true;
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.RED;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL);
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        if (!super.canAttack(entity)) return false;
        return !(entity instanceof Destroyer || entity instanceof DestroyerSegment || entity instanceof DestroyerProbe);
    }

    @Override
    public void addSkills() {}

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }
}
