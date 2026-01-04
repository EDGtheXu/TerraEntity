package org.confluence.terraentity.entity.ai.goal.behavior.condition;

/**
 * 行为树条件接口
 */
@FunctionalInterface
public interface Condition {

    boolean check();

    static Condition not(Condition condition) {
        return new NotCondition(condition);
    }

    static Condition and(Condition condition1, Condition condition2) {
        return new AndCondition(condition1, condition2);
    }

    static Condition or(Condition condition1, Condition condition2) {
        return new OrCondition(condition1, condition2);
    }

    record NotCondition(Condition condition) implements Condition {
        @Override
        public boolean check() {
            return !condition.check();
        }
    }

    record AndCondition(Condition condition1, Condition condition2) implements Condition {
        @Override
        public boolean check() {
            return condition1.check() && condition2.check();
        }
    }

    record OrCondition(Condition condition1, Condition condition2) implements Condition {
        @Override
        public boolean check() {
            return condition1.check() || condition2.check();
        }
    }

}

