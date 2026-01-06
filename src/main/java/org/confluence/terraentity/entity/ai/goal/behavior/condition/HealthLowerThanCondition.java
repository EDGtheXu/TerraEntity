package org.confluence.terraentity.entity.ai.goal.behavior.condition;

import net.minecraft.world.entity.Mob;

/**
 * 生命值小于阈值
 */
public record HealthLowerThanCondition(Mob mob, float percentage) implements Condition {

    @Override
    public boolean check() {
        return mob.getHealth() / mob.getMaxHealth() < percentage;
    }

    @Override
    public String getDesc() {
        return "Health lower than " + percentage * 100 + "%";
    }
}
