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
        setFn(g, "load", BaseLib::loadFn);
        setFn(g, "loadfile", BaseLib::loadfileFn);
        setFn(g, "dofile", BaseLib::dofile);

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
        setFn(mt, "__add", l -> strArith(l, Arith.OP_ADD, Tm.TM_ADD, "add"));
        setFn(mt, "__sub", l -> strArith(l, Arith.OP_SUB, Tm.TM_SUB, "sub"));
        setFn(mt, "__mul", l -> strArith(l, Arith.OP_MUL, Tm.TM_MUL, "mul"));
        setFn(mt, "__mod", l -> strArith(l, Arith.OP_MOD, Tm.TM_MOD, "mod"));
        setFn(mt, "__pow", l -> strArith(l, Arith.OP_POW, Tm.TM_POW, "pow"));
        setFn(mt, "__div", l -> strArith(l, Arith.OP_DIV, Tm.TM_DIV, "div"));
        setFn(mt, "__idiv", l -> strArith(l, Arith.OP_IDIV, Tm.TM_IDIV, "idiv"));
        setFn(mt, "__unm", l -> strArith(l, Arith.OP_UNM, Tm.TM_UNM, "unm"));
        L.setTypeMetatable(Lua.TSTRING, mt);
    }

    /**
     * {@code lstrlib.c: arith}。
     * <p>
     * 关键在那句 {@code else trymt(L, mtname)}：<b>转换不成数字不是立刻报错</b>，
     * 而是<b>再去第二个操作数身上找同名元方法</b>。少了这一步，
     * {@code '5' + b}（{@code b} 是带 {@code __add} 的表）会报
     * 「attempt to add a 'string' with a 'table'」，而参考实现是正常调用
     * {@code b} 的 {@code __add}——上游 {@code events.lua} 那一整串
     * {@code checkcap{"add", '5', b}} 全靠它。
     */
    private static int strArith(LuaState L, int op, int event, String name) {
        LuaValue p1 = L.arg(0);
        LuaValue p2 = L.arg(1);
        LuaValue n1 = numOf(p1);
        LuaValue n2 = numOf(p2);
        if (n1 != null && n2 != null) {
            LuaValue res = Arith.rawArith(L, op, n1, n2);
            if (res != null) {
                L.push(res);
                return 1;
            }
        }
        /* lstrlib.c: trymt —— 回到原来的两个参数，去 p2 身上找元方法 */
        L.setTop(2);
        /*
         * 「第二个操作数是字符串」时<b>不找</b>：否则 {@code '5' + 'x'} 会找到
         * 字符串元表的 {@code __add}——也就是现在正在跑的这个函数——无限递归。
         * C 版用 {@code lua_type(L, 2) == LUA_TSTRING} 挡住，正是这个原因。
         */
        if (!p2.isString()) {
            LuaValue tm = LuaTm.getTmByObj(L, p2, event);
            if (tm != null && !tm.isNil()) {
                int f1 = L.ci.func + 1; // 第一个参数的位置
                L.push(tm);
                // lua_insert(L, -3)：元方法挪到两个参数下面
                LuaValue moved = new LuaValue(L.stack[L.top - 1]);
                for (int i = L.top - 1; i > f1; i--) {
                    L.stack[i].set(L.stack[i - 1]);
                }
                L.stack[f1].set(moved);
                L.call(f1, 1); // C 版是 lua_call，允许 yield
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
        LuaTable mt = LuaTm.metatableOf(L, v);
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
        Long parsed = parseBase(v.asString().bytes, (int) base);
        if (parsed == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofInteger(parsed));
        }
        return 1;
    }

    /**
     * {@code lbaselib.c: b_str2int}。
     * <p>
     * 必须在<b>字节</b>上做，而且必须<b>回到长度上判「吃完了没有」</b>——这两点
     * 缺一个就会把 {@code tonumber('1\0', 2)} 算成 1。
     * <p>
     * C 版靠的是「{@code s} 走到哪里」与 {@code s + l} 比：数字循环遇到
     * {@code \0}（不是 {@code isalnum}）就停，后面的空格跳过一步也停，返回的
     * 指针还站在 {@code \0} 上，与 {@code s + l} 不等，于是
     * {@code luaB_tonumber} 判失败。早先这里用 {@code String.trim()} 去掉首尾
     * 空白——而 Java 的 {@code trim} 连 {@code \0} 一起去掉——这个差别直接让
     * 上游 {@code math.lua} 的 {@code assert(not f(tonumber('1\0', 2)))}
     * 挂掉。
     * <p>
     * 另外数字只看 ASCII：C 版用的是 C locale 的 {@code isalnum}，不是
     * {@code lislalnum}（后者把 {@code _} 也算字母），也不是
     * {@code Character.digit}（后者认全角数字）。
     *
     * @return 解析出的整数；不是合法的整数字面量返回 {@code null}
     */
    private static Long parseBase(byte[] b, int base) {
        int len = b.length;
        int p = 0;
        while (p < len && LCType.isSpace(b[p] & 0xFF)) {
            p++; // SPACECHARS
        }
        boolean neg = false;
        if (p < len && b[p] == '-') {
            p++;
            neg = true;
        } else if (p < len && b[p] == '+') {
            p++;
        }
        if (p >= len || !isAsciiAlnum(b[p])) {
            return null; // 没有数字
        }
        long n = 0;
        do {
            int d = (b[p] >= '0' && b[p] <= '9')
                    ? b[p] - '0'
                    : ((b[p] >= 'a' && b[p] <= 'z') ? b[p] - 'a' : b[p] - 'A') + 10;
            if (d >= base) {
                return null; // 不是这个进制里的数字
            }
            n = n * base + d; // 溢出按 Lua 的 int64 环绕处理，与 C 版一致
            p++;
        } while (p < len && isAsciiAlnum(b[p]));
        while (p < len && LCType.isSpace(b[p] & 0xFF)) {
            p++; // 尾部空白
        }
        if (p != len) {
            return null; // 还有没吃掉的字节，包括内嵌的 \0
        }
        return neg ? -n : n;
    }

    /** C locale 的 {@code isalnum}：只认 ASCII 字母与数字，不含 {@code '_'} */
    private static boolean isAsciiAlnum(byte c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
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

    /**
     * {@code lbaselib.c: pairscont}。
     * <p>
     * yield 恢复之后被调用，三个返回值（迭代器、状态、初值）那时候已经在栈上，
     * 这里只要报出「有 3 个结果」。
     */
    private static final LuaKFunction PAIRS_CONT = (L, status, ctx) -> 3;

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
            /*
             * {@code lua_callk(L, 1, 3, 0, pairscont)}：带续体，所以
             * {@code __pairs} 里 yield 是允许的（上游 nextvar.lua 有这一条）。
             * 用不带续体的 {@code call} 会在恢复时炸，因为那时 {@code ci.k} 是
             * {@code null}。
             */
            L.callK(func, 3, 0, PAIRS_CONT);
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

    /**
     * {@code luaB_error}。
     * <p>
     * 必须照 C 版的形状写，尤其那句 {@code lua_settop(L, 1)}——它不是顺手的
     * 清理，而是决定「栈顶之下那一格是什么」。
     * <p>
     * 实测（{@code coroutine.wrap} 出来的函数崩掉时）：
     * {@code luaB_auxwrap} 在错误分支里会先 {@code lua_xmove(co, L, 1)} 把错误
     * 对象搬走，再 {@code lua_closethread} → {@code luaE_resetthread}，后者
     * 走 {@code luaD_seterrorobj(L, status, L->stack + 1)}，而
     * {@code seterrorobj} 的 {@code default} 分支是
     * {@code setobjs2s(L, oldtop, L->top - 1)}——<b>搬的是「搬走一格之后」的栈顶</b>。
     * 只有 {@code settop(1)} + {@code where} + {@code pushvalue} + {@code concat}
     * 这套写法能让那一格正好是带位置的错误对象（{@code concat} 的结果把参数格
     * 覆盖掉，于是「错误对象」在栈上是连着两格的）。少了 {@code settop}，那一格
     * 还是原始的 {@code "boom"}，报出来就成了没有 {@code file:line:} 前缀的裸串。
     */
    private static int error(LuaState L) {
        int n = L.argCount();
        // 注意没有「至少一个参数」的检查：error() 等价于 error(nil)，
        // 参考实现实测 print(pcall(error)) 得到 false nil。
        long level = (n > 1 && !L.arg(1).isNil()) ? checkInteger(L, 1) : 1;
        L.setTop(1); // lua_settop(L, 1)：多出来的参数全部摘掉
        LuaValue msg = L.arg(0);
        if (msg.isString() && level > 0) {
            L.push(LuaValue.ofString(LuaString.utf8(L.whereAt((int) level)))); // luaL_where
            L.push(msg);          // lua_pushvalue(L, 1)
            Vm.concat(L, 2);      // lua_concat(L, 2)
        }
        // lua_error(L)：抛栈顶那个值。位置已经拼进去了，errorAt 的 level 传 0
        // 表示「别再加一次」——但 where 字段仍非 null，pcall 也不会补。
        throw L.errorAt(L.stack[L.top - 1], 0);
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
            /*
             * 两种「被打断」必须分开记，因为 resume 之后 {@code finishPcallK}
             * 只看 {@code recst}：
             * <ul>
             *   <li>被保护的函数正常跑着就 yield 了 → {@code LUA_OK}，续体原样
             *       把结果交给调用方。</li>
             *   <li>是<b>出错后关 tbc 变量</b>时 yield 的 → 错误还没交出去，
             *       续体要接着把剩下的变量关完，再返回 {@code false, err}。
             *       这就是 {@code finishpcallk} 里 {@code recst} 记着
             *       {@code LUA_ERRRUN} 的含义。</li>
             * </ul>
             * {@link LuaState#closeErrPending} 非空就说明是后者（错误对象和
             * 「还有变量没关完」是同一个条件，见那里的注释）。
             */
            myCi.recst = (L.closeErrPending != null) ? LuaState.LUA_ERRRUN : LuaState.LUA_OK;
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
        LuaTable mt = LuaTm.metatableOf(L, v);
        if (mt == null) {
            L.pushNil();
        } else {
            L.push(LuaValue.ofTable(mt));
        }
        return 1;
    }

    /**
     * {@code luaB_collectgarbage} 的 {@code opts[]}，顺序与 {@code luaB_collectgarbage}
     * 里那个 {@code switch} 的下标严格对应。
     */
    private static final String[] GC_OPTS = {
            "stop", "restart", "collect", "count", "step", "setpause",
            "setstepmul", "isrunning", "generational", "incremental"
    };

    /**
     * {@code luaB_collectgarbage}。
     * <p>
     * {@code "collect"} 分支做两件事：请求 JVM 做一次完整收集，然后递增
     * 「收集轮次」——弱表靠轮次决定什么时候清扫失效条目，这一点与 C 版一致
     * （Lua 是在 GC 周期末尾清弱表，不是键一不可达就立刻消失）。
     * <p>
     * 不假装统计内存：{@code collectgarbage("count")} 一律报 {@code 0.0}。
     * 报任何具体数字都是谎报（我们连自己的堆都摸不到，更没有「收集之后降了多少」
     * 这种量），宁可让依赖它的脚本拿到 0。曾试过报 JVM 堆的已用量，看起来
     * 「更真」，但上游 {@code gc.lua} 里 {@code assert(gcinfo() < x)} 仍然
     * 恒假（一百张小表对堆的影响测不出来），而 {@code repeat until gcinfo() > 3*x}
     * 那种循环还可能一直涨不到——不如恒 0 那样确定：到那儿就停，不会 hang。
     * 其余选项只保证<b>彼此自洽</b>（{@code stop} 之后 {@code isrunning}
     * 为假、{@code generational} 返回切换前的模式名）。
     */
    private static int collectgarbage(LuaState L) {
        /*
         * {@code luaL_checkoption}：<b>必须先校验选项</b>。
         * <p>
         * 这一句不是为了挑刺——上游 {@code errors.lua} 的
         * {@code (collectgarbage or print){}} 断言的就是
         * 「bad argument #1 to 'collectgarbage' (string expected, got table)」。
         * 少校验，那句里的 {@code pcall} 会成功返回，
         * {@code checkmessage} 拿到 {@code false} 去 {@code string.find}，
         * 整个文件就死在一条跟它无关的消息上。
         */
        int o = LuaAux.checkOption(L, 1, "collect", GC_OPTS);
        switch (o) {
        case 3: // "count"
            L.push(LuaValue.ofFloat(0.0));
            return 1;
        case 4: // "step"
            /*
             * C 版的 {@code LUA_GCSTEP} 只推进一步，本实现没有步子可迈，
             * 就<b>照一次完整收集来做</b>。这不是偷懒：弱表与 {@code __gc}
             * 的清理时机全挂在「收集轮次」上，{@code "step"} 不做任何事的话，
             * 上游 {@code gengc.lua} 那串「弱表该被清掉」的断言就全红——
             * 而它们红在一个跟 {@code collectgarbage} 无关的地方。
             */
            doCollect(L);
            L.push(LuaValue.ofBoolean(true));
            return 1;
        case 5: // "setpause"
        case 6: { // "setstepmul"
            LuaState m = L.mainThread;
            int old = (o == 5) ? m.gcPause : m.gcStepMul;
            long v = LuaAux.optInteger(L, 2, 0);
            if (v != 0) {
                if (o == 5) {
                    m.gcPause = (int) v;
                } else {
                    m.gcStepMul = (int) v;
                }
            }
            L.push(LuaValue.ofInteger(old));
            return 1;
        }
        case 7: // "isrunning"
            L.push(LuaValue.ofBoolean(L.mainThread.gcRunning));
            return 1;
        case 8: // "generational"
        case 9: { // "incremental"
            /*
             * {@code lbaselib.c: pushmode}：这两个选项返回的是<b>切换前</b>的
             * 模式，而且是<b>字符串</b>（{@code "incremental"} /
             * {@code "generational"}），不是 {@code LUA_GCINC} 那个整数。
             * 上游 {@code gc.lua} 开头四行就是照着字符串断言的，返回整数的话
             * 它连「testing incremental garbage collection」都打不完。
             */
            LuaState m = L.mainThread;
            int prev = m.gcKind;
            m.gcKind = (o == 8) ? LuaState.LUA_GCGEN : LuaState.LUA_GCINC;
            L.push(LuaValue.ofString(LuaString.latin1(
                    (prev == LuaState.LUA_GCINC) ? "incremental" : "generational")));
            return 1;
        }
        case 0: // "stop"
            L.mainThread.gcRunning = false;
            L.push(LuaValue.ofInteger(0));
            return 1;
        case 1: // "restart"
            L.mainThread.gcRunning = true;
            L.push(LuaValue.ofInteger(0));
            return 1;
        default:
            break; // "collect"
        }
        doCollect(L);
        L.push(LuaValue.ofInteger(0));
        return 1;
    }

    /**
     * 一次「完整收集」在本实现里的等价物：清栈死区 → {@code System.gc()} →
     * 催终结 → 排空待终结队列 → 递增收集轮次。
     * <p>
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
    private static void doCollect(LuaState L) {
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
    }

    /* ==================== 加载 ==================== */

    /** {@code LUA_SIGNATURE} 的第一个字节；以它开头的就是二进制 chunk */
    private static final int SIGNATURE0 = Lua.LUA_SIGNATURE0;

    /**
     * {@code luaB_load}。
     * <p>
     * 二进制 chunk 目前只能挡下来——{@code luaU_undump} 还没搬，硬吃掉会静默
     * 编出一个错误的 chunk。挡的时候<b>照 C 的 {@code checkmode} 先报模式错</b>，
     * 模式没错才报「不支持」，这样 mode 的语义先对上。
     */
    private static int loadFn(LuaState L) {
        LuaValue a = L.arg1(1);
        String mode = LuaAux.optString(L, 3, LuaString.latin1("bt")).toJavaString();
        int env = L.isNoneArg(4) ? 0 : 4;
        // lua_tolstring：<b>数字也算字符串</b>，load(42) 编的是 "42"
        LuaString src = (a.isString() || a.isNumber()) ? LuaAux.asString(a) : null;
        if (src != null) {
            LuaString chunkname = LuaAux.optString(L, 2, src);
            return loadAux(L, loadBuffer(L, src.bytes, chunkname.bytes, mode), env);
        }
        // 否则第一个参数必须是读函数
        LuaString chunkname = LuaAux.optString(L, 2, LuaString.latin1("=(load)"));
        LuaAux.checkType(L, 1, Lua.TFUNCTION);
        Zio.Source readerSrc = readerSource(L, a);
        return loadAux(L, loadZio(L, new Zio(readerSrc), chunkname.bytes, mode), env);
    }

    /**
     * {@code generic_reader}：把 Lua 侧的读函数包成一条惰性字节流。
     * <p>
     * <b>必须是惰性的</b>。先一股脑读完再解析，遇到永不返回 nil 的读函数就
     * 会一直累积；而 C 那边是边解析边要，语法错误一冒出来就停了。
     * <p>
     * 返回 nil 表示结束；返回非字符串非数字要报
     * {@code reader function must return a string}（这里用
     * {@code lua_isstring} 判，所以数字是合法的）。
     */
    private static Zio.Source readerSource(LuaState L, LuaValue reader) {
        return () -> {
            int f = L.top;
            L.ensureSlots(f + 2);
            L.stack[f].set(reader);
            L.top = f + 1;
            L.callNoYield(f, 1); /* lbaselib.c: generic_reader 用 lua_call —— 不可 yield */
            LuaValue r = new LuaValue(L.stack[f]);
            L.top = f;
            if (r.isNil()) {
                return null; // 读函数返回 nil：流结束
            }
            if (!(r.isString() || r.isNumber())) {
                throw L.libError("reader function must return a string");
            }
            return LuaAux.asString(r).bytes;
        };
    }

    /**
     * 编译一段源码。
     *
     * @return {@code null} 表示成功（闭包已压栈）；否则是那条加载期错误
     */
    private static LuaError loadBuffer(LuaState L, byte[] src, byte[] chunkname, String mode) {
        return loadZio(L, new Zio(src), chunkname, mode);
    }

    private static LuaError loadZio(LuaState L, Zio z, byte[] chunkname, String mode) {
        /*
         * 整段都必须在 try 里，<b>包括最前面那次窥探</b>。读函数是惰性的，
         * 第一个字节就要调它一次；而读函数里的错误（返回了非字符串、或自己
         * error 了）在 C 那边是落在 luaD_protectedparser 的保护区里，
         * 于是 load 返回 nil + 消息，<b>不抛</b>。窥探写在外面就会让
         * "reader function must return a string" 变成抛出去的错误。
         */
        try {
            int first = z.getc();
            if (first == SIGNATURE0) {
                if (mode.indexOf('b') < 0) {
                    throw new LuaError(LuaValue.ofString(LuaString.latin1(
                            "attempt to load a binary chunk (mode is '" + mode + "')")));
                }
                /*
                 * 二进制分支<b>不回退</b>首字节：C 的 luaU_undump 的注释写得很直白，
                 * "skip 1st char (already read and checked)"——头部的签名首字节
                 * 已经归 f_parser 了，checkHeader 从第二个字节开始比。
                 * 文本分支则要回退（或把首字符交给 parser），否则会吃掉源码第一个字节。
                 */
                L.push(LuaValue.ofClosure(L.undump(z, chunkname)));
                return null;
            }
            if (mode.indexOf('t') < 0) {
                throw new LuaError(LuaValue.ofString(LuaString.latin1(
                        "attempt to load a text chunk (mode is '" + mode + "')")));
            }
            /*
             * 首字节<b>传进去</b>而不是回退。回退看着等价，但对惰性读函数不等价：
             * 词法器起步时会再 {@code getc()} 一次，而那一次会<b>真的再调一遍读
             * 函数</b>。首字节是 EOZ 时差别最大——参考实现就此认定流已结束，
             * 这里却会重新去要下一批。
             */
            L.push(LuaValue.ofClosure(L.load(z, chunkname, first)));
            return null;
        } catch (LuaError e) {
            return e;
        }
    }

    /** {@code load_aux}：成功就（可能）设上 {@code _ENV} 返回 1 个值，失败给 nil + 消息 */
    private static int loadAux(LuaState L, LuaError err, int envidx) {
        if (err == null) {
            if (envidx != 0) {
                L.pushValue(envidx);
                LuaValue f = L.stack[L.top - 2];
                if (f.tt == Lua.VLIF) {
                    LClosure cl = (LClosure) f.o;
                    if (cl.upvals.length >= 1 && cl.upvals[0] != null) {
                        cl.upvals[0].value().set(L.stack[L.top - 1]);
                    }
                }
                L.top--; // 无论设没设上，env 都要弹掉
            }
            return 1;
        }
        LuaAux.pushFail(L);
        L.push(err.value == null ? LuaValue.ofNil() : err.value);
        return 2;
    }

    /** {@code luaB_loadfile} */
    private static int loadfileFn(LuaState L) {
        LuaString fname = LuaAux.optString(L, 1, null);
        LuaString modeArg = LuaAux.optString(L, 2, null);
        // luaL_loadfilex 的 mode 是 NULL 时内部按 "bt" 处理
        String mode = (modeArg == null) ? "bt" : modeArg.toJavaString();
        int env = L.isNoneArg(3) ? 0 : 3;
        if (fname == null) {
            // 无参：从 stdin 读。chunk 名是 "=stdin"
            byte[] src;
            try {
                src = L.in.readAllBytes();
            } catch (java.io.IOException e) {
                LuaAux.pushFail(L);
                L.push(LuaValue.ofString(LuaString.latin1("cannot read stdin")));
                return 2;
            }
            return loadAux(L, loadBuffer(L, src, LuaString.latin1("=stdin").bytes, mode), env);
        }
        String path = fname.toJavaString();
        byte[] src;
        try {
            // luaL_loadfilex 会先吃掉 BOM 与首行的 #!...；这一步属于加载层，
            // 用 load 加载字符串时首行写 '#' 照样报 unexpected symbol
            src = Parser.skipFilePreamble(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
        } catch (Exception e) {
            LuaAux.pushFail(L);
            L.push(LuaValue.ofString(LuaString.latin1(
                    "cannot open " + path + ": " + LuaAux.strError(LuaAux.errnoOf(
                            (e instanceof java.io.IOException)
                                    ? (java.io.IOException) e : null)))));
            return 2;
        }
        /*
         * luaL_loadfilex 的 chunk 名是 "@" + 文件名。
         * 加载期错误打印时 luaO_chunkid 会把它剥掉。
         */
        return loadAux(L, loadBuffer(L, src, LuaString.utf8("@" + path).bytes, mode), env);
    }

    /**
     * {@code lbaselib.c: dofilecont}：yield 恢复后，结果已经在栈上，只要报出个数。
     * <p>
     * C 版算的是 {@code lua_gettop(L) - 1}（栈顶减去「帧底 + 1」再减去 chunk
     * 本身那一格）。这里直接把 chunk 的下标当 {@code ctx} 传进来，省得再推一遍
     * ——{@code ctx} 在 {@code lua_callk} 里本来就是给续体用的。
     */
    private static final LuaKFunction DOFILE_CONT = (L, status, ctx) -> L.top - ctx;

    /**
     * {@code luaB_dofile}。
     * <p>
     * 注意它<b>不吞加载错误</b>：{@code dofile("nope.lua")} 直接把错误抛出去，
     * 而不是像 {@code loadfile} 那样给 {@code nil, msg}。
     */
    private static int dofile(LuaState L) {
        LuaString fname = LuaAux.optString(L, 1, null);
        L.setTop(1);
        int n = loadfileFn(L);
        if (n == 2) {
            // 加载失败：栈顶那条消息要当错误抛出去，不是当返回值交出去
            LuaValue msg = new LuaValue(L.stack[L.top - 1]);
            L.top -= 2;
            throw new LuaError(msg);
        }
        int f = L.top - 1; // 闭包在栈顶，0 个参数
        /*
         * {@code lua_callk(L, 0, LUA_MULTRET, 0, dofilecont)}：在 dofile 出来的
         * chunk 里 yield 是允许的，所以这里要带续体。续体什么也不做，直接沿用
         * 恢复时栈上已有的结果个数。
         */
        L.callK(f, LuaState.MULTRET, f, DOFILE_CONT);
        return L.top - f;
    }

    /* ==================== 小工具 ==================== */

    private static void setFn(LuaTable t, String name, LuaFunction f) {
        t.set(name, LuaValue.ofFunction(f));
    }

    /**
     * {@code luaL_checkinteger} 的 0 下标形态，<b>直接转调 {@link LuaAux#checkInteger}</b>。
     * <p>
     * 这里<b>不再自己实现一份</b>。曾经有两份，而这一份少了两样东西，四个调用点
     * （{@code tonumber} 的进制、{@code select} 的下标、{@code error} 的层号、
     * {@code ipairs} 的迭代器下标）全都跟着错：
     * <ul>
     *   <li><b>函数名前缀</b>：{@code bad argument #2 (...)} 而参考实现是
     *       {@code bad argument #2 to 'tonumber' (...)}——前缀来自
     *       {@code lauxlib.c: luaL_argerror} 里的 {@code pushglobalfuncname}。</li>
     *   <li><b>「无整数表示」这一档</b>：{@code 1.5} 报成了
     *       {@code number expected, got number}（数字说自己是数字，读着就不对），
     *       参考实现报 {@code number has no integer representation}。</li>
     * </ul>
     * 这是「同一个 C 函数两份实现」的老毛病，和 {@code Vm.formatG} /
     * {@code StringLib.generalDigits} 那次是同一类。
     */
    static long checkInteger(LuaState L, int i) {
        return LuaAux.checkInteger(L, i + 1);
    }

    /** 把 {@link LuaValue} 变成函数值（供外部注册额外库函数） */
    public static LuaValue functionValue(LuaFunction f) {
        return LuaValue.ofFunction(f);
    }
}
