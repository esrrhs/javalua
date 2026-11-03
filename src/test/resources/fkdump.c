/*
** fkdump.c — bytecode dump helper for differential tests (Lua 5.3.4).
**
** Prints a stable, field-oriented dump of a compiled prototype. javalua's
** internal ISA is still largely 5.4-derived, so CodegenTest currently
** documents that gap rather than requiring a byte-identical match.
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

static void printString(const char *s, size_t len) {
  size_t i;
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
    printf("  I %d op=%d a=%d b=%d c=%d bx=%d sbx=%d ax=%d line=%d\n",
           i, o,
           GETARG_A(inst), GETARG_B(inst), GETARG_C(inst),
           GETARG_Bx(inst), GETARG_sBx(inst), GETARG_Ax(inst),
           (f->lineinfo && i < f->sizelineinfo) ? f->lineinfo[i] : -1);
  }
  for (i = 0; i < f->sizek; i++) {
    printf("  K %d ", i);
    printConstant(&f->k[i]);
    printf("\n");
  }
  for (i = 0; i < f->sizeupvalues; i++) {
    printf("  UP %d instack=%d idx=%d name=", i,
           f->upvalues[i].instack, f->upvalues[i].idx);
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
  cl = clLvalue(L->top - 1);
  dumpFunction(cl->p, 0, 0);
  lua_close(L);
  return 0;
}
