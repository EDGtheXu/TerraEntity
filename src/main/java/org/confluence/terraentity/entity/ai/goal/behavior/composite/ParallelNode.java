package org.confluence.terraentity.entity.ai.goal.behavior.composite;

import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 并行节点（同时执行所有子节点）
 */
public class ParallelNode extends BTNode {
    private final List<BTNode> children = new ArrayList<>();
    private final Policy successPolicy;
    private final Policy failurePolicy;

    public ParallelNode(Policy successPolicy, Policy failurePolicy) {
        this.successPolicy = successPolicy;
        this.failurePolicy = failurePolicy;
    }

    public ParallelNode addChild(BTNode child) {
        children.add(child);
        return this;
    }

    @Override
    public BTStatus execute() {
        int successCount = 0;
        int failureCount = 0;
        int runningCount = 0;

        for (BTNode child : children) {

            child.tryStart();

            if (child.getStatus() == BTStatus.RUNNING) {
                child.tick();
//                if(!child.canContinueToUse()) {
//                    child.setStatus(BTStatus.FAILURE);
//                }
            }

            switch (child.getStatus()) {
                case SUCCESS -> successCount++;
                case FAILURE -> failureCount++;
                case RUNNING -> runningCount++;
            }
        }

        // 检查成功策略
        if (successPolicy == Policy.REQUIRE_ONE && successCount > 0) {
            return BTStatus.SUCCESS;
        }
        if (successPolicy == Policy.REQUIRE_ALL && successCount == children.size()) {
            return BTStatus.SUCCESS;
        }

        // 检查失败策略
        if (failurePolicy == Policy.REQUIRE_ONE && failureCount > 0) {
            return BTStatus.FAILURE;
        }
        if (failurePolicy == Policy.REQUIRE_ALL && failureCount == children.size()) {
            return BTStatus.FAILURE;
        }

        return runningCount > 0 ? BTStatus.RUNNING : BTStatus.FAILURE;
    }

    @Override
    protected void cleanup() {
        for (BTNode child : children) {
//            if(child.getStatus() == BTStatus.RUNNING){
                child.stop();
//            }
        }
    }

    @Override
    public @NotNull String toString() {
        return children.stream().reduce(
                new StringBuilder("ParallelNode[").append(children.size()).append("|"),
                (sb, node)-> sb.append(",").append(node.getClass().getSimpleName()),
                (a, b)->a ).append("]").toString();
    }

    public enum Policy {
        REQUIRE_ONE,    // 只需一个
        REQUIRE_ALL     // 需要全部
    }
}
