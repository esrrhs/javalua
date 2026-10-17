package com.github.esrrhs.fakescript.lua;

/**
 * 表达式描述符 —— 对应 {@code lparser.h} 的 {@code expdesc}。
 * <p>
 * 这是整个 codegen 的核心数据结构：解析一个表达式时，<b>不急着生成代码</b>，
 * 而是先记下「这个表达式的值现在在哪儿」。等到不得不落地时（存进变量、当实参、
 * 参与运算）再决定最省指令的做法——比如 {@code t.x} 可以一直保持
 * VINDEXSTR 不落地，直到赋值时才发一条 {@code SETFIELD}。
 * <p>
 * C 版用一个 union 存不同 kind 的载荷；Java 没有 union，这里把所有字段摊平。
 * 唯一要小心的是 C 里那些「overlapping」的写法（比如 VLOCAL 的
 * {@code var.ridx} 与 VINDEXED 的 {@code ind.t} 共用存储），摊平之后反而
 * 不会出现「读了上一个 kind 留下的位」这类错误。
 */
public final class ExpDesc {

    /* expkind —— ORDER 与上游严格一致 */
    /** 空表达式（最后一个表达式为空列表） */
    public static final int VVOID = 0;
    public static final int VNIL = 1;
    public static final int VTRUE = 2;
    public static final int VFALSE = 3;
    /** 常量在 {@code k} 里；info = 下标 */
    public static final int VK = 4;
    /** 浮点常量 */
    public static final int VKFLT = 5;
    /** 整数常量 */
    public static final int VKINT = 6;
    /** 字符串常量（由词法层驻留） */
    public static final int VKSTR = 7;
    /** 值已在某个固定寄存器里；info = 寄存器号 */
    public static final int VNONRELOC = 8;
    /** 局部变量；varRidx = 寄存器号，varVidx = actvar 下标 */
    public static final int VLOCAL = 9;
    /** upvalue；info = upvalues 下标 */
    public static final int VUPVAL = 10;
    /** 编译期 {@code <const>} 变量；info = actvar 里的绝对下标 */
    public static final int VCONST = 11;
    /** 索引变量：indT = 表寄存器，indIdx = 键的 R 下标 */
    public static final int VINDEXED = 12;
    /** 索引 upvalue：indT = 表 upvalue，indIdx = 键的 K 下标 */
    public static final int VINDEXUP = 13;
    /** 常量整数索引：indT = 表寄存器，indIdx = 键值 */
    public static final int VINDEXI = 14;
    /** 字面串索引：indT = 表寄存器，indIdx = 键的 K 下标 */
    public static final int VINDEXSTR = 15;
    /** 表达式是一个测试/比较；info = 对应跳转指令的 pc */
    public static final int VJMP = 16;
    /** 结果可以放进任意寄存器；info = 指令 pc */
    public static final int VRELOC = 17;
    /** 函数调用；info = 指令 pc */
    public static final int VCALL = 18;
    /** vararg 表达式；info = 指令 pc */
    public static final int VVARARG = 19;

    /** 当前 kind */
    public int k = VVOID;

    /** VKINT */
    public long ival;
    /** VKFLT */
    public double nval;
    /** VKSTR */
    public LuaString strval;
    /** 通用下标（VK / VUPVAL / VNONRELOC / VJMP / VRELOC / VCALL / VVARARG） */
    public int info;

    /** VINDEX*：键 */
    public int indIdx;
    /** VINDEX*：表（寄存器号或 upvalue 下标） */
    public int indT;

    /** VLOCAL：寄存器号 */
    public int varRidx;
    /** VLOCAL：actvar 里的下标 */
    public int varVidx;

    /** 「为真则跳走」的跳转链表 */
    public int t = Codegen.NO_JUMP;
    /** 「为假则跳走」的跳转链表 */
    public int f = Codegen.NO_JUMP;

    public ExpDesc() {
    }

    /** 复制（等价于 C 的 {@code *e1 = *e2}） */
    public ExpDesc(ExpDesc src) {
        set(src);
    }

    public ExpDesc set(ExpDesc src) {
        this.k = src.k;
        this.ival = src.ival;
        this.nval = src.nval;
        this.strval = src.strval;
        this.info = src.info;
        this.indIdx = src.indIdx;
        this.indT = src.indT;
        this.varRidx = src.varRidx;
        this.varVidx = src.varVidx;
        this.t = src.t;
        this.f = src.f;
        return this;
    }

    /** {@code vkisvar}：是不是变量（可赋值） */
    public boolean isVar() {
        return VLOCAL <= k && k <= VINDEXSTR;
    }

    /** {@code vkisindexed}：是不是索引变量 */
    public boolean isIndexed() {
        return VINDEXED <= k && k <= VINDEXSTR;
    }

    /** {@code hasjumps}：带跳转说明这是个短路表达式（and/or/比较） */
    public boolean hasJumps() {
        return t != f;
    }

    /* ---------- 便捷构造 ---------- */

    /** 目前唯一被用到的一个：{@code Codegen} 给一元运算配一个假的第二个操作数 */
    public static ExpDesc ofInt(long v) {
        ExpDesc e = new ExpDesc();
        e.k = VKINT;
        e.ival = v;
        return e;
    }
}
