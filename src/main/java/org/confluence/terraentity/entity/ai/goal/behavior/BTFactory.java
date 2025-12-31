package org.confluence.terraentity.entity.ai.goal.behavior;

import net.minecraft.world.entity.ai.goal.Goal;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.ParallelNode;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.SelectorNode;
import org.confluence.terraentity.entity.ai.goal.behavior.composite.SequenceNode;
import org.confluence.terraentity.entity.ai.goal.behavior.condition.Condition;
import org.confluence.terraentity.entity.ai.goal.behavior.decoration.ConditionNode;
import org.confluence.terraentity.entity.ai.goal.behavior.decoration.InverterNode;
import org.confluence.terraentity.entity.ai.goal.behavior.decoration.RepeaterNode;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.GoalWrapper;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.TimerAction;
import org.confluence.terraentity.entity.ai.goal.behavior.leaf.WaitAction;

/**
 * 行为树工厂
 */
public class BTFactory {
    public static SequenceNode sequence() {
        return new SequenceNode();
    }

    public static SelectorNode selector() {
        return new SelectorNode();
    }

    public static ParallelNode parallel(ParallelNode.Policy successPolicy,
                                        ParallelNode.Policy failurePolicy) {
        return new ParallelNode(successPolicy, failurePolicy);
    }

    public static InverterNode inverter(BTNode child) {
        return new InverterNode(child);
    }

    public static RepeaterNode repeater(BTNode child, int count) {
        return new RepeaterNode(child, count);
    }

    public static RepeaterNode infinite(BTNode child) {
        return new RepeaterNode(child, -1);
    }

    public static ConditionNode condition(Condition condition, BTNode child) {
        return new ConditionNode(condition, child);
    }

    public static WaitAction wait(int ticks) {
        return new WaitAction(ticks);
    }

    public static TimerAction timer(int duration) {
        return new TimerAction(duration);
    }

    public static ParallelNode withTimer(int duration, BTNode node) {
        return parallel(ParallelNode.Policy.REQUIRE_ONE, ParallelNode.Policy.REQUIRE_ONE)
                .addChild(timer(duration))
                .addChild(node);
    }

    public static GoalWrapper goal(Goal goal) {
        return new GoalWrapper(goal);
    }

//    // 示例：创建一个复杂的行为树
//    public static BTNode createZombieBehaviorTree(PathfinderMob zombie) {
//        return selector()
//            .addChild(sequence()
//                .addChild(condition(
//                    new MoveToAction(zombie, 1.0)
//                        .setTarget(100, 64, 200),
//                    () -> zombie.distanceToSqr(100, 64, 200) > 100
//                ))
//            )
//            .addChild(sequence()
//                .addChild(wait(20))
//                .addChild(new RandomWanderAction(zombie, 1.0))
//            );
//    }
}
