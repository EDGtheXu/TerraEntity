package org.confluence.terraentity.entity.ai.goal.behavior;

import net.minecraft.world.entity.PathfinderMob;
import org.confluence.terraentity.api.entity.IStateChangeableMob;
import org.confluence.terraentity.entity.ai.goal.behavior.blackboard.Blackboard;
import org.confluence.terraentity.entity.ai.goal.behavior.blackboard.IBlackboardHolder;
import org.confluence.terraentity.entity.ai.goal.behavior.blackboard.KeyType;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.SequenceNode;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.Condition;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.SyncAction;

/**
 * 两个阶段的怪物AI
 */
public abstract class BTBossTwoStageRoot<T extends PathfinderMob & IBlackboardHolder & IStateChangeableMob> extends BTCommonRoot<T> {

    public BTBossTwoStageRoot(T mob) {
        super(mob);
    }

    /**
     * 转换二阶段条件
     */
    protected abstract Condition createStageCondition();

    /**
     * 转换阶段前，可以添加延迟和共享状态位
     */
    protected abstract SequenceNode switchPre(SequenceNode sequence);

    /**
     * 转换阶段后，可以添加延迟和共享状态位
     */
    protected abstract SequenceNode switchPost(SequenceNode sequence);

    /**
     * 一阶段AI
     */
    protected abstract BTNode createStageOneAttack();

    /**
     * 二阶段AI
     */
    protected abstract BTNode createStageTwoAttack();

    @Override
    protected BTNode createStageTrigger() {
        return BTFactory.selector()
                // 二阶段
                .addWithCondition(new Blackboard.ContainsValue<>(this.mob, KeyType.STAGE, v -> v == 3), BTFactory.wait(10000))
                // 转换阶段
                .addWithCondition(Blackboard.containsValue(this.mob, KeyType.STAGE, v -> v == 2),
                        switchPost(
                                switchPre(BTFactory.sequence())
                                        .addChild(new SyncAction<>(this.mob, this.mob.get_DATA_STATUS_STATUS(), () -> 3))
                        ).addChild(Blackboard.setValue(this.mob, KeyType.STAGE, () -> 3))
                )
                // 一阶段
                .addWithCondition(Condition.and(this.createStageCondition(), Blackboard.containsValue(this.mob, KeyType.STAGE, v -> v == 1)), BTFactory.sequence()
                        .addChild(Blackboard.setValue(this.mob, KeyType.STAGE, () -> 2))
                        .addChild(new SyncAction<>(this.mob, this.mob.get_DATA_STATUS_STATUS(), () -> 2))
                );
    }

    @Override
    protected BTNode createAttackBehavior() {
        return BTFactory.selector()
                // 一阶段
                .addWithCondition(Blackboard.containsValue(this.mob, KeyType.STAGE, v -> v == 1), BTFactory.infinite(this.createStageOneAttack()))
                // 二阶段
                .addWithCondition(Blackboard.containsValue(this.mob, KeyType.STAGE, v -> v == 3), BTFactory.infinite(this.createStageTwoAttack()));
    }

}
