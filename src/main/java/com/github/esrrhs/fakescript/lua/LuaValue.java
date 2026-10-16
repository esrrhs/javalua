package com.github.esrrhs.fakescript.lua;

/**
 * Lua 值 —— 对应 {@code lobject.h} 的 {@code TValue}。
 * <p>
 * <b>可变槽</b>，和 C 版一样：虚拟机要能原地改写栈槽（{@code setobj}），
 * 不可变的话每条赋值都要新建对象。代价是必须显式区分「复制值」与「共享槽」，
 * 这也是 C 版用 {@code setobj} / {@code setobj2s} 表达的东西。
 * <p>
 * 载荷字段按类型复用，与 C 的 union 对应：
 * <ul>
 *   <li>boolean / integer → {@code i}</li>
 *   <li>float → {@code n}</li>
 *   <li>string / table / function / userdata / thread → {@code o}</li>
 * </ul>
 */
public final class LuaValue {

    /** 完整标签（含变体），见 {@link Lua} */
    public int tt = Lua.TNIL;

    /** 整数与布尔载荷 */
    public long i;

    /** 浮点载荷 */
    public double n;

    /** 引用载荷：{@link LuaString} / {@link LuaTable} / 闭包 / userdata / 线程 */
    public Object o;

    public LuaValue() {
    }

    /* ---------- 构造 ---------- */

    /** 复制构造：等价于 C 的 {@code setobj(L, dest, src)} */
    public LuaValue(LuaValue src) {
        set(src);
    }

    public static LuaValue ofNil() {
        return new LuaValue();
    }

    public static LuaValue ofBoolean(boolean b) {
        LuaValue v = new LuaValue();
        v.setBoolean(b);
        return v;
    }

    public static LuaValue ofInteger(long x) {
        LuaValue v = new LuaValue();
        v.setInteger(x);
        return v;
    }

    public static LuaValue ofFloat(double x) {
        LuaValue v = new LuaValue();
        v.setFloat(x);
        return v;
    }

    public static LuaValue ofString(LuaString s) {
        LuaValue v = new LuaValue();
        v.setString(s);
        return v;
    }

    public static LuaValue ofTable(LuaTable t) {
        LuaValue v = new LuaValue();
        v.setTable(t);
        return v;
    }

