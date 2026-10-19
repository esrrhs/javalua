package com.github.esrrhs.fakescript.lua;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * 与 {@code luashim} 动态库之间的那层 —— {@code dlopen}、句柄表、进出 native 的
 * 那几个原语。
 * <p>
 * 加载分三步，缺一不可：
 * <ol>
 *   <li>{@code System.load} 把库装进来（这步是 {@code RTLD_LOCAL}）；</li>
 *   <li>再 {@code dlopen} 同一路径一遍、带 {@code RTLD_GLOBAL}——不提升到全局
 *       命名空间，后面 {@code dlopen} 的扩展 .so 在 flat namespace 里<b>根本看
 *       不见</b> {@code lua_*}；</li>
 *   <li>{@code init()} 让 C 侧缓存住 {@code dispatch} 的方法 id。</li>
 * </ol>
 * <p>
 * 装不上（没这个平台的库、或者不允许加载动态库）时 {@link #AVAILABLE} 是
 * false，{@code package.loadlib} 照旧走「未启用动态库」的兜底分支——不能因为
 * native 层不可用就让整个引擎起不来。
 */
public final class NativeBridge {

    /** native 层能不能用 */
    public static final boolean AVAILABLE;

    /** 装不上的原因，调试用 */
    public static final String LOAD_ERROR;

    private static final ArrayList<Object> HANDLES = new ArrayList<>();
    private static final IdentityHashMap<Object, Integer> HANDLE_OF = new IdentityHashMap<>();

    static {
        boolean ok = false;
        String err = null;
        try {
            ok = load();
        } catch (Throwable t) {
            err = String.valueOf(t.getMessage());
        }
        AVAILABLE = ok;
        LOAD_ERROR = err;
    }

    private NativeBridge() {
    }

    /* ==================== 加载 ==================== */

    private static boolean load() throws Exception {
        String p = System.getProperty("fakescript.luashim");
        if (p == null) {
            Path f = extractBundled();
            p = (f == null) ? null : f.toString();
        }
        if (p == null) {
            return false;
        }
        System.load(p);
        // System.load 是 RTLD_LOCAL，必须再提升一次
        long h = dlopen(p, true);
        if (h == 0) {
            return false;
        }
        init();
        return true;
    }

    /** 随包带的库（{@code native/<平台>/...}）解到临时目录再加载 */
    private static Path extractBundled() throws Exception {
        String plat = platform();
        if (plat == null) {
            return null;
        }
        String res = "/native/" + plat + "/" + libFileName();
        try (InputStream in = NativeBridge.class.getResourceAsStream(res)) {
            if (in == null) {
                return null;
            }
            Path dir = Files.createTempDirectory("luashim");
            Path out = dir.resolve(libFileName());
            Files.copy(in, out);
            out.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            return out;
        }
    }

    private static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin-" + (arch.contains("aarch64") || arch.contains("arm") ? "arm64" : "x86_64");
        }
        if (os.contains("win")) {
            return "windows-" + (arch.contains("64") ? "x86_64" : "x86");
        }
        if (os.contains("linux")) {
            return "linux-" + (arch.contains("aarch64") || arch.contains("arm") ? "arm64" : "x86_64");
        }
        return null;
    }

    private static String libFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libluashim.dylib";
        }
        if (os.contains("win")) {
            return "luashim.dll";
        }
        return "libluashim.so";
    }

    /* ==================== JNI ==================== */

    static native void init();

    static native long dlopen(String path, boolean global);

    static native long dlsym(long handle, String name);

    static native String dlerror();

    /**
     * 调一个 {@code lua_CFunction}。
     *
     * @return {@code >=0} 是返回值个数；{@code -1} 是 longjmp（{@code lua_error}）；
     *         {@code -2} 是 yield；{@code -3} 是函数指针为空
     */
    static native int invokeC(long fn, long L);

    /**
     * 调一个 {@code lua_KFunction}（续体）：
     * {@code int (*)(lua_State *L, int status, lua_KContext ctx)}。
     *
     * @return 同 {@link #invokeC}
     */
    static native int invokeK(long fn, long L, int status, int ctx);

    /**
     * {@code luaL_newstate} 装的那个默认 panic 函数的地址。
     * {@code lua_atpanic} 第一次要把它作为「旧值」交回去（见
     * {@code Api#lua_atpanic}）。
     */
    static native long defaultPanic();

    /**
     * 把警告消息交给 C 的 {@code lua_WarnFunction}：
     * {@code void (*)(void *ud, const char *msg, int tocont)}。
     */
    static native void invokeWarn(long fn, long ud, byte[] msg, boolean tocont);

    /** 取回 C 侧接住的 Java 异常（{@code lua_error} 那一跳的载荷） */
    static native Throwable takePending();

    static native long alloc(long n);

    static native void free(long p);

    /** 从 {@code p} 读一个 NUL 结尾的 C 串 */
    static native byte[] bytesAt(long p);

    /** 从 {@code p} 读 {@code n} 个字节（Lua 串可以含 {@code \0}，不能按 C 串读） */
    static native byte[] bytesAtN(long p, int n);

    /** 拷进 C 侧的环形缓冲，返回一个 NUL 结尾的 {@code const char*} */
    static native long strToNative(byte[] b);

    /** 往 {@code alloc} 出来的块里写字节并补 NUL；见 {@link LuaString#nativePtr} */
    static native void putBytes(long p, byte[] b);

    static native void put64(long p, long v);

    static native long get64(long p);

    static native void put32(long p, int v);

    static native int get32(long p);

    /** 句柄换回 {@code lua_State*}：给那些要把 {@code L} 交回 C 的回调用 */
    static native long statePtr(long handle);

    /** 回调任意 C 函数指针：reader / writer / hook / alloc / 续体都走这条 */
    static native long callPtr(long fn, long a0, long a1, long a2, long a3);

    static native void bufAppend(long b, byte[] bytes);

    static native void dbgFill(long ar, long[] vals);

    static native void dbgShortSrc(long ar, byte[] b);

    /** {@code sizeof(lua_Debug)}：Java 侧要给 C 的钩子造一块 */
    static native long dbgSize();

    /* ==================== C FILE* 操作 ==================== */

    static native long cFopen(byte[] path, byte[] mode);
    static native int cFclose(long f);
    static native int cFread(long f, byte[] buf, int off, int len);
    static native int cFwrite(long f, byte[] buf, int off, int len);
    static native int cFseek(long f, long offset, int whence);
    static native long cFtell(long f);
    static native int cFflush(long f);
    static native int cFerror(long f);
    static native void cClearerr(long f);
    static native int cFeof(long f);
    static native byte[] cFgets(long f, int maxLen);
    static native int cFgetc(long f);
    static native int cFputc(long f, int c);
    static native long cPopen(byte[] cmd, byte[] mode);
    static native int cPclose(long f);
    static native int cFileno(long f);
    static native long cSizeofLuaLStream();
    static native long cNewLuaLStream(long filePtr, long closefPtr);
    static native long cStreamGetF(long streamPtr);

    /** 取 C 标准流的 FILE*：0=stdin, 1=stdout, 2=stderr */
    static native long cStdStream(int which);

    /** 取 C 的 {@code errno}（{@code cFopen} 等失败后读它） */
    static native int cGetErrno();

    /* ==================== C 内存的生命周期 ==================== */

    /**
     * 一块「跟着某个 Java 对象活到死」的 C 内存。
     * <p>
     * 环形缓冲（{@code ring_put}，32 格）只能保证「接下来 32 次调用之内还
     * 有效」，而 C 的 {@code lua_tolstring} 交回去的是<b>字符串自己的存储</b>，
     * 只要那个字符串还在就一直有效、而且<b>两次取同一个值必得同一个地址</b>。
     * 于是这里改成按对象惰性分配 + {@code Cleaner} 回收：地址稳定，也不漏。
     */
    private static final java.lang.ref.Cleaner CLEANER =
            java.lang.ref.Cleaner.create();

    private static final class FreeAction implements Runnable {
        private final long p;

        FreeAction(long p) {
            this.p = p;
        }

        @Override
        public void run() {
            free(p);
        }
    }

    /** 让 {@code p} 随 {@code owner} 一起被回收；{@code p} 必须是 {@code alloc} 来的 */
    static void freeWith(Object owner, long p) {
        CLEANER.register(owner, new FreeAction(p));
    }

    /* ==================== 句柄 ==================== */

    /** {@code lua_State*} 在 C 侧只是个下标；这里把下标换回 {@link LuaState} */
    public static synchronized long handle(Object o) {
        Integer h = HANDLE_OF.get(o);
        if (h != null) {
            return h;
        }
        HANDLES.add(o);
        int idx = HANDLES.size(); // 1 起，0 留给「没有」
        HANDLE_OF.put(o, idx);
        return idx;
    }

    public static synchronized LuaState state(long h) {
        if (h <= 0 || h > HANDLES.size()) {
            return null;
        }
        Object o = HANDLES.get((int) (h - 1));
        return o instanceof LuaState ? (LuaState) o : null;
    }

    /* ==================== C 进 Java 的入口 ==================== */

    /** {@code luashim_call} 的落点：C 侧靠方法签名 {@code (IJJJJJJD)J} 找到它 */
    static long dispatch(int id, long a0, long a1, long a2,
            long a3, long a4, long a5, double d0) {
        return CApi.dispatch(id, a0, a1, a2, a3, a4, a5, d0);
    }

    static double dispatchD(int id, long a0, long a1, long a2,
            long a3, long a4, long a5, double d0) {
        return CApi.dispatchD(id, a0, a1, a2, a3, a4, a5, d0);
    }

    /** 只是为了让 {@code Paths} 有个引用，免得编译器警告未用 */
    static Path tmpRoot() {
        return Paths.get(System.getProperty("java.io.tmpdir", "."));
    }
}
