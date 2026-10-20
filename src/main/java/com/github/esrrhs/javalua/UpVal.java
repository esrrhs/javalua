package com.github.esrrhs.javalua;

/**
 * upvalue —— 对应 {@code lobject.h} 的 {@code UpVal}。
 * <p>
 * 两态设计与 C 版完全对应：
 * <ul>
 *   <li><b>开放</b>：{@link #v} 直接引用栈上的槽对象，栈改即 upvalue 改。这正是
 *       「闭包捕获局部变量」能活过变量所在块的原因。</li>
 *   <li><b>关闭</b>：变量离开作用域时把槽的内容复制一份到自身存储，此后与栈无关。
 *       C 版是把 {@code uv->v} 从栈地址改指向 {@code &uv->u.value}，
 *       这里换成切换 {@link #v} 引用，语义等价。</li>
 * </ul>
 * <p>
 * 一个必须由调用方保证的不变式：<b>栈槽对象一旦创建就不得替换</b>。开放 upvalue
 * 持有的是槽对象的引用，若栈扩容时把槽换成新对象，upvalue 就silently 断开了。
 * 所以 {@link LuaState} 一律用 {@code stack[i].set(v)}，从不 {@code stack[i] = v}。
 */
public final class UpVal {

    /**
     * 当前指向的活槽。开放时是栈槽对象本身，关闭后是本对象自持的存储。
     */
    LuaValue v;

    /** 开放时对应的栈下标；{@code -1} 表示已关闭 */
    int stackIdx;

    /** 是否是 {@code <close>} 声明的 to-be-closed 变量 */
    boolean toBeClosed;

    private UpVal() {
    }

    /** 新建开放 upvalue，指向 {@code stackIdx} 处的栈槽 */
    static UpVal open(LuaValue slot, int stackIdx) {
        UpVal uv = new UpVal();
        uv.v = slot;
        uv.stackIdx = stackIdx;
        return uv;
    }

    /** 新建关闭 upvalue，持有 {@code value} 的一份拷贝 */
    static UpVal closed(LuaValue value) {
        UpVal uv = new UpVal();
        uv.v = new LuaValue(value);
        uv.stackIdx = -1;
        return uv;
    }

    boolean isOpen() {
        return stackIdx >= 0;
    }

    /** 读：返回活槽，调用方可直接读写（与 C 的 {@code uv->v.p} 一致） */
    LuaValue value() {
        return v;
    }

    /**
     * 关闭：把栈槽内容搬进自身存储。
     * <p>
     * 必须是<b>新建</b>一个 {@link LuaValue} 而不能复用旧槽对象——旧槽还在栈上，
     * 随时会被下一段代码改写。
     */
    void close() {
        if (stackIdx >= 0) {
            v = new LuaValue(v);
            stackIdx = -1;
        }
    }
}
