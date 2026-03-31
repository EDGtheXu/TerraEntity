package org.confluence.terraentity.utils;

import net.minecraft.util.Mth;

import java.util.function.DoubleFunction;

/**
 * 随时间平滑移动的数值
 */
public class SmoothFloat {
    public float tick;
    private final float maxTick;
    private final float scale;
    private boolean isIncrease;

    public SmoothFloat(float max, float scale) {
        this.tick = 0;
        this.maxTick = max;
        this.scale = scale;
        this.isIncrease = false;
    }

    public void update(float deltaTime) {
        this.tick = Mth.clamp(this.tick + deltaTime, 0f, this.maxTick);
        this.isIncrease = deltaTime > 0f;
    }

    public float get(float partialTick) {
        float delta = Mth.clamp((this.tick + partialTick * (this.isIncrease ? 1 : -1)) / this.maxTick, 0, 1);
        return this.scale * delta;
    }

    /**
     * 带插值的获取
     *
     * @param easing 插值函数
     */
    public float get(float partialTick, DoubleFunction<Number> easing) {
        float delta = Mth.clamp((this.tick + partialTick * (this.isIncrease ? 1 : -1)) / this.maxTick, 0, 1);
        return this.scale * easing.apply(delta).floatValue();
    }
}
