package com.github.esrrhs.javalua;

/**
 * C API 的语义 —— 对应 {@code lapi.c}，{@code luaL_*} 的在 {@link LuaAux}。
 * <p>
 * 由 {@code native/gen_capi.py} <b>初次</b>生成，之后手改：脚本见文件已存在就
 * 跳过，不会覆盖。分发表 {@link CApi} 每次都重新生成，两边靠<b>方法名</b>对上，
 * 所以这里的方法签名不能改。
 * <p>
 * 下标约定：{@code L} 是句柄（经 {@link NativeBridge#state} 换成
 * {@link LuaState}），其余整数下标就是 C 的那套——正数从 {@code ci.func+1} 起，
 * 负数从栈顶倒数，还有 {@code LUA_REGISTRYINDEX} 和 upvalue 两个伪索引。
 */
final class Api {

    private Api() {
    }

    static RuntimeException unimplemented(String what) {
        return new UnsupportedOperationException("C API 未实现: " + what);
    }

    /** 句柄换回 {@link LuaState} */
    static LuaState st(long h) {
        return NativeBridge.state(h);
    }

    /** {@code lua_compare} 的 {@code LUA_OPLE}：与 {@link Vm#lessThan} 同构 */
    private static boolean le(LuaState L, LuaValue a, LuaValue b) {
        if (a.isInteger() && b.isInteger()) {
            return a.i <= b.i;
        }
        if (a.isNumber() && b.isNumber()) {
            return !Vm.lessThan(L, b, a);
        }
        if (a.isString() && b.isString()) {
            return Vm.lStrcmp(a.asString(), b.asString()) <= 0;
        }
        return LuaTm.callOrderTm(L, a, b, Tm.TM_LE);
    }

