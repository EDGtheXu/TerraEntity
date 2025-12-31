package org.confluence.terraentity.entity.ai.goal.behavior.condition;

import net.minecraft.world.entity.Mob;

public class TargetExistCondition implements Condition {
    Mob mob;
    public TargetExistCondition(Mob mob) {
        this.mob = mob;
    }
    @Override
    public boolean check() {
        return mob.getTarget() != null;
    }
}
