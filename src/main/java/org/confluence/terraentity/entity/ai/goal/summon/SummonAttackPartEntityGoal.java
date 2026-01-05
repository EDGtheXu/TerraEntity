package org.confluence.terraentity.entity.ai.goal.summon;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.neoforged.neoforge.entity.PartEntity;
import org.confluence.terraentity.api.entity.IPartEntityTargetable;
import org.confluence.terraentity.api.entity.ISummonMob;

import java.util.EnumSet;

/**
 * 使召唤物能够攻击 PartEntity（如 WallOfFleshPart）
 * 将 PartEntity 设置为实际目标，移动逻辑由 SummonMeleeAttackGoal 处理（会移动到 PartEntity 位置）
 * 攻击时通过碰撞攻击直接攻击 PartEntity
 */
public class SummonAttackPartEntityGoal<T extends Mob & ISummonMob & IPartEntityTargetable> extends TargetGoal {
    private Entity partEntityTarget;

    public SummonAttackPartEntityGoal(T mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!(this.mob instanceof IPartEntityTargetable targetable)) {
            return false;
        }

        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        this.partEntityTarget = null;

        // 搜索附近的 PartEntity
        for (Entity entity : this.mob.level().getEntities(this.mob,
                this.mob.getBoundingBox().inflate(followRange),
                e -> e instanceof PartEntity<?> part &&
                     part.isAlive() &&
                     part.isPickable() &&
                     part.getParent() instanceof LivingEntity)) {

            if (entity instanceof PartEntity<?> partEntity) {
                // 检查是否可以攻击这个 PartEntity
                if (targetable.canAttackTarget(partEntity)) {
                    this.partEntityTarget = partEntity;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        if (this.mob instanceof IPartEntityTargetable targetable && this.partEntityTarget != null) {
            // 设置实际目标为 PartEntity（用于攻击和移动）
            targetable.setActualTargetEntity(this.partEntityTarget);
            // 将父实体设置为 Mob 的目标（让其他 Goal 可以工作，如 SlimeAttackGoal）
            if (this.partEntityTarget instanceof PartEntity<?> partEntity &&
                partEntity.getParent() instanceof LivingEntity parent) {
                this.mob.setTarget(parent);
            }
        }
        super.start();
    }

    @Override
    public void stop() {
        if (this.mob instanceof IPartEntityTargetable targetable) {
            targetable.setActualTargetEntity(null);
        }
        this.partEntityTarget = null;
        super.stop();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.partEntityTarget == null || !this.partEntityTarget.isAlive()) {
            return false;
        }

        // 检查是否仍然可以攻击目标
        if (!(this.mob instanceof IPartEntityTargetable targetable)) {
            return false;
        }

        if (!targetable.canAttackTarget(this.partEntityTarget)) {
            return false;
        }

        // 检查 PartEntity 是否仍然在范围内
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double distanceSqr = this.mob.distanceToSqr(this.partEntityTarget);

        return distanceSqr <= followRange * followRange;
    }
}

