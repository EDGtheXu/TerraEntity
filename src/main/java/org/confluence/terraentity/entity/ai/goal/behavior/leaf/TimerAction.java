package org.confluence.terraentity.entity.ai.goal.behavior.leaf;

import org.confluence.terraentity.entity.ai.goal.behavior.BTNode;

public class TimerAction extends BTNode {

    int tick = 0;
    final int duration;
    public TimerAction(int duration) {
        this.duration = duration;
    }
    @Override
    public BTStatus execute() {
        if (tick >= duration) {
            return BTStatus.SUCCESS;
        }
        tick++;
        return BTStatus.RUNNING;
    }

    @Override
    protected void cleanup() {
        this.tick = 0;
    }
}
