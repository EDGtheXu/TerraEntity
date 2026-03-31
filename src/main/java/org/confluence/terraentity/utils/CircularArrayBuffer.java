package org.confluence.terraentity.utils;

/**
 * 循环数组缓存，使用固定大小的数组，存储指定数量的元素，当数组满时，将覆盖最旧的元素。
 */
public class CircularArrayBuffer<T> {
    public final T[] positions;
    public int posPointer = -1;

    public CircularArrayBuffer(T[] positions) {
        this.positions = positions;

    }

    public void add(T pos) {
        this.next();
        this.positions[this.posPointer] = pos;
    }

    /**
     * 指针移动到下一个位置
     */
    public void next() {
        this.posPointer = (this.posPointer + 1) % this.positions.length;
    }

    /**
     * 获取距离最新元素指定位置元素
     */
    public T get(int index) {
        return this.positions[(this.posPointer + this.positions.length - index) % this.positions.length];
    }

    /**
     * 获取最新的元素
     */
    public T get() {
        return this.positions[posPointer];
    }

    public int size() {
        return this.positions.length;
    }

}
