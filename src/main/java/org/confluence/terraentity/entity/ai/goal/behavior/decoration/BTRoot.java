package org.confluence.terraentity.entity.ai.goal.behavior.decoration;

import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;
import org.jetbrains.annotations.NotNull;

public abstract class BTRoot extends BTNode {

    protected BTNode child;

    /**
     * 延迟构造行为树
     */
    @NotNull
    protected abstract BTNode createBehaviorTree();

    @Override
    public abstract boolean canUse();

    @Override
    public abstract boolean canContinueToUse();

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
