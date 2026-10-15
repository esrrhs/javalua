package com.github.esrrhs.fakescript.lua;

/**
 * 类型标签与全局常量 —— 对应 {@code lua.h} 的 {@code LUA_T*} 与
 * {@code lobject.h} 的变体标签。
 * <p>
 * Lua 5.4 的标签是「基础类型 + 变体」的两级结构：低 4 位是基础类型，
 * 高位是变体。数字(Integer/Float)、字符串(短/长)、函数(Lua/C)都靠变体区分，
 * 这一层必须保留，因为 {@code math.type(1)} 与 {@code math.type(1.0)}
 * 返回不同结果，而 {@code 1 == 1.0} 又为真。
 */
public final class Lua {

    private Lua() {
    }

    /* 基础类型（低 4 位） */

    public static final int TNONE = -1;
    public static final int TNIL = 0;
    public static final int TBOOLEAN = 1;
    public static final int TLIGHTUSERDATA = 2;
    public static final int TNUMBER = 3;
    public static final int TSTRING = 4;
    public static final int TTABLE = 5;
    public static final int TFUNCTION = 6;
    public static final int TUSERDATA = 7;
    public static final int TTHREAD = 8;

    /*
     * 变体：makevariant(t, v) = t | (v << 4)。
     *
     * 这里写成常量表达式而不是调用 variant() 方法，是因为 switch 的 case
     * 要求编译期常量，而方法调用不满足——可读性损失可以接受，注释补上算出来的值。
     */

    public static final int VNUMINT = TNUMBER | (0 << 4); // 3：整数
    public static final int VNUMFLT = TNUMBER | (1 << 4); // 19：浮点

    public static final int VSHRSTR = TSTRING | (0 << 4); // 4：短字符串（参与驻留）
    public static final int VLNGSTR = TSTRING | (1 << 4); // 20：长字符串（不驻留）

    public static final int VLIF = TFUNCTION | (0 << 4); // 6：Lua 闭包
    public static final int VLCF = TFUNCTION | (1 << 4); // 22：轻量 C 函数
    public static final int VCCL = TFUNCTION | (2 << 4); // 38：C 闭包

    /** 取基础类型：{@code novariant(x) = x & 0x0F} */
    public static int noVariant(int tt) {
        return tt & 0x0F;
    }

    /**
     * 数组部分的最大规模（2 的幂）。
     * <p>
     * C 版是 {@code 1 << 31}（MAXABITS = 31），这里压到 {@code 1 << 20}。
     * 语义上无差别：超过这个下标的整数键一律进哈希部分，只是密集大数组会比
     * C 版多花内存与时间。取 20 是为了让数组部分最坏情况下也就是一百万个槽，
     * 不至于一次分配就把堆撑爆。
     */
    public static final int MAXABITS = 20;
    public static final int MAXASIZE = 1 << MAXABITS;

    /** 短字符串阈值，超过则不驻留（对应 lstring.c 的 LUAI_MAXSHORTLEN） */
    public static final int MAXSHORTLEN = 40;

    /** {@code luaO_ceillog2}：不小于 log2(x) 的最小整数，x 必须 &gt; 0 */
    public static int ceilLog2(int x) {
        int l = 0;
        int v = x - 1;
        while (v > 0) {
            v >>>= 1;
            l++;
        }
        return l;
    }

    /** 类型名，用于 {@code type()} 与错误信息 */
    public static String typeName(int tt) {
        switch (noVariant(tt)) {
        case TNIL:
            return "nil";
        case TBOOLEAN:
            return "boolean";
        case TLIGHTUSERDATA:
            return "userdata";
        case TNUMBER:
            return "number";
        case TSTRING:
            return "string";
        case TTABLE:
            return "table";
        case TFUNCTION:
            return "function";
        case TUSERDATA:
            return "userdata";
        case TTHREAD:
            return "thread";
        default:
            return "no value";
        }
    }
}
