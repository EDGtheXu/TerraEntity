package org.confluence.terraentity.entity.ai.goal.behavior.condition;

@FunctionalInterface
public interface Condition {
    boolean check();

    static Condition not(Condition condition) {
        return new NotCondition(condition);
    }
}
