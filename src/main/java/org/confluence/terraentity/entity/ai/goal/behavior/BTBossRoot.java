package org.confluence.terraentity.entity.ai.goal.behavior;

import net.minecraft.world.entity.PathfinderMob;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.ParallelNode;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.SelectorNode;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.Condition;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.TargetExistCondition;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.RandomStrollAction;
import org.jetbrains.annotations.NotNull;

/**
 * 带阶段的AI
 */
public abstract class BTBossRoot<T extends PathfinderMob> extends BTRoot {

    protected final T mob;

    public BTBossRoot(T mob) {
        this.mob = mob;
    }

    protected abstract BTNode createStageTrigger(SelectorNode selector);

    protected abstract BTNode createAttackBehavior(SelectorNode selector);

    protected BTNode createWonderBehavior() {
        return BTFactory.sequence()
                .addChild(new RandomStrollAction(mob, 2.0f, 70));
    }

    @Override
    protected @NotNull BTNode createBehaviorTree() {
        return BTFactory.parallel(ParallelNode.Policy.REQUIRE_ALL, ParallelNode.Policy.REQUIRE_ALL)
                // 阶段触发器
                .addChild(BTFactory.infinite(this.createStageTrigger(BTFactory.selector())))
                // AI
                .addChild(BTFactory.infinite(BTFactory.selector()
                        // 游走
                        .addWithCondition(Condition.not(new TargetExistCondition(mob)), BTFactory.infinite(this.createWonderBehavior()))
                        // 攻击
                        .addWithCondition(new TargetExistCondition(mob), this.createAttackBehavior(BTFactory.selector()))
                ));
    }
}
