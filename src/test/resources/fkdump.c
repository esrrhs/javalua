/*
** fkdump.c —— 给「Java 版 Lua」做差分比对用的规范转储器。
**
** 这个文件要随仓库走：CI 的一致性 job 会先编出参考实现，再把它和
** liblua.a 一起编译（见 .github/workflows/maven.yml）。它不是产品代码，
** 只是差分比对的另一半。
**
** 为什么自己写而不用 luac -l：luac 的输出是给人看的（每条指令按 opcode 打不同
** 的字段、还夹着注释），要反解回原始字段就得把那个大 switch 抄一遍，反而更脆。
** 这里对<b>所有</b> opcode 统一打印全部字段，格式固定，Java 侧照抄即可。
**
** 用法：fkdump <file.lua>
*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

#include "ldebug.h"
#include "lfunc.h"
#include "lobject.h"
#include "lopcodes.h"
#include "lstate.h"
#include "ltm.h"
#include "lopnames.h"

static void printString(const char *s, int len) {
  int i;
  for (i = 0; i < len; i++) {
    unsigned char c = (unsigned char) s[i];
    if (c == '\\' || c == '"') {
      printf("\\%02x", c);
    } else if (c >= 0x20 && c < 0x7f) {
      putchar(c);
    } else {
      printf("\\%02x", c);
    }
  }
}

static void printConstant(const TValue *v) {
  switch (ttype(v)) {
    case LUA_TNIL:
      printf("nil");
      break;
    case LUA_TBOOLEAN:
      printf("bool:%d", !l_isfalse(v));
      break;
    case LUA_TNUMBER:
      if (ttisinteger(v))
        printf("int:%lld", (long long) ivalue(v));
      else {
        lua_Number d = fltvalue(v);
        unsigned long long bits = 0;
        memcpy(&bits, &d, sizeof(bits));
        printf("flt:%016llx", bits);
      }
      break;
    case LUA_TSTRING: {
      TString *ts = tsvalue(v);
      printf("str:");
      printString(getstr(ts), tsslen(ts));
      break;
    }
    default:
      printf("other");
      break;
  }
}

static void dumpFunction(const Proto *f, int level, int idx);

static void dumpFunction(const Proto *f, int level, int idx) {
  int i;
  printf("PROTO level=%d index=%d linedefined=%d lastlinedefined=%d\n",
         level, idx, f->linedefined, f->lastlinedefined);
  printf("  numparams=%d isvararg=%d maxstacksize=%d nups=%d nconsts=%d nsubs=%d\n",
         f->numparams, f->is_vararg, f->maxstacksize,
         f->sizeupvalues, f->sizek, f->sizep);
  for (i = 0; i < f->sizecode; i++) {
    Instruction inst = f->code[i];
    int o = GET_OPCODE(inst);
    printf("  I %d %s a=%d b=%d c=%d k=%d bx=%d sbx=%d ax=%d sb=%d sc=%d sj=%d line=%d\n",
           i, opnames[o],
           GETARG_A(inst), GETARG_B(inst), GETARG_C(inst), GETARG_k(inst),
           GETARG_Bx(inst), GETARG_sBx(inst), GETARG_Ax(inst),
           GETARG_sB(inst), GETARG_sC(inst), GETARG_sJ(inst),
           luaG_getfuncline(f, i));
  }
  for (i = 0; i < f->sizek; i++) {
    printf("  K %d ", i);
    printConstant(&f->k[i]);
    printf("\n");
  }
  for (i = 0; i < f->sizeupvalues; i++) {
    printf("  UP %d instack=%d idx=%d kind=%d name=", i,
           f->upvalues[i].instack, f->upvalues[i].idx, f->upvalues[i].kind);
    if (f->upvalues[i].name == NULL) {
      printf("?");
    } else {
      printString(getstr(f->upvalues[i].name), tsslen(f->upvalues[i].name));
    }
    printf("\n");
  }
  for (i = 0; i < f->sizep; i++) {
    dumpFunction(f->p[i], level + 1, i);
  }
}

int main(int argc, char **argv) {
  lua_State *L;
  const TValue *o;
  LClosure *cl;
  if (argc < 2) {
    fprintf(stderr, "usage: fkdump <file.lua>\n");
    return 2;
  }
  L = luaL_newstate();
  luaL_openlibs(L);
  if (luaL_loadfilex(L, argv[1], "t") != LUA_OK) {
    fprintf(stderr, "LOADERR %s\n", lua_tostring(L, -1));
    return 1;
  }
  o = s2v(L->top.p - 1);
  cl = clLvalue(o);
  dumpFunction(cl->p, 0, 0);
  lua_close(L);
  return 0;
}
