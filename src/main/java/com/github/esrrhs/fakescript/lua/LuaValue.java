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
        return o instanceof LuaUserdata ? ofUserdata((LuaUserdata) o)
                : ofUserdata(new LuaUserdata(o));
    }

    /**
     * 直接拿一个 {@link LuaUserdata} 当载荷，<b>不再套一层</b>。
     * <p>
     * {@code lua_newuserdatauv} 必须走这条：{@link LuaUserdata#userValues}
     * 建在它自己身上，再包一层的话外面那层 {@code nuvalue} 恒为 0，
     * {@code lua_getiuservalue} 就永远答 {@code LUA_TNONE}。
     */
    public static LuaValue ofUserdata(LuaUserdata ud) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.VUSERDATA, ud);
        return v;
    }

    /**
     * 轻量 userdata（{@link Lua#TLIGHTUSERDATA}）。
     * <p>
     * 与完整 userdata 的差别是<b>没有元表、不参与回收</b>，载荷就是宿主指针本身。
     * 上游用它给「身份」一类东西打标记——最典型的是
     * {@code debug.upvalueid}：C 版把某个 upvalue 的地址
     * （{@code lua_upvalueid} 返回的 {@code void *}）直接推成轻量 userdata，
     * 于是「两个闭包共享同一个 upvalue」就等价于「两个 id 原始相等」。
     * <p>
     * 这里换成 Java 对象引用做身份：Lua 闭包给 {@link UpVal} 对象自己
     * （对应 C 的 {@code *getupvalref(...)}，是 {@code UpVal *} 而不是槽的地址），
     * C 闭包给那个 upvalue 的 {@code LuaValue} 槽对象（对应 C 的
     * {@code &f->upvalue[n-1]}）。槽对象永不被替换，所以身份稳定，与 C 一一对应。
     */
    public static LuaValue ofLightUserdata(Object p) {
        LuaValue v = new LuaValue();
        v.setObject(Lua.TLIGHTUSERDATA, p);
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
        case Lua.TLIGHTUSERDATA:
            /*
             * 载荷是 {@code Long} 时它是<b>指针值</b>（来自 C 的 {@code void*}，
             * 经 {@code lua_rawgetp} / {@code lua_pushlightuserdata} 进来）。
             * C 比的是地址本身，所以两个装箱不同的 {@code Long} 也必须判等——
             * 按 {@code a.o == b.o} 比的话，{@code lua_rawsetp} 写进去的键
             * {@code lua_rawgetp} 永远取不回来。
             * <p>
             * 其余载荷（{@code debug.upvalueid} 给的 {@code UpVal}）走引用相等，
             * 与原来一致。
             */
            return (a.o instanceof Long && b.o instanceof Long)
                    ? a.o.equals(b.o) : a.o == b.o;
        default:
            return a.o == b.o; // 表/函数/userdata/线程：引用相等
        }
    }

    /**
     * 数值相等。
     * <p>
     * <b>整数与浮点（变体不同）不是「都转 double 再比」。</b>Lua 5.4 的
     * {@code luaV_equalobj} / {@code luaV_rawequalobj} 遇到变体不同的两个数，
     * 是<b>两边都按 F2Ieq 转成整数</b>再比；浮点那一侧转不成整数就判不等。
     * <p>
     * 上一版统一转 double（{@code luai_numeq} 的语义），于是
     * {@code rawequal(math.maxinteger, math.maxinteger + 1.0)} 给的是
     * {@code true}：{@code (double)(2^63 - 1)} 舍入成 {@code 2^63}，正好撞上
     * 右边那个浮点。实测参考实现给 {@code false}——并且表键也据此判成两个键
     * （{@code t[math.maxinteger]} 与 {@code t[2^63]} 不是同一项）。
     * <p>
     * 两条规则只在 {@code math.maxinteger} 往前约一千个整数上分岔（那里整数
     * 转 double 会舍入到 {@code 2^63}，而 {@code 2^63} 本身又转不回整数），
     * 所以只能靠扫边界发现，读 {@code lvm.c} 的宏容易看漏。
     */
    public static boolean numberEqual(LuaValue a, LuaValue b) {
        if (a.isInteger() && b.isInteger()) {
            return a.i == b.i;
        }
        if (a.isFloat() && b.isFloat()) {
            return a.n == b.n;
        }
        // 变体不同：把浮点那侧 F2Ieq 转整数，转不成就是不等
        long i = a.isInteger() ? a.i : b.i;
        double n = a.isFloat() ? a.n : b.n;
        return fltIsExactInt(n) && (long) n == i;
    }

    /** {@code luaV_flttointns} 的 F2Ieq 判据：这个浮点精确等于某个整数吗 */
    private static boolean fltIsExactInt(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return false;
        }
        if (n >= Long.MIN_VALUE && n < 9223372036854775808.0) {
            long k = (long) n;
            return (double) k == n; // 必须精确往返，否则 1.5 会被当成 1
        }
        return false;
    }

    /**
     * {@code luaV_flttointns} 的 F2Ieq 模式：浮点只有当<b>精确</b>等于某个整数
     * 时才转换。键的规范化与 {@link #numberEqual} 共用这一份判据——放两处
     * 迟早会有一处漏改。
     */
    public static boolean floatToIntExact(double n, long[] out) {
        if (!fltIsExactInt(n)) {
            return false;
        }
        out[0] = (long) n;
        return true;
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
