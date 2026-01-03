package org.confluence.terraentity.entity.boss.wallofflesh;

import net.minecraft.ChatFormatting;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.entity.proj.TrailProjectile;
import org.confluence.terraentity.entity.util.DifficultSelector;
import org.confluence.terraentity.init.TETags;
import org.jetbrains.annotations.NotNull;

public class WallOfFleshEye extends WallOfFleshPart implements RangedAttackMob {

    int shootDamage;
    int _shootDelay = 10;
    int _shootInterval = 40;
    final int __shootInterval = _shootInterval;
    int _shootCount = 1;
    int shootDelay;
    int shootCount;

    private static final int summonCDAll = 60;
    private int summonCD = summonCDAll;

    public float calculatedYaw = 0.0f;
    public float calculatedPitch = 0.0f;

    public WallOfFleshEye(WallOfFlesh parentMob, String name, float width, float height) {
        super(parentMob, name, width, height);
        DifficultSelector difficultSelector = parentMob.getDifficultSelector();
        this.shootDamage = difficultSelector.switchBy(8,10,12,15);
        this.shootCount = _shootCount;
        this.shootDelay = _shootDelay;
        collisionProperties.detectInternal = 20;
    }

    protected boolean canShoot(Entity target, float range) {
        LivingEntity currentTarget = this.target;
        if (currentTarget == null) return false;

        Vec3 lookAngle = this.getLookAngle().normalize();
        Vec3 toTarget = target.position().subtract(this.position()).normalize();
        double angleDiff = Math.toDegrees(Math.acos(lookAngle.dot(toTarget)));
        return Math.abs(angleDiff) <= Math.toDegrees(range) ||
                this.distanceTo(target) <= 150;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    public boolean isNoGravity(){ return true; }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        return super.hurt(pSource, pAmount) && parentMob.hurt(this,pSource,pAmount);
    }

    @Override
    public boolean shouldBeSaved(){
        if(this.parentMob != null)return this.parentMob.shouldBeSaved();
        return  false;
    }

