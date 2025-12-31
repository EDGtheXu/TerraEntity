package org.confluence.terraentity.entity.ai.goal.behavior.leaf;

import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;

/**
 * 等待节点
 */
public class WaitAction extends BTNode {
    private final int waitTicks;
    private int currentTicks = 0;

    public WaitAction(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    @Override
    public BTStatus execute() {
        currentTicks++;
        if (currentTicks >= waitTicks) {
            return BTStatus.SUCCESS;
        }
        return BTStatus.RUNNING;
    }

    @Override
    protected void cleanup() {
        currentTicks = 0;
    }
}
