package org.confluence.terraentity.entity.ai.goal.behavior.condition;

import org.jetbrains.annotations.Nullable;

/**
 * 行为树条件接口
 */
@FunctionalInterface
public interface Condition {

    boolean check();

    default @Nullable String getDesc(){
        return null;
    }

    default Condition setDesc(String desc) {
        return this;
    }

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
        @Override
        public @Nullable String getDesc() {
            if(condition.getDesc() == null) {
                return null;
            }
            return "Not " + condition.getDesc();
        }
    }

    record AndCondition(Condition condition1, Condition condition2) implements Condition {
        @Override
        public boolean check() {
            return condition1.check() && condition2.check();
        }

        @Override
        public @Nullable String getDesc() {
            if(condition1.getDesc() == null && condition2.getDesc() == null) {
                return null;
            }
            if(condition1.getDesc() == null) {
                return condition2.getDesc();
            }
            if(condition2.getDesc() == null) {
                return condition1.getDesc();
            }
            return condition1.getDesc() + " And " + condition2.getDesc();
        }
    }

    record OrCondition(Condition condition1, Condition condition2) implements Condition {
        @Override
        public boolean check() {
            return condition1.check() || condition2.check();
        }

        @Override
        public @Nullable String getDesc() {
            if(condition1.getDesc() == null && condition2.getDesc() == null) {
                return null;
            }
            if(condition1.getDesc() == null) {
                return condition2.getDesc();
            }
            if(condition2.getDesc() == null) {
                return condition1.getDesc();
            }
            return condition1.getDesc() + " Or " + condition2.getDesc();
        }
    }

}

