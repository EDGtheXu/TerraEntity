package org.confluence.terraentity.entity.ai.goal.behavior;

import org.jetbrains.annotations.NotNull;

/**
 * 行为树根节点
 */
public abstract class BTRoot extends BTNode {

    protected BTNode child;

    /**
     * 延迟构造行为树
     */
    @NotNull
    protected abstract BTNode createBehaviorTree();

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        if(this.child == null) {
            this.child = this.createBehaviorTree();
        }
        child.start();
    }

    @Override
    public void tick() {
        child.tick();
    }

    @Override
    public void stop() {
        child.stop();
    }

    @Override
    public BTStatus execute() {
        return child.execute();
    }
}
