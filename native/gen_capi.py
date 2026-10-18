#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 Lua 5.4 的头文件抽出**真实**（非宏）C API 原型，产出三样东西：

  native/luashim_ids.h      每个符号一个整数 id（C 侧用）
  native/luashim_stubs.inc  每个符号一个 C stub（打包参数 → 调 Java）
  CApi.java                 Java 侧的分发表 + 待实现的方法桩

抽出来的清单会**与真 liblua 的导出符号对撞**（见 check 函数），漏一个符号
就意味着某个 .so 会在 dlopen 时挂掉，所以这一步不能靠手写清单。

参数打包规则（C stub → Java）：
  * 整数/指针/句柄类参数依次进 a0..a5；
  * lua_Number（double）参数进 d0（全 API 里一个函数最多一个 double 参数）；
  * 返回 double 的函数走 luashim_callD，其余走 luashim_call。

Java 侧不需要真的读 native 内存：出参用 NativeBridge.put64/put32 写，
返回的 const char* 由 Java 自己调 strToNative 换好再交回来。所以除了
变参、结构体遍历、lua_Debug 填充这十几个，**其余 stub 都能生成**。
"""
import json
import os
import re
import subprocess
import sys

SRC = os.environ.get("LUA54_SRC", "/tmp/lua-5.4.7/src")
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAVA_DIR = os.path.join(ROOT, "src/main/java/com/github/esrrhs/fakescript/lua")

HEADERS = ["lua.h", "lauxlib.h", "lualib.h"]

# 需要手写的符号：变参 / 遍历 C 结构体 / 填 lua_Debug / 数据符号 / 不返回
HAND_WRITTEN = {
    "lua_pushfstring",    # ... 变参，要 vsnprintf
    "lua_pushvfstring",   # va_list
    "lua_gc",             # ... 变参
    "luaL_checkoption",   # const char *lst[]，要在 C 侧走数组
    "luaL_setfuncs",      # const luaL_Reg *，要在 C 侧走数组
    "luaL_gsub",          # 整个用 luaL_Buffer 在 C 侧做完，不进 Java
    "lua_yieldk",         # 要走 longjmp，不能在 C 里正常返回
    "lua_resume",         # int *nres 是出参，得在 C 侧写回
    "lua_getallocf",      # 交回去的必须是能直接调用的 C 函数指针
    "lua_newstate",       # 接住 lua_Alloc / ud，换掉进程级那一份
    "lua_setallocf",
    "lua_load",           # lua_Reader 是 C 函数指针，只能在 C 侧喂
    "luaL_loadfilex",     # 整个照 lauxlib.c：BOM / '#' 首行 / 二进制重开
    "luaL_buffinit", "luaL_buffinitsize", "luaL_prepbuffsize",
    "luaL_addlstring", "luaL_addstring", "luaL_addvalue",
    "luaL_pushresult", "luaL_pushresultsize",
    "lua_ident",          # 数据符号，不是函数
}

# 真 Lua 里这几个永远不返回（luaL_argerror/typeerror 内部就是 luaL_error）
NEVER_RETURNS = {"lua_error", "luaL_error", "luaL_argerror", "luaL_typeerror"}

# 在 luashim.c 里用别的 lua_* 拼出来的，Java 这侧的桩永远不会被调到。
# 桩体写成明确的「C 侧实现」，而不是 unimplemented——后者会让人以为还欠着。
C_ONLY = {
    "luaL_addlstring", "luaL_addstring", "luaL_addvalue",
    "luaL_buffinit", "luaL_buffinitsize",
    "luaL_prepbuffsize", "luaL_pushresult", "luaL_pushresultsize",
    "luaL_gsub", "luaL_setfuncs", "lua_pushfstring", "lua_pushvfstring",
    "lua_load", "luaL_loadfilex",
    "lua_getallocf", "lua_setallocf",
}

# C 侧手写的符号可能要传比声明里更多的东西：luaL_checkoption 把
# const char *lst[] 打包成了 (packed, n)，Java 签名得跟 C 传的槽位对上
ARG_OVERRIDE = {
    "luaL_checkoption": [("long", "L"), ("long", "arg"), ("long", "def"),
                         ("long", "packed"), ("long", "n")],
}

DOUBLE_TYPES = {"lua_Number"}
INT_TYPES = {"int", "size_t", "lua_Integer", "lua_Unsigned", "unsigned int", "char"}
# 函数指针：声明里没有 *，但宽度是指针。漏了它们就会被打包成 jint，指针直接截断
FN_PTR_TYPES = {"lua_CFunction", "lua_KFunction", "lua_Alloc", "lua_Hook",
                "lua_WarnFunction", "lua_Reader", "lua_Writer"}
# 64 位整数 typedef：同上，打包成 jint 就高位尽失
WIDE_TYPES = {"lua_Integer", "lua_Unsigned", "size_t", "lua_KContext"}

# LUAMOD_API 是十个 luaopen_* 用的那一个：它们声明在 lualib.h 里但不是
# LUALIB_API，漏了它就整整十个导出符号缺席 —— 只有把 nm liblua.a 的导出
# 符号与我们 dylib 的对撞才看得见（光看「头文件里有没有」看不出来）。
decl_re = re.compile(
    r"^(?P<api>LUA_API|LUALIB_API|LUAMOD_API)\s+"
    r"(?P<ret>.*?)\(\s*(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*\)\s*\((?P<args>.*)\)\s*;",
    re.S)


def join_lines(text):
    """把跨行声明合成一条；预处理指令整块跳过，否则会粘到下一条声明上。"""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    out, buf, in_macro = [], "", False
    for line in text.splitlines():
        s = line.strip()
        if not s or s.startswith("*"):
            continue
        if s.startswith("#"):
            in_macro, buf = True, ""
        if in_macro:
            if not s.endswith("\\"):
                in_macro = False
            continue
        buf += " " + s
        if buf.rstrip().endswith(";"):
            out.append(buf.strip())
            buf = ""
    return out


def split_args(a):
    a = a.strip()
    if a in ("", "void"):
        return []
    parts, depth, cur = [], 0, ""
    for ch in a:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur.strip())
    fixed = []
    for i, p in enumerate(parts):
        if not re.search(r"[A-Za-z_][A-Za-z0-9_]*\s*$", p) or p.endswith("*"):
            p = p + " a%d" % i
        fixed.append(p)
    return fixed


def parse_arg(p):
    # const 必须留着：头文件声明里有，定义里少一个就是 conflicting types
    cst = "const" in p.split()
    p = p.replace("const ", "").replace("volatile ", "").strip()
    p = re.sub(r"\s+", " ", p)
    m = re.match(r"^(.*?)(\**)\s*([A-Za-z_][A-Za-z0-9_]*)$", p)
    if not m:
        return {"type": p, "ptr": 0, "name": "x", "const": cst}
    base, stars, name = m.group(1).strip(), m.group(2), m.group(3)
    return {"type": base, "ptr": len(stars), "name": name, "const": cst}


def norm(t):
    return re.sub(r"\s+", " ", t).strip()


def extract():
    funcs = []
    for h in HEADERS:
        text = open(os.path.join(SRC, h), encoding="utf-8").read()
        for stmt in join_lines(text):
            m = decl_re.match(stmt)
            if not m:
                continue
            args = [parse_arg(a) for a in split_args(m.group("args"))]
            name = m.group("name")
            if name in ARG_OVERRIDE:
                args = [{"type": t, "ptr": 0, "name": n, "const": False}
                        for t, n in ARG_OVERRIDE[name]]
            funcs.append({
                "header": h,
                "api": m.group("api"),
                "name": name,
                "ret": norm(m.group("ret")),
                "args": args,
            })
    funcs.sort(key=lambda f: (f["header"], f["name"]))
    return funcs


def check_against_real_lib(funcs):
    """与真 liblua 的导出符号对撞。清单漏一个，某个 .so 就 dlopen 不起来。"""
    dylib = "/tmp/_gen_lua_syms.dylib"
    try:
        srcs = [f for f in sorted(os.listdir(SRC))
                if f.endswith(".c") and f not in ("lua.c", "luac.c", "fkdump.c")]
        cmd = ["cc", "-shared", "-fPIC", "-o", dylib] + srcs
        subprocess.run(cmd, cwd=SRC, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        out = subprocess.run(["nm", "-gU", dylib], capture_output=True,
                             text=True, check=True).stdout
    except Exception as e:                                  # 没编译器就跳过
        print("跳过符号对撞：%s" % e, file=sys.stderr)
        return
    syms = set()
    for line in out.splitlines():
        f = line.split()
        # lua_* / luaL_* / luaopen_* 三类；写成 `lua.` 会把 luaopen_* 全漏掉，
        # 于是生成器反而报告「我们多出十个」，把真正该看的方向指反了。
        if len(f) >= 3 and re.match(r"^_lua(_[a-z0-9]|L_|open_)", f[2]):
            syms.add(f[2][1:])
    mine = {f["name"] for f in funcs} | {"lua_ident"}
    miss = sorted(syms - mine)
    extra = sorted(mine - syms)
    print("真库公开 API %d，本清单 %d" % (len(syms), len(mine)), file=sys.stderr)
    if miss:
        print("❗漏：%s" % miss, file=sys.stderr)
    if extra:
        print("❗多：%s" % extra, file=sys.stderr)


def slot_of(a):
    """这个参数进整数槽还是 double 槽。"""
    return "d" if a["type"] in DOUBLE_TYPES else "a"


def slots_of(args):
    """
    给每个参数分配分发表的槽名：整数类依次 a0..a5，double 固定 d0（在最后）。
    返回的槽名同时在 C stub 与 Java 分发里用，两边才对得上。
    """
    out, ai = [], 0
    for a in args:
        if slot_of(a) == "d":
            out.append("d0")
        else:
            out.append("a%d" % ai)
            ai += 1
    return out


def main():
    allf = extract()
    # 变参函数没法用固定槽位打包，一律踢到手写区（漏一个就是编译不过，不是静默错）
    vararg = sorted(f["name"] for f in allf
                    if any("..." in a["type"] for a in f["args"]))
    need = sorted(set(HAND_WRITTEN) | set(vararg))
    # 不返回的几个里，非变参的由生成器自己补 luashim_throw()，变参的必须手写
    missing = sorted((set(NEVER_RETURNS) & set(vararg)) - set(HAND_WRITTEN))
    print("手写符号（%d）：%s" % (len(need), " ".join(need)), file=sys.stderr)
    if missing:
        print("❗又变参又不返回，必须手写：%s" % missing, file=sys.stderr)
    # id 与 Java 桩覆盖<b>全部</b>符号：手写的那批 C stub 由 luashim.c 自己写，
    # 但它们照样要从 C 进 Java，所以 id 和 Java 侧的入口一个都不能少。
    funcs = allf
    gen = [f for f in allf if f["name"] not in need]
    check_against_real_lib(allf)
    ids = {f["name"]: i for i, f in enumerate(funcs)}

    # ---------- luashim_ids.h ----------
    with open(os.path.join(HERE, "luashim_ids.h"), "w") as fp:
        fp.write("/* 自动生成，勿手改：由 native/gen_capi.py 产出 */\n")
        fp.write("#ifndef LUASHIM_IDS_H\n#define LUASHIM_IDS_H\n")
        fp.write("/* 与 CApi.java 的 switch 一一对应，两边都由同一个脚本产出 */\n")
        fp.write("enum {\n")
        for f in funcs:
            fp.write("  ID_%s = %d,\n" % (f["name"], ids[f["name"]]))
        fp.write("  LUASHIM_ID_COUNT = %d\n" % len(funcs))
        fp.write("};\n#endif\n")

    # ---------- luashim_stubs.inc ----------
    with open(os.path.join(HERE, "luashim_stubs.inc"), "w") as fp:
        fp.write("/* 自动生成，勿手改：由 native/gen_capi.py 产出 */\n")
        fp.write("/* 手写符号（变参/结构体遍历/数据符号）在 luashim.c 里 */\n")
        for f in gen:
            name, ret, args = f["name"], f["ret"], f["args"]
            slots = slots_of(args)
            # 七个槽位：a0..a5 是整数/指针，最后一个是唯一的 double
            buf = ["0"] * 7
            for a, s in zip(args, slots):
                if s == "d0":
                    buf[6] = "(jdouble)%s" % a["name"]
                else:
                    i = int(s[1:])
                    if a["type"] == "lua_State":
                        # lua_State* 是句柄，进出都要换：Java 只见得到下标
                        buf[i] = "LUASHIM_HANDLE(%s)" % a["name"]
                    elif a["ptr"] or a["type"] in FN_PTR_TYPES:
                        buf[i] = "(intptr_t)%s" % a["name"]
                    elif a["type"] in WIDE_TYPES:
                        buf[i] = "(jlong)%s" % a["name"]
                    else:
                        buf[i] = "(jint)%s" % a["name"]
            call = "luashim_call%s(ID_%s, %s)" % (
                "D" if ret in DOUBLE_TYPES else "", name, ", ".join(buf))
            body = []
            if ret in DOUBLE_TYPES:
                body.append("  return (lua_Number)%s;" % call)
            elif ret == "void":
                body.append("  %s;" % call)
            elif ret in INT_TYPES:
                body.append("  return (%s)%s;" % (ret, call))
            elif ret == "lua_State *":
                body.append("  return LUASHIM_STATE(%s);" % call)
            else:
                body.append("  return (%s)(intptr_t)%s;" % (ret, call))
            if name in NEVER_RETURNS:
                # 真 Lua 里这几个不返回：Java 把错误值准备好，C 侧再 longjmp 出去
                body.append("  luashim_throw();")
                if ret != "void":
                    body.append("  return 0;")
            api = f["api"]
            fp.write("\n/* %s */\n%s %s (%s) (%s) {\n%s\n}\n" % (
                f["header"],
                api,
                ret, name,
                ", ".join(decl_of(a) for a in args) or "void",
                "\n".join(body)))

    # ---------- CApi.java ----------
    with open(os.path.join(JAVA_DIR, "CApi.java"), "w") as fp:
        fp.write(CAPI_HEAD)
        for f in funcs:
            fp.write("        case %d: /* %s */\n" % (ids[f["name"]], f["name"]))
            fp.write("            ")
            # 按槽位名取分发表的参数，不是按方法自己的形参名
            call = "Api.%s(%s)" % (f["name"], ", ".join(slots_of(f["args"])))
            if f["ret"] == "void":
                fp.write("%s;\n            return 0;\n" % call)
            elif f["ret"] in DOUBLE_TYPES:
                fp.write("throw new IllegalStateException("
                         "\"double 返回走 dispatchD\");\n")
            else:
                fp.write("return %s;\n" % call)
        fp.write("        default:\n            throw unimplemented(\"id=\" + id);\n")
        fp.write("        }\n    }\n\n")
        fp.write("    /** 返回 double 的那些（{@code lua_tonumberx} 一类）走这里 */\n")
        fp.write("    static double dispatchD(int id, long a0, long a1, long a2,\n")
        fp.write("            long a3, long a4, long a5, double d0) {\n")
        fp.write("        switch (id) {\n")
        for f in funcs:
            if f["ret"] not in DOUBLE_TYPES:
                continue
            fp.write("        case %d: /* %s */\n" % (ids[f["name"]], f["name"]))
            fp.write("            return Api.%s(%s);\n" % (
                f["name"], ", ".join(slots_of(f["args"]))))
        fp.write("        default:\n            throw unimplemented(\"id=\" + id);\n")
        fp.write("        }\n    }\n")
        fp.write("}\n")
    # Api.java 只在<b>不存在</b>时生成一次：里面是要手写的语义，重跑不能冲掉
    api_path = os.path.join(JAVA_DIR, "Api.java")
    if os.path.exists(api_path):
        print("Api.java 已存在，跳过（语义是手写的，不能覆盖）", file=sys.stderr)
    else:
        with open(api_path, "w") as fp:
            fp.write(API_HEAD)
            for f in funcs:
                ret = "double" if f["ret"] in DOUBLE_TYPES else (
                    "void" if f["ret"] == "void" else "long")
                ps = ", ".join("%s %s" % ("double" if slot_of(a) == "d" else "long",
                                          a["name"]) for a in f["args"])
                fp.write("    static %s %s(%s) {\n" % (ret, f["name"], ps))
                if f["name"] in C_ONLY:
                    fp.write('        throw new IllegalStateException('
                             '"%s 在 luashim.c 里用别的 lua_* 拼出来，不进 Java");\n'
                             % f["name"])
                else:
                    fp.write("        throw unimplemented(\"%s\");\n" % f["name"])
                fp.write("    }\n\n")
            fp.write("}\n")
        print("初次生成 Api.java", file=sys.stderr)

    print("id/Java 桩 %d 个，生成 C stub %d 个，手写 %d 个"
          % (len(funcs), len(gen), len(need)), file=sys.stderr)


def decl_of(a):
    """
    还原成 C 的参数声明。
    头文件里写的是 {@code lua_State *L}、{@code luaL_Buffer *B}，不带 const——
    我们是在<b>定义</b>这些函数，签名多一个 const 就是 conflicting types。
    """
    t = a["type"]
    stars = "*" * a["ptr"]
    c = "const " if (a.get("const") and stars) else ""
    if stars:
        return "%s%s %s%s" % (c, t, stars, a["name"])
    return "%s%s %s" % (c, t, a["name"])


CAPI_HEAD = '''package com.github.esrrhs.fakescript.lua;

/**
 * C API 的分发表 —— 对应 {@code lapi.c} + {@code lauxlib.c}。
 * <p>
 * <b>本文件由 {@code native/gen_capi.py} 产出。</b>idl 与 C 侧的
 * {@code luashim_stubs.inc} 同源，改符号集要重跑脚本；重跑会覆盖这里，
 * 所以实现只能写在本文件里（{@code unimplemented} 的那批逐个替换），
 * 不要另开一个类去写，否则下次生成就丢了。
 * <p>
 * 约定：
 * <ul>
 *   <li>参数下标按声明顺序，{@code lua_State*} 就是 {@code a0}；</li>
 *   <li>指针一律是 {@code long}，取值用 {@link NativeBridge}；</li>
 *   <li>不返回的四个（{@code lua_error}/{@code luaL_error}/
 *       {@code luaL_argerror}/{@code luaL_typeerror}）在 C 侧
 *       {@code longjmp} 出去，Java 这侧只负责把错误值准备好。</li>
 * </ul>
 */
final class CApi {

    private CApi() {
    }

    static RuntimeException unimplemented(String what) {
        return new UnsupportedOperationException("C API 未实现: " + what);
    }

    /** C 侧 stub 统一走这里进来 */
    static long dispatch(int id, long a0, long a1, long a2,
            long a3, long a4, long a5, double d0) {
        switch (id) {
'''

API_HEAD = '''package com.github.esrrhs.fakescript.lua;

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

'''

if __name__ == "__main__":
    main()
