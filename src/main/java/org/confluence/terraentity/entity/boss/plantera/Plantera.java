package org.confluence.terraentity.entity.boss.plantera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.config.ServerConfig;
import org.confluence.terraentity.data.codec.TECodecs;
import org.confluence.terraentity.data.mappeddata.BossSkillMapDatas;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.proj.SeedProjectile;
import org.confluence.terraentity.entity.proj.SpikeBallProjectile;
import org.confluence.terraentity.init.TESounds;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.confluence.terraentity.network.s2c.SyncBossEventHealthPacket;
import org.confluence.terraentity.registries.mappeddata.MappedDataTypes;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 世花本体
 */
public class Plantera extends AbstractTerraBossBase implements GeoEntity, Boss {
    // 数组 - 0：一阶段 1：二阶段 2：狂暴
    // 世界之花钩 - 射程范围和移动速度
    private float hookRange;
    private float hookSpeed;
    // 本体的移动速度
    private float[] moveSpeed;
    private float[] acceleration;
    // 弹幕的发射间隔和速度
    private int[] spikeBallInterval;
    private float spikeBallSpeed;
    private float spikeBallDamage;
    private int[] seedInterval;
    private float seedSpeed;
    private float seedDamage;
    // 二阶段爆触手的数量
    private int tentacleCount;

    protected ArrayList<PlanteraHook> hooks = new ArrayList<>();
    protected ArrayList<PlanteraTentacle> tentacles = null;

    private int indexAI = 0;
    private int enragedCounter = 0; // 狂暴剩余时长
    public static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(Plantera.class, EntityDataSerializers.INT);
    SkillParams skillParams;
    public Plantera(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        setDiscardFriction(true);
        if (ServerConfig.bossNoPhysics()) {
            noPhysics = true;
        }

        collisionProperties = new CollisionProperties(1,1,0.5f);
        this.skillParams = MappedDataTypes.BOSS_SKILL_MAP_DATAS.get().getData(BossSkillMapDatas.PLANTERA_PARAMS);
        this.xpReward = skillParams.xpReward;
        this.hookRange = this.difficultSelector.switchBy(skillParams.hookRange);
        this.hookSpeed = this.difficultSelector.switchBy(skillParams.hookSpeed);
        this.moveSpeed = new float[]{
                this.difficultSelector.switchBy(skillParams.moveSpeed, 0),
                this.difficultSelector.switchBy(skillParams.moveSpeed, 4),
                this.difficultSelector.switchBy(skillParams.moveSpeed, 8),
        };
        this.acceleration = new float[]{
                this.difficultSelector.switchBy(skillParams.acceleration, 0),
                this.difficultSelector.switchBy(skillParams.acceleration, 4),
                this.difficultSelector.switchBy(skillParams.acceleration, 8),
        };
        this.spikeBallInterval = new int[]{
                this.difficultSelector.switchBy(skillParams.spikeBallInterval, 0),
                this.difficultSelector.switchBy(skillParams.spikeBallInterval, 4),
                this.difficultSelector.switchBy(skillParams.spikeBallInterval, 8),
        };
        this.spikeBallSpeed = this.difficultSelector.switchBy(skillParams.spikeBallSpeed);
        this.spikeBallDamage = this.difficultSelector.switchBy(skillParams.spikeBallDamage);
        this.seedInterval = new int[]{
                this.difficultSelector.switchBy(skillParams.seedInterval, 0),
                this.difficultSelector.switchBy(skillParams.seedInterval, 4),
                this.difficultSelector.switchBy(skillParams.seedInterval, 8),
        };
        this.seedSpeed = this.difficultSelector.switchBy(skillParams.seedSpeed);
        this.seedDamage = this.difficultSelector.switchBy(skillParams.seedDamage);
        this.tentacleCount = this.difficultSelector.switchBy(skillParams.tentacleCount);

    }

