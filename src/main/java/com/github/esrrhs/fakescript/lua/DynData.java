package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析过程中的动态结构 —— 对应 {@code lparser.h} 的 {@code Dyndata}。
 * <p>
 * 三个列表都由<b>最外层</b>的解析状态持有，层层嵌套的函数共享同一份：
 * 局部变量栈要跨函数边界（内层看到外层的局部变量才能识别出 upvalue），
 * 而 goto/标签列表本来就是整个 chunk 范围内的。
 */
public final class DynData {

    /** 变量种类 */
    public static final int VDKREG = 0;
    public static final int RDKCONST = 1;
    public static final int RDKTOCLOSE = 2;
    public static final int RDKCTC = 3;

    /** 活跃局部变量描述（{@code Vardesc}） */
    public static final class Vardesc {
        /** 编译期常量的值（仅 kind == RDKCTC 时有意义） */
        public LuaValue k;
        /** {@code kind}：VDKREG / RDKCONST / RDKTOCLOSE / RDKCTC */
        public int kind = VDKREG;
        /** 变量所在的寄存器 */
        public int ridx;
        /** 在 {@code Proto.locvars} 里的下标 */
        public int pidx;
        public LuaString name;
    }

    /** 标签或待处理的 goto（{@code Labeldesc}） */
    public static final class Labeldesc {
        public LuaString name;
        /** 代码位置 */
        public int pc;
        /** 出现的行 */
        public int line;
        /** 该位置上的活跃变量数 */
        public int nactvar;
        /** 这个 goto 是否跨越了 upvalue */
        public boolean close;
    }

    /**
     * 局部变量栈。<b>只增长不收缩</b>：{@link #actvarN} 是逻辑长度。
     * <p>
     * 这一点必须照抄上游。C 里 {@code removevars} 先把 {@code actvar.n} 减掉，
     * 然后还要顺着下标读一遍刚「删掉」的那些槽位去补调试信息的
     * {@code endpc}——靠的就是数组没被真收缩。用 {@code List.remove} 会在那里
     * 直接越界，或者更糟：读到的是别的变量。
     */
    public final List<Vardesc> actvar = new ArrayList<>();

    /** {@code actvar} 的逻辑长度 */
    public int actvarN;

    public final List<Labeldesc> gt = new ArrayList<>();
    public final List<Labeldesc> label = new ArrayList<>();

    /** 在逻辑位置 {@code n} 放一个变量描述（必要时复用旧槽位） */
    public void setActvar(int n, Vardesc v) {
        if (n < actvar.size()) {
            actvar.set(n, v);
        } else {
            actvar.add(v);
        }
    }
}