    /** {@code strstr}：在 {@code hay} 里从 {@code from} 起找 {@code needle} */
    private static int indexOf(byte[] hay, byte[] needle, int from) {
        if (needle.length == 0) {
            return from;
        }
        for (int i = from; i + needle.length <= hay.length; i++) {
            int j = 0;
            while (j < needle.length && hay[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameBytes(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static long strToNative(String s) {
        return NativeBridge.strToNative(s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    /** {@code %p} 的文本：C 的 {@code lua_topointer} 打出来是 {@code 0x...} */
    private static String ptrText(long p) {
        return "0x" + Long.toHexString(p);
    }

    /**
     * {@code luaL_loadbufferx} / {@code luaL_loadstring} 共用的加载。
     * <p>
     * chunk 名是<b>字节串</b>：{@code luaL_loadbufferx} 的 {@code name} 是
     * {@code const char*}，可以是任意字节（还可能是 NULL，那时 C 用 {@code "?"}）。
     * 走 Java String 再 UTF-8 编码回去既会失真，{@code name == 0} 时还会
     * 直接 NPE——实测 {@code luaL_loadbufferx(L, s, n, NULL, "t")} 在参考实现上
     * 是合法调用。
     */
    private static long loadBytes(long L, byte[] src, byte[] chunkName, String mode) {
        LuaState S = st(L);
        /*
         * 走 {@link BaseLib#loadZio} 而不是自己再写一份：{@code mode} 那两档
         * 报错、二进制 chunk 的识别、以及「读函数里的错误不抛而是变成返回值」
         * 三件事都在里面。这里绕开它自己解析，{@code luaL_loadbufferx(s, n,
         * "=x", "b")} 就会静默成功——而参考实现给的是
         * {@code nil, "attempt to load a text chunk (mode is 'b')"}。
         * <p>
         * {@code mode == null} 是 {@code luaL_loadstring} 与
         * {@code luaL_loadbufferx} 的默认档：C 的 {@code checkmode} 对
         * {@code NULL} 一律放行，等价于 {@code "bt"}。
         */
        LuaError err = BaseLib.loadZio(S, new Zio(src),
                (chunkName == null) ? new byte[] {'?'} : chunkName,
                (mode == null) ? "bt" : mode);
        if (err == null) {
            return 0; // LUA_OK，闭包已在栈顶
        }
        pushVal(S, (err.value == null) ? LuaValue.ofNil() : err.value);
        /*
         * {@code lua.h: LUA_ERRSYNTAX} 是 <b>3</b>，不是 2。{@code lua_load}
         * 的返回值 C 扩展是拿去和 {@code LUA_OK / LUA_ERRSYNTAX} 比的，
         * 给 2 会被当成别的失败档。
         */
        return LuaState.LUA_ERRSYNTAX;
    }

    /** {@code lua_rotate} 用的原地翻转 */
    private static void reverse(LuaState s, int from, int to) {
        while (from < to) {
            LuaValue tmp = new LuaValue(s.stack[from]);
            s.stack[from].set(s.stack[to]);
            s.stack[to].set(tmp);
            from++;
            to--;
        }
    }

    /** {@code luaH_get} 的裸查：不走 {@code __index} */
    private static LuaValue rawSlot(LuaTable t, LuaValue key) {
        return t.get(key);
    }

    /* ==================== 下标与取值 ==================== */

    /** {@code lua.h: LUA_REGISTRYINDEX} = {@code -LUAI_MAXSTACK - 1000} */
    static final int REGISTRY = -1001000;

    /** {@code lua.h: LUA_RIDX_LAST}（= {@code LUA_RIDX_GLOBALS}） */
    static final int RIDX_LAST = 2;

    /** {@code lauxlib.c: #define freelist (LUA_RIDX_LAST + 1)} */
    static final int FREELIST = RIDX_LAST + 1;

    /** {@code lua_upvalueindex(i)} —— i 从 1 起，与 C 一致 */
    static int upvalueIndex(int i) {
        return REGISTRY - i;
    }

    /**
     * C 的 {@code index2addr}。
     * <p>
     * 越界给 {@code null}：C 里那是对着 {@code nilvalue} 取值，而 {@code lua_type}
     * 报 {@code LUA_TNONE}——两者要分得开，所以这里不能随手折成 nil。
     */
    static LuaValue val(LuaState L, int idx) {
        if (idx == REGISTRY) {
            return LuaValue.ofTable(L.registry);
        }
        if (idx < REGISTRY) {
            return L.upvalue(REGISTRY - idx - 1);
        }
        int p = idx > 0 ? L.ci.func + idx : L.top + idx;
        return (p >= 0 && p < L.top) ? L.stack[p] : null;
    }

    static void setVal(LuaState L, int idx, LuaValue v) {
        if (idx == REGISTRY) {
            throw new IllegalStateException("registry 不可整体赋值");
        }
        if (idx < REGISTRY) {
            LuaValue f = L.stack[L.ci.func];
            int i = REGISTRY - idx - 1;
            if (f.tt == Lua.VCCL) {
                ((CClosure) f.o).upvalue[i].set(v);
            } else {
                ((LClosure) f.o).upvals[i].v.set(v);
            }
            return;
        }
        int p = idx > 0 ? L.ci.func + idx : L.top + idx;
        if (p >= 0 && p < L.top) {
            L.stack[p].set(v);
        }
    }

    static int typeOf(LuaState L, int idx) {
        LuaValue v = val(L, idx);
        return v == null ? Lua.TNONE : v.baseType();
    }

    /*
     * {@code lobject.h: #define ttype(o) (novariant(rawtt(o)))}
     *
     * <b>5.4.7 的 {@code ttype} 是剥掉变体的</b>（{@code novariant}，不是
     * {@code withvariant}），和 {@code ttnov} 是同一个数：整数和浮点都答
     * {@code 3}，表里不存在答 {@code 0}({@code LUA_TNIL})。
     *
     * 踩过一次：按 5.4.0 的印象以为 {@code ttype} 带变体（浮点 19），于是把
     * getfield 系写成基础、gettable 系写成带变体。参考实测
     * （{@code /tmp/ttypeprobe.c}）七个函数对 1.5 一律给 3，拆是拆错的。
     * 要带变体的是 {@code ttypetag}，不是 {@code ttype}。
     */
    private static int ttype(LuaValue v) {
        return v.baseType();
    }

    /** {@code luaL_checkstack} 之外，所有「栈要长」的地方都过这里 */
    static void grow(LuaState L, int n) {
        L.ensureSlots(L.top + n);
    }

    /** 往栈顶压一个值，槽位不够就先长栈 */
    static void pushVal(LuaState L, LuaValue v) {
        grow(L, 1);
        L.stack[L.top++].set(v);
    }


    static void luaL_addgsub(long b, long s, long p, long r) {
        byte[] sb = NativeBridge.bytesAt(s);
        byte[] pb = NativeBridge.bytesAt(p);
        byte[] rb = NativeBridge.bytesAt(r);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int from = 0;
        while (true) {
            int at = indexOf(sb, pb, from);
            if (at < 0) {
                break;
            }
            out.write(sb, from, at - from);
            out.write(rb, 0, rb.length);
            from = at + pb.length;
        }
        out.write(sb, from, sb.length - from);
        NativeBridge.bufAppend(b, out.toByteArray());
    }

    @Generated
    static void luaL_addlstring(long B, long s, long l) {
        throw new IllegalStateException("luaL_addlstring 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    static void luaL_addstring(long B, long s) {
        throw new IllegalStateException("luaL_addstring 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    static void luaL_addvalue(long B) {
        throw new IllegalStateException("luaL_addvalue 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long luaL_argerror(long L, long arg, long extramsg) {
        /*
         * 不返回。抛出去的 LuaError 会被 shim 接住转成 longjmp——所以这里<b>要</b>
         * 抛，不能只是把消息压栈。
         */
        String m = extramsg == 0 ? "" : new String(NativeBridge.bytesAt(extramsg), java.nio.charset.StandardCharsets.ISO_8859_1);
        throw st(L).argError((int) arg, m);
    }

    @Generated
    static void luaL_buffinit(long L, long B) {
        throw new IllegalStateException("luaL_buffinit 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    static long luaL_buffinitsize(long L, long B, long sz) {
        throw new IllegalStateException("luaL_buffinitsize 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long luaL_callmeta(long L, long obj, long e) {
        LuaState S = st(L);
        long k = NativeBridge.strToNative(NativeBridge.bytesAt(e));
        if (luaL_getmetafield(L, obj, k) == Lua.TNIL) {
            return 0;
        }
        LuaValue f = S.stack[--S.top];
        LuaValue o = val(S, (int) obj);
        grow(S, 3);
        S.stack[S.top++].set(f);
        S.stack[S.top++].set(o);
        S.call(S.top - 2, 1);
        return 1;
    }

    static void luaL_checkany(long L, long arg) {
        LuaState S = st(L);
        if (S.isNoneArg((int) arg)) {
            throw S.argError((int) arg, "value expected");
        }
    }

    static long luaL_checkinteger(long L, long arg) {
        LuaState S = st(L);
        LuaValue v = S.arg1((int) arg);
        if (v.isInteger()) {
            return v.asInteger();
        }
        if (v.isFloat()) {
            long[] out = new long[1];
            if (LuaValue.floatToIntExact(v.asFloat(), out)) {
                return out[0];
            }
            throw S.argError((int) arg, "number has no integer representation");
        }
        if (S.isNoneArg((int) arg)) {
            throw S.typeArgError((int) arg, "number");
        }
        LuaValue n = v.isString() ? Vm.strToNum(v) : null;
        if (n != null && n.isInteger()) {
            return n.asInteger();
        }
        throw S.typeArgError((int) arg, "number");
    }

    static long luaL_checklstring(long L, long arg, long l) {
        /*
         * {@code lauxlib.c} 是「先 {@code lua_tolstring}、转不出来才
         * {@code tag_error}」——所以数字<b>照样会被原地换掉</b>，与
         * {@link #lua_tolstring} 同形。早先这里是「先判类型、再自己转」，
         * 于是不做原地转换。
         */
        LuaState S = st(L);
        long p = lua_tolstring(L, arg, l);
        if (p == 0) {
            throw S.typeArgError((int) arg, "string");
        }
        return p;
    }

    static double luaL_checknumber(long L, long arg) {
        LuaState S = st(L);
        LuaValue v = S.arg1((int) arg);
        if (v.isNumber()) {
            return v.isInteger() ? (double) v.asInteger() : v.asFloat();
        }
        if (!S.isNoneArg((int) arg) && v.isString()) {
            LuaValue n = Vm.strToNum(v);
            if (n != null) {
                return n.isInteger() ? (double) n.asInteger() : n.asFloat();
            }
        }
        throw S.typeArgError((int) arg, "number");
    }

    static long luaL_checkoption(long L, long arg, long def, long packed, long n) {
        LuaState S = st(L);
        LuaString d = def == 0 ? null : LuaString.of(NativeBridge.bytesAt(def));
        LuaString s = LuaAux.optString(S, (int) arg, d);
        long p = packed;
        for (int i = 0; i < (int) n; i++) {
            byte[] b = NativeBridge.bytesAt(p);
            if (sameBytes(b, s.bytes)) {
                return i;
            }
            p += b.length + 1;
        }
        throw S.argError((int) arg, "invalid option '" + s.toLatin1String() + "'");
    }

    static void luaL_checkstack(long L, long sz, long msg) {
        LuaState S = st(L);
        try {
            grow(S, (int) sz);
        } catch (RuntimeException e) {
            String m = (msg == 0) ? "" : new String(NativeBridge.bytesAt(msg), java.nio.charset.StandardCharsets.ISO_8859_1);
            throw S.libError("stack overflow" + (m.isEmpty() ? "" : " (" + m + ")"));
        }
    }

    static void luaL_checktype(long L, long arg, long t) {
        LuaState S = st(L);
        if (S.argBaseType((int) arg) != (int) t) {
            throw S.typeArgError((int) arg, Lua.typeName((int) t) + "");
        }
    }

    static long luaL_checkudata(long L, long ud, long tname) {
        long p = luaL_testudata(L, ud, tname);
        if (p == 0) {
            String tn = tname == 0 ? "?" : new String(NativeBridge.bytesAt(tname), java.nio.charset.StandardCharsets.ISO_8859_1);
            throw st(L).typeArgError((int) ud, tn);
        }
        return p;
    }

    static void luaL_checkversion_(long L, double ver, long sz) {
        /*
         * lauxlib.c：sz 是 LUAL_NUMSIZES，等于 sizeof(lua_Integer)*16 + sizeof(lua_Number)。
         * 真库 mac/linux 上都是 136——扩展编的时候这个值就烧进 .so 了，对不上说明
         * 两边数值类型不一致，C 版直接报错而不是继续跑。
         */
        if (sz != 136) {
            throw st(L).libError("core and library have incompatible numeric types");
        }
        /*
         * 后面那半句原来是漏的：只比 sz，不比版本。于是任何一个声明
         * 「我需要 5.3」的老扩展都能静默加载进来，真 Lua 在这里是
         * {@code luaL_error}（不是返回错误码，是抛）。
         * <p>
         * 消息里两个 {@code %f} 是 C99 默认精度的定点格式（6 位小数），
         * 走 {@code BigDecimal} 而不是 {@code String.format}——后者连舍两次
         * （见 README 浮点格式化那条）。
         */
        if (ver != Lua.VERSION_NUM) {
            /*
             * {@code %f} 在 {@code lua_pushvfstring} 里走的不是 printf 那条路：
             * {@code lobject.c} 的 {@code case 'f'} 是 {@code addnum2buff}，
             * 也就是 {@code tostringbuff} —— {@code %.14g} 之后再给整数样的
             * 结果补 {@code .0}。所以参考实现打的是 {@code 503.0} 而不是
             * {@code 503.000000}（一开始照 printf 写，网格当场红了一条）。
             */
            String msg = "version mismatch: app. needs "
                    + Vm.numberToString(LuaValue.ofFloat(ver))
                    + ", Lua core provides "
                    + Vm.numberToString(LuaValue.ofFloat(Lua.VERSION_NUM));
            throw st(L).libError(msg);
        }
    }

    /*
     * {@code luaL_error} 整条搬到 {@code luashim.c} 里去了：
     * <pre>
     * luaL_where(L, 1); lua_pushvfstring(L, fmt, ap); lua_concat(L, 2);
     * lua_error(L);
     * </pre>
     * 三件事都得在 C 侧做才对：变参只能在 C 里读；{@code lua_pushvfstring}
     * 的动词表和 printf <b>不是</b>一套（见 {@code pushvfshim}）；
     * {@code lua_error} 抛出去得走 {@code luashim_throw} 的 longjmp，
     * 不能让 Java 异常跨过 JNI 帧。
     * <p>
     * 早先这里是「C 侧 {@code vsnprintf} 展开 → Java 拼 where + 抛」，
     * 于是 {@code luaL_error(L, "%f", 1.5)} 给 {@code 1.500000}（参考
     * {@code 1.5}）、{@code "%5d"} 照打不误（参考报
     * {@code invalid option '%5'}）。
     */
    @Generated
    static long luaL_error(long L, long fmt, long a2) {
        throw new IllegalStateException(
                "luaL_error 在 luashim.c 里用 luaL_where + lua_pushvfstring 拼出来，不进 Java");
    }

    /*
     * {@code luaL_execresult} / {@code luaL_fileresult}：<b>errno 是 C 侧塞进来的</b>。
     * <p>
     * {@code lauxlib.c} 里这两个的第一句都是 {@code int en = errno;}
     * （注释明写 {@code calls to Lua API may change this value}）。errno 是
     * 每个线程一份的 C 变量，Java 读不到——所以 {@code luashim.c} 在 C 侧先取
     * 出来，多占一个参数槽交给这里（见 {@code gen_capi.py} 的
     * {@code ARG_OVERRIDE}）。
     * <p>
     * 早先这两个是「生成出来的普通桩」，errno 一律当 0，后果有两处，而且都
     * 是<b>我们自己的库永远撞不到</b>的（{@code io} / {@code os} 走的是
     * {@code LuaAux} 另一条路，那边 errno 本来就是从 Java 异常反查的）：
     * <ol>
     *   <li>{@code luaL_fileresult} 永远答 {@code (no extra info), 0}；</li>
     *   <li>{@code luaL_execresult} 那条 {@code stat != 0 && errno != 0}
     *       的分支永远不走，于是扩展里「{@code system()} 失败」全被当成
     *       「进程正常退出」。</li>
     * </ol>
     */
    static long luaL_execresult(long L, long stat, long en, long what) {
        /*
         * {@code l_inspectstat} 已经在 C 侧用本平台的 wait 宏做完了，这里拿到
         * 的是<b>已经换算过</b>的 {@code stat} 与 {@code what}
         * （{@code "exit"} / {@code "signal"}）。
         */
        LuaState S = st(L);
        LuaString w = LuaString.of(NativeBridge.bytesAt(what));
        int st = (int) stat;
        if (st == 0 && w.equals(LuaString.latin1("exit"))) {
            pushVal(S, LuaValue.ofBoolean(true));
        } else {
            LuaAux.pushFail(S);
        }
        pushVal(S, LuaValue.ofString(w));
        pushVal(S, LuaValue.ofInteger(st));
        return 3;
    }

    static long luaL_fileresult(long L, long stat, long fname, long en) {
        LuaState S = st(L);
        boolean ok = stat != 0;
        LuaString fn = fname == 0 ? null : LuaString.of(NativeBridge.bytesAt(fname));
        /*
         * <b>返回值个数随 {@code stat} 变</b>：成功只压一个 {@code true}、
         * 返回 1；失败压 {@code nil, msg, errno}、返回 3。
         * 早先这里恒返回 3，成功那一支就把栈顶上面两格的<b>陈值</b>当成
         * 结果交出去了。参考实现实测 {@code luaL_fileresult(L, 1, NULL)}
         * 只给一个 {@code true}。
         */
        LuaAux.fileResult(S, ok, fn, (int) en);
        return ok ? 1 : 3;
    }

    static long luaL_getmetafield(long L, long obj, long e) {
        LuaState S = st(L);
        if (lua_getmetatable(L, obj) == 0) {
            return Lua.TNIL;
        }
        long k = NativeBridge.strToNative(NativeBridge.bytesAt(e));
        lua_getfield(L, -1, k);
        if (S.stack[S.top - 1].isNil()) {
            S.top -= 2;
            return Lua.TNIL;
        }
        // 去掉元表，只留字段值
        S.stack[S.top - 2].set(S.stack[S.top - 1]);
        S.top--;
        return S.stack[S.top - 1].baseType();
    }

    static long luaL_getsubtable(long L, long idx, long fname) {
        LuaState S = st(L);
        long k = NativeBridge.strToNative(NativeBridge.bytesAt(fname));
        lua_getfield(L, idx, k);
        if (S.stack[S.top - 1].isTable()) {
            return 1;
        }
        S.top--;
        int i = (int) (int) lua_absindex(L, idx);
        lua_createtable(L, 0, 0);
        lua_pushvalue(L, -1);
        lua_setfield(L, i, k);
        return 0;
    }

    /**
     * C_ONLY：{@code luashim.c} 里照 {@code lauxlib.c} 用
     * {@code luaL_buffinit} + {@code luaL_addgsub} + {@code luaL_pushresult}
     * 拼出来，这个入口永远不会被调到——真正干活的是
     * {@link #luaL_addgsub}（buffer 的三个队友都是 C 侧桩，只有替换本身进
     * Java，因为要比的是字节串）。
     */
    static long luaL_gsub(long L, long s, long p, long r) {
        throw new IllegalStateException("luaL_gsub 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long luaL_len(long L, long idx) {
        LuaState S = st(L);
        lua_len(L, idx);
        LuaValue v = S.stack[--S.top];
        if (!v.isInteger()) {
            throw S.libError("object length is not an integer");
        }
        return v.asInteger();
    }

    static long luaL_loadbufferx(long L, long buff, long sz, long name, long mode) {
        byte[] src = (buff == 0 || sz <= 0) ? new byte[0]
                : NativeBridge.bytesAtN(buff, (int) sz);
        byte[] cn = (name == 0) ? null : NativeBridge.bytesAt(name);
        byte[] md = (mode == 0) ? null : NativeBridge.bytesAt(mode);
        return loadBytes(L, src, cn,
                (md == null) ? null : new String(md, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    @Generated
    static long luaL_loadfilex(long L, long filename, long mode) {
        throw new IllegalStateException("luaL_loadfilex 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long luaL_loadstring(long L, long s) {
        /*
         * {@code lauxlib.c: luaL_loadstring} 就是
         * {@code luaL_loadbuffer(L, s, strlen(s), s)} —— <b>源串自己就是 chunk
         * 名</b>，所以报错长成 {@code [string "x = "]:1: ...}。传 null 会把
         * 这段前缀整个丢掉。
         */
        byte[] b = NativeBridge.bytesAt(s);
        /*
         * chunk 名就是源码本身（{@code luaL_loadstring} 里的
         * {@code luaL_loadbufferx(L, s, strlen(s), s, NULL)}），
         * mode 传 NULL = 全部放行。
         */
        return loadBytes(L, b, b, null);
    }

    static long luaL_newmetatable(long L, long tname) {
        LuaState S = st(L);
        long k = NativeBridge.strToNative(NativeBridge.bytesAt(tname));
        lua_getfield(L, REGISTRY, k);
        if (!S.stack[S.top - 1].isNil()) {
            return 0;
        }
        S.top--;
        lua_createtable(L, 0, 2);
        lua_pushstring(L, k);
        lua_setfield(L, -2, strToNative("__name"));
        lua_pushvalue(L, -1);
        lua_setfield(L, REGISTRY, k);
        return 1;
    }

    static long luaL_newstate() {
        LuaState s = new LuaState();
        LuaLibs.openAll(s);
        return NativeBridge.handle(s);
    }

    static long luaL_optinteger(long L, long arg, long def) {
        LuaState S = st(L);
        if (S.isNoneArg((int) arg) || S.arg1((int) arg).isNil()) {
            return def;
        }
        return luaL_checkinteger(L, arg);
    }

    static long luaL_optlstring(long L, long arg, long def, long l) {
        LuaState S = st(L);
        if (S.isNoneArg((int) arg) || S.arg1((int) arg).isNil()) {
            /*
             * {@code def == NULL} 必须原样交回 <b>NULL（0）</b>，不能退化成
             * {@code ""}。差别是可观测的：{@code luaL_optstring(L, n, NULL)}
             * 最典型的用处就是给 {@code lua_load} / {@code luaL_loadfilex} 传
             * {@code mode}，而 {@code checkmode} 对 {@code NULL} 一律放行、
             * 对 {@code ""} 却会报
             * {@code attempt to load a text chunk (mode is '')}。
             * 长度参数同步写 0（{@code lauxlib.c} 里 {@code def} 为 NULL 时
             * {@code *len} 也是 0）。
             */
            if (def == 0) {
                if (l != 0) {
                    NativeBridge.put64(l, 0);
                }
                return 0;
            }
            byte[] b = NativeBridge.bytesAt(def);
            if (l != 0) {
                NativeBridge.put64(l, b.length);
            }
            return NativeBridge.strToNative(b);
        }
        return luaL_checklstring(L, arg, l);
    }

    static double luaL_optnumber(long L, long arg, double def) {
        LuaState S = st(L);
        if (S.isNoneArg((int) arg) || S.arg1((int) arg).isNil()) {
            return def;
        }
        return luaL_checknumber(L, arg);
    }

    @Generated
    static long luaL_prepbuffsize(long B, long sz) {
        throw new IllegalStateException("luaL_prepbuffsize 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    static void luaL_pushresult(long B) {
        throw new IllegalStateException("luaL_pushresult 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    static void luaL_pushresultsize(long B, long sz) {
        throw new IllegalStateException("luaL_pushresultsize 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long luaL_ref(long L, long t) {
        LuaState S = st(L);
        if (S.stack[S.top - 1].isNil()) {
            S.top--;
            return -1; // LUA_REFNIL
        }
        int ti = (int) lua_absindex(L, t);
        /*
         * {@code lauxlib.c: #define freelist (LUA_RIDX_LAST + 1)} —— 空闲链表
         * 的头存在下标 <b>3</b>，不是 0。
         * <p>
         * 而且「首次访问」那一支会<b>先把 t[3] 写成 0</b> 再去取
         * {@code lua_rawlen}：注册表原本 {@code rawlen} 是 2，写完这一格变成 3，
         * 于是第一个引用是 <b>4</b>。漏掉这次写、或者把链表头放在 0，都会让引用
         * 号整体错位 —— 实测参考实现连调两次都是 4。
         */
        lua_rawgeti(L, ti, FREELIST);
        long ref;
        if (S.stack[S.top - 1].isNil()) { // first access
            ref = 0;
            S.top--;
            lua_pushinteger(L, 0);
            lua_rawseti(L, ti, FREELIST);
        } else {
            ref = S.stack[--S.top].asInteger();
        }
        if (ref != 0) {
            lua_rawgeti(L, ti, ref);
            lua_rawseti(L, ti, FREELIST);
        } else {
            ref = lua_rawlen(L, ti) + 1;
        }
        lua_rawseti(L, ti, ref);
        return ref;
    }

    static void luaL_requiref(long L, long modname, long openf, long glb) {
        LuaState S = st(L);
        long k = NativeBridge.strToNative(NativeBridge.bytesAt(modname));
        luaL_getsubtable(L, REGISTRY, strToNative("_LOADED"));
        lua_getfield(L, -1, k);
        if (!S.stack[S.top - 1].toBoolean()) {
            S.top--;
            lua_pushcclosure(L, openf, 0);
            lua_pushstring(L, k);
            lua_callk(L, 1, 1, 0, 0);
            lua_pushvalue(L, -1);
            lua_setfield(L, -3, k);
        }
        S.stack[S.top - 2].set(S.stack[S.top - 1]);
        S.top--;
        if (glb != 0) {
            lua_pushvalue(L, -1);
            lua_setglobal(L, k);
        }
    }

    @Generated
    static void luaL_setfuncs(long L, long l, long nup) {
        throw new IllegalStateException("luaL_setfuncs 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static void luaL_setmetatable(long L, long tname) {
        LuaState S = st(L);
        lua_getfield(L, REGISTRY, NativeBridge.strToNative(NativeBridge.bytesAt(tname)));
        lua_setmetatable(L, -2);
    }

    static long luaL_testudata(long L, long ud, long tname) {
        LuaState S = st(L);
        long p = lua_touserdata(L, ud);
        if (p == 0) {
            return 0;
        }
        if (lua_getmetatable(L, ud) == 0) {
            return 0;
        }
        lua_getfield(L, REGISTRY, NativeBridge.strToNative(NativeBridge.bytesAt(tname)));
        boolean eq = LuaValue.rawEqual(S.stack[S.top - 1], S.stack[S.top - 2]);
        S.top -= 2;
        return eq ? p : 0;
    }

    static long luaL_tolstring(long L, long idx, long len) {
        LuaState S = st(L);
        int i = (int) (int) lua_absindex(L, idx);
        if (luaL_callmeta(L, i, strToNative("__tostring")) != 0) {
            if (!S.stack[S.top - 1].isString()) {
                throw S.libError("'__tostring' must return a string");
            }
        } else {
            LuaValue v = val(S, i);
            int tt = typeOf(S, i);
            if (tt == Lua.TNUMBER || tt == Lua.TSTRING) {
                pushVal(S, v);
            } else if (tt == Lua.TBOOLEAN) {
                pushVal(S, LuaValue.ofString(
                        LuaString.latin1(v.toBoolean() ? "true" : "false")));
            } else if (tt == Lua.TNIL) {
                pushVal(S, LuaValue.ofString(LuaString.latin1("nil")));
            } else {
                int nt = (int) luaL_getmetafield(L, i, strToNative("__name"));
                String kind = nt == Lua.TSTRING
                        ? S.stack[S.top - 1].asString().toLatin1String()
                        : Lua.typeName(tt);
                pushVal(S, LuaValue.ofString(LuaString.latin1(
                        kind + ": " + ptrText(lua_topointer(L, i)))));
                if (nt != Lua.TNONE) {
                    S.stack[S.top - 2].set(S.stack[S.top - 1]);
                    S.top--;
                }
            }
        }
        return lua_tolstring(L, -1, len);
    }

    static void luaL_traceback(long L, long L1, long msg, long level) {
        /*
         * 与 {@code debug.traceback} / 未捕获错误的 {@code msghandler}
         * <b>同一份 {@link DebugLib#traceback}</b>：三处只要有一处是自己拼的，
         * 「{@code debug.traceback} 对的、C 扩展里 {@code luaL_traceback} 少一层」
         * 这种分歧就查不出来。
         * <p>
         * {@code msg} 是 {@code const char*}，按字节取且<b>不再过
         * {@code luaL_tolstring}</b>——C 版也是直接 {@code strlen(msg)} 用，
         * 所以 {@code nil} 就是没有消息，不是字符串 "nil"。
         */
        LuaState S = st(L);
        LuaState T = st(L1);
        byte[] mb = NativeBridge.bytesAt(msg);
        LuaString m = (mb == null) ? null : LuaString.of(mb);
        pushVal(S, LuaValue.ofString(
                DebugLib.traceback(S, (T == null) ? S : T, m, (int) level)));
    }

    static long luaL_typeerror(long L, long arg, long tname) {
        String tn = tname == 0 ? "?" : new String(NativeBridge.bytesAt(tname), java.nio.charset.StandardCharsets.ISO_8859_1);
        throw st(L).typeArgError((int) arg, tn);
    }

    static void luaL_unref(long L, long t, long ref) {
        if (ref >= 0) {
            int ti = (int) lua_absindex(L, t);
            lua_rawgeti(L, ti, FREELIST);
            lua_rawseti(L, ti, ref);
            lua_pushinteger(L, ref);
            lua_rawseti(L, ti, FREELIST);
        }
    }

    /*
     * {@code lauxlib.c: luaL_where}：压 {@code "<short_src>:<line>: "}，
     * 拿不到就压空串。
     * <p>
     * <b>这一条曾经是空串桩</b>（TODO 上写着「C2 阶段接上」），而它正是
     * {@code luaL_error} 的第一句——于是所有从 C 扩展里 {@code luaL_error}
     * 出来的消息都没有位置前缀。参考实测（{@code f_where}）从 Lua 函数里
     * 调进来是 {@code <用例文件>:<行号>: }（差分用例落在
     * {@code target/rt/<pid>/case.lua}，见测试里的 {@code Diff.RT_ROOT}），我们给空串。
     * <p>
     * 两个容易踩的点：
     * <ol>
     *   <li><b>C 帧也算一层</b>。{@code lua_getstack} 是顺着 {@code ci} 链
     *       退，不分 Lua / C。所以 {@code print(pcall(f))} 里
     *       {@code luaL_where(L, 1)} 拿到的是 <b>pcall 那个 C 帧</b>
     *       （{@code currentline == -1}），得 {@code level == 2} 才是主
     *       chunk —— 这也是为什么「从 C 函数里 {@code luaL_error}」常常
     *       <b>没有</b>前缀：中间隔着一个 pcall。</li>
     *   <li>{@code short_src} 是<b>字节串</b>（chunk 名可以是任意字节），
     *       拼的时候不能绕 Java String。</li>
     * </ol>
     */
    static void luaL_where(long L, long lvl) {
        LuaState S = st(L);
        LuaString where = LuaString.latin1("");
        DebugLib.LuaDebug d = new DebugLib.LuaDebug();
        if (DebugLib.getStack(S, (int) lvl, d)) {
            DebugLib.getInfo(S, "Sl", d);
            if (d.currentline > 0) {
                where = d.shortSrc.concat(
                        LuaString.latin1(":" + d.currentline + ": "));
            }
        }
        pushVal(S, LuaValue.ofString(where));
    }

    static long lua_absindex(long L, long idx) {
        LuaState S = st(L);
        int i = (int) idx;
        return (i > 0 || i <= REGISTRY) ? i : (S.top - S.ci.func) + i;
    }

    static void lua_arith(long L, long op) {
        /*
         * {@code lapi.c: lua_arith}：一元运算（{@code UNM} / {@code BNOT}）
         * 先在栈顶<b>复制一份操作数</b>，之后就与二元同形——结果一律落在
         * {@code top - 2}，再把 {@code top} 收一格。
         * <p>
         * 注意 {@code top - 2} 与第一个操作数是<b>同一个槽</b>：两个操作数先取
         * 引用再算，别在算之前把槽清了（与 {@link #lua_gettable} 同一个坑）。
         */
        LuaState S = st(L);
        int o = (int) op;
        if (o == Arith.OP_UNM || o == Arith.OP_BNOT) {
            grow(S, 1);
            S.stack[S.top].set(S.stack[S.top - 1]);
            S.top++;
        }
        LuaValue p1 = S.stack[S.top - 2];
        LuaValue p2 = S.stack[S.top - 1];
        LuaValue res = Arith.rawArith(S, o, p1, p2);
        if (res == null) {
            /*
             * {@code luaO_arith} → {@code luaT_trybinTM}：元方法事件号是
             * {@code (op - LUA_OPADD) + TM_ADD}，而 {@code LUA_OPADD == 0}，
             * 所以就是 {@code op + TM_ADD}——{@code UNM}
             * （12）落到 {@code TM_UNM}（18）、{@code BNOT}（13）落到
             * {@code TM_BNOT}（19），与 {@code lobject.h} 的 {@code ORDER TM}
             * 顺序一致。
             */
            LuaTm.tryBinTm(S, p1, p2, S.top - 2, o + Tm.TM_ADD);
        } else {
            S.stack[S.top - 2].set(res);
        }
        S.top -= 1;
    }

    /*
     * lapi.c: lua_atpanic 只是「换个函数指针、交回旧的」。
     * <p>
     * 至于<b>什么时候真的调它</b>：C 版是 {@code luaD_throw} 发现
     * {@code L->errorJmp == NULL}（错误逃出了所有保护区）时调，调完 {@code abort()}。
     * 本实现的错误是 Java 异常，逃出保护区后一路冒到宿主（{@code pcall} 或者
     * {@code Runner}），那里才是「要不要 panic」的决定点——所以这里只存不调。
     * 真要接上，宿主在自己的捕获点取 {@link LuaState#panicFn} 即可。
     */
    static long lua_atpanic(long L, long panicf) {
        LuaState S = st(L);
        long old = S.panic();
        S.setPanic(panicf);
        return old;
    }

    static void lua_callk(long L, long nargs, long nresults, long ctx, long k) {
        LuaState S = st(L);
        int funcIdx = S.top - (int) nargs - 1;
        /*
         * lapi.c: {@code k != NULL && yieldable(L)} 才把续体记到当前 C 帧上，
         * 否则走 {@code luaD_callnoyield}——「不可 yield」在 C 里是靠
         * {@code nyci} 计数的，回调里 yield 会报跨 C 调用边界而不是真的挂起。
         */
        if (k != 0 && S.yieldable()) {
            S.callK(funcIdx, (int) nresults, (int) ctx, new NativeKFunction(k));
        } else {
            S.callNoYield(funcIdx, (int) nresults);
        }
    }

    static long lua_checkstack(long L, long n) {
        LuaState S = st(L);
        try {
            grow(S, (int) n);
            return 1;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /*
     * {@code lua_close}。
     * <p>
     * <b>有意偏离，别当 bug 修</b>：C 版会把整个 {@code lua_State} 连同所有对象
     * 一起释放，本实现的内存归 JVM 管，没有「释放」这个动作可做。这里做的是
     * <b>把线程复位到空</b>，于是之后再用它就是 C 里的 use-after-free（未定义）——
     * 至少不会「关完了还能正常跑」，那才是真正的假象。
     */
    static void lua_close(long L) {
        st(L).reset();
    }

    @Generated
    /*
     * {@code lapi.c: lua_closeslot}：把某个槽上刚标记的 tbc 变量<b>当场关掉</b>，
     * 然后把它置 nil。
     * <p>
     * {@code luaF_close(L, level, CLOSEKTOP, 0)} 的最后一个参数是 {@code 0}——
     * <b>{@code __close} 里不许 yield</b>（{@code lua_toclose} 那条路的
     * {@code moveresults} 传的是 1，允许）。所以这里 {@code forceYy} 取 false。
     */
    static void lua_closeslot(long L, long idx) {
        LuaState S = st(L);
        int level = stackIdx(S, (int) idx);
        S.closeUpvals(level, null, false);
        S.stack[level].setNil();
    }

    /** {@code index2stack}：与 {@link #val} 同一套换算，只是要有越界保护 */
    private static int stackIdx(LuaState L, int idx) {
        int p = idx > 0 ? L.ci.func + idx : L.top + idx;
        if (p < 0 || p >= L.top) {
            throw new IllegalArgumentException("bad stack index " + idx);
        }
        return p;
    }

    /*
     * lstate.c: {@code lua_closethread} 就是 {@code luaE_resetthread(L, L->status)}。
     * {@code coroutine.close} 用的是同一条路，这里直接复用。
     */
    static long lua_closethread(long L, long from) {
        LuaState S = st(L);
        LuaState f = (from == 0) ? null : st(from);
        return S.closeThread(f);
    }

    static long lua_compare(long L, long idx1, long idx2, long op) {
        LuaState S = st(L);
        LuaValue a = val(S, (int) idx1);
        LuaValue b = val(S, (int) idx2);
        if (a == null || b == null) {
            return 0;
        }
        switch ((int) op) {
            case 0: return LuaValue.rawEqual(a, b) ? 1 : 0;
            case 1: return Vm.lessThan(S, a, b) ? 1 : 0;
            case 2: return le(S, a, b) ? 1 : 0;
            default: return 0;
        }
    }

    static void lua_concat(long L, long n) {
        LuaState S = st(L);
        Vm.concat(S, (int) n);
    }

    static void lua_copy(long L, long fromidx, long toidx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) fromidx);
        setVal(S, (int) toidx, v == null ? LuaValue.ofNil() : v);
    }

    static void lua_createtable(long L, long narr, long nrec) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofTable(new LuaTable((int) narr, (int) nrec)));
    }

    static long lua_dump(long L, long writer, long data, long strip) {
        /*
         * {@code lapi.c: lua_dump}：只对 <b>Lua 函数</b>做，别的（含 C 函数）
         * 一律返回 <b>1</b> 表示「不是我该干的」。判据是 {@code isLfunction}，
         * 也就是 {@link Lua#VLIF}，不是「能不能调用」。
         * <p>
         * 字节交给 writer 走 {@code strToNative} 的环形缓冲：dump 出来的是
         * 二进制、中间有 {@code \0}，但长度是我们自己知道的，C 侧按
         * {@code sz} 取就不会被截断。
         * <p>
         * <b>只调 writer 一次</b>：C 版 {@code luaU_dump} 是分块的（一个串、
         * 一段指令各一次）。写得很规矩的 writer 两种都吃；返回值取 writer
         * 自己的（非 0 即失败）——{@code string.dump} 那条路是另一份实现，
         * 这里不复用。
         */
        LuaState S = st(L);
        LuaValue v = S.stack[S.top - 1];
        if (v.tt != Lua.VLIF) {
            return 1;
        }
        byte[] b = Dump.dumpBytes(((LClosure) v.o).p, strip != 0);
        long p = NativeBridge.strToNative(b);
        long r = NativeBridge.callPtr(writer,
                NativeBridge.statePtr(L), p, b.length, data);
        /*
         * writer 声明的是 {@code int}，而 {@code callPtr} 取的是
         * {@code jlong}。arm64 上返回 {@code int} 只保证低 32 位有意义，
         * 高 32 位是未定义的——这里必须截断，否则 {@code return 0} 的 writer
         * 可能被读成一个巨大的正数。
         */
        return (int) r;
    }

    static long lua_error(long L) {
        LuaState S = st(L);
        /*
         * {@code lapi.c: lua_error} 干两件事：取栈顶那个值当错误对象，然后
         * {@code luaG_errormsg}。<b>位置是 {@code luaL_error} 自己用
         * {@code luaL_where} 拼好的</b>，{@code lua_error} 一律不加——所以
         * 这里 {@code where} 给 {@code ""}（「已定稿」），保保护边界别再补一次。
         * <p>
         * 唯一的特判是内存错误：错误对象<b>恰好</b>是驻留短串
         * {@code "not enough memory"} 时，C 走 {@code luaM_error} 而不是
         * {@code luaG_errormsg}。可观测——{@code LUA_ERRMEM} 这一档
         * {@code xpcall} 的处理器<b>接不到</b>（见 {@link LuaState#errorAt}）。
         */
        LuaValue v = new LuaValue(S.stack[S.top - 1]);
        LuaError e = new LuaError(v, "");
        e.errMem = LuaState.isMemErr(v);
        throw e;
    }

    static long lua_gc(long L, long what, long a2) {
        /*
         * {@code lapi.c: lua_gc} 与 {@code lbaselib.c: luaB_collectgarbage}
         * 是<b>同一份状态</b>的两张脸——{@link BaseLib#collectgarbage} 里的
         * 注释全适用于这里（尤其是「{@code count} 一律报 0」那条：报任何具体
         * 数字都是谎报）。这里照 {@code lua.h} 的 {@code LUA_GC*} 常量逐档映射。
         * <p>
         * 只丢了一样东西：{@code LUA_GCGEN} 的 {@code majormul} 与
         * {@code LUA_GCINC} 的 {@code stepmul}/{@code stepsize}。变参在 C 侧
         * 是打包进槽位的，而生成器只给变参函数留了三个槽（{@code L}、
         * {@code what}、第一个变参），多出来的进不来。丢的是「调节量」——
         * 我们本来也不按它调（没有分代收集器可调），返回值 {@code res}
         * 只取决于切之前的模式，照样算得对。
         */
        LuaState S = st(L);
        LuaState m = S.mainThread;
        int w = (int) what;
        switch (w) {
        case 0: // LUA_GCSTOP
            m.gcRunning = false;
            return 0;
        case 1: // LUA_GCRESTART
            m.gcRunning = true;
            return 0;
        case 2: // LUA_GCCOLLECT
            BaseLib.doCollect(S);
            return 0;
        case 3: // LUA_GCCOUNT —— KB，本实现恒 0
        case 4: // LUA_GCCOUNTB —— 余数字节，同上
            return 0;
        case 5: // LUA_GCSTEP
            /*
             * 返回 1 = 「一个收集周期刚跑完」。{@code collectgarbage("step")}
             * 就是拿这个 1 去推 {@code true} 的，所以这里必须给 1，
             * 否则 {@code lua_gc} 与 {@code collectgarbage} 两条路自相矛盾。
             */
            BaseLib.doCollect(S);
            return 1;
        case 6: { // LUA_GCSETPAUSE —— setgcparam 无条件赋值，见 BaseLib#collectgarbage
            int old = m.gcPause();
            m.setGcPause((int) a2);
            return old;
        }
        case 7: { // LUA_GCSETSTEPMUL
            int old = m.gcStepMul();
            m.setGcStepMul((int) a2);
            return old;
        }
        case 9: // LUA_GCISRUNNING
            return m.gcRunning ? 1 : 0;
        case 10: // LUA_GCGEN
        case 11: { // LUA_GCINC
            /*
             * {@code res} 是<b>切换前</b>的模式码（{@code LUA_GCGEN} /
             * {@code LUA_GCINC}），不是 {@code collectgarbage} 那个字符串。
             * 判据照 {@code isdecGCmodegen}——见 {@link LuaState#gcLastAtomic}。
             */
            boolean prevGen = (m.gcKind == LuaState.LUA_GCGEN) || m.gcLastAtomic;
            m.gcKind = (w == 10) ? LuaState.LUA_GCGEN : LuaState.LUA_GCINC;
            m.gcLastAtomic = false;
            return prevGen ? 10 : 11;
        }
        default:
            return -1; // 无效选项
        }
    }

    /*
     * {@code lua_getallocf} / {@code lua_setallocf}：整对在 {@code luashim.c} 里。
     * <p>
     * 为什么不能进 Java：{@code lua_getallocf} 交回去的是一个<b>能被直接调用的
     * C 函数指针</b>，而 {@code lpeg} 这类扩展拿它就是为了自己 malloc/free
     * （{@code lpcode.c} 的 {@code newcode} / {@code freecode} / {@code realloccode}
     * 三处都这么干），Java 交不出这种东西。
     */
    @Generated
    static long lua_getallocf(long L, long ud) {
        throw new IllegalStateException("lua_getallocf 在 luashim.c 里就地实现，不进 Java");
    }

    static long lua_getfield(long L, long idx, long k) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaString key = LuaString.of(NativeBridge.bytesAt(k));
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, t, LuaValue.ofString(key), S.top);
        S.top++;
        /*
         * {@code lapi.c: auxgetstr} 的返回值是
         * {@code ttype(s2v(L->top - 1))} —— <b>取到的那个值</b>的类型，
         * 不是被查的那张表的。以前这里返回 {@code t} 的类型，于是
         * {@code lua_getfield} 在表上恒给 {@code LUA_TTABLE}：扩展里拿返回值
         * 判「这个字段在不在 / 是不是函数」的判断整个反过来
         * （实测：新状态上 {@code lua_getglobal(L, "print")} 参考给
         * {@code LUA_TFUNCTION}，本实现给 {@code LUA_TTABLE}）。
         */
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_getglobal(long L, long name) {
        LuaState S = st(L);
        LuaString key = LuaString.of(NativeBridge.bytesAt(name));
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, LuaValue.ofTable(S.globals), LuaValue.ofString(key), S.top);
        S.top++;
        // 同 lua_getfield：返回<b>取到的值</b>的类型
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_gethook(long L) {
        /*
         * 只认<b>本实现装进去的</b> C 钩子：脚本侧 {@code debug.sethook} 装的
         * 是 Java 的 {@link LuaHook}，没有函数指针可还，交 0（= NULL）。
         * C 版当然原样还那个指针，但这里「还 0」与「还一个假地址」之间
         * 必须选前者——扩展拿到地址多半是去比较或去调。
         */
        LuaHook h = st(L).getHook();
        return (h instanceof NativeHook) ? ((NativeHook) h).fn : 0;
    }

    static long lua_gethookcount(long L) {
        /*
         * 是 {@code basehookcount}（周期），<b>不是还剩几条</b>——
         * {@code debug.gethook()} 的第三个结果要的也是这个，两边必须同答。
         */
        return st(L).getHookCount();
    }

    static long lua_gethookmask(long L) {
        return st(L).getHookMask();
    }

    static long lua_geti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, t, LuaValue.ofInteger(n), S.top);
        S.top++;
        // 同 lua_getfield：返回<b>取到的值</b>的类型，不是 {@code t} 的
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_getinfo(long L, long what, long ar) {
        /*
         * {@code what} 里带 {@code '>'} 时要先把栈顶那个函数弹掉（{@code getInfo}
         * 内部会这么做），带 {@code 'f'} / {@code 'L'} 时又会往栈上推——
         * 与 {@code debug.getinfo} 完全同一条路，所以直接复用
         * {@link DebugLib#getInfo}。
         */
        LuaState S = st(L);
        DebugLib.LuaDebug d = arOf(ar);
        if (d == null) {
            return 0; // 没先调 lua_getstack：C 里 ar->i_ci 是野的
        }
        byte[] wb = NativeBridge.bytesAt(what);
        boolean ok = DebugLib.getInfo(S,
                (wb == null) ? "" : new String(wb, java.nio.charset.StandardCharsets.ISO_8859_1), d);
        fillDebug(ar, d);
        return ok ? 1 : 0;
    }

    static long lua_getiuservalue(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        LuaUserdata ud = udata(v);
        /*
         * 越界（含 {@code nuvalue == 0}）时 C 是<b>推一个 nil 并返回
         * {@code LUA_TNONE}</b>，不是什么都不推 —— 栈高一格这点差别，
         * 扩展里「{@code if (lua_getiuservalue(... ) != LUA_TNONE)}」就反了。
         */
        if (ud == null || ud.userValues == null || n <= 0 || n > ud.userValues.length) {
            pushVal(S, LuaValue.ofNil());
            return Lua.TNONE;
        }
        pushVal(S, ud.userValues[(int) n - 1]);
        return ud.userValues[(int) n - 1].baseType();
    }

    /** 取完整 userdata；不是就给 {@code null}（C 的 {@code api_check} 是断言） */
    private static LuaUserdata udata(LuaValue v) {
        return (v != null && v.baseType() == Lua.TUSERDATA)
                ? (LuaUserdata) v.o : null;
    }

    static long lua_getlocal(long L, long ar, long n) {
        /*
         * {@code ar == NULL} 是「查非活动函数」那一支：只看栈顶那个 Lua 函数
         * 在<b>入口处</b>还活着的参数（{@code luaF_getlocalname(p, n, 0)}），
         * 不压任何值。{@code ldblib.c} 的 {@code getlocal} 就是这么分的。
         */
        LuaState S = st(L);
        DebugLib.LuaDebug d = (ar == 0) ? null : arOf(ar);
        if (ar != 0 && d == null) {
            return 0;
        }
        String name = DebugLib.getLocal(S, d, (int) n);
        return (name == null) ? 0 : strToNative(name);
    }

    static long lua_getmetatable(long L, long objindex) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) objindex);
        LuaTable mt = (v == null) ? null : LuaTm.metatableOf(S, v);
        if (mt == null) {
            return 0;
        }
        pushVal(S, LuaValue.ofTable(mt));
        return 1;
    }

    /*
     * {@code lua_Debug} 是 <b>C 栈上的结构体</b>，Java 这边只见得到一个地址。
     * 而 {@code lua_getstack} 写进去的 {@code i_ci}（我们的
     * {@link DebugLib.LuaDebug}）是个 Java 对象，塞不进那块内存。
     * <p>
     * 于是用一张「地址 → Java 侧状态」的表搭桥：{@code lua_getstack} 存，
     * 紧接着的 {@code lua_getinfo} / {@code lua_getlocal} 取。C 的用法本来
     * 就是「先 getstack 再 getinfo，同一个 {@code ar}」，一张表够用。
     * <p>
     * 表必须有上界：{@code ar} 常常是循环里的局部结构体，地址会被复用，
     * 不复用的话跑一晚上就攒出几万个条目。{@code 64} 条是「同一个瞬间
     * 最多同时有几个活的 {@code ar}」的宽裕估计，淘汰的是最久没用到的。
     */
    private static final int AR_CACHE_MAX = 64;

    private static final java.util.LinkedHashMap<Long, DebugLib.LuaDebug> AR_CACHE =
            new java.util.LinkedHashMap<Long, DebugLib.LuaDebug>(
                    16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<Long, DebugLib.LuaDebug> e) {
                    return size() > AR_CACHE_MAX;
                }
            };

    private static synchronized DebugLib.LuaDebug arOf(long ptr) {
        return AR_CACHE.get(ptr);
    }

    private static synchronized void arPut(long ptr, DebugLib.LuaDebug ar) {
        AR_CACHE.put(ptr, ar);
    }

    /**
     * 把 {@link DebugLib.LuaDebug} 的字段按 {@code luashim.c} 的
     * {@code DBG_FIELD} 顺序写回 C 结构体。
     * <p>
     * 顺序与宽度是<b>两套代码之间的契约</b>：C 侧按 {@code offsetof} 落字段，
     * 这里按下标给值，改任何一边都必须两边一起改。
     */
    static void fillDebug(long arPtr, DebugLib.LuaDebug ar) {
        long[] v = new long[15];
        v[0] = ar.event;
        v[1] = strToNative(ar.name == null ? "" : ar.name);
        v[2] = strToNative(ar.namewhat == null ? "" : ar.namewhat);
        v[3] = strToNative(ar.what == null ? "" : ar.what);
        LuaString src = (ar.source == null) ? LuaString.latin1("=?") : ar.source;
        v[4] = NativeBridge.strToNative(src.bytes);
        v[5] = src.length();
        v[6] = ar.currentline;
        v[7] = ar.linedefined;
        v[8] = ar.lastlinedefined;
        v[9] = ar.nups;
        v[10] = ar.nparams;
        v[11] = ar.isvararg ? 1 : 0;
        v[12] = ar.istailcall ? 1 : 0;
        v[13] = ar.ftransfer;
        v[14] = ar.ntransfer;
        NativeBridge.dbgFill(arPtr, v);
        NativeBridge.dbgShortSrc(arPtr,
                (ar.shortSrc == null) ? new byte[0] : ar.shortSrc.bytes);
    }

    static long lua_getstack(long L, long level, long ar) {
        LuaState S = st(L);
        DebugLib.LuaDebug d = new DebugLib.LuaDebug();
        if (!DebugLib.getStack(S, (int) level, d)) {
            return 0;
        }
        arPut(ar, d);
        return 1;
    }

    static long lua_gettable(long L, long idx) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue key = S.stack[--S.top];
        /*
         * 结果就写在「key 原来那一格」上（C 是 {@code L->top - 1} 原地替换），
         * 所以 key <b>必须先拷出来</b>：它和目的槽是同一个 {@link LuaValue}
         * 对象，先 {@code setNil} 或者 {@code getTable} 内部往那一格写东西，
         * key 就被自己踩掉了 —— 症状是 {@code t[k]} 恒给 nil。
         */
        LuaValue k2 = new LuaValue(key);
        grow(S, 1);
        Vm.getTable(S, t, k2, S.top);
        S.top++;
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_gettop(long L) {
        LuaState S = st(L);
        return S.top - S.ci.func - 1;
    }

    static long lua_getupvalue(long L, long funcindex, long n) {
        /*
         * 与 {@code debug.getupvalue} 同一条 {@code auxupvalue}：C 闭包给
         * {@code ""}、Lua 闭包给真正的名字、没名字给 {@code "(no name)"}
         * （不是 {@code "?"}——那是 {@code ldebug.c: upvalname} 的答法）。
         */
        LuaState S = st(L);
        LuaValue f = val(S, (int) funcindex);
        LuaValue[] v = new LuaValue[1];
        String name = (f == null) ? null : DebugLib.auxUpValue(f, (int) n, v, null);
        if (name == null) {
            return 0;
        }
        pushVal(S, v[0]);
        return strToNative(name);
    }

    static long lua_iscfunction(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        return (v.tt == Lua.VLCF || v.tt == Lua.VCCL) ? 1 : 0;
    }

    static long lua_isinteger(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        return (v != null && v.isInteger()) ? 1 : 0;
    }

    static long lua_isnumber(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        return (v != null && (v.isNumber() || Vm.strToNum(v) != null)) ? 1 : 0;
    }

    static long lua_isstring(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        return (v != null && (v.isString() || v.isNumber())) ? 1 : 0;
    }

    static long lua_isuserdata(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        return (v.baseType() == Lua.TUSERDATA || v.tt == Lua.TLIGHTUSERDATA) ? 1 : 0;
    }

    static long lua_isyieldable(long L) {
        /*
         * {@code ldo.c: yieldable(L)} 就是 {@code L->nNy == 0}：每进一层不可
         * yield 的 C 帧就 +1。主线程恒为 1（{@code lua_newstate} 里的
         * {@code incnny}），所以「能不能 yield」和「在不在协程里」是同一件事。
         */
        return ((L == 0) ? false : st(L).isYieldable()) ? 1 : 0;
    }

    static void lua_len(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.objLen(S, S.top, v);
        S.top++;
    }

    @Generated
    static long lua_load(long L, long reader, long dt, long chunkname, long mode) {
        throw new IllegalStateException("lua_load 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    @Generated
    /*
     * {@code lua_newstate}：起一个<b>全新的</b> Lua 状态。
     * <p>
     * 与 {@link #luaL_newstate} 的差别是<b>不开库</b>——C 版也是这样，
     * {@code luaL_openlibs} 得调用方自己喊。
     * <p>
     * 分配器那两个参数由 {@code luashim.c} 的手写 stub 接住（它顺手把进程级那
     * 份 {@code g_allocf} 也换掉，于是 {@code lua_getallocf} 能原样交回去）。
     */
    static long lua_newstate(long f, long ud) {
        return NativeBridge.handle(new LuaState());
    }

    static long lua_newthread(long L) {
        LuaState S = st(L);
        LuaState co = S.newThread();
        pushVal(S, LuaValue.ofThread(co));
        return NativeBridge.handle(co);
    }

    static long lua_newuserdatauv(long L, long sz, long nuvalue) {
        LuaState S = st(L);
        NativeMemory m = new NativeMemory(NativeBridge.alloc(sz), sz);
        /*
         * 载荷就是 {@code m} 本身（元表、用户值挂在 {@link LuaUserdata} 那层上）。
         * 这里<b>必须走 {@code ofUserdata(LuaUserdata)} 那条重载</b>：
         * 之前先 {@code new LuaUserdata(m)} 再交给 {@code ofUserdata(Object)}，
         * 等于套了两层，于是 {@code lua_touserdata} 取到的 {@code data} 是个
         * {@link LuaUserdata} 而不是 {@link NativeMemory}，一律给 0 ——
         * {@code luaL_checkudata} 因此连自己刚建出来的 userdata 都不认
         * （报 {@code NatBox expected, got NatBox}）。
         * 今天 {@code ofUserdata(Object)} 对 {@link LuaUserdata} 参数直接转发，
         * 所以两层写法也能跑；但用户值数组仍然只建在里面那层上，外面那层
         * {@code nuvalue} 恒为 0，{@code lua_getiuservalue} 永远答
         * {@code LUA_TNONE}——这是个「能跑但全错」的坑，留着注释别再踩回去。
         */
        pushVal(S, LuaValue.ofUserdata(new LuaUserdata(m, (int) nuvalue, (int) sz)));
        return m.ptr;
    }

    static long lua_next(long L, long idx) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        if (!t.isTable()) {
            throw S.runError("'__next' not implemented for this type");
        }
        LuaValue key = S.stack[S.top - 1];
        LuaValue[] kv = t.asTable().next(key.isNil() ? null : key);
        if (kv == null) {
            S.top -= 1;
            return 0;
        }
        S.stack[S.top - 1].set(kv[0]);
        grow(S, 1);
        S.stack[S.top++].set(kv[1]);
        return 1;
    }

    static long lua_pcallk(long L, long nargs, long nresults, long errfunc, long ctx, long k) {
        LuaState S = st(L);
        int funcIdx = S.top - (int) nargs - 1;
        int ef = (int) errfunc;
        LuaValue handler = ef == 0 ? null
                : (ef == REGISTRY ? null : val(S, ef));
        if (k != 0 && S.yieldable()) {
            /*
             * lapi.c 这个分支<b>不建本地恢复点</b>：错误（和 yield）一路冒到
             * {@code lua_resume}，由 {@code precover} → {@code finishpcallk}
             * 收尾，收完之后才调续体 —— 续体拿到的 {@code status} 就是那个错误码。
             * 所以这里不接 {@code LuaError}，让它照 C 那样穿出去。
             */
            S.pcallK(funcIdx, (int) nresults, handler, (int) ctx,
                    new NativeKFunction(k));
            return 0;
        }
        LuaValue err = S.pcall(funcIdx, (int) nresults, handler);
        if (err == null) {
            return 0;
        }
        /*
         * {@code ldo.c: luaD_pcall} 出错后走 {@code luaD_seterrorobj(L, status,
         * restorestack(old_top))}：错误值被写回<b>函数原来那一格</b>，且
         * {@code L->top = oldtop + 1} —— 无论 {@code nresults} 是多少，出错时
         * 栈上都只有这一个值。不写的话那一格还是被调用的函数，C 侧
         * {@code lua_tostring(L, -1)} 拿到的就是一个函数。
         */
        S.top = funcIdx + 1;
        S.stack[funcIdx].set(err);
        return LuaState.LUA_ERRRUN;
    }

    static void lua_pushboolean(long L, long b) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofBoolean(b != 0));
    }

    static void lua_pushcclosure(long L, long fn, long n) {
        LuaState S = st(L);
        int nup = (int) n;
        grow(S, 1);
        LuaFunction f = new LuaNativeFunction(fn);
        if (nup == 0) {
            S.stack[S.top++].setObject(Lua.VLCF, f);
        } else {
            CClosure cc = new CClosure(f, nup);
            for (int i = 0; i < nup; i++) {
                cc.upvalue[i].set(S.stack[S.top - nup + i]);
            }
            S.top -= nup;
            S.stack[S.top++].setObject(Lua.VCCL, cc);
        }
    }

    @Generated
    static long lua_pushfstring(long L, long fmt, long a2) {
        throw new IllegalStateException("lua_pushfstring 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static void lua_pushinteger(long L, long n) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofInteger(n));
    }

    static void lua_pushlightuserdata(long L, long p) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofLightUserdata(Long.valueOf(p)));
    }

    static long lua_pushlstring(long L, long s, long len) {
        LuaState S = st(L);
        byte[] b = (s == 0 || len <= 0) ? new byte[0] : NativeBridge.bytesAtN(s, (int) len);
        pushVal(S, LuaValue.ofString(LuaString.of(b)));
        return NativeBridge.strToNative(b);
    }

    static void lua_pushnil(long L) {
        LuaState S = st(L);
        grow(S, 1);
        S.stack[S.top++].setNil();
    }

    static void lua_pushnumber(long L, double n) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofFloat(n));
    }

    static long lua_pushstring(long L, long s) {
        LuaState S = st(L);
        if (s == 0) {
            grow(S, 1);
            S.stack[S.top++].setNil();
            return 0;
        }
        byte[] b = NativeBridge.bytesAt(s);
        pushVal(S, LuaValue.ofString(LuaString.of(b)));
        return NativeBridge.strToNative(b);
    }

    static long lua_pushthread(long L) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofThread(S));
        return S.mainThread == S ? 1 : 0;
    }

    static void lua_pushvalue(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        pushVal(S, v == null ? LuaValue.ofNil() : v);
    }

    @Generated
    static long lua_pushvfstring(long L, long fmt, long argp) {
        throw new IllegalStateException("lua_pushvfstring 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");
    }

    static long lua_rawequal(long L, long idx1, long idx2) {
        LuaState S = st(L);
        LuaValue a = val(S, (int) idx1);
        LuaValue b = val(S, (int) idx2);
        return (a != null && b != null && LuaValue.rawEqual(a, b)) ? 1 : 0;
    }

    static long lua_rawget(long L, long idx) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue key = S.stack[--S.top];
        LuaValue r = t.isTable() ? rawSlot(t.asTable(), key) : LuaValue.ofNil();
        grow(S, 1);
        S.stack[S.top++].set(r == null ? LuaValue.ofNil() : r);
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_rawgeti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue r = t.isTable() ? rawSlot(t.asTable(), LuaValue.ofInteger(n)) : LuaValue.ofNil();
        grow(S, 1);
        S.stack[S.top++].set(r == null ? LuaValue.ofNil() : r);
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_rawgetp(long L, long idx, long p) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        /*
         * 键是<b>那个指针本身</b>推成的轻量 userdata，不是字符串：C 版
         * {@code lua_rawgetp} 就是 {@code luaH_get(hvalue(t),
         * luaP_lightuserdata(L, p))}。注册表里「按 C 地址存东西」的扩展
         * （给每个 userdata 挂一份宿主状态）全靠这条，之前这里只推了个 nil，
         * 取回来恒是 nil。
         */
        LuaValue key = LuaValue.ofLightUserdata(Long.valueOf(p));
        LuaValue r = (t != null && t.isTable()) ? rawSlot(t.asTable(), key) : null;
        pushVal(S, (r == null) ? LuaValue.ofNil() : r);
        return ttype(S.stack[S.top - 1]);
    }

    static long lua_rawlen(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        if (v.isString()) {
            return v.asString().length();
        }
        if (v.isTable()) {
            return v.asTable().length();
        }
        if (v.baseType() == Lua.TUSERDATA) {
            LuaUserdata ud = (LuaUserdata) v.o;
            /*
             * C 是 {@code uvalue(o)->len}：<b>与载荷是什么无关</b>，就是建
             * userdata 时那一段的字节数。早先这里只看「载荷是不是
             * {@link NativeMemory}」，于是我们自己造的文件句柄一律答 0
             * （参考答 16，见 {@link LuaUserdata#len}）。
             */
            return ud.len;
        }
        return 0;
    }

    static void lua_rawset(long L, long idx) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        LuaValue key = new LuaValue(S.stack[S.top - 2]);
        if (t.isTable()) {
            t.asTable().set(key, val_);
        }
        S.top -= 2;
    }

    static void lua_rawseti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        if (t.isTable()) {
            t.asTable().set(LuaValue.ofInteger(n), val_);
        }
        S.top -= 1;
    }

    static void lua_rawsetp(long L, long idx, long p) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        if (t != null && t.isTable()) {
            t.asTable().set(LuaValue.ofLightUserdata(Long.valueOf(p)), val_);
        }
        S.top -= 1;
    }

    /** {@code lua_resetthread} 已废弃，等价于 {@code lua_closethread(L, NULL)} */
    static long lua_resetthread(long L) {
        return st(L).closeThread(null);
    }

    /*
     * {@code lua_resume}。注意 {@code nres} 是 <b>C 侧的出参</b>：
     * {@code luashim.c} 的手写 stub 在 Java 返回后把它写回 {@code *nres}，
     * 这里拿到的那个值用不上。返回值打包成 {@code status << 32 | nresults}，
     * C 侧再拆开。
     */
    static long lua_resume(long L, long from, long narg, long nres) {
        LuaState S = st(L);
        LuaState f = (from == 0) ? null : st(from);
        int[] out = new int[1];
        int r = S.resume(f, (int) narg, out);
        return ((long) r << 32) | (out[0] & 0xFFFFFFFFL);
    }

    static void lua_rotate(long L, long idx, long n) {
        LuaState S = st(L);
        int p = (int) idx > 0 ? S.ci.func + (int) idx : S.top + (int) idx;
        int t = S.top - 1;
        int m = ((int) n >= 0) ? t - (int) n : p - (int) n - 1;
        reverse(S, p, m);
        reverse(S, m + 1, t);
        reverse(S, p, t);
    }

    /** 同 {@link #lua_getallocf}：整对在 {@code luashim.c} 里就地实现 */
    @Generated
    static void lua_setallocf(long L, long f, long ud) {
        throw new IllegalStateException("lua_setallocf 在 luashim.c 里就地实现，不进 Java");
    }

    /*
     * lstate.c：5.4.7 里这个函数<b>什么都不做</b>，恒返回 {@code LUAI_MAXCCALLS}，
     * 参数连 {@code UNUSED} 都懒得写——别照名字去实现「设上限」。
     * {@code ldblib.c} 的 {@code debug.setcstacklimit} 就靠它把旧值带回去。
     */
    static long lua_setcstacklimit(long L, long limit) {
        return LuaState.MAXCCALLS;
    }

    static void lua_setfield(long L, long idx, long k) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        LuaString key = LuaString.of(NativeBridge.bytesAt(k));
        Vm.setTable(S, t, LuaValue.ofString(key), val_);
        S.top -= 1;
    }

    static void lua_setglobal(long L, long name) {
        LuaState S = st(L);
        LuaString key = LuaString.of(NativeBridge.bytesAt(name));
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        Vm.setTable(S, LuaValue.ofTable(S.globals), LuaValue.ofString(key), val_);
        S.top -= 1;
    }

    static void lua_sethook(long L, long func, long mask, long count) {
        /*
         * C 的钩子是 {@code void (*)(lua_State*, lua_Debug*)}，装进来得先套一层
         * {@link LuaHook}。两端的桥是那块 {@code lua_Debug}：Java 侧填好字段
         * （含 {@code event}），回调时把地址交给 C 函数——它读的正是
         * {@code ar->event} / {@code ar->currentline} 这些。
         * <p>
         * 尺寸按 {@code sizeof(lua_Debug)} 现取，不写死：{@code LUA_IDSIZE}
         * 与结尾那段私有区都可能随版本变，写死就是越界写。
         */
        LuaState S = st(L);
        int m = (int) mask;
        S.setHook((func == 0 || m == 0) ? null : new NativeHook(func), m, (int) count);
    }

    static void lua_seti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        Vm.setTable(S, t, LuaValue.ofInteger(n), val_);
        S.top -= 1;
    }

    static long lua_setiuservalue(long L, long idx, long n) {
        LuaState S = st(L);
        int res = 0;
        LuaUserdata ud = udata(val(S, (int) idx));
        if (ud != null && ud.userValues != null && n >= 1 && n <= ud.userValues.length) {
            ud.userValues[(int) n - 1].set(S.stack[S.top - 1]);
            res = 1;
        }
        S.top -= 1; // 无论成不成都要弹掉那个值
        return res;
    }

    static long lua_setlocal(long L, long ar, long n) {
        LuaState S = st(L);
        DebugLib.LuaDebug d = (ar == 0) ? null : arOf(ar);
        if (d == null) {
            return 0; // C 版 ar 为 NULL 时 luaG_findlocal 会解引用野指针
        }
        String name = DebugLib.setLocal(S, d, (int) n);
        return (name == null) ? 0 : strToNative(name);
    }

    static long lua_setmetatable(long L, long objindex) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) objindex);
        LuaValue mt = S.stack[--S.top];
        LuaTm.setMetatable(S, v, mt.isNil() ? null : mt.asTable());
        return 1;
    }

    static void lua_settable(long L, long idx) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        LuaValue key = new LuaValue(S.stack[S.top - 2]);
        Vm.setTable(S, t, key, val_);
        S.top -= 2;
    }

    static void lua_settop(long L, long idx) {
        LuaState S = st(L);
        int base = S.ci.func + 1;
        int t;
        if (idx >= 0) {
            t = base + (int) idx;
        } else {
            t = S.top + (int) idx + 1;
            if (t < base) {
                t = base; // 负过头：C 里是 api_check，UB
            }
        }
        if (t > S.top) {
            grow(S, t - S.top);
            while (S.top < t) {
                S.stack[S.top++].setNil();
            }
        } else {
            while (S.top > t) {
                S.stack[--S.top].setNil();
            }
        }
    }

    static long lua_setupvalue(long L, long funcindex, long n) {
        LuaState S = st(L);
        LuaValue f = val(S, (int) funcindex);
        LuaValue[] v = new LuaValue[1];
        String name = (f == null) ? null : DebugLib.auxUpValue(f, (int) n, v, null);
        if (name == null) {
            return 0;
        }
        LuaValue src = new LuaValue(S.stack[S.top - 1]);
        v[0].set(src);
        S.top -= 1;
        return strToNative(name);
    }

    @Generated
    /*
     * {@code lapi.c: lua_setwarnf}。三个取值在 {@link LuaState#setWarnf} 里写清楚了；
     * 这里只强调一点：<b>{@code f == 0} 是「设成 NULL」而不是「恢复默认」</b>——
     * C 里 {@code luaE_warning} 的判据是 {@code wf != NULL}，设成 NULL 之后
     * 连 {@code warn("@on")} 都开不了开关。
     */
    static void lua_setwarnf(long L, long f, long ud) {
        st(L).setWarnf(f, ud);
    }

    static long lua_status(long L) {
        return (L == 0) ? 0 : st(L).getStatus();
    }

    static long lua_stringtonumber(long L, long s) {
        /*
         * {@code lapi.c: lua_stringtonumber} = {@code luaO_str2num} + 压栈，
         * 返回 <b>{@code strlen(s) + 1}</b>。
         * <p>
         * 那个 {@code +1} 不是笔误：{@code l_str2int} / {@code l_str2dloc}
         * 返回的是<b>结尾那个 {@code '\0'} 的地址</b>，{@code luaO_str2num}
         * 再写 {@code (e - s) + 1}。三个内部调用点
         * （{@code luaB_tonumber}、{@code l_strlib}、{@code liolib} 的
         * {@code readnum}）一律比 {@code == len + 1}，也就是「整串都得是数字」——
         * 所以 {@code lua_stringtonumber("42abc")} 是 <b>0</b>（{@code tonumber
         * ("42abc")} 给 nil 就是这么来的），不是「认出 42 返回 2」。
         * <p>
         * 本实现 {@link Numeral#str2num} 本来就要求整串吃完（词法分析要的是
         * 这个语义），正好对上；C 版那句 {@code *s != '\0'} 的判据也一致。
         */
        LuaState S = st(L);
        byte[] b = NativeBridge.bytesAt(s);
        if (b == null) {
            return 0;
        }
        LuaValue v = Numeral.str2num(new String(b, java.nio.charset.StandardCharsets.ISO_8859_1));
        if (v == null) {
            return 0;
        }
        pushVal(S, v);
        return b.length + 1;
    }

    static long lua_toboolean(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        return (v != null && v.toBoolean()) ? 1 : 0;
    }

    static long lua_tocfunction(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        if (v.tt == Lua.VLCF) {
            /*
             * 只有<b>从 .so 里来</b>的才有真 C 指针；标准库那些是 Java
             * lambda，没有地址可给。但 C 版对它们同样返回非 NULL（它认的是
             * 「是不是 C 函数」，不是「有没有地址」），所以这里退回句柄——
             * 与 {@code lua_topointer} 一致，非 0 即可。直接 cast 会在
             * {@code print} 这类库函数上抛 ClassCastException。
             */
            return (v.o instanceof LuaNativeFunction)
                    ? ((LuaNativeFunction) v.o).fn : NativeBridge.handle(v.o);
        }
        if (v.tt == Lua.VCCL) {
            Object f = ((CClosure) v.o).f;
            return (f instanceof LuaNativeFunction)
                    ? ((LuaNativeFunction) f).fn : NativeBridge.handle(f);
        }
        return 0;
    }

    @Generated
    /*
     * {@code lapi.c: lua_toclose}：把一个栈槽标成 to-be-closed，等这个 <b>C
     * 函数返回时</b>关掉。
     * <p>
     * 「本函数有待关闭变量」在 C 里编码在 {@code ci->nresults} 上：
     * {@code codeNresults(n) = -n - 3}，于是任何 {@code n} 编码后都
     * {@code < LUA_MULTRET}，{@code hastocloseCfunc} 一眼能认出来，
     * {@code decodeNresults} 又能还原。本实现不另开一个 boolean —— 那会让
     * {@code moveresults} 里「先关变量还是先报返回钩子」的顺序失去依据。
     */
    static void lua_toclose(long L, long idx) {
        LuaState S = st(L);
        S.newTbcUpval(stackIdx(S, (int) idx));
        if (S.ci.nresults >= LuaState.MULTRET) {
            S.ci.nresults = -S.ci.nresults - 3;
        }
    }

    /**
     * {@code lvm.c: luaV_tointeger(o, &res, F2Ieq)}：先 {@code l_strton} 把
     * <b>字符串</b>转成数字，再判「恰好是整数」。
     * <p>
     * 少了「转成数字之后再判一遍」这一句，{@code lua_tointegerx(L, i)}
     * 在 {@code "42.0"} 上给 {@code 0/false} 而参考实现给
     * {@code 42/true} —— 字符串转出来是<b>浮点</b> 42.0，它确实恰好是整数值，
     * 只是不在整数子类型里。
     */
    private static boolean toIntegerExact(LuaValue v, long[] out) {
        if (v == null) {
            return false;
        }
        if (v.isInteger()) {
            out[0] = v.asInteger();
            return true;
        }
        if (v.isFloat()) {
            return LuaValue.floatToIntExact(v.asFloat(), out);
        }
        if (v.isString()) {
            LuaValue n = Vm.strToNum(v);
            if (n == null) {
                return false;
            }
            if (n.isInteger()) {
                out[0] = n.asInteger();
                return true;
            }
            return LuaValue.floatToIntExact(n.asFloat(), out);
        }
        return false;
    }

    static long lua_tointegerx(long L, long idx, long isnum) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        long[] out = new long[1];
        int ok = toIntegerExact(v, out) ? 1 : 0;
        if (isnum != 0) {
            NativeBridge.put32(isnum, ok);
        }
        return ok != 0 ? out[0] : 0;
    }

    static long lua_tolstring(long L, long idx, long len) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null || !(v.isString() || v.isNumber())) {
            if (len != 0) {
                NativeBridge.put64(len, 0);
            }
            return 0;
        }
        LuaString str;
        if (v.isString()) {
            str = v.asString();
        } else {
            /*
             * luaO_tostring：<b>原地</b>把栈上那个数字换成字符串。这一步是可观测的——
             * 之后 lua_type(L, idx) 就是 string 了。
             */
            str = LuaString.latin1(Vm.numberToString(v));
            setVal(S, (int) idx, LuaValue.ofString(str));
        }
        if (len != 0) {
            NativeBridge.put64(len, str.length());
        }
        return str.nativePtr();
    }

    static double lua_tonumberx(long L, long idx, long isnum) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        int ok = 0;
        double r = 0;
        if (v != null) {
            if (v.isNumber()) {
                r = v.isInteger() ? (double) v.asInteger() : v.asFloat();
                ok = 1;
            } else if (v.isString()) {
                LuaValue n = Vm.strToNum(v);
                if (n != null) {
                    r = n.isInteger() ? (double) n.asInteger() : n.asFloat();
                    ok = 1;
                }
            }
        }
        if (isnum != 0) {
            NativeBridge.put32(isnum, ok);
        }
        return r;
    }

    /**
     * {@code lapi.c: lua_topointer} 的取值部分，与 {@code luaL_tolstring} 里
     * {@code "%p"} 打的是<b>同一个值</b>。
     * <p>
     * 轻量 userdata 这一支<b>必须取载荷里的指针值</b>，不能退化成对象的
     * identity hash：C 的 {@code lua_topointer} 对 {@code LUA_TLIGHTUSERDATA}
     * 返回的正是 {@code pvalue(o)}。{@code cjson.null} 是
     * {@code lua_pushlightuserdata(L, NULL)} 推出来的，参考实现
     * {@code print(cjson.null)} 打的是 {@code userdata: 0x0}——拿 identity
     * hash 顶上会打出一串看着很像、其实毫无意义的十六进制。
     */
    static long topointer(LuaValue v) {
        if (v == null) {
            return 0;
        }
        if (v.baseType() == Lua.TUSERDATA) {
            Object d = ((LuaUserdata) v.o).data;
            return d instanceof NativeMemory ? ((NativeMemory) d).ptr : 0;
        }
        if (v.tt == Lua.TLIGHTUSERDATA) {
            return v.o instanceof Long ? ((Long) v.o).longValue() : 0;
        }
        switch (v.baseType()) {
        case Lua.TTABLE:
        case Lua.TFUNCTION:
        case Lua.TTHREAD:
            return NativeBridge.handle(v.o);
        default:
            return 0;
        }
    }

    static long lua_topointer(long L, long idx) {
        /*
         * {@code lapi.c: lua_topointer} 的 switch <b>没有 default 分支指向对象</b>：
         * 数字、字符串、布尔、nil 一律落到 {@code return NULL}。
         * 之前这里拿 {@code NativeBridge.handle} 兜底，于是
         * {@code lua_topointer(L, 1) != NULL} 在数字上给 true，而参考实现给
         * false —— 扩展里「这个值有没有地址可当键用」的判断会整个反过来。
         */
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        return topointer(v);
    }

    static long lua_tothread(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        return (v != null && v.isThread()) ? NativeBridge.handle(v.asThread()) : 0;
    }

    static long lua_touserdata(long L, long idx) {
        LuaState S = st(L);
        LuaValue v = val(S, (int) idx);
        if (v == null) {
            return 0;
        }
        if (v.baseType() == Lua.TUSERDATA) {
            Object d = ((LuaUserdata) v.o).data;
            return d instanceof NativeMemory ? ((NativeMemory) d).ptr : 0;
        }
        if (v.tt == Lua.TLIGHTUSERDATA) {
            return v.o instanceof Long ? ((Long) v.o).longValue() : 0;
        }
        return 0;
    }

    static long lua_type(long L, long idx) {
        return typeOf(st(L), (int) idx);
    }

    static long lua_typename(long L, long tp) {
        /*
         * luaT_typenames_[tp]，按<b>基础类型</b>取——lua_type 返回的已经是基础
         * 类型，不用再过一遍 noVariant。LUA_TNONE(-1) 落在 default 上，给
         * "no value"，与参考实现一致。
         */
        return strToNative(Lua.typeName((int) tp));
    }

    static long lua_upvalueid(long L, long fidx, long n) {
        /*
         * 返回的是「身份」：Lua 闭包给 {@link UpVal} 对象、C 闭包给那个
         * {@link LuaValue} 槽——与 {@code debug.upvalueid} 同一个
         * {@code ownerOut}，所以两个闭包共享 upvalue 时两边都相等。
         * C 版交出去的是 {@code void*}，这里只能给一个稳定句柄：
         * {@code NativeBridge#handle} 的下标，{@code != NULL} 与
         * 「两个 id 相等」两种判断都还成立。
         */
        LuaState S = st(L);
        LuaValue f = val(S, (int) fidx);
        Object[] owner = new Object[1];
        if (f == null || DebugLib.auxUpValue(f, (int) n, new LuaValue[1], owner) == null
                || owner[0] == null) {
            return 0;
        }
        return NativeBridge.handle(owner[0]);
    }

    static void lua_upvaluejoin(long L, long fidx1, long n1, long fidx2, long n2) {
        /*
         * 只接 Lua 闭包：C 的 {@code getupvalref} 对 C 闭包返回的是
         * {@code &f->upvalue[n-1]} 的地址，而 {@code *up1 = *up2} 换的是
         * {@code UpVal*}——C 闭包没有 {@code UpVal}，{@code lua_upvalueid}
         * 虽然给得出地址，join 这一步在 C 里也只有 Lua 闭包走得通
         * （{@code ldblib.c} 也是先 {@code "Lua function expected"}）。
         */
        LuaState S = st(L);
        LuaValue f1 = val(S, (int) fidx1);
        LuaValue f2 = val(S, (int) fidx2);
        Object[] o1 = new Object[1];
        Object[] o2 = new Object[1];
        if (f1 == null || f2 == null
                || DebugLib.auxUpValue(f1, (int) n1, new LuaValue[1], o1) == null
                || DebugLib.auxUpValue(f2, (int) n2, new LuaValue[1], o2) == null) {
            return; // 越界：C 里是断言
        }
        if (f1.tt != Lua.VLIF || f2.tt != Lua.VLIF) {
            return;
        }
        ((LClosure) f1.o).setUpVal((int) n1 - 1, (UpVal) o2[0]);
    }

    /**
     * {@code lapi.c: lua_version} —— 恒返回 {@code LUA_VERSION_NUM}，
     * 连 {@code L} 都不看（{@code UNUSED(L)}）。
     * <p>
     * 几乎所有 C 扩展的 {@code luaopen_xxx} 开头都有
     * {@code luaL_checkversion(L)}，那个宏展开就是调它。
     */
    static double lua_version(long L) {
        return Lua.VERSION_NUM;
    }

    static void lua_warning(long L, long msg, long tocont) {
        /*
         * {@code lapi.c: lua_warning} 是 {@code luaE_warning} 的一层薄壳，
         * 与 {@code warn} 内建（{@link BaseLib#warn}）
         * <b>同一条路</b>——{@code warn("@on")} 那套开关在这里照样生效。
         * 消息是 {@code const char*}，按字节取，别走 Java String。
         */
        st(L).warning(LuaString.of(NativeBridge.bytesAt(msg)), tocont != 0);
    }

    static void lua_xmove(long from, long to, long n) {
        LuaState.xmove(st(from), st(to), (int) n);
    }

    /*
     * {@code lua_yieldk}。
     * <p>
     * <b>这里不抛 {@link LuaYield}</b>：C 侧的 {@code lua_yieldk} stub 在本方法
     * 返回之后置 {@code g_yielding} 再 {@code longjmp} 出去，
     * {@code invokeC} 因此返回 {@code -2}，{@link LuaNativeFunction} 才抛。
     * 反过来的话 Java 异常会先被 {@code luashim_call} 捕获、按 error 处理。
     * <p>
     * 记账与 {@link LuaState#yieldk} 一致（{@code status}、{@code ci.nyield}），
     * 续体记在 {@code ci} 上——恢复时 {@code doResume} / {@code unroll} 会调它。
     * {@code k == 0} 就是不续：C 版 {@code luaB_yield} 正是这么用的，恢复时
     * 直接把 resume 的参数当成那个 C 调用的返回值。
     */
    static long lua_yieldk(long L, long nresults, long ctx, long k) {
        LuaState S = st(L);
        if (!S.yieldable()) {
            throw S.runError((S.mainThread == S)
                    ? "attempt to yield from outside a coroutine"
                    : "attempt to yield across a C-call boundary");
        }
        S.status = LuaState.LUA_YIELD;
        S.ci.nyield = (int) nresults;
        S.ci.k = (k == 0) ? null : new NativeKFunction(k);
        S.ci.ctx = (int) ctx;
        return 0;
    }

    static void luaL_openlibs(long L) {
        /*
         * C 版是逐个 {@code luaL_requiref(L, name, luaopen_X, 1)}，而
         * {@code luaL_requiref} 命中 {@code _LOADED} 就跳过——所以
         * {@code luaL_openlibs} <b>可以连调两次</b>。直接开十次会在
         * {@code io} 上炸（{@code FILE*} 元表已存在），实测参考实现调两次
         * 安静无事。
         */
        LuaLibs.openAllSkippingLoaded(st(L));
    }

    /* ================================================================
     * lualib.h: luaopen_*
     * ================================================================ */

    /*
     * 这十个是符号表对撞撞出来的：{@code nm liblua.a} 的导出符号里有它们，
     * 我们的 dylib 里一个都没有——因为 {@code lualib.h} 用的是
     * {@code LUAMOD_API} 而不是 {@code LUALIB_API}，生成器原本只认后两个，
     * 于是整整十个缺席。光看「头文件里有没有」看不出来。
     * <p>
     * 语义要和 {@code luaL_requiref} 分清：{@code luaopen_X} <b>不动全局表、
     * 不动 {@code package.loaded}</b>，它只是 <b>{@code luaL_newlib}</b>——
     * 造一张<b>全新的</b>表，把库函数填进去，压栈返回 1。
     * 所以 {@code rawequal(n.openlib("table"), table)} 参考给 <b>false</b>：
     * 每次调都是一张新表，改它不影响真的 {@code table}。
     * 第一版这里压的是 {@code package.loaded} 里那份，整好反了。
     */
    private static long openLib(LuaState S, String name,
            java.util.function.Consumer<LuaState> open) {
        LuaValue cur = S.loadedTable().get(name);
        if (cur == null || !cur.isTable()) {
            open.accept(S);
            cur = S.loadedTable().get(name);
        }
        grow(S, 1);
        S.stack[S.top++].setTable(freshCopy(cur));
        return 1;
    }

    /**
     * 照一张表<b>浅拷</b>出一张新表。
     * <p>
     * 库表都是平的（键到函数，没有嵌套），浅拷与 C 的 {@code luaL_newlib}
     * 造出来的那张在观测上一致：同一个 {@code next} 序列、同样改它不影响原表。
     * 之所以不直接「再跑一遍 open」：{@code IoLib} 会去建 {@code FILE*} 元表，
     * 建第二遍就炸（{@code luaL_openlibs} 连调两次都得靠
     * {@code openAllSkippingLoaded} 绕开，同一件事）。
     */
    private static LuaTable freshCopy(LuaValue src) {
        LuaTable dst = new LuaTable();
        if (src == null || !src.isTable()) {
            return dst;
        }
        LuaTable t = src.asTable();
        LuaValue k = LuaValue.ofNil();
        while (true) {
            LuaValue[] kv = t.next(k);
            if (kv == null) {
                break;
            }
            dst.set(new LuaValue(kv[0]), new LuaValue(kv[1]));
            k = kv[0];
        }
        return dst;
    }

    static long luaopen_base(long L) {
        LuaState S = st(L);
        if (S.globals.get("print") == null) {
            BaseLib.open(S);
        }
        /*
         * {@code lbaselib.c: luaopen_base} 与其余九个不同：它 {@code luaL_setfuncs}
         * 到<b>全局表</b>上，压回来的是全局表本身 —— 不是一张新表，
         * 也不是 {@code package.loaded._G}（那份压根不存在）。
         */
        grow(S, 1);
        S.stack[S.top++].setTable(S.globals);
        return 1;
    }

    static long luaopen_coroutine(long L) {
        return openLib(st(L), "coroutine", CoroutineLib::open);
    }

    static long luaopen_table(long L) {
        return openLib(st(L), "table", TableLib::open);
    }

    static long luaopen_io(long L) {
        return openLib(st(L), "io", IoLib::open);
    }

    static long luaopen_os(long L) {
        return openLib(st(L), "os", OsLib::open);
    }

    static long luaopen_string(long L) {
        return openLib(st(L), "string", StringLib::open);
    }

    static long luaopen_math(long L) {
        return openLib(st(L), "math", MathLib::open);
    }

    static long luaopen_utf8(long L) {
        return openLib(st(L), "utf8", Utf8Lib::open);
    }

    static long luaopen_debug(long L) {
        return openLib(st(L), "debug", DebugLib::open);
    }

    static long luaopen_package(long L) {
        return openLib(st(L), "package", PackageLib::open);
    }

}
