package com.github.esrrhs.fakescript.lua;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 基础库 —— 对应 {@code lbaselib.c}，加上 {@code lstrlib.c} 里那组字符串算术元方法。
 * <p>
 * 阶段 3 只求「脚本能跑起来并对 stdout 做差分」，所以这里挑的是差分比对真正
 * 用得上的函数：输出、类型、转换、元表、迭代、错误处理。字符串 / 表 / os /
 * io 等库随阶段 7 各自成文件。
 * <p>
 * <b>为什么这里要挂字符串的算术元方法。</b>{@code "10" + 1 == 11} 这件事
 * <b>不是</b> VM 做的——VM 的算术宏只认真正的数字。它来自 {@code lstrlib.c}
 * 给字符串元表装的 {@code __add}：先把两侧 {@code lua_stringtonumber}，
 * 再 {@code lua_arith}。少了这一组，字符串参与算术就会走到「attempt to perform
 * arithmetic on a string value」——一个看起来合理但与参考实现不同的错误。
 */
public final class BaseLib {

    private BaseLib() {
    }

    /** 把基础库装进全局表 */
    public static void open(LuaState L) {
        LuaTable g = L.globals;

        setFn(g, "print", BaseLib::print);
        setFn(g, "type", BaseLib::type);
        setFn(g, "tostring", BaseLib::tostring);
        setFn(g, "tonumber", BaseLib::tonumber);
        setFn(g, "rawget", BaseLib::rawget);
        setFn(g, "rawset", BaseLib::rawset);
        setFn(g, "rawequal", BaseLib::rawequal);
        setFn(g, "rawlen", BaseLib::rawlen);
        setFn(g, "next", BaseLib::next);
        setFn(g, "pairs", BaseLib::pairs);
        setFn(g, "ipairs", BaseLib::ipairs);
        setFn(g, "select", BaseLib::select);
        setFn(g, "error", BaseLib::error);
        setFn(g, "assert", BaseLib::assertFn);
        setFn(g, "pcall", BaseLib::pcall);
        setFn(g, "xpcall", BaseLib::xpcall);
        setFn(g, "setmetatable", BaseLib::setmetatable);
        setFn(g, "getmetatable", BaseLib::getmetatable);
        setFn(g, "collectgarbage", BaseLib::collectgarbage);

        openStringMetatable(L);
    }

    /* ==================== 字符串元表 ==================== */

    /**
     * {@code lstrlib.c: createmetatable} + 算术元方法。
     * <p>
     * 上游还给这个元表设了 {@code __index = string} 库（所以 {@code ("x"):upper()}
     * 能用），那部分随字符串库一起到阶段 7 再补。
     */
    private static void openStringMetatable(LuaState L) {
        LuaTable mt = new LuaTable();
        setFn(mt, "__add", l -> strArith(l, Arith.OP_ADD, "add"));
        setFn(mt, "__sub", l -> strArith(l, Arith.OP_SUB, "sub"));
        setFn(mt, "__mul", l -> strArith(l, Arith.OP_MUL, "mul"));
        setFn(mt, "__mod", l -> strArith(l, Arith.OP_MOD, "mod"));
        setFn(mt, "__pow", l -> strArith(l, Arith.OP_POW, "pow"));
        setFn(mt, "__div", l -> strArith(l, Arith.OP_DIV, "div"));
        setFn(mt, "__idiv", l -> strArith(l, Arith.OP_IDIV, "idiv"));
        setFn(mt, "__unm", l -> strArith(l, Arith.OP_UNM, "unm"));
        L.setTypeMetatable(Lua.TSTRING, mt);
    }

    /** {@code lstrlib.c: arith} */
    private static int strArith(LuaState L, int op, String name) {
        LuaValue p1 = L.arg(0);
        LuaValue p2 = L.arg(1);
        LuaValue n1 = numOf(p1);
        LuaValue n2 = numOf(p2);
        if (n1 != null && n2 != null) {
            LuaValue res = Arith.rawArith(op, n1, n2);
            if (res != null) {
                L.push(res);
                return 1;
            }
        }
        throw L.libError("attempt to " + name + " a '" + p1.typeName() + "' with a '"
                + p2.typeName() + "'");
    }