    public record SkillParams(int xpReward,
                              List<Float> hookRange, List<Float> hookSpeed,
                              List<Float> moveSpeed, List<Float> acceleration,
                              List<Integer> spikeBallInterval, List<Float> spikeBallSpeed, List<Float> spikeBallDamage,
                              List<Integer> seedInterval, List<Float> seedSpeed, List<Float> seedDamage,
                              List<Integer> tentacleCount

    ){
        public static Codec<SkillParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("xp_reward").forGetter(SkillParams::xpReward),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("hook_range").forGetter(SkillParams::hookRange),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("hook_speed").forGetter(SkillParams::hookSpeed),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("move_speed").forGetter(SkillParams::moveSpeed),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("acceleration").forGetter(SkillParams::acceleration),
                TECodecs.INT_LIST_CODEC.fieldOf("spike_ball_interval").forGetter(SkillParams::spikeBallInterval),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("spike_ball_speed").forGetter(SkillParams::spikeBallSpeed),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("spike_ball_damage").forGetter(SkillParams::spikeBallDamage),
                TECodecs.INT_LIST_CODEC.fieldOf("seed_interval").forGetter(SkillParams::seedInterval),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("seed_speed").forGetter(SkillParams::seedSpeed),
                TECodecs.FLOAT_LIST_CODEC.fieldOf("seed_damage").forGetter(SkillParams::seedDamage),
                TECodecs.INT_LIST_CODEC.fieldOf("tentacle_count").forGetter(SkillParams::tentacleCount)
        ).apply(instance, SkillParams::new));

        public static SkillParams getDefaultParams(){
            return new SkillParams(2000,
                    // 钩子距离和速度
                    List.of(32f, 32f, 32f, 32f),
                    List.of(2f, 2f, 2f, 2f),
                    // 移速[]和加速度[]; → 囊度, ↓ 阶段
                    List.of(0.2f, 0.2f, 0.2f, 0.2f,
                            0.2f, 0.2f, 0.2f, 0.2f,
                            0.2f, 0.2f, 0.2f, 0.2f),
                    List.of(0.1f, 0.1f, 0.1f, 0.1f,
                            0.1f, 0.1f, 0.1f, 0.1f,
                            0.1f, 0.1f, 0.1f, 0.1f),
                    // 刺球攻击间隔[]，弹幕速度，伤害
                    List.of(8, 8, 8, 8,
                            6, 6, 6, 6,
                            3, 3, 3, 3),
                    List.of(0.85f, 0.85f, 0.85f, 0.85f),
                    List.of(11.4f, 11.4f, 11.4f, 11.4f),
                    // 种子攻击间隔[]，弹幕速度，伤害
                    List.of(5, 5, 5, 5,
                            3, 3, 3, 3,
                            2, 2, 2, 2),
                    List.of(2.5f, 2.5f, 2.5f, 2.5f),
                    List.of(5.14f, 5.14f, 5.14f, 5.14f),
                    // 触手数量
                    List.of(25, 25, 25, 25)
            );
        }
    }

    // 考虑狂暴和进度后得出数值array中的索引
    public int getPhaseIndex() {
        if (enragedCounter > 0) return 2;
        // 使用tentacles判定是否已经进入二阶段
        return tentacles == null ? 0 : 1;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void addSkills() {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return TESounds.DRIPPLER_HURT.get();
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (pSource.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        return super.hurt(pSource, pAmount); // confluence mixin here
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TESounds.TR_ZOMBIE_DEATH.get();
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new MoveGoal());
        targetSelector.addGoal(1, new HookGoal());
        targetSelector.addGoal(1, new ProjectileGoal());
        targetSelector.addGoal(1, new TentacleGoal());

        this.registerRandomStrollGoal();

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
    }

    @Override
    public void tick() {
        boolean server = false;
        if (level() instanceof ServerLevel) {
            server = true;
        }

        super.tick();

        if (server) {
            indexAI ++;
            enragedCounter --;
        }
    }

    @Override
    public float[] getBossEventProgress(){
        float value = getHealth();
        float getMax = getMaxHealth();
        if (tentacles == null) {
            value += (float) (tentacleCount * PlanteraTentacle.MAX_HEALTH);
            getMax += (float) (tentacleCount * PlanteraTentacle.MAX_HEALTH);
        }
        else {
            for (PlanteraTentacle tentacle : tentacles) {
                value += tentacle.getHealth();
                getMax += tentacle.getMaxHealth();
            }
        }
        PacketDistributor.sendToAllPlayers(new SyncBossEventHealthPacket(bossEvent.getId(), value, getMax));
        return new float[]{value , getMax};
    }



    @Override
    public boolean canAttack(LivingEntity entity) {
        if (!super.canAttack(entity)) return false;
        return !(entity instanceof Plantera);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, @NotNull BlockState state, @NotNull BlockPos pos) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldDoCollision() {
        return super.shouldDoCollision();
    }

    @Override
    public void firstSpawn() {
        if (!level().isClientSide) {
            this.playSound(TESounds.ROAR.get());
        }
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor(){
        return BossEvent.BossBarColor.GREEN;
    };

    @Override
    protected boolean shouldOverPlayer(){
        return false;
    }

    protected void enrage() {
        if (this.enragedCounter <= 0) {
            playSound(TESounds.ROAR.get());
        }
        this.enragedCounter = 200;
    }

    /*
     * GOALS
     */

    public class MoveGoal extends Goal {

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            if (getTarget() == null){
                return;
            }

            float spd = moveSpeed[getPhaseIndex()];
            float accMag = acceleration[getPhaseIndex()];
            // 向玩家奔去
            Vec3 acc = getTarget().position().subtract(position());
            acc = acc.normalize().scale( accMag );
            // 触手最远距离
            for (PlanteraHook hook : hooks) {
                if (! hook.grabbed) continue;

                Vec3 offset = hook.position().subtract(position());
                double lenSqr = offset.lengthSqr();
                if (lenSqr > hookRange * hookRange) {
                    double len = Math.sqrt(lenSqr);
                    // 在5格时获得与acc相等的拉力，且拉力随距离增长
                    offset = offset.scale( (len - hookRange) * accMag / (len * 25) );
                    acc = acc.add(offset);
                }
            }
            // 更新速度
            Vec3 vel = getDeltaMovement().add(acc);
            if (vel.lengthSqr() > spd * spd ) {
                vel = vel.normalize().scale( spd );
            }
            setDeltaMovement(vel);

            lookAt(90);
        }
    }

    public class HookGoal extends Goal {
        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private BlockPos getHookPos() {
            Vec3 axis = getTarget().position().subtract(position());
            Vec3 dir = TEUtils.rotToDir(TEUtils.dirToRot(axis)[0] + 45, 0).scale( hookRange );
            Quaterniond rotator = new Quaterniond().fromAxisAngleDeg(
                    axis.x, axis.y, axis.z, 20 * Math.signum(random.nextFloat()));
            // 初始化方向
            Vector3d dir3d = new Vector3d(dir.x, dir.y, dir.z);
            Quaterniond initRotator = new Quaterniond().fromAxisAngleDeg(
                    axis.x, axis.y, axis.z, random.nextFloat() * 360);
            initRotator.transform(dir3d, dir3d);

            for (int i = 0; i < 18; i ++) {
                BlockHitResult result = level().isBlockInLine(
                        new ClipBlockStateContext(position(), position().add(dir3d.x(), dir3d.y(), dir3d.z()),
                                BlockBehaviour.BlockStateBase::canOcclude) );
                if (result.getType() == HitResult.Type.BLOCK) {
                    return result.getBlockPos();
                }
                rotator.transform(dir3d, dir3d);
            }

            // 找不到
            return null;
        }

        @Override
        public void tick() {
            if (getTarget() == null){
                return;
            }

            // 如果玩家离得太远直接红温
            if (getTarget().position().distanceToSqr(position()) > hookRange * hookRange) {
                enrage();
            }

            // 清理消失的钩子
            for (int idx = 0; idx < hooks.size(); idx ++) {
                if (hooks.get(idx).isAlive()) continue;
                hooks.remove(idx);
                idx --;
            }

            // 发射钩子
            if (indexAI % 50 == 0) {
                System.out.println("Hook attempt; " + hooks.size());
                if (hooks.size() < 3) {
                    BlockPos hookPos = getHookPos();
                    // 抓不到方块直接红温
                    if (hookPos == null) {
                        enrage();
                    }
                    else {
                        PlanteraHook hook = TEUtils.spawnEntity(
                                ()->new PlanteraHook(TEBossEntities.PLANTERA_HOOK.get(), level(), Plantera.this, hookPos, hookSpeed),
                                (ServerLevel)level(), position());
                        hooks.add(hook);
                    }
                }
            }
            // 收回钩子
            else if (indexAI % 50 == 25) {
                double maxDistSqr = 0;
                PlanteraHook hookToRemove = null;
                int hooksGrabbed = 0;
                for (PlanteraHook hook : hooks) {
                    if (! hook.isAlive()) continue;
                    if (! hook.grabbed) continue;
                    hooksGrabbed ++;
                    double distSqr = hook.position().distanceToSqr(getTarget().position());
                    if (distSqr > maxDistSqr) {
                        maxDistSqr = distSqr;
                        hookToRemove = hook;
                    }
                }
                // 收回钩子
                if (hooksGrabbed >= 3 && hookToRemove != null) {
                    hookToRemove.retract();
                }
            }
        }
    }

    public class ProjectileGoal extends Goal {
        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (getTarget() == null){
                return;
            }

            // 防止阴险的零帧起手
            if (indexAI < 100) return;

            // 种子
            if (indexAI % seedInterval[getPhaseIndex()] == 0) {
                Vec3 velocity = getTarget().position().subtract(position()).normalize().scale(seedSpeed);
                SeedProjectile seed = new SeedProjectile(TEProjectileEntities.SEED.get(), level(), getTarget());
                seed.addDamage(seedDamage);
                seed.setPos(position());
                seed.setOwner(Plantera.this);
                seed.shoot(velocity.x, velocity.y, velocity.z, seedSpeed, 0f);
                level().addFreshEntity(seed);
            }

            // 刺球
            if (indexAI % spikeBallInterval[getPhaseIndex()] == 0) {
                Vec3 velocity = getTarget().position().subtract(position()).normalize().scale(spikeBallSpeed);
                SpikeBallProjectile spikeBall = new SpikeBallProjectile(TEProjectileEntities.SPIKE_BALL.get(), level(), getTarget());
                spikeBall.addDamage(spikeBallDamage);
                spikeBall.setPos(position());
                spikeBall.setOwner(Plantera.this);
                spikeBall.shoot(velocity.x, velocity.y, velocity.z, spikeBallSpeed, 0f);
                level().addFreshEntity(spikeBall);
            }
        }
    }

    public class TentacleGoal extends Goal {
        @Override
        public boolean canUse() {
            return tentacles == null && (getHealth() / getMaxHealth()) < 0.5;
        }

        @Override
        public void tick() {
            tentacles = new ArrayList<>();
            for (int i = 0; i < tentacleCount; i ++) {
                Vec3 spawnLocOffset = TEUtils.rotToDir(random.nextFloat() * 360, random.nextFloat() * 180 - 90);
                PlanteraTentacle tentacle = TEUtils.spawnEntity(
                        ()->new PlanteraTentacle(TEBossEntities.PLANTERA_TENTACLE.get(), level(), Plantera.this),
                        (ServerLevel)level(), position().add(spawnLocOffset));
                tentacles.add(tentacle);
            }
        }
    }
}
