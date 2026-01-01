package org.confluence.terraentity.entity.ai.goal.behavior.condition;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public record DistanceCondition(Mob mob, double distance) implements Condition {

    @Override
    public boolean check() {
        LivingEntity entity = mob.getTarget();
        if(entity == null) {
            return false;
        }
        return entity.distanceToSqr(mob) <= distance * distance;
    }
}
