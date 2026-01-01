package org.confluence.terraentity.entity.ai.goal.behavior.condition;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Mob;

import java.util.function.Predicate;

/**
 * 实体数据值条件
 */
public record EntityDataCondition<T>(Mob mob,
                                     EntityDataAccessor<T> dataAccessor,
                                     Predicate<T> predicate) implements Condition {
    @Override
    public boolean check() {
        return predicate.test(mob.getEntityData().get(dataAccessor));
    }
}