    /** {@code lstrlib.c: tonum}：已是数字就原样返回，否则试着把整个字符串解释成数字 */
    private static LuaValue numOf(LuaValue v) {
        if (v.isNumber()) {
            return v;
        }
        return Vm.strToNum(v);
    }

    /* ==================== 各函数 ==================== */

    /** {@code luaB_print}：制表符分隔，末尾换行 */
    private static int print(LuaState L) {
        int n = L.argCount();
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                tmp.write('\t');
            }
            LuaString s = toLString(L, L.arg(i));
            tmp.write(s.bytes, 0, s.bytes.length);
        }
        tmp.write('\n');
        byte[] all = tmp.toByteArray();
        L.out.write(all, 0, all.length);
        L.out.flush();
        return 0;
    }

    /** {@code luaB_type} */
    private static int type(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'type' (value expected)");
        }
        L.push(LuaValue.ofString(LuaString.latin1(L.arg(0).typeName())));
        return 1;
    }

    /** {@code luaB_tostring} */
    private static int tostring(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'tostring' (value expected)");
        }
        L.push(LuaValue.ofString(toLString(L, L.arg(0))));
        return 1;
    }

    /** {@code luaL_tolstring}：先看 {@code __tostring}，再按类型格式化 */
    static LuaString toLString(LuaState L, LuaValue v) {
        LuaValue ts = metamethodByName(L, v, "__tostring");
        if (ts != null) {
            // 调 __tostring(v)
            int func = L.top;
            L.ensureSlots(func + 2);
            L.stack[func].set(ts);
            L.stack[func + 1].set(v);
            L.top = func + 2;
            /*
             * luaL_callmeta 走的是 lua_call，也就是 luaD_callnoyield：
             * __tostring 里 yield 会报「跨 C 调用边界」。实测参考实现
             * print(setmetatable({}, {__tostring = f})) 里 f 一 yield 就炸，
             * 换成 L.call 会让它「成功」，反而与参考实现不一致。
             */
            L.callNoYield(func, 1);
            LuaValue res = L.stack[--L.top];
            /*
             * 判据是 {@code lua_isstring} 而不是「是不是字符串」：
             * <b>数字也算</b>，会被就地转换成字符串。实测
             * {@code print(setmetatable({}, {__tostring = function() return 42 end}))}
             * 打印 {@code 42}，只有返回表这类真不行的才报
             * 「'__tostring' must return a string」。
             */
            if (res.isString()) {
                return res.asString();
            }
            if (res.isNumber()) {
                return LuaString.latin1(Vm.numberToString(res));
            }
            throw L.libError("'__tostring' must return a string");
        }
        switch (v.baseType()) {
        case Lua.TNUMBER:
            return LuaString.latin1(Vm.numberToString(v));
        case Lua.TSTRING:
            return v.asString();
        case Lua.TBOOLEAN:
            return LuaString.latin1(v.toBoolean() ? "true" : "false");
        case Lua.TNIL:
            return LuaString.latin1("nil");
        default: {
            String kind = v.typeName();
            LuaValue name = metamethodByName(L, v, "__name");
            if (name != null && name.isString()) {
                kind = name.asString().toJavaString();
            }
            String ptr = String.format("0x%016x", System.identityHashCode(v.o));
            return LuaString.latin1(kind + ": " + ptr);
        }
        }
    }

    private static LuaValue metamethodByName(LuaState L, LuaValue v, String name) {
        LuaTable mt = null;
        if (v.baseType() == Lua.TTABLE) {
            mt = v.asTable().getMetatable();
        } else {
            mt = L.typeMetatable(v.baseType());
        }
        if (mt == null) {
            return null;
        }
        LuaValue r = mt.get(LuaString.latin1(name));
        return (r == null || r.isNil()) ? null : r;
    }

    /** {@code luaB_tonumber} */
    private static int tonumber(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.libError("bad argument #1 to 'tonumber' (value expected)");
        }
        LuaValue v = L.arg(0);
        if (n == 1 || L.arg(1).isNil()) {
            if (v.isNumber()) {
                L.push(v);
                return 1;
            }
            LuaValue num = Vm.strToNum(v);
            if (num != null) {
                L.push(num);
                return 1;
            }
            L.pushNil();
            return 1;
        }
        // 带进制
        long base = checkInteger(L, 1);
        if (base < 2 || base > 36) {
            throw L.libError("bad argument #2 to 'tonumber' (base out of range)");
        }
        if (!v.isString()) {
            throw L.libError("bad argument #1 to 'tonumber' (string expected, got "
                    + v.typeName() + ")");
        }
        String s = new String(v.asString().bytes, StandardCharsets.ISO_8859_1).trim();
        Long parsed = parseBase(s, (int) base);
        if (parsed == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofInteger(parsed));
        }
        return 1;
    }

    private static Long parseBase(String s, int base) {
        boolean neg = false;
        int p = 0;
        if (p < s.length() && (s.charAt(p) == '-' || s.charAt(p) == '+')) {
            neg = s.charAt(p) == '-';
            p++;
        }
        if (p >= s.length()) {
            return null;
        }
        long a = 0;
        for (; p < s.length(); p++) {
            int d = Character.digit(s.charAt(p), base);
            if (d < 0) {
                return null;
            }
            a = a * base + d; // 溢出按 Lua 的 int64 环绕处理，与 C 版一致
        }
        return neg ? -a : a;
    }

    /** {@code luaB_rawget} */
    private static int rawget(LuaState L) {
        LuaValue t = L.arg(0);
        LuaValue k = L.arg(1);
        if (!t.isTable()) {
            throw L.libError("bad argument #1 to 'rawget' (table expected, got " + t.typeName()
                    + ")");
        }
        if (k.isNil()) {
            throw L.libError("table index is nil");
        }
        LuaValue v = t.asTable().get(k);
        L.push((v == null) ? LuaValue.ofNil() : v);
        return 1;
    }

    /** {@code luaB_rawset} */
    private static int rawset(LuaState L) {
        LuaValue t = L.arg(0);
        LuaValue k = L.arg(1);
        LuaValue v = L.arg(2);
        if (!t.isTable()) {
            throw L.libError("bad argument #1 to 'rawset' (table expected, got " + t.typeName()
                    + ")");
        }
        t.asTable().set(k, v);
        L.push(t);
        return 1;
    }

    /** {@code luaB_rawequal} */
    private static int rawequal(LuaState L) {
        L.push(LuaValue.ofBoolean(LuaValue.rawEqual(L.arg(0), L.arg(1))));
        return 1;
    }

    /** {@code luaB_rawlen} */
    private static int rawlen(LuaState L) {
        LuaValue v = L.arg(0);
        if (v.isTable()) {
            L.push(LuaValue.ofInteger(v.asTable().length()));
            return 1;
        }
        if (v.isString()) {
            L.push(LuaValue.ofInteger(v.asString().length()));
            return 1;
        }
        throw L.libError("bad argument #1 to 'rawlen' (table or string expected, got "
                + v.typeName() + ")");
    }

    /** {@code luaB_next} */
    private static int next(LuaState L) {
        LuaValue t = L.arg(0);
        LuaValue k = L.argCount() > 1 ? L.arg(1) : LuaValue.ofNil();
        if (!t.isTable()) {
            throw L.libError("bad argument #1 to 'next' (table expected, got " + t.typeName()
                    + ")");
        }
        LuaValue[] kv;
        try {
            kv = t.asTable().next(k);
        } catch (IllegalArgumentException e) {
            throw L.libError("invalid key to 'next'");
        }
        if (kv == null) {
            L.pushNil();
            return 1;
        }
        L.push(kv[0]);
        L.push(kv[1]);
        return 2;
    }

    /** {@code luaB_pairs}（含 {@code __pairs} 元方法） */
    private static int pairs(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'pairs' (value expected)");
        }
        LuaValue t = L.arg(0);
        LuaValue pf = metamethodByName(L, t, "__pairs");
        if (pf != null) {
            int func = L.top;
            L.ensureSlots(func + 2);
            L.stack[func].set(pf);
            L.stack[func + 1].set(t);
            L.top = func + 2;
            L.call(func, 3);
            return 3;
        }
        if (!t.isTable()) {
            throw L.libError("bad argument #1 to 'pairs' (table expected, got " + t.typeName()
                    + ")");
        }
        L.pushFunction(BaseLib::next);
        L.push(t);
        L.pushNil();
        return 3;
    }

    /** {@code luaB_ipairs} */
    private static int ipairs(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'ipairs' (value expected)");
        }
        L.pushFunction(BaseLib::ipairsAux);
        L.push(L.arg(0));
        L.push(LuaValue.ofInteger(0));
        return 3;
    }

    /**
     * {@code ipairsaux}。
     * <p>
     * 这里有一个容易写反的地方：<b>C 函数的 n 个返回值是「栈顶最后 n 个」</b>，
     * 不是「参数之后的前 n 个」。所以先压 {@code i}、再压 {@code t[i]}，然后
     * {@code return 1}——取到的是刚压进去的那个值（结束时是 {@code nil}），
     * 而不是 {@code i}。写成「先压 i 就返回 i」的直觉版本，泛型 for 会看到
     * 首值非 nil 而<b>永不终止</b>：参考实现里 {@code ipairs} 能停住，靠的
     * 就是这个「取最后 n 个」的约定，不是靠判 nil 提前返回。
     */
    private static int ipairsAux(LuaState L) {
        long i = checkInteger(L, 1) + 1;
        LuaValue t = L.arg(0);
        L.push(LuaValue.ofInteger(i));
        int vIdx = L.top;
        L.pushNil();
        Vm.getTable(L, t, LuaValue.ofInteger(i), vIdx);
        return L.stack[vIdx].isNil() ? 1 : 2;
    }

    /** {@code luaB_select} */
    private static int select(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.libError("bad argument #1 to 'select' (value expected)");
        }
        LuaValue first = L.arg(0);
        if (first.isString() && first.asString().toJavaString().equals("#")) {
            L.push(LuaValue.ofInteger(n - 1));
            return 1;
        }
        long i = checkInteger(L, 0);
        if (i < 0) {
            i = n + i;
        } else if (i > n) {
            i = n;
        }
        if (i < 1) {
            throw L.libError("bad argument #1 to 'select' (index out of range)");
        }
        int start = L.ci.func + 1 + (int) i; // 绝对下标：跳过前 i 个参数
        int count = n - (int) i;
        for (int j = 0; j < count; j++) {
            L.push(L.stack[start + j]);
        }
        return count;
    }

    /** {@code luaB_error} */
    private static int error(LuaState L) {
        int n = L.argCount();
        // 注意没有「至少一个参数」的检查：error() 等价于 error(nil)，
        // 参考实现实测 print(pcall(error)) 得到 false nil。
        long level = (n > 1 && !L.arg(1).isNil()) ? checkInteger(L, 1) : 1;
        LuaValue msg = (n > 0) ? L.arg(0) : LuaValue.ofNil();
        throw L.errorAt(msg, (int) level);
    }

    /** {@code luaB_assert} */
    private static int assertFn(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.libError("bad argument #1 to 'assert' (value expected)");
        }
        if (L.arg(0).toBoolean()) {
            for (int i = 0; i < n; i++) {
                L.push(L.arg(i));
            }
            return n;
        }
        LuaValue msg = (n > 1) ? L.arg(1)
                : LuaValue.ofString(LuaString.latin1("assertion failed!"));
        throw L.errorAt(msg, 1);
    }

    /** {@code luaB_pcall} */
    private static int pcall(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'pcall' (value expected)");
        }
        return doPcall(L, false);
    }

    /** {@code luaB_xpcall} */
    private static int xpcall(LuaState L) {
        if (L.argCount() < 2) {
            throw L.libError("bad argument #2 to 'xpcall' (value expected)");
        }
        return doPcall(L, true);
    }

    /**
     * pcall / xpcall 的公共部分。
     * <p>
     * 布局按 {@code lbaselib.c} 来：先在结果区起点塞 {@code true}，再把被调函数
     * 与它的参数整体上移一格，于是调用结果自然接在 {@code true} 后面，返回时数
     * {@code L.top - start} 就得到「true + 全部结果」。
     * <p>
     * xpcall 的坑在<b>消息处理函数夹在被调函数和参数中间</b>
     * （原始布局 {@code [f, msgh, args...]}）。必须先把它摘出来、把参数左移一格，
     * 否则它会变成传给 {@code f} 的第一个实参——表现是处理函数收到的
     * 错误对象是 {@code nil}。参考实现用 {@code lua_rotate(L, 3, 2)} 把
     * {@code true} 和 {@code f} 转到参数前面，这里直接删掉中间那格，等价且更好读。
     */
    private static int doPcall(LuaState L, boolean hasHandler) {
        int base = L.ci.func;
        CallInfo myCi = L.ci; // 本函数自己的帧；yield 时要往上面挂续体
        LuaValue handler = null;
        if (hasHandler) {
            handler = new LuaValue(L.stack[base + 2]);
            // 抹掉 msgh：参数整体左移一格
            for (int p = base + 2; p < L.top - 1; p++) {
                L.stack[p].set(L.stack[p + 1]);
            }
            L.top--;
        }
        int start = base + 1; // 结果区起点，当前放着被调函数
        L.ensureSlots(L.top + 1);
        for (int p = L.top; p > start; p--) {
            L.stack[p].set(L.stack[p - 1]);
        }
        L.top++;
        L.stack[start].setBoolean(true);
        int f = start + 1;
        try {
            LuaValue err = L.pcall(f, LuaState.MULTRET, handler);
            if (err == null) {
                return L.top - start;
            }
            L.top = start;
            L.push(LuaValue.ofBoolean(false));
            L.push(err);
            return 2;
        } catch (LuaYield y) {
            /*
             * pcall 可以 yield —— 实测参考实现就是这样。但 yield 是异常抛出去的，
             * Java 栈帧到这里就没了，「收尾」只能交给续体。
             * 这里做的事等价于 C 版 lua_pcallk 里那段
             * 「ci->u.c.k = k; ci->callstatus |= CIST_YPCALL; ...」：
             * 记下恢复点，让 resume 之后的 unroll 能找到它。
             */
            myCi.yieldPcall = true;
            myCi.k = PCALL_CONT;
            myCi.ctx = start;
            myCi.funcIdx = f;
            myCi.errHandler = handler;
            myCi.recst = LuaState.LUA_OK;
            throw y;
        }
    }

    /**
     * {@code lbaselib.c: finishpcall} —— pcall 的续体。
     * <p>
     * {@code ctx} 是结果区起点（就是 {@code true} 那一格），错误对象由
     * {@code finishPcallK} 放在 {@code ctx + 1}。
     */
    private static final LuaKFunction PCALL_CONT = (L, status, ctx) -> {
        if (status != LuaState.LUA_OK && status != LuaState.LUA_YIELD) {
            LuaValue err = new LuaValue(L.stack[ctx + 1]);
            L.top = ctx;
            L.push(LuaValue.ofBoolean(false));
            L.push(err);
            return 2;
        }
        return L.top - ctx;
    };

    /** {@code luaB_setmetatable} */
    private static int setmetatable(LuaState L) {
        LuaValue t = L.arg(0);
        LuaValue mt = L.arg(1);
        if (!t.isTable()) {
            throw L.libError("bad argument #1 to 'setmetatable' (table expected, got "
                    + t.typeName() + ")");
        }
        if (!(mt.isNil() || mt.isTable())) {
            throw L.libError("bad argument #2 to 'setmetatable' (nil or table expected)");
        }
        if (metamethodByName(L, t, "__metatable") != null) {
            throw L.libError("cannot change a protected metatable");
        }
        t.asTable().setMetatable(mt.isNil() ? null : mt.asTable());
        // __gc 只在设元表的当下登记一次，之后往元表里塞 __gc 不生效（实测参考实现如此）
        t.asTable().markFinalizable(L);
        L.push(t);
        return 1;
    }

    /** {@code luaB_getmetatable} */
    private static int getmetatable(LuaState L) {
        if (L.argCount() == 0) {
            throw L.libError("bad argument #1 to 'getmetatable' (value expected)");
        }
        LuaValue v = L.arg(0);
        LuaValue protect = metamethodByName(L, v, "__metatable");
        if (protect != null) {
            L.push(protect);
            return 1;
        }
        LuaTable mt = (v.baseType() == Lua.TTABLE) ? v.asTable().getMetatable()
                : L.typeMetatable(v.baseType());
        if (mt == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofTable(mt));
        }
        return 1;
    }

    /**
     * {@code luaB_collectgarbage}。
     * <p>
     * 这里做两件事：请求 JVM 做一次完整收集，然后递增「收集轮次」——弱表靠
     * 轮次决定什么时候清扫失效条目，这一点与 C 版一致（Lua 是在 GC 周期末尾
     * 清弱表，不是键一不可达就立刻消失）。
     * <p>
     * 返回值一律 0。不假装统计内存：{@code collectgarbage("count")} 报任何具体
     * 数字都是谎报（我们连自己的堆都摸不到），宁可让依赖它的脚本拿到 0，
     * 也不要编一个看起来合理的数。
     */
    private static int collectgarbage(LuaState L) {
        /*
         * 先把所有栈的「死区」清掉，再请求收集。顺序不能反：
         * {@code lgc.c: traversethread} 的 atomic 分支做的正是这件事——
         * mark 只扫 {@code [stack, top)}，扫完把 {@code [top, stack_last)}
         * 整段置 nil。也就是说 {@code top} 之上的槽在 Lua 里<b>对 GC 不可见</b>。
         * <p>
         * 这里必须显式做，因为 {@code stack} 是一条 Java 数组：数组里每个槽
         * 对 JVM 都是活引用。不清的话，凡是「寄存器落在 {@code top} 之上」的
         * 对象都收不掉——循环里最后一次迭代建的表就挂在这样的寄存器上
         * （{@code for i = 1, 5 do t[i] = {i} end} 之后 {@code {5}} 还留在
         * 寄存器里，而 {@code top} 只到调用参数处），于是弱表清不掉它。
         */
        L.clearDeadStackSlices();
        System.gc();
        /*
         * 光 gc 不够：不可达对象的 {@code finalize} 是由 JVM 的终结线程跑的，
         * 不主动催一下，哨兵还没入队我们就往下走了。{@code runFinalization} 会
         * 尽量把队列排干，之后才能排空待终结队列。
         */
        System.runFinalization();
        L.mainThread.drainFinalizers(L);
        LuaTable.gcGeneration++;
        L.push(LuaValue.ofInteger(0));
        return 1;
    }

    /* ==================== 小工具 ==================== */

    private static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(name, LuaValue.ofFunction(f));
    }

    static long checkInteger(LuaState L, int i) {
        LuaValue v = L.arg(i);
        if (v.isInteger()) {
            return v.i;
        }
        if (v.isFloat()) {
            Long k = Arith.fltToInteger(v.n, Arith.F2I_EQ);
            if (k != null) {
                return k;
            }
        }
        if (v.isString()) {
            LuaValue n = Vm.strToNum(v);
            if (n != null && n.isInteger()) {
                return n.i;
            }
            if (n != null) {
                Long k = Arith.fltToInteger(n.n, Arith.F2I_EQ);
                if (k != null) {
                    return k;
                }
            }
        }
        throw L.libError("bad argument #" + (i + 1) + " (number expected, got " + v.typeName()
                + ")");
    }

    /** 把 {@link LuaValue} 变成函数值（供外部注册额外库函数） */
    public static LuaValue functionValue(LuaFunction f) {
        return LuaValue.ofFunction(f);
    }
}
