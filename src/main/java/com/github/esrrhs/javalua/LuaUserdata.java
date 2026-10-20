package com.github.esrrhs.javalua;

/**
 * 完整 userdata —— 对应 {@code lobject.h} 的 {@code Udata}。
 * <p>
 * C 的 {@code Udata} 是「元表 + 用户值数组 + 字节载荷」，载荷由调用方
 * {@code lua_newuserdatauv} 之后自己解释。Java 里没有「在对象后面贴一块内存」
 * 这回事，所以载荷退化成一个 {@link Object} 引用；真要塞字节流的宿主自己
 * 传 {@code byte[]} 进来。
 * <p>
 * <b>元表必须在对象上，不能在类型上。</b>之前的实现里 userdata 走的是
 * {@code LuaState#typeMetatable}，也就是「同一类型共享一张元表」。这在只有
 * 内部 userdata（随机数状态、{@code gmatch} 状态）时看不出问题，一旦要开
 * {@code io} 库就崩：{@code luaL_testudata} 判的是「这个 userdata 的元表
 * 是不是注册表里 {@code FILEHANDLE*} 那一张」，要是所有 userdata 共用，
 * 随机数状态也会被当成文件句柄。
 */
public final class LuaUserdata {

    /** 宿主载荷 */
    public final Object data;

    /**
     * {@code lua_newuserdatauv} 的第 2 个参数：紧跟在载荷后面的用户值数组。
     * <p>
     * C 的 {@code Udata} 是「元表 + 用户值数组 + 字节载荷」连在一起的一块内存，
     * {@code lua_getiuservalue / lua_setiuservalue} 按 {@code 1..nuvalue} 访问。
     * 这里退化成 {@code LuaValue[]}：{@code nuvalue == 0} 时为 {@code null}，
     * 与 C 里「数组长度为 0」等价（越界访问给 {@code LUA_TNONE} / 0）。
     */
    public final LuaValue[] userValues;

    /**
     * 元表；{@code null} 表示没有。
     * <p>
     * 注意与「已关闭」无关：文件句柄的关闭标记在载荷自己身上。
     */
    public LuaTable metatable;

    /**
     * {@code lua_rawlen} 眼里的长度，也就是 C 里 {@code Udata} 载荷那一段的
     * 字节数（{@code lapi.c: case LUA_VUSERDATA: return uvalue(o)->len;}）。
     * <p>
     * 只有两种来源：{@code lua_newuserdatauv} 传进来的 {@code sz}，或者
     * 本实现自己造的 userdata 按「对应的 C 结构体有多大」填。
     * <b>不能一律给 0</b>——扩展里有
     * {@code lua_rawlen(L, 1) != sizeof(...)} 这种自检查，也有拿它当缓冲区
     * 长度的；我们自己的 {@code io} 文件句柄对应 {@code LStream}
     * （{@code FILE*} + {@code lua_CFunction}，LP64 上 16 字节），参考实测
     * {@code lua_rawlen(io.stdout)} 就是 16。
     */
    public int len;

    public LuaUserdata(Object data) {
        this(data, 0);
    }

    public LuaUserdata(Object data, int nuvalue) {
        this(data, nuvalue, 0);
    }

    public LuaUserdata(Object data, int nuvalue, int len) {
        LuaState.chargeGc();
        this.data = data;
        this.len = len;
        if (nuvalue > 0) {
            LuaValue[] uv = new LuaValue[nuvalue];
            for (int i = 0; i < nuvalue; i++) {
                uv[i] = new LuaValue();
            }
            this.userValues = uv;
        } else {
            this.userValues = null;
        }
    }

    /** 取 {@link LuaValue} 里的 userdata 载荷；调用方已确认是 userdata */
    public static Object dataOf(LuaValue v) {
        return ((LuaUserdata) v.o).data;
    }

    /* ==================== __gc ==================== */

    /** 挂了 {@code __gc} 时创建的哨兵；没挂则为 {@code null} */
    private GcSentinel gcSentinel;

    private boolean gcDone;

    /**
     * {@code luaC_checkfinalizer}：挂了 {@code __gc} 就登记一个哨兵。
     * <p>
     * 与 {@link LuaTable#markFinalizable} 同构，判据也相同——<b>只在设元表的
     * 当下看一次</b>。{@code io} 库的文件句柄靠这个在脚本忘了 {@code f:close()}
     * 时把文件关掉。
     */
    void markFinalizable(LuaState L) {
        if (gcSentinel != null || gcDone || metatable == null) {
            return;
        }
        LuaValue tm = metatable.get(LuaString.latin1("__gc"));
        if (tm == null || tm.isNil()) {
            return;
        }
        gcSentinel = new GcSentinel(this, L.mainThread, L.mainThread.registerFinalizable(this));
    }

    /**
     * 跑一次 {@code __gc(ud)}。
     * <p>
     * 与 {@link LuaTable#runGcMetamethod} 一致：{@code __gc} 里的错误一律吞掉，
     * 且只允许跑一次。
     */
    void runGcMetamethod(LuaState L) {
        if (gcDone) {
            return;
        }
        gcDone = true;
        if (metatable == null) {
            return;
        }
        LuaValue tm = metatable.get(LuaString.latin1("__gc"));
        if (tm == null || tm.isNil()) {
            return;
        }
        int func = L.top;
        try {
            L.ensureSlots(func + 2);
            L.stack[func].set(tm);
            L.stack[func + 1].setObject(Lua.VUSERDATA, this);
            L.top = func + 2;
            // 与 LuaTable 同：CIST_FIN 打在发起终结的那一帧上
            boolean wasFin = L.ci.fin;
            L.ci.fin = true;
            try {
                L.callNoYield(func, 0);
            } finally {
                L.ci.fin = wasFin;
            }
        } catch (LuaError e) {
            // __gc 里的错误一律静默（实测参考实现如此）
        } finally {
            L.top = func;
        }
    }
}
