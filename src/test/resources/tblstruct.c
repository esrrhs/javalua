/*
** tblstruct.c —— 表结构快照转储器（差分比对的另一半）。
**
** 这个文件要随仓库走：CI 的一致性 job 会先编出参考实现，再把它和
** liblua.a 一起编译（见 .github/workflows/maven.yml）。它不是产品代码，
** 只是差分比对的另一半，与 fkdump.c 同一类。
**
** 为什么需要它：{@code #t} / {@code rawlen} 这些<b>可观测</b>的输出只覆盖了
** 表结构的一部分。C 5.4.7 的数组部分边界不是一个数，而是 {@code alimit} 加一位
** 提示位（{@code lobject.h:725-736} 的 {@code BITRAS}）：{@code isrealasize} 为真时
** {@code alimit} 就是数组的真实尺寸；为假时真实尺寸是「不小于它的最小 2 的幂」，
** 而 {@code alimit} 只是 {@code #t} 的<b>起步提示</b>。两份表可以给出相同的
** {@code #t}、却在 {@code alimit} / 真实尺寸 / 提示位上已经分岔——那种分岔现在
** 看不见，等下游某次 {@code luaH_getn} 从不同起点出发才会露出来。
** 这个转储器把内部字段直接摊开，让「构造器路径的数组尺寸按位精确」这件事
** 有一条能逐条核对的证据，而不是只靠差分 + 全量套件跑绿这种间接证据。
**
** 用法：tblstruct <file.lua>
**
** 被跑的 chunk 要以全局变量的形式交出两张表：
**   TBLSTRUCT_RESULT  —— 一张密集数组，第 i 项就是第 i 张待快照的表；
**   TBLSTRUCT_COUNT   —— 表的张数。
** 用显式计数而不是 {@code #TBLSTRUCT_RESULT}：读长度本身会改写被读表的
** {@code alimit} 提示（{@code luaH_getn} 的进口），那会把「快照」变成
** 「快照前先动过手脚」。两张输出表都不是待快照的表，读它们不影响结论，
** 但计数这件事还是要它自己说清楚，不能靠 {@code #} 推。
**
** 每张表打一行，四个字段（与 Java 侧 {@code TableStructTest} 同一口径）：
**   <数组真实尺寸> <alimit> <isrealasize> <哈希节点分配尺寸>
*/

#include <stdio.h>
#include <stdlib.h>

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

#include "lobject.h"
#include "ltable.h"
#include "lstate.h"

static int dumpOne (lua_State *L, lua_Integer i) {
  const TValue *o;
  const Table *t;
  lua_rawgeti(L, -1, i);  /* push TBLSTRUCT_RESULT[i] */
  if (!ttistable(s2v(L->top.p - 1))) {
    /*
    ** 本该每项都是表。真出了非表项，就照实写一行让它当场对不上——
    ** 打印一个占位符比默默跳过强：跳过会让「少了张表」看起来像一致。
    */
    printf("nontable\n");
    lua_pop(L, 1);
    return 0;
  }
  o = s2v(L->top.p - 1);
  t = hvalue(o);
  /*
  ** luaH_realasize：数组的真实尺寸（{@code ltable.c} 里那个 LUAI_FUNC）。
  ** allocsizenode（{@code ltable.h}）：哈希节点<b>分配</b>尺寸，dummy 表为 0——
  ** 与 Java 侧 nodeSize 的 0 语义对齐（C 的 sizenode 宏对 dummy 会报 1）。
  */
  printf("%u %u %d %u\n",
         luaH_realasize(t),
         t->alimit,
         isrealasize(t) ? 1 : 0,
         allocsizenode(t));
  lua_pop(L, 1);
  return 0;
}

int main (int argc, char **argv) {
  lua_State *L;
  lua_Integer count, i;
  if (argc < 2) {
    fprintf(stderr, "usage: tblstruct <file.lua>\n");
    return 2;
  }
  L = luaL_newstate();
  luaL_openlibs(L);
  if (luaL_loadfilex(L, argv[1], "t") != LUA_OK) {
    fprintf(stderr, "LOADERR %s\n", lua_tostring(L, -1));
    return 1;
  }
  if (lua_pcall(L, 0, 0, 0) != LUA_OK) {
    fprintf(stderr, "RUNERR %s\n", lua_tostring(L, -1));
    return 1;
  }
  lua_getglobal(L, "TBLSTRUCT_COUNT");
  if (!lua_isinteger(L, -1)) {
    fprintf(stderr, "NOCOUNT\n");
    return 1;
  }
  count = lua_tointeger(L, -1);
  lua_pop(L, 1);
  lua_getglobal(L, "TBLSTRUCT_RESULT");
  if (!lua_istable(L, -1)) {
    fprintf(stderr, "NORESULT\n");
    return 1;
  }
  for (i = 1; i <= count; i++) {
    dumpOne(L, i);
  }
  lua_close(L);
  return 0;
}