    /** C 函数（轻量 C 函数变体 {@link Lua#VLCF}） */
    public static LuaValue ofFunction(LuaFunction f) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VLCF, f);
        return v;
    }

    /** Lua 闭包（变体 {@link Lua#VLIF}） */
    public static LuaValue ofClosure(LClosure c) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VLIF, c);
        return v;
    }

    /** 线程 / 协程（{@link Lua#TTHREAD}） */
    public static LuaValue ofThread(LuaState t) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VTHREAD, t);
        return v;
    }

    /**
     * 完整 userdata（变体 {@link Lua#VUSERDATA}）：宿主对象进 Lua 值的通道。
     * <p>
     * 载荷一律包一层 {@link LuaUserdata}，好让每个 userdata 带上自己的元表
     * （见 {@link LuaUserdata} 的类注释）。取回载荷用 {@link LuaUserdata#dataOf}。
     */
    public static LuaValue ofUserdata(Object o) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VUSERDATA, new LuaUserdata(o));
        return v;
    }

    /** 带元表的 userdata（{@code lua_newuserdatauv} + {@code luaL_setmetatable}） */
    public static LuaValue ofUserdata(Object o, LuaTable mt) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VUSERDATA, new LuaUserdata(o, mt));
        return v;
    }

    /** C 闭包（变体 {@link Lua#VCCL}） */
    public static LuaValue ofCClosure(CClosure c) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VCCL, c);
        return v;
    }

    /* ---------- 写入 ---------- */

    public void set(LuaValue src) {
        this.tt = src.tt;
        this.i = src.i;
        this.n = src.n;
        this.o = src.o;
    }

    public void setNil() {
        tt = Lua.TNIL;
        i = 0;
        n = 0;
        o = null;
    }

    public void setBoolean(boolean b) {
        tt = Lua.TBOOLEAN;
        i = b ? 1 : 0;
        n = 0;
        o = null;
    }

    public void setInteger(long x) {
        tt = Lua.VNUMINT;
        i = x;
        n = 0;
        o = null;
    }

    public void setFloat(double x) {
        tt = Lua.VNUMFLT;
        i = 0;
        n = x;
        o = null;
    }

    public void setString(LuaString s) {
        tt = s.isShort() ? Lua.VSHRSTR : Lua.VLNGSTR;
        i = 0;
        n = 0;
        o = s;
    }

    public void setTable(LuaTable t) {
        tt = Lua.TTABLE;
        i = 0;
        n = 0;
        o = t;
    }

    /** 直接写入引用载荷，调用方负责保证标签匹配（供函数/userdata/线程使用） */
    public void setObject(int variantTag, Object obj) {
        tt = variantTag;
        i = 0;
        n = 0;
        o = obj;
    }

    /* ---------- 类型判定 ---------- */

    public boolean isNil() {
        return tt == Lua.TNIL || tt == Lua.TNONE;
    }

    public boolean isNone() {
        return tt == Lua.TNONE;
    }

    public boolean isBoolean() {
        return tt == Lua.TBOOLEAN;
    }

    /** 数字（整数或浮点） */
    public boolean isNumber() {
        return Lua.noVariant(tt) == Lua.TNUMBER;
    }

    public boolean isInteger() {
        return tt == Lua.VNUMINT;
    }

    public boolean isFloat() {
        return tt == Lua.VNUMFLT;
    }

    public boolean isString() {
        return Lua.noVariant(tt) == Lua.TSTRING;
    }

    public boolean isTable() {
        return tt == Lua.TTABLE;
    }

    public boolean isFunction() {
        return Lua.noVariant(tt) == Lua.TFUNCTION;
    }

    public boolean isThread() {
        return tt == Lua.VTHREAD;
    }

    public LuaState asThread() {
        return (LuaState) o;
    }

    public int baseType() {
        return Lua.noVariant(tt);
    }

    public String typeName() {
        return Lua.typeName(tt);
    }

    /* ---------- 相等性 ---------- */

    /**
     * {@code lvm.c: luaV_rawequalobj}：不经元方法的原始相等。
     * <p>
     * 表拿它当键的比较用，所以这里的行为直接决定 {@code t[1]} 与 {@code t[1.0]}
     * 是同一个键、而 {@code t[1]} 与 {@code t["1"]} 不是：
     * <ul>
     *   <li>基础类型不同 → 不等（所以整数与字符串永不等）</li>
     *   <li>数字 → 按数值比较，整数与浮点可以相等（{@code 1 == 1.0}）</li>
     *   <li>字符串 → 逐字节比较</li>
     *   <li>表 / 函数 / userdata / 线程 → 引用相等</li>
     * </ul>
     * NaN 与自身不等，与 Lua 一致（所以用 NaN 作键每次都会新建条目）。
     */
    public static boolean rawEqual(LuaValue a, LuaValue b) {
        if (a.isNil() && b.isNil()) {
            return true;
        }
        if (a.baseType() != b.baseType()) {
            return false;
        }
        switch (a.baseType()) {
        case Lua.TNIL:
            return true;
        case Lua.TBOOLEAN:
            return a.i == b.i;
        case Lua.TNUMBER:
            return numberEqual(a, b);
        case Lua.TSTRING:
            return a.asString().equals(b.asString());
        default:
            return a.o == b.o; // 表/函数/userdata/线程：引用相等
        }
    }

    /** 数值相等：整数与整数比 long，其余统一转 double（与 {@code luai_numeq} 一致） */
    public static boolean numberEqual(LuaValue a, LuaValue b) {
        if (a.isInteger() && b.isInteger()) {
            return a.i == b.i;
        }
        if (a.isFloat() && b.isFloat()) {
            return a.n == b.n;
        }
        double x = a.isInteger() ? (double) a.i : a.n;
        double y = b.isInteger() ? (double) b.i : b.n;
        return x == y;
    }

    /* ---------- 读取 ---------- */

    /**
     * Lua 的真假判定：只有 {@code nil} 与 {@code false} 为假，
     * 其余（含 {@code 0} 与空字符串）全为真 —— 这是 Lua 与 C 最容易搞混的一条。
     */
    public boolean toBoolean() {
        return !(isNil() || (tt == Lua.TBOOLEAN && i == 0));
    }

    public boolean asBoolean() {
        return i != 0;
    }

    public long asInteger() {
        return i;
    }

    public double asFloat() {
        return n;
    }

    public LuaString asString() {
        return (LuaString) o;
    }

    public LuaTable asTable() {
        return (LuaTable) o;
    }

    @Override
    public String toString() {
        switch (Lua.noVariant(tt)) {
        case Lua.TNIL:
            return "nil";
        case Lua.TNONE:
            return "none";
        case Lua.TBOOLEAN:
            return i != 0 ? "true" : "false";
        case Lua.TNUMBER:
            return isInteger() ? Long.toString(i) : Double.toString(n);
        case Lua.TSTRING:
            return asString().toJavaString();
        case Lua.TTABLE:
            return "table: " + System.identityHashCode(o);
        case Lua.TFUNCTION:
            return "function: " + System.identityHashCode(o);
        default:
            return Lua.typeName(tt);
        }
    }
}
