/*
** tblstruct.c — table structure snapshot dumper for Lua 5.3.4.
**
** 5.3 uses sizearray (not 5.4's alimit / isrealasize). javalua mirrors
** sizearray via array.length; alimit/alimitIsReal are compatibility fields
** kept equal to sizearray / true. TableStructTest requires an exact match.
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
  const Table *t;
  lua_rawgeti(L, -1, i);
  if (!ttistable(L->top - 1)) {
    printf("nontable\n");
    lua_pop(L, 1);
    return 0;
  }
  t = hvalue(L->top - 1);
  /* sizearray  asize  (always "real")  node alloc size */
  printf("%u %u %d %u\n",
         t->sizearray,
         t->sizearray,
         1,
         (unsigned) allocsizenode(t));
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
  if (luaL_dofile(L, argv[1]) != LUA_OK) {
    fprintf(stderr, "%s\n", lua_tostring(L, -1));
    return 1;
  }
  lua_getglobal(L, "TBLSTRUCT_COUNT");
  count = lua_tointeger(L, -1);
  lua_pop(L, 1);
  lua_getglobal(L, "TBLSTRUCT_RESULT");
  for (i = 1; i <= count; i++) {
    dumpOne(L, i);
  }
  lua_close(L);
  return 0;
}
