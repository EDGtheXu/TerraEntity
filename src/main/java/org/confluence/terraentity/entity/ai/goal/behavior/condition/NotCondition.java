package org.confluence.terraentity.entity.ai.goal.behavior.condition;

public class NotCondition implements Condition {
    private final Condition condition;
    public NotCondition(Condition condition) {
        this.condition = condition;
    }
    @Override
    public boolean check() {
        return!condition.check();
    }
}
