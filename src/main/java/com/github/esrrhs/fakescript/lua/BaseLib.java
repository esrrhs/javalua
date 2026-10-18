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
        setFn(g, "warn", BaseLib::warn);

        /*
         * {@code lbaselib.c: luaopen_base} 最后两件事：设 {@code _G} 与
         * {@code _VERSION}。{@code _G} 在 {@link LuaState} 的构造里就已经挂上了
         * （注册表也要用到它），这里只补 {@code _VERSION}。
         * <p>
         * 别小看这一个字符串：{@code _VERSION} 是<b>最常见的版本探测手段</b>，
         * 真实项目里到处是 {@code if _VERSION == "Lua 5.1" then ... end} 这种
         * 分支，缺了它走的是 else —— 不报错，只是行为悄悄不同，正好是最难查的那类。
         */
        g.set("_VERSION", LuaValue.ofString(LuaString.latin1(Lua.LUA_VERSION)));

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

    /** {@code luaB_type}：{@code luaL_argcheck(t != LUA_TNONE, 1, "value expected")} */
    private static int type(LuaState L) {
        LuaAux.argCheck(L, !L.isNoneArg(1), 1, "value expected");
        L.push(LuaValue.ofString(LuaString.latin1(L.arg(0).typeName())));
        return 1;
    }

    /** {@code luaB_tostring} */
    private static int tostring(LuaState L) {
        LuaAux.checkAny(L, 1);
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
            LuaString kind = LuaString.latin1(v.typeName());
            LuaValue name = metamethodByName(L, v, "__name");
            if (name != null && name.isString()) {
                /*
                 * __name 走的是 C 字符串语义：lauxlib.c: luaL_tolstring 取到 __name
                 * 后用 lua_tostring 转成 const char*，再拿 "%s" 拼进 "名字: 0x地址"，
                 * 于是名字在第一个 \0 处被砍断。__name = "A\0B" 只打印出 "A"。
                 */
                kind = name.asString().toCString();
            }
            /*
             * 地址用 C 的 "%p" 格式：0x 加十六进制、<b>不补前导零</b>
             * （macOS 打出 0x7ab30002c0，glibc 打出 0x55d3...）。补成 16 位
             * 只是看着整齐，跟参考实现对不上。
             */
            String ptr = "0x" + Integer.toHexString(System.identityHashCode(v.o));
            return kind.concat(LuaString.latin1(": " + ptr));
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

    /**
     * {@code luaB_tonumber}。
     * <p>
     * 无参那句是 {@code luaL_checkany(L, 1)}，落点是
     * {@code luaL_argerror(L, 1, "value expected")}，函数名同样<b>由调用者那一帧
     * 反推</b>：{@code local f = tonumber; f()} 参考实现报 {@code 'f'}，不是
     * {@code 'tonumber'}。以前这里把全名写死，与
     * {@code coroutine} 那六个函数是同一个错（见 {@link CoroutineLib#getco}）。
     */
    private static int tonumber(LuaState L) {
        int n = L.argCount();
        if (n == 0) {
            throw L.argError(1, "value expected");
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
        /*
         * 三道校验的<b>顺序</b>照抄 C：{@code luaL_checkinteger(2)} →
         * {@code luaL_checktype(1, LUA_TSTRING)} → {@code luaL_argcheck(2 <= base <= 36)}。
         * 范围检查在<b>最后</b>，所以 {@code tonumber(1, 1)} 报的是
         * {@code bad argument #1 (string expected, got number)} 而不是
         * {@code bad argument #2 (base out of range)} —— 曾经把范围检查放在
         * 中间，那两条全反了。
         */
        long base = checkInteger(L, 1);
        LuaAux.checkType(L, 1, Lua.TSTRING);
        LuaAux.argCheck(L, base >= 2 && base <= 36, 2, "base out of range");
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

    /**
     * {@code luaB_rawget}。
     * <p>
     * C 只做 {@code luaL_checktype(1, TABLE)} 与 {@code luaL_checkany(2)}
     * <b>两条</b>校验，<b>没有</b>「键不能是 nil」那条 —— {@code rawget({}, nil)}
     * 是 {@code true nil}（nil 键走 {@code luaH_get} 落到 dummynode）。报
     * 「table index is nil」的是 {@code rawset}，那条来自
     * {@code ltable.c: luaH_newkey}。
     * <p>
     * 曾经这里既漏了 {@code checkAny(2)}，又自作主张加了 nil 键校验。
     */
    private static int rawget(LuaState L) {
        LuaAux.checkType(L, 1, Lua.TTABLE);
        LuaAux.checkAny(L, 2);
        LuaValue t = L.arg(0);
        LuaValue k = L.arg(1);
        LuaValue v = t.asTable().get(k);
        L.push((v == null) ? LuaValue.ofNil() : v);
        return 1;
    }

    /** {@code luaB_rawset} */
    private static int rawset(LuaState L) {
        LuaAux.checkType(L, 1, Lua.TTABLE);
        LuaAux.checkAny(L, 2);
        LuaAux.checkAny(L, 3);
        LuaValue t = L.arg(0);
        t.asTable().set(L.arg(1), L.arg(2));
        L.push(t);
        return 1;
    }

    /**
     * {@code luaB_rawequal}。
     * <p>
     * {@code luaL_checkany} 要做<b>两遍</b>（第 1、第 2 参数各一遍）：
     * {@code rawequal(1)} 报 {@code bad argument #2 ... (value expected)}。
     * 曾经一遍也没做，于是 {@code pcall(rawequal, 1)} 直接返回 {@code true true}。
     */
    private static int rawequal(LuaState L) {
        LuaAux.checkAny(L, 1);
        LuaAux.checkAny(L, 2);
        L.push(LuaValue.ofBoolean(LuaValue.rawEqual(L.arg(0), L.arg(1))));
        return 1;
    }

    /**
     * {@code luaB_rawlen}：走 {@code luaL_argexpected}，条件是「类型是表或字符串」。
     * <p>
     * 缺参时 {@code lua_type} 是 {@code LUA_TNONE}，既不是表也不是字符串，
     * 所以报 {@code got no value} —— 用 {@link LuaState#argTypeName} 取类型名，
     * 它把越界翻译成 {@code no value}。
     */
    private static int rawlen(LuaState L) {
        int t = L.argBaseType(1);
        LuaAux.argExpected(L, t == Lua.TTABLE || t == Lua.TSTRING, 1, "table or string");
        LuaValue v = L.arg(0);
        if (v.isTable()) {
            L.push(LuaValue.ofInteger(v.asTable().length()));
            return 1;
        }
        L.push(LuaValue.ofInteger(v.asString().length()));
        return 1;
    }

    /** {@code luaB_next} */
    private static int next(LuaState L) {
        /*
         * C 是 {@code luaL_checktype(1, TABLE)} 然后 {@code lua_settop(L, 2)}
         * —— 第二个参数不给就<b>补一个</b>（补出来的是 nil），不是报错。
         */
        LuaAux.checkType(L, 1, Lua.TTABLE);
        LuaValue t = L.arg(0);
        LuaValue k = L.arg(1);
        LuaValue[] kv;
        try {
            kv = t.asTable().next(k);
        } catch (IllegalArgumentException e) {
            /*
             * 必须是 {@link LuaState#runError} 而不是 {@link LuaState#libError}。
             * <p>
             * C 里这句话在 {@code ltable.c: findindex}，是
             * {@code luaG_runerror} 而不是 {@code luaL_error} —— 前者只在
             * {@code isLua(ci)} 时才加位置，而跑到这儿 {@code ci} 是
             * {@code luaB_next} 这个 <b>C 帧</b>，于是<b>永远没有位置</b>。
             * {@code libError} 走的是 {@code luaL_where(L, 1)}，会往回跳一层
             * 拿到调用者的行号，于是凭空多出 {@code case.lua:N:}（约定 #8 那句
             * 「库函数一律 libError」在这里不成立——这条消息的源头不在库代码里）。
             * <p>
             * 实测：{@code next({}, 1)} 参考给 {@code invalid key to 'next'}，
             * 本实现给 {@code case.lua:12: invalid key to 'next'}。
             */
            throw L.runError("invalid key to 'next'");
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

    /**
     * {@code luaB_pairs}（含 {@code __pairs} 元方法）。
     * <p>
     * C 只做 {@code luaL_checkany(L, 1)}——<b>不</b>要求是表。没有
     * {@code __pairs} 时照样返回 {@code (next, 值, nil)}，于是
     * {@code pairs(5)} 是三个返回值而不是报错；真正炸是在迭代时
     * {@code next(5)} 报 {@code bad argument #1 to 'next' (table expected, got number)}。
     * 曾经这里多加了「必须是表」的校验，把 {@code pairs(5)} 变成报错。
     */
    private static int pairs(LuaState L) {
        LuaAux.checkAny(L, 1);
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
        L.pushFunction(BaseLib::next);
        L.push(t);
        L.pushNil();
        return 3;
    }

    /** {@code luaB_ipairs}：与 {@code pairs} 一样只 {@code luaL_checkany} */
    private static int ipairs(LuaState L) {
        LuaAux.checkAny(L, 1);
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

    /**
     * {@code luaB_select}。
     * <p>
     * 两处容易写错，都是实测出来的：
     * <ul>
     *   <li><b>{@code #} 只比第一个字节</b>：C 是
     *       {@code *lua_tostring(L, 1) == '#'}，所以 {@code select("#abc", 1)}
     *       也是「数个数」，返回 1 —— 判断整串等于 {@code "#"} 就漏了。</li>
     *   <li><b>缺参报的是 {@code number expected, got no value}</b>：没有
     *       「至少一个参数」那条检查，缺参直接落进
     *       {@code luaL_checkinteger(L, 1)}。曾经先判 {@code n == 0} 报
     *       {@code value expected}，跟参考实现对不上。</li>
     * </ul>
     */
    private static int select(LuaState L) {
        int n = L.argCount();
        LuaValue first = L.arg(0);
        if (first.isString() && first.asString().length() > 0
                && first.asString().byteAt(0) == '#') {
            L.push(LuaValue.ofInteger(n - 1));
            return 1;
        }
        long i = checkInteger(L, 0);
        if (i < 0) {
            i = n + i;
        } else if (i > n) {
            i = n;
        }
        LuaAux.argCheck(L, i >= 1, 1, "index out of range");
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
            L.push(LuaValue.ofString(L.whereAt((int) level))); // luaL_where（字节串，别转 Java String）
            L.push(msg);          // lua_pushvalue(L, 1)
            Vm.concat(L, 2);      // lua_concat(L, 2)
        }
        // lua_error(L)：抛栈顶那个值。位置已经拼进去了，errorAt 的 level 传 0
        // 表示「别再加一次」——但 where 字段仍非 null，pcall 也不会补。
        throw L.errorAt(L.stack[L.top - 1], 0);
    }

    /**
     * {@code luaB_assert}。
     * <p>
     * 校验的位置照抄 C：{@code luaL_checkany} 在<b>条件为假</b>那一支里，
     * 不在函数开头。{@code assert()} 走的是「条件假 → 报 value expected」。
     * 曾经写成「一个参数也没有就报」，措辞对、位置不对 —— 而真正咬人的是
     * {@code L.arg(0)} 会读到残值：残值为真时 {@code assert()} 直接返回 0 个值。
     */
    private static int assertFn(LuaState L) {
        int n = L.argCount();
        if (L.arg(0).toBoolean()) {
            for (int i = 0; i < n; i++) {
                L.push(L.arg(i));
            }
            return n;
        }
        LuaAux.checkAny(L, 1);
        LuaValue msg = (n > 1) ? L.arg(1)
                : LuaValue.ofString(LuaString.latin1("assertion failed!"));
        throw L.errorAt(msg, 1);
    }

    /** {@code luaB_pcall} */
    private static int pcall(LuaState L) {
        LuaAux.checkAny(L, 1);
        return doPcall(L, false);
    }

    /**
     * {@code luaB_xpcall}。
     * <p>
     * 第一件事是 {@code luaL_checktype(L, 2, LUA_TFUNCTION)} —— <b>消息处理函数
     * 必须是函数</b>，而且这条检查在调用被调函数<b>之前</b>就发生。曾经写成
     * 「只判参数够不够两个」，于是 {@code xpcall(f, 5)} 不报错，而是照常调用
     * {@code f}：{@code f} 抛错后拿 5 当处理函数，得到
     * {@code true, false, "error in error handling"}；参考实现给的是
     * {@code false  bad argument #2 to 'xpcall' (function expected, got number)}。
     * 参数不足时同理：报 {@code function expected, got no value}，
     * 而不是含糊的 {@code value expected}。
     */
    private static int xpcall(LuaState L) {
        LuaAux.checkType(L, 2, Lua.TFUNCTION);
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
        LuaAux.checkType(L, 1, Lua.TTABLE);
        /*
         * 第二个参数走 {@code luaL_argexpected}，措辞是
         * 「nil or table expected, got <b>实际类型</b>」——那个 {@code got} 不能省，
         * 省了就成了裸的「nil or table expected」。
         * 条件是 {@code lua_type(2)} 属于 {nil, table}：<b>缺参是 TNONE</b>，
         * 不属于，所以 {@code setmetatable({})} 报错，报的是 {@code got no value}。
         */
        int t2 = L.argBaseType(2);
        LuaAux.argExpected(L, t2 == Lua.TNIL || t2 == Lua.TTABLE, 2, "nil or table");
        LuaValue t = L.arg(0);
        LuaValue mt = L.arg(1);
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
        LuaAux.checkAny(L, 1);
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
            /*
             * 判据是 {@code isdecGCmodegen(g)}（{@code gckind == KGC_GEN ||
             * lastatomic != 0}）而不是 {@code gckind} 本身 —— 见
             * {@link LuaState#gcLastAtomic}。两种切换都会把 {@code lastatomic}
             * 清 0，所以切过一次之后就只看 {@code gckind} 了。
             */
            boolean prevGen = (m.gcKind == LuaState.LUA_GCGEN) || m.gcLastAtomic;
            m.gcKind = (o == 8) ? LuaState.LUA_GCGEN : LuaState.LUA_GCINC;
            m.gcLastAtomic = false;
            L.push(LuaValue.ofString(LuaString.latin1(
                    prevGen ? "generational" : "incremental")));
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
        /*
         * 请求 GC、催终结、跑 __gc 三件事的循环都在里面：System.gc() 与 JVM
         * 的终结线程之间是异步的（见 LuaState#collectAndRunFinalizers）。
         */
        L.mainThread.collectAndRunFinalizers(L);
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
        // checkmode 比的是 const char*：load(src, name, "t\0b") 的模式是 "t"
        // mode 会原样拼进错误消息（mode is 'x'），Latin-1 往返才不丢字节
        String mode = LuaAux.optString(L, 3, LuaString.latin1("bt")).toCString().toLatin1String();
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

    /** {@code lbaselib.c: checkmode} —— 结尾是 {@code luaD_throw(LUA_ERRSYNTAX)}，直跳 */
    private static LuaError modeError(String what, String mode) {
        LuaError e = new LuaError(LuaValue.ofString(LuaString.latin1(
                "attempt to load a " + what + " chunk (mode is '" + mode + "')")));
        e.directThrow = true;
        return e;
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
                    throw modeError("binary", mode);
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
                throw modeError("text", mode);
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
            /*
             * 整段 try 对应 C 的 {@code luaD_protectedparser}，而它把
             * {@code L->errfunc} <b>原样</b>传进内层的 {@code luaD_pcall}。
             * 于是「不是 {@code luaD_throw} 直跳的错」在<b>抛出点</b>就被外层
             * {@code xpcall} 的处理器接住了 —— 实测
             * {@code xpcall(function() return load(function() error("boom") end) end, H)}
             * 参考实现给 {@code nil, "H:case.lua:13: boom"}；语法错误因为走
             * {@code luaD_throw} 直跳，给的是裸消息，两种都得对上。
             */
            if (!e.directThrow && L.errFunc != null) {
                return new LuaError(L.applyErrFunc(e.value), e.where);
            }
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
        String mode = (modeArg == null) ? "bt" : modeArg.toCString().toLatin1String();
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
        // luaL_loadfilex 的 filename 是 const char*：第一个 \0 之后的内容既进不了
        // fopen，也进不了 "cannot open ..." 那条消息
        String path = fname.toCString().toJavaString();
        byte[] src;
        try {
            // luaL_loadfilex 会先吃掉 BOM 与首行的 #!...；这一步属于加载层，
            // 用 load 加载字符串时首行写 '#' 照样报 unexpected symbol
            src = Parser.skipFilePreamble(
                    java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
        } catch (Exception e) {
            /*
             * 空路径是 C 与 Java 唯一判不一致的一处：{@code fopen("")} 给
             * {@code ENOENT}（实测 {@code loadfile("")} 报
             * {@code cannot open : No such file or directory}），而 Java 把空路径
             * 解析成当前目录、读的时候给 {@code Is a directory}。
             * <p>
             * 目录走的是<b>另一条</b>分支，之前这里是错的：{@code loadfile("/tmp")}
             * 在 C 里 fopen <b>成功</b>、失败在读，{@code luaL_loadfilex} 的
             * {@code ferror} 于是为真，报的是
             * {@code cannot read /tmp: Is a directory} —— 不是
             * {@code cannot open ...: Input/output error}。实测（参考二进制）：
             * <pre>
             * loadfile(dir) -> nil, "cannot read /tmp/fkdir/adir.lua: Is a directory"
             * </pre>
             */
            boolean isDir = !path.isEmpty() && new java.io.File(path).isDirectory();
            int en = path.isEmpty() ? 2
                    : isDir ? 21
                    : LuaAux.errnoOf((e instanceof java.io.IOException)
                            ? (java.io.IOException) e : null);
            LuaAux.pushFail(L);
            L.push(LuaValue.ofString(LuaString.latin1(
                    (isDir ? "cannot read " : "cannot open ") + path + ": "
                            + LuaAux.strError(en))));
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

    /**
     * {@code lbaselib.c: luaB_warn}。
     * <p>
     * 两条不显眼的约束，都是逐句照 C 抄出来的，凭记忆推会推反：
     * <ul>
     *   <li><b>先把参数全查完，再开始拼消息。</b>C 的注释就写在函数头上：
     *       「Check first for errors; otherwise an error may interrupt the
     *       composition of a warning, leaving it unfinished」。所以
     *       {@code warn("PARTIAL", {})} 一条警告都不吐，而不是先吐半句再炸——
     *       实测参考实现的 stderr 上确实什么都没有。</li>
     *   <li><b>参数是 C 字符串。</b>循环里用的是 {@code lua_tostring}，拿到的是
     *       {@code const char*}，所以 {@code warn("a\0b")} 只写 {@code a}。
     *       这条还连带影响控制消息：{@code warn("@on\0x")} 截断后是 {@code "@on"}，
     *       <b>能</b>把警告打开；不截断就变成未知控制消息被吃掉。</li>
     * </ul>
     * <p>
     * 数字参数是<b>允许</b>的（{@code luaL_checkstring} 底下是
     * {@code lua_tolstring}，数字会被转成字符串）：{@code warn(1, 2.5, "x")}
     * 参考实现输出 {@code Lua warning: 12.5x}。
     */
    private static int warn(LuaState L) {
        int n = L.getTop();
        if (n < 1) {
            /*
             * C 版循环之前单独有一句 {@code luaL_checkstring(L, 1)}：一个参数
             * 都没给时这个检查<b>照样发生</b>，报的是
             * 「bad argument #1 to 'warn' (string expected, got no value)」。
             * 只写 {@code for i = 1..n} 的话 n==0 时循环一次都不进，这条报错
             * 就没了，而 {@code warn()} 会静默成功。
             */
            LuaAux.checkString(L, 1);
        }
        /*
         * 存 {@link LuaString} 而不是 {@code String}：{@code lua_tostring} 拿到的是
         * {@code const char*}，截断发生在字节上，而 {@code toJavaString} 会先按
         * UTF-8 解码——{@code warn("\xff")} 必须原样写出 {@code 0xFF}。
         */
        LuaString[] parts = new LuaString[n];
        for (int i = 1; i <= n; i++) {
            parts[i - 1] = LuaAux.checkString(L, i);
        }
        for (int i = 0; i < n - 1; i++) {
            L.warning(parts[i], true);
        }
        L.warning(parts[n - 1], false);
        return 0;
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
