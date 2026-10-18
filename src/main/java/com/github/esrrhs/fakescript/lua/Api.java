package com.github.esrrhs.fakescript.lua;

/**
 * C API 的语义 —— 对应 {@code lapi.c}，{@code luaL_*} 的在 {@link CAux}。
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
    private static long loadBytes(long L, byte[] src, byte[] chunkName) {
        LuaState S = st(L);
        try {
            LClosure cl = S.load(new Zio(src),
                    (chunkName == null) ? new byte[] {'?'} : chunkName);
            pushVal(S, LuaValue.ofClosure(cl));
            return 0; // LUA_OK
        } catch (Throwable t) {
            LuaString m = (t instanceof LuaError)
                    ? LuaAux.asString(((LuaError) t).value)
                    : LuaString.latin1(String.valueOf(t.getMessage()));
            pushVal(S, LuaValue.ofString(m));
            /*
             * {@code lua.h: LUA_ERRSYNTAX} 是 <b>3</b>，不是 2。{@code lua_load}
             * 的返回值 C 扩展是拿去和 {@code LUA_OK / LUA_ERRSYNTAX} 比的，
             * 给 2 会被当成别的失败档。
             */
            return 3; // LUA_ERRSYNTAX
        }
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
        throw unimplemented("luaL_addvalue");
    }

    static long luaL_argerror(long L, long arg, long extramsg) {
        /*
         * 不返回。抛出去的 LuaError 会被 shim 接住转成 longjmp——所以这里<b>要</b>
         * 抛，不能只是把消息压栈。
         */
        String m = extramsg == 0 ? "" : new String(NativeBridge.bytesAt(extramsg));
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
        LuaState S = st(L);
        LuaValue v = S.arg1((int) arg);
        if (!v.isString() && !v.isNumber()) {
            throw S.typeArgError((int) arg, "string");
        }
        LuaString str = v.isString() ? v.asString()
                : LuaString.latin1(Vm.numberToString(v));
        if (l != 0) {
            NativeBridge.put64(l, str.length());
        }
        return NativeBridge.strToNative(str.bytes);
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
            String m = (msg == 0) ? "" : new String(NativeBridge.bytesAt(msg));
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
            String tn = tname == 0 ? "?" : new String(NativeBridge.bytesAt(tname));
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
    }

    static long luaL_error(long L, long fmt, long a2) {
        /*
         * C 侧已经把 fmt 展开成字节了（变参在 C 里才拼得出来），这里只负责
         * luaL_where + 消息 + 抛。抛出去同样是转 longjmp。
         */
        LuaState S = st(L);
        byte[] b = (fmt == 0 || a2 <= 0) ? new byte[0] : NativeBridge.bytesAtN(fmt, (int) a2);
        luaL_where(L, 1);
        LuaString where = S.stack[S.top - 1].asString();
        S.top--;
        throw S.libError(where.concat(LuaString.of(b)));
    }

    static long luaL_execresult(long L, long stat) {
        LuaAux.execResult(st(L), (int) stat);
        return 1;
    }

    static long luaL_fileresult(long L, long stat, long fname) {
        LuaState S = st(L);
        boolean ok = stat != 0;
        LuaString fn = fname == 0 ? null : LuaString.of(NativeBridge.bytesAt(fname));
        LuaAux.fileResult(S, ok, fn, 0);
        return 3;
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

    static long luaL_gsub(long L, long s, long p, long r) {
        LuaState S = st(L);
        pushVal(S, LuaValue.ofString(LuaString.of(NativeBridge.bytesAt(s))));
        luaL_addgsub(S.stack[S.top - 1].hashCode() == 0 ? 0 : 0, s, p, r);
        return 0;
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
        return loadBytes(L, src, cn);
    }

    @Generated
    static long luaL_loadfilex(long L, long filename, long mode) {
        throw unimplemented("luaL_loadfilex");
    }

    static long luaL_loadstring(long L, long s) {
        /*
         * {@code lauxlib.c: luaL_loadstring} 就是
         * {@code luaL_loadbuffer(L, s, strlen(s), s)} —— <b>源串自己就是 chunk
         * 名</b>，所以报错长成 {@code [string "x = "]:1: ...}。传 null 会把
         * 这段前缀整个丢掉。
         */
        byte[] b = NativeBridge.bytesAt(s);
        return loadBytes(L, b, b);
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
            byte[] b = (def == 0) ? new byte[0] : NativeBridge.bytesAt(def);
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

    @Generated
    static void luaL_traceback(long L, long L1, long msg, long level) {
        throw unimplemented("luaL_traceback");
    }

    static long luaL_typeerror(long L, long arg, long tname) {
        String tn = tname == 0 ? "?" : new String(NativeBridge.bytesAt(tname));
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

    static void luaL_where(long L, long lvl) {
        /*
         * TODO：需要 lua_getstack / lua_getinfo 的 "Sl"，C2 阶段接上。
         * 现在给空串——与「拿不到位置信息」那条分支一致，不会编造位置。
         */
        LuaState S = st(L);
        pushVal(S, LuaValue.ofString(LuaString.latin1("")));
    }

    static long lua_absindex(long L, long idx) {
        LuaState S = st(L);
        int i = (int) idx;
        return (i > 0 || i <= REGISTRY) ? i : (S.top - S.ci.func) + i;
    }

    @Generated
    static void lua_arith(long L, long op) {
        throw unimplemented("lua_arith");
    }

    @Generated
    static long lua_atpanic(long L, long panicf) {
        throw unimplemented("lua_atpanic");
    }

    static void lua_callk(long L, long nargs, long nresults, long ctx, long k) {
        LuaState S = st(L);
        int funcIdx = S.top - (int) nargs - 1;
        S.call(funcIdx, (int) nresults);
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

    @Generated
    static void lua_close(long L) {
        throw unimplemented("lua_close");
    }

    @Generated
    static void lua_closeslot(long L, long idx) {
        throw unimplemented("lua_closeslot");
    }

    @Generated
    static long lua_closethread(long L, long from) {
        throw unimplemented("lua_closethread");
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

    @Generated
    static long lua_dump(long L, long writer, long data, long strip) {
        throw unimplemented("lua_dump");
    }

    @Generated
    static long lua_error(long L) {
        throw unimplemented("lua_error");
    }

    @Generated
    static long lua_gc(long L, long what, long a2) {
        throw unimplemented("lua_gc");
    }

    @Generated
    static long lua_getallocf(long L, long ud) {
        throw unimplemented("lua_getallocf");
    }

    static long lua_getfield(long L, long idx, long k) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaString key = LuaString.of(NativeBridge.bytesAt(k));
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, t, LuaValue.ofString(key), S.top);
        S.top++;
        return t.isTable() ? Lua.TTABLE : t.baseType();
    }

    static long lua_getglobal(long L, long name) {
        LuaState S = st(L);
        LuaString key = LuaString.of(NativeBridge.bytesAt(name));
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, LuaValue.ofTable(S.globals), LuaValue.ofString(key), S.top);
        S.top++;
        return Lua.TTABLE;
    }

    @Generated
    static long lua_gethook(long L) {
        throw unimplemented("lua_gethook");
    }

    @Generated
    static long lua_gethookcount(long L) {
        throw unimplemented("lua_gethookcount");
    }

    @Generated
    static long lua_gethookmask(long L) {
        throw unimplemented("lua_gethookmask");
    }

    static long lua_geti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        grow(S, 1);
        S.stack[S.top].setNil();
        Vm.getTable(S, t, LuaValue.ofInteger(n), S.top);
        S.top++;
        return t.isTable() ? Lua.TTABLE : t.baseType();
    }

    @Generated
    static long lua_getinfo(long L, long what, long ar) {
        throw unimplemented("lua_getinfo");
    }

    @Generated
    static long lua_getiuservalue(long L, long idx, long n) {
        throw unimplemented("lua_getiuservalue");
    }

    @Generated
    static long lua_getlocal(long L, long ar, long n) {
        throw unimplemented("lua_getlocal");
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

    @Generated
    static long lua_getstack(long L, long level, long ar) {
        throw unimplemented("lua_getstack");
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
        return t.isTable() ? Lua.TTABLE : t.baseType();
    }

    static long lua_gettop(long L) {
        LuaState S = st(L);
        return S.top - S.ci.func - 1;
    }

    @Generated
    static long lua_getupvalue(long L, long funcindex, long n) {
        throw unimplemented("lua_getupvalue");
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

    @Generated
    static long lua_isyieldable(long L) {
        throw unimplemented("lua_isyieldable");
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
        throw unimplemented("lua_load");
    }

    @Generated
    static long lua_newstate(long f, long ud) {
        throw unimplemented("lua_newstate");
    }

    @Generated
    static long lua_newthread(long L) {
        throw unimplemented("lua_newthread");
    }

    static long lua_newuserdatauv(long L, long sz, long nuvalue) {
        LuaState S = st(L);
        NativeMemory m = new NativeMemory(NativeBridge.alloc(sz), sz);
        /*
         * 载荷就是 {@code m} 本身——{@code LuaValue.ofUserdata} 会再包一层
         * {@link LuaUserdata}（元表挂在那层上）。之前这里先
         * {@code new LuaUserdata(m)} 再交给 {@code ofUserdata}，等于套了两层，
         * 于是 {@code lua_touserdata} 取到的 {@code data} 是个
         * {@link LuaUserdata} 而不是 {@link NativeMemory}，一律给 0 ——
         * {@code luaL_checkudata} 因此连自己刚建出来的 userdata 都不认
         * （报 {@code NatBox expected, got NatBox}）。
         */
        pushVal(S, LuaValue.ofUserdata(m));
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
        return t.isTable() ? Lua.TTABLE : t.baseType();
    }

    static long lua_rawgeti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue r = t.isTable() ? rawSlot(t.asTable(), LuaValue.ofInteger(n)) : LuaValue.ofNil();
        grow(S, 1);
        S.stack[S.top++].set(r == null ? LuaValue.ofNil() : r);
        return t.isTable() ? Lua.TTABLE : t.baseType();
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
        return t.isTable() ? Lua.TTABLE : t.baseType();
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
            Object d = ((LuaUserdata) v.o).data;
            return d instanceof NativeMemory ? ((NativeMemory) d).size : 0;
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

    @Generated
    static long lua_resetthread(long L) {
        throw unimplemented("lua_resetthread");
    }

    @Generated
    static long lua_resume(long L, long from, long narg, long nres) {
        throw unimplemented("lua_resume");
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

    @Generated
    static void lua_setallocf(long L, long f, long ud) {
        throw unimplemented("lua_setallocf");
    }

    @Generated
    static long lua_setcstacklimit(long L, long limit) {
        throw unimplemented("lua_setcstacklimit");
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

    @Generated
    static void lua_sethook(long L, long func, long mask, long count) {
        throw unimplemented("lua_sethook");
    }

    static void lua_seti(long L, long idx, long n) {
        LuaState S = st(L);
        LuaValue t = val(S, (int) idx);
        LuaValue val_ = new LuaValue(S.stack[S.top - 1]);
        Vm.setTable(S, t, LuaValue.ofInteger(n), val_);
        S.top -= 1;
    }

    @Generated
    static long lua_setiuservalue(long L, long idx, long n) {
        throw unimplemented("lua_setiuservalue");
    }

    @Generated
    static long lua_setlocal(long L, long ar, long n) {
        throw unimplemented("lua_setlocal");
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

    @Generated
    static long lua_setupvalue(long L, long funcindex, long n) {
        throw unimplemented("lua_setupvalue");
    }

    @Generated
    static void lua_setwarnf(long L, long f, long ud) {
        throw unimplemented("lua_setwarnf");
    }

    @Generated
    static long lua_status(long L) {
        throw unimplemented("lua_status");
    }

    @Generated
    static long lua_stringtonumber(long L, long s) {
        throw unimplemented("lua_stringtonumber");
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
    static void lua_toclose(long L, long idx) {
        throw unimplemented("lua_toclose");
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
        return NativeBridge.strToNative(str.bytes);
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

    @Generated
    static long lua_upvalueid(long L, long fidx, long n) {
        throw unimplemented("lua_upvalueid");
    }

    @Generated
    static void lua_upvaluejoin(long L, long fidx1, long n1, long fidx2, long n2) {
        throw unimplemented("lua_upvaluejoin");
    }

    @Generated
    static double lua_version(long L) {
        throw unimplemented("lua_version");
    }

    @Generated
    static void lua_warning(long L, long msg, long tocont) {
        throw unimplemented("lua_warning");
    }

    static void lua_xmove(long from, long to, long n) {
        LuaState.xmove(st(from), st(to), (int) n);
    }

    @Generated
    static long lua_yieldk(long L, long nresults, long ctx, long k) {
        throw unimplemented("lua_yieldk");
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

}
