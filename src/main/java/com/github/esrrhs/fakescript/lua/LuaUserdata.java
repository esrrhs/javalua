package com.github.esrrhs.fakescript.lua;

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
 * <p>
 * 用户值（{@code lua_getiuservalue}）这里不实现——{@code liolib} 建的
 * {@code LStream} 用 {@code nuvalue = 0}，阶段 7 用不上。
 */
public final class LuaUserdata {

    /** 宿主载荷 */
    public final Object data;

    /**
     * 元表；{@code null} 表示没有。
     * <p>
     * 注意与「已关闭」无关：文件句柄的关闭标记在载荷自己身上。
     */
    public LuaTable metatable;

    public LuaUserdata(Object data) {
        LuaState.chargeGc();
        this.data = data;
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
        gcSentinel = new GcSentinel(this, L.mainThread);
        L.mainThread.registerFinalizable(this);
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