    @Override
    public void tickPart(double offsetX, double offsetY, double offsetZ) {
        this.findTarget();
        if (this.parentMob== null || !this.parentMob.isAlive()) return;

        Vec3 forward = this.parentMob.getForward().normalize();

        // 检查目标是否为创造模式或观察者模式的玩家
        if(this.target != null && this.target instanceof Player) {
            Player player = (Player) this.target;
            if(player.isCreative() || player.isSpectator()) {
                this.target = null;
            }
        }

        // 计算眼睛旋转角度
        if (this.target != null && this.target.isAlive() && !this.target.isRemoved()) {

            if (!this.level().isClientSide) {
                // 采样计算目标速度
                this.aimTracker.tick(this.target, this.level().getGameTime());
            }

            WallOfFlesh parentMob = this.parentMob;
            if (parentMob.isAlive()) {
                Vec3 wallForward = parentMob.getForward();
                Vec3 targetPos = this.target.getEyePosition();
                Vec3 entityPos = this.getEyePosition();

                // 计算到目标的方向向量
                Vec3 toTarget = targetPos.subtract(entityPos);

                // 计算水平距离
                double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);

                if (horizontalDistance > 0.001) {
                    // 计算俯仰角（上下看的角度）
                    float pitch = (float) Math.toDegrees(Math.atan2(-toTarget.y, horizontalDistance));
                    pitch = Mth.clamp(pitch, -45.0F, 45.0F);

                    // 计算偏航角（左右看的角度）
                    float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));

                    // 计算墙体的偏航角
                    float wallYaw = (float) Math.toDegrees(Math.atan2(-wallForward.x, wallForward.z));

                    // 计算相对于墙体的角度差
                    float relativeYaw = yaw - wallYaw;
                    relativeYaw = Mth.wrapDegrees(relativeYaw);

                    // 限制头部转动范围
                    relativeYaw = Mth.clamp(relativeYaw, -60.0F, 60.0F);

                    this.calculatedYaw = relativeYaw * 0.017453292F;
                    this.calculatedPitch = pitch * 0.017453292F;
                } else {
                    this.calculatedYaw = 0.0f;
                    this.calculatedPitch = 0.0f;
                }
            } else {
                this.calculatedYaw = 0.0f;
                this.calculatedPitch = 0.0f;
            }
        } else {
            this.aimTracker.reset(); // 失去目标就重置
            this.calculatedYaw = 0.0f;
            this.calculatedPitch = 0.0f;
        }

        if (!this.level().isClientSide) {
            if (--summonCD > 0) return;
            if (canShoot(this.target,0.75F) && this.target.isAlive() && this.stareCount >= 10) {
                this.shoot(this.target);
            }
        }
    }

    public double getEyeY() {
        return this.position().y + this.getBbHeight() / 2;
    }

    private void shoot(LivingEntity target){
        if(--this.shootDelay <= 0){
            --this.shootCount;

            if(this.shootCount <= 0){
                this.shootCount = _shootCount;
                this.shootDelay = _shootInterval + this.getRandom().nextInt(20);

                this.performRangedAttack(target, 1.0f); // 最后一击增加射速
            }else{
                this.shootDelay = _shootDelay;
                this.performRangedAttack(target, 0.5f);
            }
        }
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return this.parentMob==null?super.canUsePortal(allowPassengers):this.parentMob.canUsePortal(allowPassengers);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if(source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_DROWNING)){
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public void performRangedAttack(LivingEntity livingEntity, float v) {
        // 基础速度参数
        double v0 = 1.5;

        Vec3 shooterPos = this.position();
        // 目标中心点（考虑身高偏移）
        Vec3 targetPos = livingEntity.position().add(0, livingEntity.getBbHeight() * 0.5, 0);

        // 调用内部类获取预测画像
        AimResult result = aimTracker.predict(shooterPos, targetPos, v0);

        // 执行发射逻辑
        Vec3 predictedPos = targetPos.add(result.offset);
        Vec3 dir = predictedPos.subtract(shooterPos);

        TrailProjectile proj = new TrailProjectile(this.level(), ChatFormatting.DARK_PURPLE.getColor()) {
            @Override
            protected boolean canHitEntity(@NotNull Entity target) {
                return super.canHitEntity(target) && !target.getType().is(TETags.EntityTypes.FLESH_ALLIANCE);
            }
        };
        proj.setDamage(this.shootDamage);
        proj.setOwner(this.parentMob);
        proj.setPos(shooterPos);
        proj.shoot(dir.x, dir.y, dir.z, (float)v0, 0.0f);
        level().addFreshEntity(proj);
    }

    private final AimTracker aimTracker = new AimTracker();

    /**
     * 目标追踪器
     * 采用“每帧采样 + 动态平滑 + 距离衰减”算法
     */
    private static class AimTracker {
        private Vec3 lastTargetPos = null;
        private Vec3 manualVel = Vec3.ZERO;
        private long lastUpdateTime = 0;

        /**
         * 实时监控玩家位置变化，计算平滑后的速度向量
         */
        public void tick(LivingEntity target, long currentTime) {
            // 获取玩家当前中心点位置
            Vec3 currentPos = target.position();

            if (lastTargetPos != null) {
                long tickDiff = currentTime - lastUpdateTime;

                // 只有时间流逝了才计算（防止同 1 tick 内多次调用）
                if (tickDiff > 0) {
                    // 1. 计算瞬时水平位移速度 (忽略 Y 轴，防止跳跃干扰)
                    Vec3 instantVel = new Vec3(
                            (currentPos.x - lastTargetPos.x) / tickDiff,
                            0,
                            (currentPos.z - lastTargetPos.z) / tickDiff
                    );

                    // 2. 死区过滤：如果玩家位移极小，视为静止
                    // 0.0025 是 0.05 速度的平方，低于此值认为玩家没动
                    if (instantVel.horizontalDistanceSqr() < 0.0025) {
                        // 快速收回预判量 (刹车)
                        manualVel = manualVel.scale(0.5);
                        if (manualVel.lengthSqr() < 0.0001) manualVel = Vec3.ZERO;
                    } else {
                        // 3. 异常速度限制：防止玩家传送或被击退导致激光乱飞
                        if (instantVel.length() > 0.4) instantVel = instantVel.normalize().scale(0.3);

                        // 4. 动态平滑 (Lerp)：让预判线不抖动
                        // 0.2 表示 20% 权重给新速度，80% 保留旧速度
                        double lerp = 0.2;
                        manualVel = manualVel.scale(1.0 - lerp).add(instantVel.scale(lerp));
                    }
                }
            }

            lastTargetPos = currentPos;
            lastUpdateTime = currentTime;
        }

        /**
         * 基于当前采样到的速度，计算射击偏移量
         */
        public AimResult predict(Vec3 shooterPos, Vec3 targetPos, double v0) {
            double distance = shooterPos.distanceTo(targetPos);

            // 1. 计算基础弹道时间 (Ticks)
            double travelTime = distance / v0;

            // 2. 网络延迟补偿 (Latency Compensation)
            // 补偿服务端与客户端之间的同步误差，通常增加 1.5 ~ 2.0 tick
            double predictTicks = travelTime + 1.5;

            // 3. 预判衰减 (Confidence Decay)
            // 飞行时间越长（距离越远），玩家越可能变向，因此缩减提前量
            double confidence = 1.0;
            if (predictTicks > 40) { // 超过 2 秒的弹道
                confidence = Math.max(0.4, 1.0 - (predictTicks - 40) * 0.015);
            }

            // 4. 距离修正系数 (Lead Modifier)
            // 随距离轻微增加提前量，补偿远距离下的角度误差
            double leadModifier = 1.0 + (0.02 * Mth.clamp(distance / 100.0, 0, 1.0));

            // 5. 生成最终偏移向量
            Vec3 offset = new Vec3(
                    manualVel.x * predictTicks * leadModifier * confidence,
                    -0.1, // 略微向下修正，瞄准玩家腰部以下
                    manualVel.z * predictTicks * leadModifier * confidence
            );

            return new AimResult(offset, travelTime, leadModifier, distance, manualVel.horizontalDistance());
        }

        /**
         * 丢失目标时重置，防止下次切换目标时产生巨大的瞬时速度
         */
        public void reset() {
            this.lastTargetPos = null;
            this.manualVel = Vec3.ZERO;
            this.lastUpdateTime = 0;
        }

    }

    /**
     * 预测结果的数据载体
     */
    private record AimResult(Vec3 offset, double travelTime, double leadModifier, double distance, double targetSpeed) {}

    @Override
    protected void onParentChangeState(int state){
        if(state == 2){
            this._shootInterval = (int) (this.__shootInterval * 0.7f);
            this._shootCount = 3;
            float healthPercent = parentMob.getHealthPercentage();
        }
    }
}
