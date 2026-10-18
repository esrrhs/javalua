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
#include <pthread.h>
#include <dlfcn.h>

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
#include "luashim_ids.h"

/* ==================== 进 Java ==================== */

static JavaVM *g_vm = NULL;
static jclass g_cls = NULL;
static jmethodID g_dispatch = NULL;
static jmethodID g_dispatchD = NULL;

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
#define SLOT_MAXCHUNK 256

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
        g_chunks[g_nchunks++] = (char *)calloc(SLOT_CHUNK * SLOT_STRIDE, 1);
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
        if ((char *)L < b || (char *)L >= b + SLOT_CHUNK * SLOT_STRIDE) {
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
        r = g_yielding ? -2 : -1;
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

static const char *pushvfshim(lua_State *L, const char *fmt, va_list ap) {
    char small[512];
    char *heap = NULL;
    char *buf = small;
    size_t len;
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(small, sizeof small, fmt, ap2);
    va_end(ap2);
    if (n < 0) {
        return NULL;
    }
    len = (size_t)n;
    if (len >= sizeof small) {
        heap = (char *)malloc(len + 1);
        if (heap == NULL) {
            return NULL;
        }
        vsnprintf(heap, len + 1, fmt, ap);
        buf = heap;
    }
    luashim_call(ID_lua_pushlstring, LUASHIM_HANDLE(L),
            (intptr_t)buf, (jlong)len, 0, 0, 0, 0.0);
    buf = ring_put(buf, len);
    free(heap);
    return buf;
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

LUALIB_API int (luaL_error) (lua_State *L, const char *fmt, ...) {
    char small[512];
    va_list ap;
    int n;
    size_t len;
    va_start(ap, fmt);
    n = vsnprintf(small, sizeof small, fmt, ap);
    va_end(ap);
    if (n < 0) {
        len = 0;
    } else {
        len = (size_t)n;
        if (len >= sizeof small) {
            len = sizeof small - 1;
        }
    }
    /* Java 侧负责拼 luaL_where + 这条消息，然后压栈 */
    luashim_call(ID_luaL_error, LUASHIM_HANDLE(L), (intptr_t)small, (jlong)len, 0, 0, 0, 0.0);
    luashim_throw();
    return 0;
}

/* luaL_Reg 数组在 C 侧，Java 读不到：照 lauxlib.c 逐个注册 */
LUALIB_API void (luaL_setfuncs) (lua_State *L, const luaL_Reg *l, int nup) {
    static const char noslots[] = "not enough slots";
    luashim_call(ID_luaL_checkstack, LUASHIM_HANDLE(L), (jint)nup,
            (intptr_t)noslots, 0, 0, 0, 0.0);
    for (; l->name != NULL; l++) {
        int i;
        for (i = 0; i < nup; i++) {
            luashim_call(ID_lua_pushvalue, LUASHIM_HANDLE(L), (jint)(-nup), 0, 0, 0, 0, 0.0);
        }
        luashim_call(ID_lua_pushcclosure, LUASHIM_HANDLE(L),
                (intptr_t)(intptr_t)l->func, (jint)nup, 0, 0, 0, 0.0);
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

LUALIB_API void (luaL_addvalue) (luaL_Buffer *B) {
    luashim_call(ID_luaL_addvalue, LUASHIM_HANDLE(B->L), (intptr_t)B, 0, 0, 0, 0, 0.0);
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
