package com.github.esrrhs.javalua;

/**
 * 表达式描述符 —— 对应 Lua 5.3.4 {@code lparser.h} 的 {@code expdesc}。
 */
public final class ExpDesc {

    public static final int VVOID = 0;
    public static final int VNIL = 1;
    public static final int VTRUE = 2;
    public static final int VFALSE = 3;
    public static final int VK = 4;
    public static final int VKFLT = 5;
    public static final int VKINT = 6;
    /** Parser sugar before stringK — discharge to VK */
    public static final int VKSTR = 100;
    public static final int VNONRELOC = 7;
    public static final int VLOCAL = 8;
    public static final int VUPVAL = 9;
    public static final int VINDEXED = 10;
    public static final int VJMP = 11;
    public static final int VRELOC = 12; /* C: VRELOCABLE */
    public static final int VCALL = 13;
    public static final int VVARARG = 14;

    public int k = VVOID;
    public long ival;
    public double nval;
    public int info;
    /** VINDEXED：键（寄存器或 RK） */
    public int indIdx;
    /** VINDEXED：表（寄存器号或 upvalue 下标） */
    public int indT;
    /** VINDEXED：表是 VLOCAL 还是 VUPVAL */
    public int indVt;
    /** @deprecated 5.4 local register alias — same as info for VLOCAL in 5.3 */
    public int varRidx;
    public int varVidx;
    public LuaString strval;
    public int t = Codegen.NO_JUMP;
    public int f = Codegen.NO_JUMP;

    public void init(int k, int i) {
        this.k = k;
        this.info = i;
        this.t = Codegen.NO_JUMP;
        this.f = Codegen.NO_JUMP;
    }

    public ExpDesc() {}
    public ExpDesc(ExpDesc o) { copyFrom(o); }

    public void copyFrom(ExpDesc o) {
        this.k = o.k;
        this.ival = o.ival;
        this.nval = o.nval;
        this.info = o.info;
        this.indIdx = o.indIdx;
        this.indT = o.indT;
        this.indVt = o.indVt;
        this.t = o.t;
        this.f = o.f;
    }

    public static boolean isVar(int k) {
        return VLOCAL <= k && k <= VINDEXED;
    }

    public static boolean isInReg(int k) {
        return k == VNONRELOC || k == VLOCAL;
    }

    public boolean isVar() { return isVar(k); }
    public boolean isIndexed() { return k == VINDEXED; }

    /** 5.4 VINDEXSTR/VINDEXI/VCONST removed — map to VINDEXED/VK */
    public static final int VINDEXSTR = VINDEXED;
    public static final int VINDEXI = VINDEXED;
    public static final int VINDEXUP = VINDEXED;
    public static final int VCONST = VK;
}

