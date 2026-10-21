/*
 * luashim —— 让 .so 里未定义的 lua_* / luaL_* 解析到我们的 Java 实现。
 *
 * 原理（三件事，缺一不可）：
 *   1. 本库被 dlopen 成 RTLD_GLOBAL，扩展 .so 的 flat namespace 查找才找得到；
 *   2. lua_State* 从头到尾只是个句柄，C 侧从不解引用，所以可以是假指针；
 *   3. lua_error 的 longjmp 只跳到<b>最内层 Java→C 边界</b>（invokeC 的
 *      setjmp），绝不跨过 Java 帧——跨过去 JVM 就崩了。
 *
 * 大部分函数是 native/gen_capi.py 生成的薄 stub（见 luashim_stubs.inc），
 * 这里只写那些打包不了的：变参、要遍历 C 结构体的、数据符号、以及 yield。
 */
#include <jni.h>
#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <pthread.h>
#include <dlfcn.h>
#include <locale.h>   /* setlocale / localeconv */
#include <langinfo.h>   /* nl_langinfo(RADIXCHAR) —— 诊断用（macOS 上没有 DECIMAL_POINT 这个名字） */
#if defined(__APPLE__)
/*
 * macOS 的 SDK 只在 {@code __XSI_VISIBLE} 下声明 {@code nl_langinfo_l}，
 * 而 glibc 一直是公开的。参数类型在两边都是 int（macOS 的 nl_item_t 就是 int），
 * 这里补一条声明，不改任何行为。
 */
extern char *nl_langinfo_l(int item, locale_t loc);
#endif
#include <sys/wait.h>   /* luaL_execresult 要用本平台的 WIFEXITED / WTERMSIG */
#include <unistd.h>   /* pipe / read / write / close */
#include <spawn.h>    /* posix_spawn：子进程不能归 JVM 管，原因见 cSpawn */
#include <signal.h>   /* sigfillset + POSIX_SPAWN_SETSIGDEF */

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
#include "luashim_ids.h"

/* ==================== 进 Java ==================== */

static JavaVM *g_vm = NULL;
static jclass g_cls = NULL;
static jmethodID g_dispatch = NULL;
static jmethodID g_dispatchD = NULL;
/** LuaYield 类：Java 异常要跨过 C 帧时，用它判「这是 yield 还是 error」 */
static jclass g_clsYield = NULL;

/** longjmp 落点：每个 Java→C 调用在<b>最外层 C 帧</b>设一个，嵌套时串成链 */
static _Thread_local jmp_buf *g_jmp = NULL;
/** lua_yieldk 走的是同一条 longjmp，只是出口标记不同 */
static _Thread_local int g_yielding = 0;
/** Java 实现抛出来的异常：存成全局引用，等边界取回去重抛 */
static _Thread_local jthrowable g_pending = NULL;

