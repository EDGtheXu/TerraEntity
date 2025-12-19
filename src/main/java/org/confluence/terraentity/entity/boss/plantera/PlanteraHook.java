package org.confluence.terraentity.entity.boss.plantera;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.init.TESounds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

/**
 * 世花钩子
 */
public class PlanteraHook extends AbstractTerraBossBase implements Boss {
    Plantera owner;
    BlockPos targetBlock;
    float hookSpeed;
    boolean grabbed = false;
    boolean retracting = false;

    public PlanteraHook(EntityType<? extends Monster> entityType, Level level) {
        this(entityType, level, null, null, 0f);
    }

    public PlanteraHook(EntityType<? extends Monster> entityType, Level level, @Nullable Plantera owner, @Nullable BlockPos targetBlock, float hookSpeed) {
        super(entityType, level);
        setDiscardFriction(true);

        collisionProperties = new CollisionProperties(1,1,0.5f);

        this.owner = owner;
        this.targetBlock = targetBlock;
        this.hookSpeed = hookSpeed;

        // 防止卡位置导致动不了
        this.noPhysics = true;
        // 防止超出包围盒不渲染
        this.noCulling = true;
    }

    protected void retract() {
        this.retracting = true;
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
        return false;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TESounds.TR_ZOMBIE_DEATH.get();
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1,new MoveGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    public float[] getBossEventProgress(){
        if (owner == null) return new float[]{1f, 1f};
        return owner.getBossEventProgress();
    }

    @Override
    public boolean canAttack(LivingEntity entity) {
        return false;
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
            if (owner == null || !owner.isAlive()) {
                remove(RemovalReason.DISCARDED);
                return;
            }

            Vec3 targetLoc = retracting ? owner.position() : targetBlock.getCenter();
            // 更新速度
            Vec3 vel = targetLoc.subtract(position());
            double lenSqr = vel.lengthSqr();
            if (lenSqr > hookSpeed * hookSpeed) {
                vel = vel.normalize().scale(hookSpeed);
                grabbed = false;
            }
            else {
                if (retracting) remove(RemovalReason.DISCARDED);
                else grabbed = true;
                vel = vel.scale(0.9);
            }
            setDeltaMovement(vel);

            // 视角 - 远离本体
            Vec3 lookDir = position().subtract(owner.position()).normalize();
            lookControl.setLookAt(getX() + lookDir.x, getY() + lookDir.y, getZ() + lookDir.z, 360, 360);
        }
    }

    @Override
    public boolean shouldShowBossBar() {
        return false;
    }

    @Override
    public boolean shouldEscape() {
        return false;
    }

    @Override
    public boolean isMainBody() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor(){
        return BossEvent.BossBarColor.GREEN;
    };

    @Override
    protected boolean shouldOverPlayer(){
        return false;
    }
}
