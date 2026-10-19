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
#include <sys/wait.h>   /* luaL_execresult 要用本平台的 WIFEXITED / WTERMSIG */

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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_init(JNIEnv *env, jclass cls) {
    g_cls = (*env)->NewGlobalRef(env, cls);
    g_dispatch = (*env)->GetStaticMethodID(env, cls, "dispatch",
            "(IJJJJJJD)J");
    g_dispatchD = (*env)->GetStaticMethodID(env, cls, "dispatchD",
            "(IJJJJJJD)D");
    {
        jclass y = (*env)->FindClass(env,
                "com/github/esrrhs/fakescript/lua/LuaYield");
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_takePending(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_invokeWarn(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dlopen(JNIEnv *env,
        jclass cls, jstring path, jboolean global) {
    (void)cls;
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    void *h = dlopen(p, RTLD_NOW | (global ? RTLD_GLOBAL : RTLD_LOCAL));
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong)(intptr_t)h;
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dlsym(JNIEnv *env,
        jclass cls, jlong h, jstring name) {
    (void)cls;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    void *s = dlsym((void *)(intptr_t)h, n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jstring JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dlerror(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_invokeC(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_invokeK(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_alloc(JNIEnv *env,
        jclass cls, jlong n) {
    (void)env;
    (void)cls;
    return (jlong)(intptr_t)calloc(1, (size_t)(n > 0 ? n : 1));
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_free(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    free((void *)(intptr_t)p);
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_bytesAt(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_bytesAtN(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_strToNative(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_putBytes(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_put64(JNIEnv *env,
        jclass cls, jlong p, jlong v) {
    (void)env;
    (void)cls;
    if (p != 0) {
        *((jlong *)(intptr_t)p) = v;
    }
}

JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_get64(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    return p == 0 ? 0 : *((jlong *)(intptr_t)p);
}

JNIEXPORT void JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_put32(JNIEnv *env,
        jclass cls, jlong p, jint v) {
    (void)env;
    (void)cls;
    if (p != 0) {
        *((jint *)(intptr_t)p) = v;
    }
}

JNIEXPORT jint JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_get32(JNIEnv *env,
        jclass cls, jlong p) {
    (void)env;
    (void)cls;
    return p == 0 ? 0 : *((jint *)(intptr_t)p);
}

/** {@code sizeof(lua_Debug)}：Java 侧要给 C 的钩子分配一块，尺寸不能猜 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dbgSize(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_statePtr(JNIEnv *env,
        jclass cls, jlong idx) {
    (void)env;
    (void)cls;
    return (jlong)(intptr_t)slot_ptr(idx);
}

/** 回调任意 C 函数指针：reader / writer / hook / alloc / 续体都走这一条 */
JNIEXPORT jlong JNICALL
Java_com_github_esrrhs_fakescript_lua_NativeBridge_callPtr(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_bufAppend(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_defaultPanic(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dbgFill(JNIEnv *env,
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
Java_com_github_esrrhs_fakescript_lua_NativeBridge_dbgShortSrc(JNIEnv *env,
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

/* ==================== 生成的 stub ==================== */

#include "luashim_stubs.inc"