static JNIEnv *env_now(void) {
    JNIEnv *env = NULL;
    if (g_vm == NULL) {
        return NULL;
    }
    if ((*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK
            || env == NULL) {
        (*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL);
    }
    return env;
}

static void capture_pending(JNIEnv *env) {
    jthrowable t = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    if (t == NULL) {
        return;
    }
    if (g_pending != NULL) {
        (*env)->DeleteGlobalRef(env, g_pending);
    }
    g_pending = (*env)->NewGlobalRef(env, t);
    (*env)->DeleteLocalRef(env, t);
}

void luashim_throw(void);

jlong luashim_call(int id, jlong a0, jlong a1, jlong a2,
        jlong a3, jlong a4, jlong a5, jdouble d0) {
    JNIEnv *env = env_now();
    if (env == NULL || g_cls == NULL) {
        return 0;
    }
    jlong r = (*env)->CallStaticLongMethod(env, g_cls, g_dispatch,
            (jint)id, a0, a1, a2, a3, a4, a5, d0);
    if ((*env)->ExceptionCheck(env)) {
        /* Java 侧抛了：转成 C 侧的 longjmp，不能让异常穿过 C 帧 */
        capture_pending(env);
        luashim_throw();
    }
    return r;
}

jdouble luashim_callD(int id, jlong a0, jlong a1, jlong a2,
        jlong a3, jlong a4, jlong a5, jdouble d0) {
    JNIEnv *env = env_now();
    if (env == NULL || g_cls == NULL) {
        return 0;
    }
    jdouble r = (*env)->CallStaticDoubleMethod(env, g_cls, g_dispatchD,
            (jint)id, a0, a1, a2, a3, a4, a5, d0);
    if ((*env)->ExceptionCheck(env)) {
        capture_pending(env);
        luashim_throw();
    }
    return r;
}

void luashim_throw(void) {
    if (g_jmp != NULL) {
        longjmp(*g_jmp, 1);
    }
    /* 没有边界说明是在 C 栈之外调的 lua_error，真 Lua 这里是 panic */
    fprintf(stderr, "luashim: 没有可跳回的调用边界\n");
    abort();
}

/* ==================== lua_State* 句柄 ==================== */
/*
 * 假指针落在真实分配块里，块内偏移 8..16 留作 LUA_EXTRASPACE——
 * lua_getextraspace 是宏 ((char*)L - LUA_EXTRASPACE)，不动这块就是野指针。
 */
#define SLOT_STRIDE 16
#define SLOT_CHUNK 256
#define SLOT_MAXCHUNK 4096
/*
 * 一块要 (SLOT_CHUNK + 1) 格，不是 SLOT_CHUNK 格。
 * <p>
 * {@code slot_ptr(i)} 交出去的是 {@code base + (i-1)%CHUNK*STRIDE + STRIDE}——
 * 那个 {@code + STRIDE} 是给 LUA_EXTRASPACE 留的（{@code lua_getextraspace}
 * 是 {@code (char*)L - LUA_EXTRASPACE}，越界一格就是野指针）。于是第
 * {@code SLOT_CHUNK} 号句柄（每块的最后一格）落在 {@code base + CHUNK*STRIDE}，
 * 正好在块尾之外：{@code slot_idx} 认不出它，交回 0，C 侧就拿到 <b>NULL 的
 * lua_State*</b>。
 * <p>
 * 实测：跑到第 256 个句柄时 {@code luaopen_nat} 收到 NULL，紧接着
 * {@code luaL_newmetatable(NULL, ...)} 在 Java 侧 NPE。主线程上只有 79 条
 * 用例时句柄用不到 256，一直全绿；把语料塞进协程再跑一遍（158 条）就撞上了。
 */
#define SLOT_BYTES ((SLOT_CHUNK + 1) * SLOT_STRIDE)

static char *g_chunks[SLOT_MAXCHUNK];
static int g_nchunks = 0;
static pthread_mutex_t g_slotlock = PTHREAD_MUTEX_INITIALIZER;

static char *slot_ptr(jlong idx) {
    char *base;
    if (idx <= 0) {
        return NULL;
    }
    jlong c = (idx - 1) / SLOT_CHUNK;
    jlong o = (idx - 1) % SLOT_CHUNK;
    if (c >= SLOT_MAXCHUNK) {
        return NULL;
    }
    pthread_mutex_lock(&g_slotlock);
    while (c >= g_nchunks) {
        g_chunks[g_nchunks++] = (char *)calloc(SLOT_BYTES, 1);
    }
    base = g_chunks[c];
    pthread_mutex_unlock(&g_slotlock);
    return base + o * SLOT_STRIDE + SLOT_STRIDE;
}

static jlong slot_idx(void *L) {
    if (L == NULL) {
        return 0;
    }
    for (int c = 0; c < g_nchunks; c++) {
        char *b = g_chunks[c];
        if ((char *)L < b || (char *)L >= b + SLOT_BYTES) {
            continue;
        }
        jlong off = (char *)L - b;
        return c * SLOT_CHUNK + off / SLOT_STRIDE; /* 1 起 */
    }
    return 0;
}

#define LUASHIM_HANDLE(L) ((jlong)slot_idx((void *)(L)))
#define LUASHIM_STATE(i) ((lua_State *)slot_ptr(i))

/* ==================== 字符串回传 ==================== */
/*
 * lua_tostring 要交一个真的 const char* 出去。真 Lua 返回的是串内部地址（存活
 * 到 GC），我们只能给一块环形缓冲——C 代码<b>跨好几个 lua_* 调用</b>攥着这个
 * 指针就会读到后面的串。真要攥住，得自己 strdup。
 */
#define STR_RING 32
static _Thread_local char *g_str[STR_RING];
static _Thread_local size_t g_strcap[STR_RING];
static _Thread_local int g_stri = 0;

static char *ring_put(const char *b, size_t n) {
    int i = g_stri;
    g_stri = (g_stri + 1) % STR_RING;
    if (g_strcap[i] < n + 1) {
        free(g_str[i]);
        g_strcap[i] = (n + 1 < 64) ? 64 : n + 1;
        g_str[i] = (char *)malloc(g_strcap[i]);
    }
    memcpy(g_str[i], b, n);
    g_str[i][n] = '\0';
    return g_str[i];
}

/* ==================== JNI 入口 ==================== */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_init(JNIEnv *env, jclass cls) {
    g_cls = (*env)->NewGlobalRef(env, cls);
    g_dispatch = (*env)->GetStaticMethodID(env, cls, "dispatch",
            "(IJJJJJJD)J");
    g_dispatchD = (*env)->GetStaticMethodID(env, cls, "dispatchD",
            "(IJJJJJJD)D");
    {
        jclass y = (*env)->FindClass(env,
                "com/github/esrrhs/javalua/LuaYield");
        if (y != NULL) {
            g_clsYield = (*env)->NewGlobalRef(env, y);
            (*env)->DeleteLocalRef(env, y);
        }
    }
}

/**
 * 「刚捕获的 Java 异常是不是 yield」。
 * <p>
 * C 里 {@code luaD_throw(L, LUA_YIELD)} 和抛错走的是同一条 longjmp，靠
 * {@code g_yielding} 区分。但本实现多一种情形：yield 是被调函数<b>在 Java 侧</b>
 * 抛出来的（{@code coroutine.yield} 经由 {@code lua_callk} 一路冒上来），
 * C 函数里那一句 {@code lua_yieldk} 压根没执行到，{@code g_yielding} 就是 0，
 * 旧代码会把它当成 error——{@code coroutine.wrap} 里 C 函数调用 Lua 回调再
 * yield 的场景必错。所以出口处再问一次 Java。
 */
static int pending_is_yield(void) {
    JNIEnv *env;
    int r = 0;
    if (g_pending == NULL || g_clsYield == NULL) {
        return 0;
    }
    env = env_now();
    if (env != NULL) {
        r = (*env)->IsInstanceOf(env, g_pending, g_clsYield) ? 1 : 0;
    }
    return r;
}

JNIEXPORT jobject JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_takePending(JNIEnv *env,
        jclass cls) {
    jobject r = NULL;
    (void)cls;
    /* 交出去的是局部引用，全局引用当场删——不然每次报错都漏一个 */
    if (g_pending != NULL) {
        r = (*env)->NewLocalRef(env, g_pending);
        (*env)->DeleteGlobalRef(env, g_pending);
        g_pending = NULL;
    }
    return r;
}

/*
 * 把警告消息交给 {@code lua_WarnFunction}。
 * <p>
 * 消息是一次性的：回调同步跑完就返回，所以给一块栈上的缓冲足够；
 * 超长才落到堆上。
 */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_invokeWarn(JNIEnv *env,
        jclass cls, jlong fn, jlong ud, jbyteArray b, jboolean tocont) {
    char small[512];
    char *buf = small;
    jsize n;
    jbyte *tmp;
    void (*f)(void *, const char *, int);
    (void)cls;
    if (fn == 0 || b == NULL) {
        return;
    }
    n = (*env)->GetArrayLength(env, b);
    if (n < 0) {
        return;
    }
    if ((size_t)n + 1 > sizeof(small)) {
        buf = (char *)malloc((size_t)n + 1);
        if (buf == NULL) {
            return;
        }
    }
    tmp = (*env)->GetByteArrayElements(env, b, NULL);
    if (tmp == NULL) {
        if (buf != small) {
            free(buf);
        }
        return;
    }
    memcpy(buf, tmp, (size_t)n);
    buf[n] = '\0';
    (*env)->ReleaseByteArrayElements(env, b, tmp, JNI_ABORT);
    f = (void (*)(void *, const char *, int))(intptr_t)fn;
    f((void *)(intptr_t)ud, buf, tocont ? 1 : 0);
    if (buf != small) {
        free(buf);
    }
}

/** 把本库提升进全局命名空间：System.load 用的是 RTLD_LOCAL，不提升扩展就找不到 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dlopen(JNIEnv *env,
        jclass cls, jstring path, jboolean global) {
    (void)cls;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    void *h = dlopen(p, RTLD_NOW | (global ? RTLD_GLOBAL : RTLD_LOCAL));
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong)(intptr_t)h;
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dlsym(JNIEnv *env,
        jclass cls, jlong h, jstring name) {
    (void)cls;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    void *s = dlsym((void *)(intptr_t)h, n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jstring JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dlerror(JNIEnv *env,
        jclass cls) {
    (void)cls;
    const char *e = dlerror();
    return e == NULL ? NULL : (*env)->NewStringUTF(env, e);
}

/*
 * 调一个 lua_CFunction。setjmp 就设在这里——<b>整个 C 侧唯一的落点</b>。
 * 返回：>=0 是结果个数；-1 是 longjmp（lua_error）；-2 是 yield。
 */
JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_invokeC(JNIEnv *env,
        jclass cls, jlong fn, jlong L) {
    (void)cls;
    if (fn == 0) {
        return -3;
    }
    jmp_buf buf;
    jmp_buf *prev = g_jmp;
    volatile int r;
    g_yielding = 0;
    g_jmp = &buf;
    if (setjmp(buf) == 0) {
        int (*f)(void *) = (int (*)(void *))(intptr_t)fn;
        r = f(LUASHIM_STATE(L));
    } else {
        r = (g_yielding || pending_is_yield()) ? -2 : -1;
    }
    g_jmp = prev;
    g_yielding = 0;
    return (jint)r;
}

/*
 * 调一个 lua_KFunction：{@code int (*)(lua_State *L, int status, lua_KContext ctx)}。
 * <p>
 * 续体是 <b>resume 之后</b>才跑的，所以它也必须有自己的落点——它里面照样可以
 * 再 {@code lua_yieldk}、照样可以抛错。返回码与 {@code invokeC} 同义。
 */
JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_invokeK(JNIEnv *env,
        jclass cls, jlong fn, jlong L, jint status, jlong ctx) {
    (void)cls;
    if (fn == 0) {
        return -3;
    }
    jmp_buf buf;
    jmp_buf *prev = g_jmp;
    volatile int r;
    g_yielding = 0;
    g_jmp = &buf;
    if (setjmp(buf) == 0) {
        int (*f)(void *, int, long) = (int (*)(void *, int, long))(intptr_t)fn;
        r = f(LUASHIM_STATE(L), (int)status, (long)ctx);
    } else {
        r = (g_yielding || pending_is_yield()) ? -2 : -1;
    }
    g_jmp = prev;
    g_yielding = 0;
    return (jint)r;
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_alloc(JNIEnv *env,
        jclass cls, jlong n) {
    (void)env;
    (void)cls;
    return (jlong)(intptr_t)calloc(1, (size_t)(n > 0 ? n : 1));
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_free(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    free((void *)(intptr_t)p);
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_bytesAt(JNIEnv *env,
        jclass cls, jlong p) {
    (void)cls;
    if (p == 0) {
        return NULL;
    }
    size_t n = strlen((const char *)(intptr_t)p);
    jbyteArray a = (*env)->NewByteArray(env, (jsize)n);
    if (a != NULL && n > 0) {
        (*env)->SetByteArrayRegion(env, a, 0, (jsize)n, (const jbyte *)(intptr_t)p);
    }
    return a;
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_bytesAtN(JNIEnv *env,
        jclass cls, jlong p, jint n) {
    (void)cls;
    if (p == 0 || n < 0) {
        return NULL;
    }
    jbyteArray a = (*env)->NewByteArray(env, n);
    if (a != NULL && n > 0) {
        (*env)->SetByteArrayRegion(env, a, 0, n, (const jbyte *)(intptr_t)p);
    }
    return a;
}

/** 拷进环形缓冲，返回 NUL 结尾的 C 串地址 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_strToNative(JNIEnv *env,
        jclass cls, jbyteArray b) {
    (void)cls;
    if (b == NULL) {
        return 0;
    }
    jsize n = (*env)->GetArrayLength(env, b);
    jbyte *tmp = (*env)->GetByteArrayElements(env, b, NULL);
    if (tmp == NULL) {
        return 0;
    }
    char *p = ring_put((const char *)tmp, (size_t)n);
    (*env)->ReleaseByteArrayElements(env, b, tmp, JNI_ABORT);
    return (jlong)(intptr_t)p;
}

/**
 * 往 {@code alloc} 出来的那块里写字节，末尾补一个 NUL。
 * 给 {@link LuaString#nativePtr} 用：那边要的是「随字符串活到死」的地址，
 * 不能用环形缓冲（见 ring_put 的注释）。
 */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_putBytes(JNIEnv *env,
        jclass cls, jlong p, jbyteArray b) {
    (void)cls;
    if (p == 0 || b == NULL) {
        return;
    }
    jsize n = (*env)->GetArrayLength(env, b);
    jbyte *tmp = (*env)->GetByteArrayElements(env, b, NULL);
    if (tmp == NULL) {
        return;
    }
    memcpy((void *)(intptr_t)p, tmp, (size_t)n);
    ((char *)(intptr_t)p)[n] = '\0';
    (*env)->ReleaseByteArrayElements(env, b, tmp, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_put64(JNIEnv *env,
        jclass cls, jlong p, jlong v) {
    (void)env;
    (void)cls;
    if (p != 0) {
        *((jlong *)(intptr_t)p) = v;
    }
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_get64(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    return p == 0 ? 0 : *((jlong *)(intptr_t)p);
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_put32(JNIEnv *env,
        jclass cls, jlong p, jint v) {
    (void)env;
    (void)cls;
    if (p != 0) {
        *((jint *)(intptr_t)p) = v;
    }
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_get32(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    return p == 0 ? 0 : *((jint *)(intptr_t)p);
}

/** {@code sizeof(lua_Debug)}：Java 侧要给 C 的钩子分配一块，尺寸不能猜 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dbgSize(JNIEnv *env,
        jclass cls) {
    (void)env;
    (void)cls;
    return (jlong)sizeof(lua_Debug);
}

/*
 * 句柄换回 {@code lua_State*}。{@code lua_dump} 的 writer 第一个参数是
 * {@code lua_State*}，而 Java 侧手里只有下标——交 0 过去，那些会拿
 * {@code L} 去调别的 {@code lua_*} 的 writer 就当场崩了。
 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_statePtr(JNIEnv *env,
        jclass cls, jlong idx) {
    (void)env;
    (void)cls;
    return (jlong)(intptr_t)slot_ptr(idx);
}

/** 回调任意 C 函数指针：reader / writer / hook / alloc / 续体都走这一条 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_callPtr(JNIEnv *env,
        jclass cls, jlong fn, jlong a0, jlong a1, jlong a2, jlong a3) {
    (void)env;
    (void)cls;
    if (fn == 0) {
        return 0;
    }
    jlong (*f)(jlong, jlong, jlong, jlong) =
            (jlong (*)(jlong, jlong, jlong, jlong))(intptr_t)fn;
    return f(a0, a1, a2, a3);
}

/* ==================== 手写的那批符号 ==================== */

const char lua_ident[] =
        "$LuaVersion: Lua 5.4.7 Copyright (C) 1994-2024 Lua.org, PUC-Rio $"
        "$LuaAuthors: R. Ierusalimschy, L. H. de Figueiredo, W. Celes $";

/*
 * luaO_pushvfstring 的忠实移植。
 *
 * 为什么不能拿 vsnprintf 糊过去：lobject.c 里它只认 s c d I f p U % 八个
 * 动词，其余一律 luaG_runerror("invalid option '%%%c' to 'lua_pushfstring'")；
 * 而且 %d/%I/%f 走的是 addnum2buff（= luaO_tostr，%.14g，整数样的补 .0），
 * 不是 printf 的 %d/%f。早先这里直接 vsnprintf，三处全错：
 *   lua_pushfstring(L, "%f", 1.5)   我们 1.500000 / 参考 1.5
 *   lua_pushfstring(L, "%x", 42)    我们 2a       / 参考报错
 *   lua_pushfstring(L, "%5d", 42)   我们 "  42"   / 参考报错
 * 而 luaL_error 就是 luaL_where + lua_pushvfstring，所以扩展里任何一条
 * 带 %f 或带精度的 luaL_error 都会露出来。
 */

/* lua_Integer 是 long long，于是 l_uacInt 也是；LP64 之外的平台要改这里 */
typedef long long shim_uacInt;

typedef struct {
    char *b;
    size_t n;
    size_t cap;
    char small[256];
} FSBuf;

static void fs_add(FSBuf *f, const char *s, size_t l) {
    if (f->n + l + 1 > f->cap) {
        size_t cap = f->cap;
        while (cap < f->n + l + 1) {
            cap *= 2;
        }
        char *nb = (char *) malloc(cap);
        if (nb == NULL) {
            return; /* 认了：参考会抛 "not enough memory"，这里截断 */
        }
        memcpy(nb, f->b, f->n);
        if (f->b != f->small) {
            free(f->b);
        }
        f->b = nb;
        f->cap = cap;
    }
    memcpy(f->b + f->n, s, l);
    f->n += l;
    f->b[f->n] = '\0';
}

/*
 * 数字必须经 Lua 自己的 tostring，不能 snprintf：%.14g 之后给整数样的结果
 * 补 .0（参考 lua_pushfstring(L,"%f",503.0) 给 "503.0"），而 printf 的 %f
 * 给 "503.000000"。所以这里推到栈上再 lua_tolstring 取回来。
 */
static void fs_addnum(lua_State *L, FSBuf *f, int isint,
        long long iv, double dv) {
    size_t len = 0;
    const char *s;
    luaL_checkstack(L, 1, NULL);
    if (isint) {
        lua_pushinteger(L, (lua_Integer) iv);
    } else {
        lua_pushnumber(L, (lua_Number) dv);
    }
    s = lua_tolstring(L, -1, &len);
    if (s != NULL) {
        fs_add(f, s, len);
    }
    lua_pop(L, 1);
}

/** lobject.c: luaO_utf8esc —— %U 把码点编成 UTF-8，倒着写进 buff 尾部 */
static int shim_utf8esc(char *buff, unsigned long x) {
    int n = 1;
    if (x < 0x80) {
        buff[8 - 1] = (char) x;
    } else {
        unsigned int mfb = 0x3f;
        do {
            buff[8 - (n++)] = (char) (0x80 | (x & 0x3f));
            x >>= 6;
            mfb >>= 1;
        } while (x > mfb);
        buff[8 - n] = (char) ((~mfb << 1) & x);
    }
    return n;
}

static const char *pushvfshim(lua_State *L, const char *fmt, va_list ap) {
    FSBuf f;
    const char *e;
    f.b = f.small;
    f.n = 0;
    f.cap = sizeof f.small;
    f.small[0] = '\0';
    while ((e = strchr(fmt, '%')) != NULL) {
        fs_add(&f, fmt, (size_t) (e - fmt));
        switch (*(e + 1)) {
            case 's': {
                const char *s = va_arg(ap, char *);
                if (s == NULL) {
                    s = "(null)";
                }
                fs_add(&f, s, strlen(s));
                break;
            }
            case 'c': {
                char c = (char) (unsigned char) va_arg(ap, int);
                fs_add(&f, &c, 1);
                break;
            }
            case 'd':
                fs_addnum(L, &f, 1, (long long) va_arg(ap, int), 0.0);
                break;
            case 'I':
                fs_addnum(L, &f, 1, (long long) va_arg(ap, shim_uacInt), 0.0);
                break;
            case 'f':
                fs_addnum(L, &f, 0, 0, (double) va_arg(ap, double));
                break;
            case 'p': {
                char pb[32];
                void *p = va_arg(ap, void *);
                int l = snprintf(pb, sizeof pb, "%p", p);
                if (l > 0) {
                    fs_add(&f, pb, (size_t) l);
                }
                break;
            }
            case 'U': {
                char ub[8];
                int l = shim_utf8esc(ub, (unsigned long) va_arg(ap, long));
                fs_add(&f, ub + 8 - l, (size_t) l);
                break;
            }
            case '%':
                fs_add(&f, "%", 1);
                break;
            default: {
                /*
                 * 参考是 luaG_runerror：从 C 帧抛，<b>不带</b>位置前缀
                 * （isLua(ci) 为假就不 addinfo）。所以这里用 lua_error
                 * 而不是 luaL_error——后者会先拼 luaL_where。
                 */
                char msg[64];
                int l = snprintf(msg, sizeof msg,
                        "invalid option '%%%c' to 'lua_pushfstring'", *(e + 1));
                if (l < 0) {
                    l = 0;
                }
                if (f.b != f.small) {
                    free(f.b);
                }
                lua_pushlstring(L, msg, (size_t) l);
                lua_error(L); /* 不返回 */
                return NULL;
            }
        }
        fmt = e + 2;
    }
    fs_add(&f, fmt, strlen(fmt));
    lua_pushlstring(L, f.b, f.n);
    {
        char *r = ring_put(f.b, f.n);
        if (f.b != f.small) {
            free(f.b);
        }
        return r;
    }
}

LUA_API const char *(lua_pushvfstring) (lua_State *L, const char *fmt,
        va_list argp) {
    return pushvfshim(L, fmt, argp);
}

LUA_API const char *(lua_pushfstring) (lua_State *L, const char *fmt, ...) {
    const char *r;
    va_list ap;
    va_start(ap, fmt);
    r = pushvfshim(L, fmt, ap);
    va_end(ap);
    return r;
}

LUA_API int (lua_gc) (lua_State *L, int what, ...) {
    /* 只有这四个 what 带变参，乱读 va_arg 会拿到垃圾 */
    jlong e0 = 0, e1 = 0, e2 = 0;
    va_list ap;
    va_start(ap, what);
    switch (what) {
        case LUA_GCSTEP:
        case LUA_GCSETPAUSE:
        case LUA_GCSETSTEPMUL:
            e0 = va_arg(ap, int);
            break;
        case LUA_GCGEN:
            e0 = va_arg(ap, int);
            e1 = va_arg(ap, int);
            break;
        case LUA_GCINC:
            e0 = va_arg(ap, int);
            e1 = va_arg(ap, int);
            e2 = va_arg(ap, int);
            break;
        default:
            break;
    }
    va_end(ap);
    return (int)luashim_call(ID_lua_gc, LUASHIM_HANDLE(L), (jint)what, e0, e1, e2, 0, 0.0);
}

/*
 * lauxlib.c 原文四行，一行都不许省：
 *   luaL_where(L, 1); lua_pushvfstring(L, fmt, ap);
 *   lua_concat(L, 2); return lua_error(L);
 * 变参只能在 C 侧读；pushvfstring 的动词表是 Lua 自己的（见 pushvfshim）；
 * lua_error 走 luashim_throw 的 longjmp，而那个落点只在本文件的最外层 C 帧。
 */
LUALIB_API int (luaL_error) (lua_State *L, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    luaL_where(L, 1);
    lua_pushvfstring(L, fmt, ap);
    va_end(ap);
    lua_concat(L, 2);
    lua_error(L); /* 不返回 */
    return 0;
}

/*
 * luaL_fileresult / luaL_execresult：errno 是<b>每个线程一份的 C 变量</b>，
 * Java 读不到，只有在 C 侧先取出来再作为参数传进去。lauxlib.c 的第一句就是
 * `int en = errno;`（注释写着 "calls to Lua API may change this value"），
 * 顺序照抄。
 */
LUALIB_API int (luaL_fileresult) (lua_State *L, int stat, const char *fname) {
    int en = errno;
    return (int) luashim_call(ID_luaL_fileresult, LUASHIM_HANDLE(L),
            (jint) stat, (intptr_t) fname, (jint) en, 0, 0, 0.0);
}

LUALIB_API int (luaL_execresult) (lua_State *L, int stat) {
    static const char exitw[] = "exit";
    static const char signalw[] = "signal";
    const char *what = exitw;
    int en = errno;
    if (stat != 0 && en != 0) {
        return luaL_fileresult(L, 0, NULL);
    }
    /*
     * lauxlib.c 的 l_inspectstat。用<b>本平台</b>的 wait 宏，别自己造位运算：
     * 实测（macOS）execresult(-1) 给 "exit",-1 而不是 "signal",127 ——
     * 因为 BSD 的 WIFSIGNALED 排除了 _W_STOPPED(0177)，而 (x & 0x7f) 恰好
     * 是 0177。照 Linux 那套位运算写就会错在这一条上。
     */
    if (WIFEXITED(stat)) {
        stat = WEXITSTATUS(stat);
    } else if (WIFSIGNALED(stat)) {
        stat = WTERMSIG(stat);
        what = signalw;
    }
    return (int) luashim_call(ID_luaL_execresult, LUASHIM_HANDLE(L),
            (jint) stat, (jint) en, (intptr_t) what, 0, 0, 0.0);
}

/* luaL_Reg 数组在 C 侧，Java 读不到：照 lauxlib.c 逐个注册 */
LUALIB_API void (luaL_setfuncs) (lua_State *L, const luaL_Reg *l, int nup) {
    static const char noslots[] = "not enough slots";
    luashim_call(ID_luaL_checkstack, LUASHIM_HANDLE(L), (jint)nup,
            (intptr_t)noslots, 0, 0, 0, 0.0);
    for (; l->name != NULL; l++) {
        int i;
        if (l->func == NULL) {
            /* lauxlib.c 的「占位」分支：压一个 false，不开闭包 */
            luashim_call(ID_lua_pushboolean, LUASHIM_HANDLE(L), (jint)0,
                    0, 0, 0, 0, 0.0);
        } else {
            for (i = 0; i < nup; i++) {
                luashim_call(ID_lua_pushvalue, LUASHIM_HANDLE(L), (jint)(-nup), 0, 0, 0, 0, 0.0);
            }
            luashim_call(ID_lua_pushcclosure, LUASHIM_HANDLE(L),
                    (intptr_t)(intptr_t)l->func, (jint)nup, 0, 0, 0, 0.0);
        }
        luashim_call(ID_lua_setfield, LUASHIM_HANDLE(L), (jint)(-nup - 2),
                (intptr_t)l->name, 0, 0, 0, 0.0);
    }
    luashim_call(ID_lua_settop, LUASHIM_HANDLE(L), (jint)(-nup - 1), 0, 0, 0, 0, 0.0);
}

/* const char *const lst[]：打包成 "a\0b\0c\0" 连长度一起交给 Java */
/* 整个照 lauxlib.c：buffinit + addgsub + pushresult 全在 C 侧 */
LUALIB_API const char *(luaL_gsub) (lua_State *L, const char *s,
        const char *p, const char *r) {
    luaL_Buffer b;
    luaL_buffinit(L, &b);
    luaL_addgsub(&b, s, p, r);
    luaL_pushresult(&b);
    return lua_tostring(L, -1);
}

/* ---- lua_load / luaL_loadfilex：reader 是 C 函数指针，只能在 C 侧喂 ---- */
/*
 * 读函数得在 C 侧调：它是 {@code const char *(*)(lua_State*, void*, size_t*)}，
 * 出参 {@code size_t*} 要写 C 内存。若让 Java 去调，不但得凭空造一块
 * {@code size_t} 的落脚点，还要让 longjmp 跨过一层 JNI 帧——跨不得。
 * 于是这里把源<b>整段读进一块缓冲</b>再交给 Java 的
 * {@code luaL_loadbufferx}。
 *
 * 「整段读」与 C 版的「边读边编」在这里等价：{@code luaD_protectedparser}
 * 无论如何都要把流读到 EOF 才返回结果，读函数的调用次序也完全一致。
 */
LUA_API int (lua_load) (lua_State *L, lua_Reader reader, void *dt,
        const char *chunkname, const char *mode) {
    size_t cap = 1024;
    size_t n = 0;
    char *buf;
    int r;
    if (reader == NULL) {
        return LUA_ERRSYNTAX;
    }
    buf = (char *)malloc(cap);
    if (buf == NULL) {
        return LUA_ERRMEM;
    }
    for (;;) {
        size_t sz = 0;
        const char *p = reader(L, dt, &sz);
        if (p == NULL || sz == 0) {
            break; /* EOF：读函数返回 NULL 或长度 0 都算结束 */
        }
        while (n + sz > cap) {
            char *nb;
            cap *= 2;
            nb = (char *)realloc(buf, cap);
            if (nb == NULL) {
                free(buf);
                return LUA_ERRMEM;
            }
            buf = nb;
        }
        memcpy(buf + n, p, sz);
        n += sz;
    }
    r = (int)luashim_call(ID_luaL_loadbufferx, LUASHIM_HANDLE(L),
            (intptr_t)buf, (jlong)n, (intptr_t)chunkname, (intptr_t)mode, 0, 0.0);
    free(buf);
    return r;
}

/* 下面四个是 lauxlib.c 里 luaL_loadfilex 的零件，照抄——BOM、'#' 首行、
 * 二进制重开这三件事都在这里，少一件都会让某个真实文件加载出错 */
typedef struct LoadF {
    int n;              /* 预读的字符数 */
    FILE *f;            /* 正在读的文件 */
    char buff[BUFSIZ];  /* 读块缓冲 */
} LoadF;

static const char *getF (lua_State *L, void *ud, size_t *size) {
    LoadF *lf = (LoadF *)ud;
    (void)L;
    if (lf->n > 0) {          /* 有预读的字符？ */
        *size = lf->n;        /* 先交出去 */
        lf->n = 0;
    }
    else {                    /* 从文件读一块 */
        if (feof(lf->f)) {
            return NULL;      /* 见 lauxlib 的注释：避免多等一次输入 */
        }
        *size = fread(lf->buff, 1, sizeof(lf->buff), lf->f);
    }
    return lf->buff;
}

static int skipBOM (FILE *f) {
    int c = getc(f);
    if (c == 0xEF && getc(f) == 0xBB && getc(f) == 0xBF) {
        return getc(f);
    }
    return c;
}

static int skipcomment (FILE *f, int *cp) {
    int c = *cp = skipBOM(f);
    if (c == '#') {  /* 首行是注释（Unix 可执行脚本）？ */
        do {
            c = getc(f);
        } while (c != EOF && c != '\n');
        *cp = getc(f);  /* 注释之后那个字符 */
        return 1;
    }
    return 0;
}

static int errfile (lua_State *L, const char *what, int fnameindex) {
    int err = errno;
    const char *filename = lua_tostring(L, fnameindex) + 1;
    if (err != 0) {
        lua_pushfstring(L, "cannot %s %s: %s", what, filename, strerror(err));
    }
    else {
        lua_pushfstring(L, "cannot %s %s", what, filename);
    }
    lua_remove(L, fnameindex);
    return LUA_ERRFILE;
}

LUALIB_API int (luaL_loadfilex) (lua_State *L, const char *filename,
        const char *mode) {
    LoadF lf;
    int status, readstatus;
    int c;
    int fnameindex = lua_gettop(L) + 1;  /* 文件名在栈上的位置 */
    if (filename == NULL) {
        lua_pushliteral(L, "=stdin");
        lf.f = stdin;
    }
    else {
        lua_pushfstring(L, "@%s", filename);
        errno = 0;
        lf.f = fopen(filename, "r");
        if (lf.f == NULL) {
            return errfile(L, "open", fnameindex);
        }
    }
    lf.n = 0;
    if (skipcomment(lf.f, &c)) {  /* 读开头那一小段 */
        lf.buff[lf.n++] = '\n';   /* 补一个换行，好让行号不错位 */
    }
    if (c == LUA_SIGNATURE[0]) {  /* 二进制文件？ */
        lf.n = 0;                 /* 可能补进去的换行去掉 */
        if (filename) {           /* 真文件才需要以二进制重开 */
            errno = 0;
            lf.f = freopen(filename, "rb", lf.f);
            if (lf.f == NULL) {
                return errfile(L, "reopen", fnameindex);
            }
            skipcomment(lf.f, &c);  /* 重新读开头 */
        }
    }
    if (c != EOF) {
        lf.buff[lf.n++] = (char)c;  /* 第一个有效字符 */
    }
    errno = 0;
    status = lua_load(L, getF, &lf, lua_tostring(L, -1), mode);
    readstatus = ferror(lf.f);
    if (filename) {
        fclose(lf.f);  /* 出错也要关 */
    }
    if (readstatus) {
        lua_settop(L, fnameindex);  /* 丢掉 lua_load 的结果 */
        return errfile(L, "read", fnameindex);
    }
    lua_remove(L, fnameindex);
    return status;
}

LUALIB_API int (luaL_checkoption) (lua_State *L, int arg, const char *def,
        const char *const lst[]) {
    size_t total = 0;
    int n = 0;
    int r;
    char *packed;
    char *w;
    int i;
    for (i = 0; lst[i] != NULL; i++) {
        total += strlen(lst[i]) + 1;
        n++;
    }
    packed = (char *)malloc(total + 1);
    if (packed == NULL) {
        return 0;
    }
    w = packed;
    for (i = 0; i < n; i++) {
        size_t k = strlen(lst[i]);
        memcpy(w, lst[i], k + 1);
        w += k + 1;
    }
    *w = '\0';
    r = (int)luashim_call(ID_luaL_checkoption, LUASHIM_HANDLE(L), (jint)arg,
            (intptr_t)def, (intptr_t)packed, (jint)n, (jlong)total, 0.0);
    free(packed);
    return r;
}

/* ---- luaL_Buffer：b/size/n 是 C 侧的三个字段，Java 改不了，只能在 C 里长 ---- */

static void buffgrow(luaL_Buffer *B, size_t extra) {
    size_t want = B->n + extra + 1;
    size_t ns = B->size < 64 ? 64 : B->size;
    char *nb;
    while (ns < want) {
        ns *= 2;
    }
    if (B->b == B->init.b) {
        nb = (char *)malloc(ns);
        if (nb == NULL) {
            return;
        }
        memcpy(nb, B->b, B->n);
    } else {
        nb = (char *)realloc(B->b, ns);
        if (nb == NULL) {
            return;
        }
    }
    B->b = nb;
    B->size = ns;
}

static void buffreset(luaL_Buffer *B) {
    if (B->b != B->init.b) {
        free(B->b);
    }
    B->b = B->init.b;
    B->size = LUAL_BUFFERSIZE;
    B->n = 0;
}

LUALIB_API void (luaL_buffinit) (lua_State *L, luaL_Buffer *B) {
    B->L = L;
    B->b = B->init.b;
    B->size = LUAL_BUFFERSIZE;
    B->n = 0;
}

LUALIB_API char *(luaL_buffinitsize) (lua_State *L, luaL_Buffer *B, size_t sz) {
    luaL_buffinit(L, B);
    return luaL_prepbuffsize(B, sz);
}

LUALIB_API char *(luaL_prepbuffsize) (luaL_Buffer *B, size_t sz) {
    if (B->size - B->n < sz) {
        buffgrow(B, sz);
    }
    return B->b + B->n;
}

LUALIB_API void (luaL_addlstring) (luaL_Buffer *B, const char *s, size_t l) {
    char *d = luaL_prepbuffsize(B, l);
    if (d != NULL) {
        memcpy(d, s, l);
        B->n += l;
    }
}

LUALIB_API void (luaL_addstring) (luaL_Buffer *B, const char *s) {
    luaL_addlstring(B, s, strlen(s));
}

/*
 * 照 lauxlib.c：lua_tolstring 取栈顶那个值的字节，追加进缓冲，再弹掉。
 * 整个在 C 侧做完——Java 侧拿不到 luaL_Buffer 的三个字段（b、size、n），
 * 硬要在 Java 里做就得按固定 offset 去改 C 栈上的结构体，那比这里多一整层
 * 脆弱的偏移假设。
 *
 * 用的是 lua_tolstring 而不是 luaL_tolstring：**不认 __tostring**。给表调它
 * 会追加空（lua_tolstring 返回 NULL、长度 0），与参考一致——别"顺手"改成认
 * 元方法，那会把 table 变成 "table: 0x..." 写进结果串。
 */
LUALIB_API void (luaL_addvalue) (luaL_Buffer *B) {
    lua_State *L = B->L;
    size_t len = 0;
    const char *s = lua_tolstring(L, -1, &len);
    if (s != NULL) {
        luaL_addlstring(B, s, len);
    }
    lua_pop(L, 1);
}

LUALIB_API void (luaL_pushresult) (luaL_Buffer *B) {
    luashim_call(ID_lua_pushlstring, LUASHIM_HANDLE(B->L),
            (intptr_t)B->b, (jlong)B->n, 0, 0, 0, 0.0);
    buffreset(B);
}

LUALIB_API void (luaL_pushresultsize) (luaL_Buffer *B, size_t sz) {
    B->n += sz;
    luaL_pushresult(B);
}

/** 给 Java 用：往 luaL_Buffer 里追加字节 */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_bufAppend(JNIEnv *env,
        jclass cls, jlong bp, jbyteArray b) {
    (void)cls;
    luaL_Buffer *B = (luaL_Buffer *)(intptr_t)bp;
    jsize n;
    jbyte *tmp;
    if (bp == 0 || b == NULL) {
        return;
    }
    n = (*env)->GetArrayLength(env, b);
    tmp = (*env)->GetByteArrayElements(env, b, NULL);
    if (tmp == NULL) {
        return;
    }
    luaL_addlstring(B, (const char *)tmp, (size_t)n);
    (*env)->ReleaseByteArrayElements(env, b, tmp, JNI_ABORT);
}

/*
 * 分配器 —— {@code lua_getallocf} / {@code lua_setallocf}。
 * <p>
 * 必须在 C 侧就地实现：{@code lua_getallocf} 交出去的是<b>能被直接调用的 C 函数
 * 指针</b>，扩展拿它就是为了自己管内存（{@code lpeg} 的 {@code lpcode.c} 有三处
 * {@code f(ud, ptr, osize, nsize)}），Java 交不出这种东西。
 * <p>
 * 语义照 {@code lauxlib.c} 的 {@code l_alloc}：{@code nsize == 0} 就是释放。
 * <p>
 * 这两个是<b>进程级</b>而不是 {@code global_State} 级的（真 Lua 是后者）。差得
 * 远的场景基本不存在——一个进程里同时跑好几个 Lua 状态、还各自换分配器。
 */
static void *luashim_allocf(void *ud, void *ptr, size_t osize, size_t nsize) {
    (void)ud;
    (void)osize;
    if (nsize == 0) {
        free(ptr);
        return NULL;
    }
    if (ptr == NULL) {
        return malloc(nsize);
    }
    return realloc(ptr, nsize);
}

static lua_Alloc g_allocf = luashim_allocf;
static void *g_allocud = NULL;

/*
 * {@code lua_newstate} 的分配器参数在 C 侧接住：Java 那头只管起状态，而
 * {@code g->frealloc} / {@code g->ud} 得真的换掉，否则 {@code lua_getallocf}
 * 交回去的还是默认那个，调用方换分配器的效果就丢了。
 */
LUA_API lua_State *(lua_newstate) (lua_Alloc f, void *ud) {
    jlong h = luashim_call(ID_lua_newstate, (intptr_t)f, (intptr_t)ud,
            0, 0, 0, 0, 0.0);
    if (h == 0) {
        return NULL;
    }
    g_allocf = (f != NULL) ? f : luashim_allocf;
    g_allocud = ud;
    return LUASHIM_STATE(h);
}

LUA_API lua_Alloc (lua_getallocf) (lua_State *L, void **ud) {
    (void)L;
    if (ud != NULL) {
        *ud = g_allocud;
    }
    return g_allocf;
}

LUA_API void (lua_setallocf) (lua_State *L, lua_Alloc f, void *ud) {
    (void)L;
    g_allocf = (f != NULL) ? f : luashim_allocf;
    g_allocud = ud;
}

/*
 * 默认 panic 函数，照 lauxlib.c 那个 {@code panic} 抄。
 * <p>
 * 存在的意义是<b>初值</b>：{@code lua_newstate} 里 {@code g->panic = NULL}，
 * 但 {@code luaL_newstate} 紧接着 {@code lua_atpanic(L, &panic)} —— 所以从
 * Lua 解释器里看，{@code lua_atpanic} 第一次返回的<b>不是</b> NULL（实测探针
 * 第一条 {@code old == NULL} 给 false）。
 */
static int luashim_panic(lua_State *L) {
    const char *msg = (lua_type(L, -1) == LUA_TSTRING)
            ? lua_tostring(L, -1)
            : "error object is not a string";
    fprintf(stderr, "PANIC: unprotected error in call to Lua API (%s)\n", msg);
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_defaultPanic(JNIEnv *env,
        jclass cls) {
    (void)env;
    (void)cls;
    return (jlong)(intptr_t)luashim_panic;
}

/*
 * yield 与 error 走同一条 longjmp，只是出口标记不同——invokeC 据此返回 -2，
 * Java 侧再抛 LuaYield。这里不能用生成的 stub：生成版会正常返回，C 函数就继续
 * 往下跑了，那不是 yield 的语义。
 */
LUA_API int (lua_yieldk) (lua_State *L, int nresults, lua_KContext ctx,
        lua_KFunction k) {
    luashim_call(ID_lua_yieldk, LUASHIM_HANDLE(L), (jint)nresults,
            (jlong)ctx, (intptr_t)k, 0, 0, 0.0);
    g_yielding = 1;
    luashim_throw();
    return 0;
}

/*
 * {@code lua_resume} 的 {@code nres} 是<b>出参</b>（{@code int *}），而 Java 那头
 * 只能回一个 long。所以打包：高 32 位是 status，低 32 位是该从协程栈上搬走的
 * 结果个数。
 */
LUA_API int (lua_resume) (lua_State *L, lua_State *from, int nargs,
        int *nres) {
    jlong r = luashim_call(ID_lua_resume, LUASHIM_HANDLE(L),
            LUASHIM_HANDLE(from), (jint)nargs, 0, 0, 0, 0.0);
    int status = (int)(r >> 32);
    if (nres != NULL) {
        *nres = (int)(r & 0xffffffffL);
    }
    return status;
}

/* ---- lua_Debug：C 侧的 struct，Java 按固定下标给值，C 按 offsetof 落字段 ---- */

#define DBG_N 15
static const struct {
    size_t off;
    size_t size;
} DBG_FIELD[DBG_N] = {
    {offsetof(lua_Debug, event), sizeof(int)},
    {offsetof(lua_Debug, name), sizeof(const char *)},
    {offsetof(lua_Debug, namewhat), sizeof(const char *)},
    {offsetof(lua_Debug, what), sizeof(const char *)},
    {offsetof(lua_Debug, source), sizeof(const char *)},
    {offsetof(lua_Debug, srclen), sizeof(size_t)},
    {offsetof(lua_Debug, currentline), sizeof(int)},
    {offsetof(lua_Debug, linedefined), sizeof(int)},
    {offsetof(lua_Debug, lastlinedefined), sizeof(int)},
    {offsetof(lua_Debug, nups), sizeof(unsigned char)},
    {offsetof(lua_Debug, nparams), sizeof(unsigned char)},
    {offsetof(lua_Debug, isvararg), sizeof(char)},
    {offsetof(lua_Debug, istailcall), sizeof(char)},
    {offsetof(lua_Debug, ftransfer), sizeof(unsigned short)},
    {offsetof(lua_Debug, ntransfer), sizeof(unsigned short)},
};

/** 把 Java 给的 DBG_N 个值按各自宽度写进 lua_Debug */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dbgFill(JNIEnv *env,
        jclass cls, jlong arp, jlongArray vals) {
    (void)cls;
    char *ar = (char *)(intptr_t)arp;
    jlong *v;
    int i;
    if (arp == 0 || vals == NULL) {
        return;
    }
    if ((*env)->GetArrayLength(env, vals) < DBG_N) {
        return;
    }
    v = (*env)->GetLongArrayElements(env, vals, NULL);
    if (v == NULL) {
        return;
    }
    for (i = 0; i < DBG_N; i++) {
        void *dst = ar + DBG_FIELD[i].off;
        switch (DBG_FIELD[i].size) {
            case 1: *((char *)dst) = (char)v[i]; break;
            case 2: *((short *)dst) = (short)v[i]; break;
            case 4: *((int *)dst) = (int)v[i]; break;
            default: *((long *)dst) = (long)v[i]; break;
        }
    }
    (*env)->ReleaseLongArrayElements(env, vals, v, JNI_ABORT);
}

/** short_src 是定长数组，单独拷 */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_dbgShortSrc(JNIEnv *env,
        jclass cls, jlong arp, jbyteArray b) {
    (void)cls;
    char *ar = (char *)(intptr_t)arp;
    jsize n;
    jbyte *tmp;
    char *dst = ar + offsetof(lua_Debug, short_src);
    if (arp == 0 || b == NULL) {
        return;
    }
    n = (*env)->GetArrayLength(env, b);
    tmp = (*env)->GetByteArrayElements(env, b, NULL);
    if (tmp == NULL) {
        return;
    }
    if (n > LUA_IDSIZE - 1) {
        n = LUA_IDSIZE - 1;
    }
    memcpy(dst, tmp, (size_t)n);
    dst[n] = '\0';
    (*env)->ReleaseByteArrayElements(env, b, tmp, JNI_ABORT);
}

/* ==================== C FILE* 操作 ==================== */

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFopen(JNIEnv *env,
        jclass cls, jbyteArray path, jbyteArray mode) {
    (void)cls;
    jsize pathLen = (*env)->GetArrayLength(env, path);
    jsize modeLen = (*env)->GetArrayLength(env, mode);
    /* Java byte arrays 没有 NUL 结尾，必须手动加 */
    char *pPath = (char *)malloc((size_t)pathLen + 1);
    char *pMode = (char *)malloc((size_t)modeLen + 1);
    if (pPath == NULL || pMode == NULL) {
        free(pPath);
        free(pMode);
        return 0;
    }
    (*env)->GetByteArrayRegion(env, path, 0, pathLen, (jbyte *)pPath);
    pPath[pathLen] = '\0';
    (*env)->GetByteArrayRegion(env, mode, 0, modeLen, (jbyte *)pMode);
    pMode[modeLen] = '\0';
    FILE *f = fopen(pPath, pMode);
    free(pPath);
    free(pMode);
    return (jlong)(intptr_t)f;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFclose(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    if (f == 0) return -1;
    return fclose((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFread(JNIEnv *env,
        jclass cls, jlong f, jbyteArray buf, jint off, jint len) {
    (void)cls;
    jbyte *tmp = (*env)->GetByteArrayElements(env, buf, NULL);
    size_t n = fread(tmp + off, 1, (size_t)len, (FILE *)(intptr_t)f);
    (*env)->ReleaseByteArrayElements(env, buf, tmp, 0);
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFwrite(JNIEnv *env,
        jclass cls, jlong f, jbyteArray buf, jint off, jint len) {
    (void)cls;
    jbyte *tmp = (*env)->GetByteArrayElements(env, buf, NULL);
    size_t n = fwrite(tmp + off, 1, (size_t)len, (FILE *)(intptr_t)f);
    (*env)->ReleaseByteArrayElements(env, buf, tmp, JNI_ABORT);
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFseek(JNIEnv *env,
        jclass cls, jlong f, jlong offset, jint whence) {
    (void)env; (void)cls;
    return fseeko((FILE *)(intptr_t)f, (off_t)offset, whence);
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFtell(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return (jlong)ftello((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFflush(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return fflush((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFerror(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return ferror((FILE *)(intptr_t)f);
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cClearerr(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    clearerr((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFeof(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return feof((FILE *)(intptr_t)f);
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFgets(JNIEnv *env,
        jclass cls, jlong f, jint maxLen) {
    (void)cls;
    char *buf = (char *)malloc((size_t)maxLen + 1);
    if (buf == NULL) return NULL;
    char *r = fgets(buf, maxLen + 1, (FILE *)(intptr_t)f);
    if (r == NULL) { free(buf); return NULL; }
    jsize n = (jsize)strlen(r);
    jbyteArray a = (*env)->NewByteArray(env, n);
    if (a != NULL && n > 0)
        (*env)->SetByteArrayRegion(env, a, 0, n, (const jbyte *)r);
    free(buf);
    return a;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFgetc(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return fgetc((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFputc(JNIEnv *env,
        jclass cls, jlong f, jint c) {
    (void)env; (void)cls;
    return fputc(c, (FILE *)(intptr_t)f);
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cPopen(JNIEnv *env,
        jclass cls, jbyteArray cmd, jbyteArray mode) {
    (void)cls;
    jsize cmdLen = (*env)->GetArrayLength(env, cmd);
    jsize modeLen = (*env)->GetArrayLength(env, mode);
    char *pCmd = (char *)malloc((size_t)cmdLen + 1);
    char *pMode = (char *)malloc((size_t)modeLen + 1);
    if (pCmd == NULL || pMode == NULL) {
        free(pCmd);
        free(pMode);
        return 0;
    }
    (*env)->GetByteArrayRegion(env, cmd, 0, cmdLen, (jbyte *)pCmd);
    pCmd[cmdLen] = '\0';
    (*env)->GetByteArrayRegion(env, mode, 0, modeLen, (jbyte *)pMode);
    pMode[modeLen] = '\0';
    fflush(NULL);
    FILE *f = popen(pCmd, pMode);
    free(pCmd);
    free(pMode);
    return (jlong)(intptr_t)f;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cPclose(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return pclose((FILE *)(intptr_t)f);
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cFileno(JNIEnv *env,
        jclass cls, jlong f) {
    (void)env; (void)cls;
    return fileno((FILE *)(intptr_t)f);
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cSizeofLuaLStream(JNIEnv *env,
        jclass cls) {
    (void)env; (void)cls;
    return (jlong)(sizeof(FILE *) + sizeof(void (*)(void)));
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cNewLuaLStream(JNIEnv *env,
        jclass cls, jlong filePtr, jlong closefPtr) {
    (void)env; (void)cls;
    void **p = (void **)calloc(2, sizeof(void *));
    if (p == NULL) return 0;
    p[0] = (void *)(intptr_t)filePtr;
    p[1] = (void *)(intptr_t)closefPtr;
    return (jlong)(intptr_t)p;
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cStreamGetF(JNIEnv *env,
        jclass cls, jlong streamPtr) {
    (void)env; (void)cls;
    if (streamPtr == 0) return 0;
    void **p = (void **)(intptr_t)streamPtr;
    return (jlong)(intptr_t)p[0];
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cStdStream(JNIEnv *env,
        jclass cls, jint which) {
    (void)env; (void)cls;
    switch (which) {
        case 0: return (jlong)(intptr_t)stdin;
        case 1: return (jlong)(intptr_t)stdout;
        case 2: return (jlong)(intptr_t)stderr;
        default: return 0;
    }
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cGetErrno(JNIEnv *env,
        jclass cls) {
    (void)env; (void)cls;
    return (jint)errno;
}

/*
 * 「本进程该用哪个小数点」的记录：{@code 0} = 还没人设过 locale，
 * 那时照 C 一样现场问 {@code localeconv()}。
 * <p>
 * 为什么要有这份记录而不是每次都问 {@code localeconv()}：见
 * {@link Java_com_github_esrrhs_javalua_NativeBridge_cSetlocale} 的注释——
 * 在 JVM 进程里 {@code setlocale} 会把小数点那一档<b>留在 C.UTF-8 上</b>。
 * 单字节的一次写入，Java 侧的读要么是旧值要么是新值，两者都对应当时
 * 已经生效的那份 locale，不需要锁。
 */
static volatile char luashim_radix = 0;

/**
 * 问 libc：{@code name} 这份 locale 的 {@code RADIXCHAR} 是哪个字符。
 * <p>
 * 只处理会影响小数点的两档：{@code LC_ALL} 与 {@code LC_NUMERIC}。
 * {@code newlocale} 装不出来（这台机器没这份数据）就<b>什么都不记</b>，
 * 让 {@code localeconv()} 继续答话——那时 C 自己也会失败，行为一致。
 */
static char luashim_nl_status = 0;

/*
 * Lua 的类目<b>下标</b>（{@code os.setlocale} 的第二参在
 * {@code {"all","collate","ctype","monetary","numeric","time"}} 里的位置）
 * 到本平台 {@code LC_*} <b>常量</b>的表，照 {@code loslib.c: os_setlocale}
 * 里那一份抄。
 * <p>
 * <b>必须在 C 侧翻</b>：两边的常量编号不一样——macOS/BSD 是
 * {@code LC_ALL=0, LC_COLLATE=1, LC_CTYPE=2, LC_MONETARY=3, LC_NUMERIC=4,
 * LC_TIME=5}，glibc 是 {@code LC_CTYPE=0, LC_NUMERIC=1, LC_TIME=2,
 * LC_COLLATE=3, LC_MONETARY=4, LC_MESSAGES=5, LC_ALL=6}。Java 侧以前把
 * Lua 的下标直接当常量传（{@code catToLc} 是 {@code return idx;}，注释写着
 * 「macOS/Linux 刚好一一对应」），于是在 Linux 上 {@code os.setlocale(loc)}
 * 设的其实是 {@code LC_CTYPE}：locale 名字照样返回成功，小数点那一档却一动
 * 没动——CI 上「只有 {@code LC_CTYPE=pt_BR.UTF-8}、其余仍是 {@code C.UTF-8}」
 * 的诊断就是这么来的，不是 glibc 有什么怪癖。
 */
static const int luashim_cat[] = {LC_ALL, LC_COLLATE, LC_CTYPE, LC_MONETARY,
                                  LC_NUMERIC, LC_TIME};

static int luashim_category(int catIdx) {
    if (catIdx < 0
            || catIdx >= (int)(sizeof luashim_cat / sizeof luashim_cat[0])) {
        return LC_ALL;      /* 与 loslib.c 一样：checkOption 只会给 0..5，兜底给 all */
    }
    return luashim_cat[catIdx];
}

static void luashim_remember_radix(int category, const char *name) {
    locale_t l;
    const char *rp;
    if (name == NULL) {
        return;
    }
    if (category != LC_ALL && category != LC_NUMERIC) {
        return;     /* collate / time / monetary 那些与小数点无关 */
    }
    /*
     * <b>只按 LC_NUMERIC 建</b>。小数点是这一家的属性，C 的
     * {@code lua_getlocaledecpoint()} 取的也是 {@code LC_NUMERIC} 的
     * {@code decimal_point}；用 {@code LC_ALL_MASK} 等于要求「所有类目都得有
     * 数据」，那比 C 实际要求的宽——CI 那台 runner 上实测就是这样：
     * {@code newlocale(LC_ALL_MASK, "pt_BR.UTF-8", 0)} 失败（诊断里
     * {@code cache=?}），而参考进程只把 {@code LC_NUMERIC} 换成逗号就成功了。
     */
    l = newlocale(LC_NUMERIC_MASK, name, (locale_t)0);
    if (l == (locale_t)0) {
        luashim_nl_status = 2;                 /* 这份 locale 连 numeric 都装不出 */
        return;
    }
    rp = nl_langinfo_l(RADIXCHAR, l);
    if (rp != NULL && rp[0] != '\0') {
        luashim_radix = rp[0];
    }
    luashim_nl_status = 1;
    freelocale(l);
}

/*
 * {@code setlocale(category, locale)} —— 直接调 C 的 setlocale。
 * <p>
 * {@code locale} 为 {@code NULL} 时只查询不设置；返回的字符串是 C 运行时的
 * 内部状态，不能 free。Java 侧要把字节拷走（{@code NewStringUTF} 或
 * {@code GetByteArrayRegion}），不能直接存指针。
 */
JNIEXPORT jbyteArray JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cSetlocale(JNIEnv *env,
        jclass cls, jint category, jbyteArray locale) {
    (void)cls;
    const char *loc = NULL;
    char *locBuf = NULL;
    if (locale != NULL) {
        jsize len = (*env)->GetArrayLength(env, locale);
        locBuf = (char *)malloc((size_t)len + 1);
        if (locBuf == NULL) return NULL;
        (*env)->GetByteArrayRegion(env, locale, 0, len, (jbyte *)locBuf);
        locBuf[len] = '\0';
        loc = locBuf;
    }
    int cat = luashim_category((int)category);
    char *result = setlocale(cat, loc);
    /*
     * 顺手记下这份 locale 的小数点（见 luashim_remember_radix）。
     * <p>
     * 为什么还要专门记一次，而不是等 {@code cLocaleDecpoint} 现场问
     * {@code localeconv()}：问 {@code localeconv} 的前提是「这个线程/进程的
     * {@code LC_NUMERIC} 真的切过去了」。这两件事在纯 C 进程里等价，但
     * {@code os.setlocale} 的类目以前是按 macOS 的编号硬编码传的，glibc 上
     * 设到的根本不是 {@code LC_NUMERIC}（见 {@link luashim_category}）——
     * 编号已经修对，但**记下来的这一份与 C 的 lua_getlocaledecpoint() 取自
     * 同一个 locale 对象**，比「先改全局状态、再从另一个 JNI 调用里读回」
     * 少一层时机依赖，也少一类「setlocale 成功而读取仍是旧值」的平台差异。
     */
    luashim_remember_radix(cat, loc);
    free(locBuf);
    if (result == NULL) return NULL;
    /*
     * 把结果拷回 Java byte[]：{@code NewStringUTF} 走 MUTF-8，locale 名字里
     * 可能有非 ASCII（如 {@code zh_CN.UTF-8}），但实测 C 的 setlocale 返回的都是
     * ASCII，这里用字节数组更稳妥，Java 侧按 UTF-8 解。
     */
    jsize rlen = (jsize)strlen(result);
    jbyteArray arr = (*env)->NewByteArray(env, rlen);
    if (arr == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, arr, 0, rlen, (jbyte *)result);
    return arr;
}

/*
 * {@code localeconv()->decimal_point[0]} —— 就是 C 的
 * {@code lua_getlocaledecpoint()}（{@code luaconf.h}）。
 * <p>
 * Lua 5.4 的数字进出全都过这个字符：{@code l_str2d} 解析失败后会把它
 * 换进去再试一次，{@code luaO_tostring} / {@code string.format} 的浮点
 * 转换则把它写出去。{@code os.setlocale("pt_BR")} 之后它是 ','。
 * <p>
 * 结构体是 locale 的<b>静态存储</b>，只能把这一个字节取走，不能存指针。
 */
JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cLocaleDecpoint(JNIEnv *env,
        jclass cls) {
    (void)env; (void)cls;
    struct lconv *lc;
    if (luashim_radix != 0) {
        return (jint)(unsigned char)luashim_radix;
    }
    lc = localeconv();
    if (lc == NULL || lc->decimal_point == NULL || lc->decimal_point[0] == '\0')
        return (jint)'.';
    return (jint)(unsigned char)lc->decimal_point[0];
}

/*
 * 纯查询，不改任何状态：把「libc 眼里的当前 locale」和「libc 自己会用的
 * 小数点」四种口径一起打出来，用于定位 {@code os.setlocale} 在 JVM 里
 * 与在 C 进程里表现不一致的那条路（Linux 上 {@code setlocale} 返回了名字，
 * {@code localeconv()} 却仍给 '.'）。
 */
JNIEXPORT jstring JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cLocaleDiag(JNIEnv *env,
        jclass cls) {
    (void)cls;
    char out[512];
    char probe[16];
    const char *all = setlocale(LC_ALL, NULL);
    const char *num = setlocale(LC_NUMERIC, NULL);
    struct lconv *lc = localeconv();
    snprintf(probe, sizeof probe, "%.1f", 1.5);
    snprintf(out, sizeof out, "all=%s|numeric=%s|conv=%c|probe=%s|langinfo=%s|cache=%c",
             all ? all : "?", num ? num : "?",
             (lc != NULL && lc->decimal_point != NULL && lc->decimal_point[0] != '\0')
                     ? lc->decimal_point[0] : '?',
             probe, nl_langinfo(RADIXCHAR),
             luashim_radix != 0 ? luashim_radix : '?');
    snprintf(out + strlen(out), sizeof out - strlen(out), "|nlok=%c",
             luashim_nl_status == 1 ? 'y' : (luashim_nl_status == 2 ? 'n' : '-'));
    return (*env)->NewStringUTF(env, out);
}

/* 定义在本文件末尾的 cSpawn 那一节 */
static void luashim_pending_take(pid_t pid);

/*
 * {@code waitpid(pid, &status, 0)} —— 拿原始退出状态，区分 exit/signal。
 * <p>
 * Java 的 {@code Process.waitFor()} 只给退出码，信号杀死的进程一律按 exit 1 报。
 * C 的 {@code waitpid} 把状态编码在 {@code status} 里：{@code WIFEXITED(status)}
 * 判正常退出，{@code WEXITSTATUS(status)} 取退出码；{@code WIFSIGNALED(status)}
 * 判信号杀死，{@code WTERMSIG(status)} 取信号编号。Lua 的 {@code os.execute}
 * 走的就是这条路（{@code loslib.c: os_execute} → {@code luaL_execresult}）。
 * <p>
 * 收到尸的那一刻才可以放手 {@code cSpawn} 为这个 pid 存的那份命令文本——
 * 那时子进程早就 exec 完了，argv 的内容已经被内核拷走。
 */
JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cWaitpid(JNIEnv *env,
        jclass cls, jlong pid, jlongArray statusOut) {
    (void)cls;
    int status = 0;
    pid_t r = waitpid((pid_t)pid, &status, 0);
    if (r < 0) return -1;
    luashim_pending_take((pid_t)pid);
    if (statusOut != NULL) {
        jlong s = (jlong)status;
        (*env)->SetLongArrayRegion(env, statusOut, 0, 1, &s);
    }
    return 0;
}

/*
 * {@code WIFEXITED(status)} / {@code WEXITSTATUS(status)} /
 * {@code WIFSIGNALED(status)} / {@code WTERMSIG(status)} —— 把 waitpid 拿到的
 * 原始 status 解码成 Java 能直接用的四个字段。
 * <p>
 * 宏的定义平台相关（macOS 和 Linux 的位布局不一样），不能直接在 Java 里算，
 * 所以走 JNI 调 C 的宏。返回值打包成一个 {@code long[4]}：
 * {@code [wifexit, wexit, wifsig, wtermsig]}。
 */
JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cDecodeStatus(JNIEnv *env,
        jclass cls, jint status, jlongArray out) {
    (void)cls;
    jlong vals[4];
    vals[0] = WIFEXITED(status) ? 1 : 0;
    vals[1] = WEXITSTATUS(status);
    vals[2] = WIFSIGNALED(status) ? 1 : 0;
    vals[3] = WTERMSIG(status);
    (*env)->SetLongArrayRegion(env, out, 0, 4, vals);
}

/* ==================== 子进程：spawn / pipe / close ==================== */

/*
 * {@code os.execute} 与 {@code io.popen} 的子进程。<b>为什么不能继续用
 * {@code ProcessBuilder}</b>：JVM 是自己孩子的父进程，它为了
 * {@code Process.onExit()} 会起一条 reaper 线程去 {@code waitpid} 那个 pid。
 * 我们想拿<b>原始 status</b>（区分 {@code WIFSIGNALED} 与退出码）就只能再调一次
 * {@code waitpid}，于是两条线程抢同一个孩子，谁先回来纯看调度：实测
 * {@code kill -TERM $$} 二十次里输掉十五次，输了就退回 {@code waitFor()} 拿到
 * JDK 自己折算的 {@code 128+n}，报成 {@code ("exit",143)} 而不是
 * {@code ("signal",15)}；赢了反而把 JVM 那侧掏空，{@code waitFor()} 回来 0。
 * 也就是说同一行脚本可以两次跑出不同答案。
 * <p>
 * 这里改成 {@code posix_spawn} 由 C 起：孩子不属于 JVM，reaper 不认识它,
 * {@code cWaitpid} 是唯一的收割者，抢不到也不可能失败。没接管的 fd 一律
 * 继承，与 C 的 {@code system()} / {@code popen()} 同构。
 */

/* 老 glibc 的 unistd.h 不在这里给 environ 的声明 */
extern char **environ;

/*
 * {@code argv} 里那几个字符串必须活到<b>子进程真的 exec 之后</b>。
 * <p>
 * {@code posix_spawn} 交回控制权时子进程<b>还没 exec 完</b>（glibc 是
 * {@code clone()} 完就返回，macOS 的 {@code exec} 由内核在主线程继续摆），而
 * argv 是<b>指针</b>：内容在 exec 那一刻才从父进程的地址空间里拷走。所以
 * {@code ReleaseByteArrayElements} 之后立刻把那块还回去，命令就成了随机字节
 * （实测 {@code sh: line 1: Ó%ú: command not found}）。
 * <p>
 * 于是 {@code strdup} 一份存着，等到 {@code cWaitpid} 收到同一个 pid 再放——
 * 那一刻子进程必然早就 exec 完了。表满（同时有 64 个没收尸的孩子）时当场释放
 * 新的一份：那条支路只可能出现在「脚本压根不关 popen 句柄」，宁可命令文本
 * 说错，也不留一个没有上界的泄漏。
 */
#define LUASHIM_MAX_PENDING 64

static struct {
    pid_t pid;
    char *cmd;
} luashim_pending[LUASHIM_MAX_PENDING];

static pthread_mutex_t luashim_pending_lock = PTHREAD_MUTEX_INITIALIZER;

static void luashim_pending_add(pid_t pid, char *cmd) {
    int i;
    pthread_mutex_lock(&luashim_pending_lock);
    for (i = 0; i < LUASHIM_MAX_PENDING; i++) {
        if (luashim_pending[i].cmd == NULL) {
            luashim_pending[i].pid = pid;
            luashim_pending[i].cmd = cmd;
            pthread_mutex_unlock(&luashim_pending_lock);
            return;
        }
    }
    pthread_mutex_unlock(&luashim_pending_lock);
    free(cmd);
}

static void luashim_pending_take(pid_t pid) {
    int i;
    pthread_mutex_lock(&luashim_pending_lock);
    for (i = 0; i < LUASHIM_MAX_PENDING; i++) {
        if (luashim_pending[i].cmd != NULL && luashim_pending[i].pid == pid) {
            free(luashim_pending[i].cmd);
            luashim_pending[i].cmd = NULL;
            luashim_pending[i].pid = 0;
            break;
        }
    }
    pthread_mutex_unlock(&luashim_pending_lock);
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cSpawn(JNIEnv *env, jclass cls,
        jbyteArray cmd, jboolean outPipe, jboolean errPipe, jboolean inPipe,
        jlongArray fdsOut) {
    (void)cls;
    int ofd[2] = { -1, -1 }, efd[2] = { -1, -1 }, ifd[2] = { -1, -1 };
    char *argv[4];
    posix_spawn_file_actions_t fa;
    posix_spawnattr_t sa;
    pid_t pid = -1;
    int rc;
    jlong fds[3];
    jbyte *c;
    char *dup;

    if (outPipe && pipe(ofd) != 0) return -1;
    if (errPipe && pipe(efd) != 0) { if (ofd[0] >= 0) { close(ofd[0]); close(ofd[1]); } return -1; }
    if (inPipe  && pipe(ifd) != 0) { if (ofd[0] >= 0) { close(ofd[0]); close(ofd[1]); }
                                     if (efd[0] >= 0) { close(efd[0]); close(efd[1]); } return -1; }
    c = (jbyte *)(*env)->GetByteArrayElements(env, cmd, NULL);
    if (c == NULL) return -1;
    /*
     * 拷一份出来：argv 是指针，得活到子进程 exec 之后（见 luashim_pending_add）。
     * <b>必须按数组长度拷并自己补 '\0'</b> —— Java 的 {@code byte[]} 后面没有
     * 结束符，用 {@code strdup} 会一路读到堆上<b>碰巧</b>出现的 0 字节，把垃圾
     * 一起塞进 argv。实测症状：{@code mkdir -p target/rt/61640/fkerm/d*\xe8\x80...}
     * 报 {@code Illegal byte sequence}，于是同一条 {@code os.execute} 时灵时不灵。
     */
    {
        jsize len = (*env)->GetArrayLength(env, cmd);
        dup = (char *)malloc((size_t)len + 1);
        if (dup != NULL) {
            memcpy(dup, c, (size_t)len);
            dup[len] = '\0';
        }
    }
    (*env)->ReleaseByteArrayElements(env, cmd, c, JNI_ABORT);
    if (dup == NULL) return -1;
    argv[0] = "sh";                       /* C 是 execl("/bin/sh","sh",...)：argv[0] 不是路径 */
    argv[1] = "-c";
    argv[2] = dup;
    argv[3] = NULL;
    if (posix_spawn_file_actions_init(&fa) != 0) { free(dup); return -1; }
    /*
     * 三条管道各有两头，孩子只该拿到属于自己的那一头。<b>必须把另一头也
     * addclose 掉</b>：{@code posix_spawn} 跟 {@code fork}+{@code exec} 一样，
     * 父进程其余 fd 是<b>整个继承下去的</b>（{@code pipe()} 给的 fd 没有
     * FD_CLOEXEC），孩子于是握着「父端」那一份，自己等自己写 ——
     * 实测症状是 {@code io.popen("cat > /dev/null","w"):close()} 卡死：
     * 父母那边把写端关了想给 {@code cat} 一个 EOF，可孩子手里还有一份写端，
     * EOF 永远不来。C 的 {@code popen} 是靠 {@code close} 掉另一头来避免的。
     */
    if (outPipe) {
        posix_spawn_file_actions_adddup2(&fa, ofd[1], 1);
        posix_spawn_file_actions_addclose(&fa, ofd[0]);
        posix_spawn_file_actions_addclose(&fa, ofd[1]);
    }
    if (errPipe) {
        posix_spawn_file_actions_adddup2(&fa, efd[1], 2);
        posix_spawn_file_actions_addclose(&fa, efd[0]);
        posix_spawn_file_actions_addclose(&fa, efd[1]);
    }
    if (inPipe) {
        posix_spawn_file_actions_adddup2(&fa, ifd[0], 0);
        posix_spawn_file_actions_addclose(&fa, ifd[0]);
        posix_spawn_file_actions_addclose(&fa, ifd[1]);
    }
    /*
     * 信号恢复默认处置：JVM 会把 SIGPIPE / SIGQUIT 设成 {@code SIG_IGN}，而
     * <b>{@code SIG_IGN} 是穿过 exec 继承的</b>（装了 handler 的那些才会复位）。
     * {@code sh}/{@code cat} 从 JVM 这边生下来就带着「忽略 SIGPIPE」，于是
     * {@code io.popen(...):close()} 该拿的 {@code ("signal",13)} 永远拿不到。
     * C 的 {@code system()} 从一个不碰这些信号的进程里 fork，不会有这个问题，
     * 所以这里显式把全套信号拉回默认，向 C 看齐。
     */
    if (posix_spawnattr_init(&sa) == 0) {
        sigset_t all;
        sigfillset(&all);
        if (posix_spawnattr_setsigdefault(&sa, &all) == 0) {
#ifdef POSIX_SPAWN_SETSIGDEF
            posix_spawnattr_setflags(&sa, POSIX_SPAWN_SETSIGDEF);
#endif
            rc = posix_spawn(&pid, "/bin/sh", &fa, &sa, argv, environ);
        } else {
            rc = posix_spawn(&pid, "/bin/sh", &fa, NULL, argv, environ);
        }
        posix_spawnattr_destroy(&sa);
    } else {
        rc = posix_spawn(&pid, "/bin/sh", &fa, NULL, argv, environ);
    }
    posix_spawn_file_actions_destroy(&fa);
    if (rc != 0) {
        errno = rc;                        /* posix_spawn 用返回值传错，不碰 errno */
        free(dup);
        if (ofd[0] >= 0) { close(ofd[0]); close(ofd[1]); }
        if (efd[0] >= 0) { close(efd[0]); close(efd[1]); }
        if (ifd[0] >= 0) { close(ifd[0]); close(ifd[1]); }
        return -1;
    }
    luashim_pending_add(pid, dup);
    /* 父进程只留自己要用那头 */
    if (outPipe) close(ofd[1]);
    if (errPipe) close(efd[1]);
    if (inPipe)  close(ifd[0]);
    fds[0] = ofd[0];
    fds[1] = efd[0];
    fds[2] = ifd[1];
    (*env)->SetLongArrayRegion(env, fdsOut, 0, 3, fds);
    return (jlong) pid;
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cPipeRead(JNIEnv *env, jclass cls,
        jlong fd, jbyteArray buf) {
    (void)cls;
    jbyte *b = (jbyte *)(*env)->GetByteArrayElements(env, buf, NULL);
    jint n;
    if (b == NULL) return -1;
    do {
        n = (jint) read((int) fd, b, (size_t)(*env)->GetArrayLength(env, buf));
    } while (n < 0 && errno == EINTR);
    if (n > 0) (*env)->ReleaseByteArrayElements(env, buf, b, 0);   /* 拷回 Java */
    else (*env)->ReleaseByteArrayElements(env, buf, b, JNI_ABORT);
    return n;                              /* 0 = EOF，<0 = 出错（含 EBADF：句柄已关） */
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cPipeWrite(JNIEnv *env, jclass cls,
        jlong fd, jbyteArray buf, jint off, jint len) {
    (void)cls;
    jbyte *b = (jbyte *)(*env)->GetByteArrayElements(env, buf, NULL);
    jint written = 0;
    if (b == NULL) return -1;
    while (written < len) {
        ssize_t w = write((int) fd, b + off + written, (size_t)(len - written));
        if (w < 0) {
            if (errno == EINTR) continue;
            written = -1;                  /* EPIPE 之类：子进程已经走了 */
            break;
        }
        written += (jint) w;
    }
    (*env)->ReleaseByteArrayElements(env, buf, b, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_javalua_NativeBridge_cCloseFd(JNIEnv *env, jclass cls,
        jlong fd) {
    (void)env; (void)cls;
    if (fd >= 0) close((int) fd);
}

/* ==================== 生成的 stub ==================== */

#include "luashim_stubs.inc"
