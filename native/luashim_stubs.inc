/* 自动生成，勿手改：由 native/gen_capi.py 产出 */
/* 手写符号（变参/结构体遍历/数据符号）在 luashim.c 里 */

/* lauxlib.h */
LUALIB_API void (luaL_addgsub) (luaL_Buffer *b, const char *s, const char *p, const char *r) {
  luashim_call(ID_luaL_addgsub, (intptr_t)b, (intptr_t)s, (intptr_t)p, (intptr_t)r, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_argerror) (lua_State *L, int arg, const char *extramsg) {
  return (int)luashim_call(ID_luaL_argerror, LUASHIM_HANDLE(L), (jint)arg, (intptr_t)extramsg, 0, 0, 0, 0);
  luashim_throw();
  return 0;
}

/* lauxlib.h */
LUALIB_API int (luaL_callmeta) (lua_State *L, int obj, const char *e) {
  return (int)luashim_call(ID_luaL_callmeta, LUASHIM_HANDLE(L), (jint)obj, (intptr_t)e, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_checkany) (lua_State *L, int arg) {
  luashim_call(ID_luaL_checkany, LUASHIM_HANDLE(L), (jint)arg, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API lua_Integer (luaL_checkinteger) (lua_State *L, int arg) {
  return (lua_Integer)luashim_call(ID_luaL_checkinteger, LUASHIM_HANDLE(L), (jint)arg, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API const char * (luaL_checklstring) (lua_State *L, int arg, size_t *l) {
  return (const char *)(intptr_t)luashim_call(ID_luaL_checklstring, LUASHIM_HANDLE(L), (jint)arg, (intptr_t)l, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API lua_Number (luaL_checknumber) (lua_State *L, int arg) {
  return (lua_Number)luashim_callD(ID_luaL_checknumber, LUASHIM_HANDLE(L), (jint)arg, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_checkstack) (lua_State *L, int sz, const char *msg) {
  luashim_call(ID_luaL_checkstack, LUASHIM_HANDLE(L), (jint)sz, (intptr_t)msg, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_checktype) (lua_State *L, int arg, int t) {
  luashim_call(ID_luaL_checktype, LUASHIM_HANDLE(L), (jint)arg, (jint)t, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void * (luaL_checkudata) (lua_State *L, int ud, const char *tname) {
  return (void *)(intptr_t)luashim_call(ID_luaL_checkudata, LUASHIM_HANDLE(L), (jint)ud, (intptr_t)tname, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_checkversion_) (lua_State *L, lua_Number ver, size_t sz) {
  luashim_call(ID_luaL_checkversion_, LUASHIM_HANDLE(L), (jlong)sz, 0, 0, 0, 0, (jdouble)ver);
}

/* lauxlib.h */
LUALIB_API int (luaL_execresult) (lua_State *L, int stat) {
  return (int)luashim_call(ID_luaL_execresult, LUASHIM_HANDLE(L), (jint)stat, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_fileresult) (lua_State *L, int stat, const char *fname) {
  return (int)luashim_call(ID_luaL_fileresult, LUASHIM_HANDLE(L), (jint)stat, (intptr_t)fname, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_getmetafield) (lua_State *L, int obj, const char *e) {
  return (int)luashim_call(ID_luaL_getmetafield, LUASHIM_HANDLE(L), (jint)obj, (intptr_t)e, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_getsubtable) (lua_State *L, int idx, const char *fname) {
  return (int)luashim_call(ID_luaL_getsubtable, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)fname, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API lua_Integer (luaL_len) (lua_State *L, int idx) {
  return (lua_Integer)luashim_call(ID_luaL_len, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_loadbufferx) (lua_State *L, const char *buff, size_t sz, const char *name, const char *mode) {
  return (int)luashim_call(ID_luaL_loadbufferx, LUASHIM_HANDLE(L), (intptr_t)buff, (jlong)sz, (intptr_t)name, (intptr_t)mode, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_loadstring) (lua_State *L, const char *s) {
  return (int)luashim_call(ID_luaL_loadstring, LUASHIM_HANDLE(L), (intptr_t)s, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_newmetatable) (lua_State *L, const char *tname) {
  return (int)luashim_call(ID_luaL_newmetatable, LUASHIM_HANDLE(L), (intptr_t)tname, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API lua_State * (luaL_newstate) (void) {
  return LUASHIM_STATE(luashim_call(ID_luaL_newstate, 0, 0, 0, 0, 0, 0, 0));
}

/* lauxlib.h */
LUALIB_API lua_Integer (luaL_optinteger) (lua_State *L, int arg, lua_Integer def) {
  return (lua_Integer)luashim_call(ID_luaL_optinteger, LUASHIM_HANDLE(L), (jint)arg, (jlong)def, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API const char * (luaL_optlstring) (lua_State *L, int arg, const char *def, size_t *l) {
  return (const char *)(intptr_t)luashim_call(ID_luaL_optlstring, LUASHIM_HANDLE(L), (jint)arg, (intptr_t)def, (intptr_t)l, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API lua_Number (luaL_optnumber) (lua_State *L, int arg, lua_Number def) {
  return (lua_Number)luashim_callD(ID_luaL_optnumber, LUASHIM_HANDLE(L), (jint)arg, 0, 0, 0, 0, (jdouble)def);
}

/* lauxlib.h */
LUALIB_API int (luaL_ref) (lua_State *L, int t) {
  return (int)luashim_call(ID_luaL_ref, LUASHIM_HANDLE(L), (jint)t, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_requiref) (lua_State *L, const char *modname, lua_CFunction openf, int glb) {
  luashim_call(ID_luaL_requiref, LUASHIM_HANDLE(L), (intptr_t)modname, (intptr_t)openf, (jint)glb, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_setmetatable) (lua_State *L, const char *tname) {
  luashim_call(ID_luaL_setmetatable, LUASHIM_HANDLE(L), (intptr_t)tname, 0, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void * (luaL_testudata) (lua_State *L, int ud, const char *tname) {
  return (void *)(intptr_t)luashim_call(ID_luaL_testudata, LUASHIM_HANDLE(L), (jint)ud, (intptr_t)tname, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API const char * (luaL_tolstring) (lua_State *L, int idx, size_t *len) {
  return (const char *)(intptr_t)luashim_call(ID_luaL_tolstring, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)len, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_traceback) (lua_State *L, lua_State *L1, const char *msg, int level) {
  luashim_call(ID_luaL_traceback, LUASHIM_HANDLE(L), LUASHIM_HANDLE(L1), (intptr_t)msg, (jint)level, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API int (luaL_typeerror) (lua_State *L, int arg, const char *tname) {
  return (int)luashim_call(ID_luaL_typeerror, LUASHIM_HANDLE(L), (jint)arg, (intptr_t)tname, 0, 0, 0, 0);
  luashim_throw();
  return 0;
}

/* lauxlib.h */
LUALIB_API void (luaL_unref) (lua_State *L, int t, int ref) {
  luashim_call(ID_luaL_unref, LUASHIM_HANDLE(L), (jint)t, (jint)ref, 0, 0, 0, 0);
}

/* lauxlib.h */
LUALIB_API void (luaL_where) (lua_State *L, int lvl) {
  luashim_call(ID_luaL_where, LUASHIM_HANDLE(L), (jint)lvl, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_absindex) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_absindex, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_arith) (lua_State *L, int op) {
  luashim_call(ID_lua_arith, LUASHIM_HANDLE(L), (jint)op, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_CFunction (lua_atpanic) (lua_State *L, lua_CFunction panicf) {
  return (lua_CFunction)(intptr_t)luashim_call(ID_lua_atpanic, LUASHIM_HANDLE(L), (intptr_t)panicf, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_callk) (lua_State *L, int nargs, int nresults, lua_KContext ctx, lua_KFunction k) {
  luashim_call(ID_lua_callk, LUASHIM_HANDLE(L), (jint)nargs, (jint)nresults, (jlong)ctx, (intptr_t)k, 0, 0);
}

/* lua.h */
LUA_API int (lua_checkstack) (lua_State *L, int n) {
  return (int)luashim_call(ID_lua_checkstack, LUASHIM_HANDLE(L), (jint)n, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_close) (lua_State *L) {
  luashim_call(ID_lua_close, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_closeslot) (lua_State *L, int idx) {
  luashim_call(ID_lua_closeslot, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_closethread) (lua_State *L, lua_State *from) {
  return (int)luashim_call(ID_lua_closethread, LUASHIM_HANDLE(L), LUASHIM_HANDLE(from), 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_compare) (lua_State *L, int idx1, int idx2, int op) {
  return (int)luashim_call(ID_lua_compare, LUASHIM_HANDLE(L), (jint)idx1, (jint)idx2, (jint)op, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_concat) (lua_State *L, int n) {
  luashim_call(ID_lua_concat, LUASHIM_HANDLE(L), (jint)n, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_copy) (lua_State *L, int fromidx, int toidx) {
  luashim_call(ID_lua_copy, LUASHIM_HANDLE(L), (jint)fromidx, (jint)toidx, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_createtable) (lua_State *L, int narr, int nrec) {
  luashim_call(ID_lua_createtable, LUASHIM_HANDLE(L), (jint)narr, (jint)nrec, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_dump) (lua_State *L, lua_Writer writer, void *data, int strip) {
  return (int)luashim_call(ID_lua_dump, LUASHIM_HANDLE(L), (intptr_t)writer, (intptr_t)data, (jint)strip, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_error) (lua_State *L) {
  return (int)luashim_call(ID_lua_error, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
  luashim_throw();
  return 0;
}

/* lua.h */
LUA_API int (lua_getfield) (lua_State *L, int idx, const char *k) {
  return (int)luashim_call(ID_lua_getfield, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)k, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_getglobal) (lua_State *L, const char *name) {
  return (int)luashim_call(ID_lua_getglobal, LUASHIM_HANDLE(L), (intptr_t)name, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_Hook (lua_gethook) (lua_State *L) {
  return (lua_Hook)(intptr_t)luashim_call(ID_lua_gethook, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_gethookcount) (lua_State *L) {
  return (int)luashim_call(ID_lua_gethookcount, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_gethookmask) (lua_State *L) {
  return (int)luashim_call(ID_lua_gethookmask, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_geti) (lua_State *L, int idx, lua_Integer n) {
  return (int)luashim_call(ID_lua_geti, LUASHIM_HANDLE(L), (jint)idx, (jlong)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_getinfo) (lua_State *L, const char *what, lua_Debug *ar) {
  return (int)luashim_call(ID_lua_getinfo, LUASHIM_HANDLE(L), (intptr_t)what, (intptr_t)ar, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_getiuservalue) (lua_State *L, int idx, int n) {
  return (int)luashim_call(ID_lua_getiuservalue, LUASHIM_HANDLE(L), (jint)idx, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_getlocal) (lua_State *L, const lua_Debug *ar, int n) {
  return (const char *)(intptr_t)luashim_call(ID_lua_getlocal, LUASHIM_HANDLE(L), (intptr_t)ar, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_getmetatable) (lua_State *L, int objindex) {
  return (int)luashim_call(ID_lua_getmetatable, LUASHIM_HANDLE(L), (jint)objindex, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_getstack) (lua_State *L, int level, lua_Debug *ar) {
  return (int)luashim_call(ID_lua_getstack, LUASHIM_HANDLE(L), (jint)level, (intptr_t)ar, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_gettable) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_gettable, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_gettop) (lua_State *L) {
  return (int)luashim_call(ID_lua_gettop, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_getupvalue) (lua_State *L, int funcindex, int n) {
  return (const char *)(intptr_t)luashim_call(ID_lua_getupvalue, LUASHIM_HANDLE(L), (jint)funcindex, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_iscfunction) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_iscfunction, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_isinteger) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_isinteger, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_isnumber) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_isnumber, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_isstring) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_isstring, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_isuserdata) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_isuserdata, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_isyieldable) (lua_State *L) {
  return (int)luashim_call(ID_lua_isyieldable, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_len) (lua_State *L, int idx) {
  luashim_call(ID_lua_len, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_State * (lua_newstate) (lua_Alloc f, void *ud) {
  return LUASHIM_STATE(luashim_call(ID_lua_newstate, (intptr_t)f, (intptr_t)ud, 0, 0, 0, 0, 0));
}

/* lua.h */
LUA_API lua_State * (lua_newthread) (lua_State *L) {
  return LUASHIM_STATE(luashim_call(ID_lua_newthread, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0));
}

/* lua.h */
LUA_API void * (lua_newuserdatauv) (lua_State *L, size_t sz, int nuvalue) {
  return (void *)(intptr_t)luashim_call(ID_lua_newuserdatauv, LUASHIM_HANDLE(L), (jlong)sz, (jint)nuvalue, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_next) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_next, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_pcallk) (lua_State *L, int nargs, int nresults, int errfunc, lua_KContext ctx, lua_KFunction k) {
  return (int)luashim_call(ID_lua_pcallk, LUASHIM_HANDLE(L), (jint)nargs, (jint)nresults, (jint)errfunc, (jlong)ctx, (intptr_t)k, 0);
}

/* lua.h */
LUA_API void (lua_pushboolean) (lua_State *L, int b) {
  luashim_call(ID_lua_pushboolean, LUASHIM_HANDLE(L), (jint)b, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushcclosure) (lua_State *L, lua_CFunction fn, int n) {
  luashim_call(ID_lua_pushcclosure, LUASHIM_HANDLE(L), (intptr_t)fn, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushinteger) (lua_State *L, lua_Integer n) {
  luashim_call(ID_lua_pushinteger, LUASHIM_HANDLE(L), (jlong)n, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushlightuserdata) (lua_State *L, void *p) {
  luashim_call(ID_lua_pushlightuserdata, LUASHIM_HANDLE(L), (intptr_t)p, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_pushlstring) (lua_State *L, const char *s, size_t len) {
  return (const char *)(intptr_t)luashim_call(ID_lua_pushlstring, LUASHIM_HANDLE(L), (intptr_t)s, (jlong)len, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushnil) (lua_State *L) {
  luashim_call(ID_lua_pushnil, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushnumber) (lua_State *L, lua_Number n) {
  luashim_call(ID_lua_pushnumber, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, (jdouble)n);
}

/* lua.h */
LUA_API const char * (lua_pushstring) (lua_State *L, const char *s) {
  return (const char *)(intptr_t)luashim_call(ID_lua_pushstring, LUASHIM_HANDLE(L), (intptr_t)s, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_pushthread) (lua_State *L) {
  return (int)luashim_call(ID_lua_pushthread, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_pushvalue) (lua_State *L, int idx) {
  luashim_call(ID_lua_pushvalue, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_rawequal) (lua_State *L, int idx1, int idx2) {
  return (int)luashim_call(ID_lua_rawequal, LUASHIM_HANDLE(L), (jint)idx1, (jint)idx2, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_rawget) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_rawget, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_rawgeti) (lua_State *L, int idx, lua_Integer n) {
  return (int)luashim_call(ID_lua_rawgeti, LUASHIM_HANDLE(L), (jint)idx, (jlong)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_rawgetp) (lua_State *L, int idx, const void *p) {
  return (int)luashim_call(ID_lua_rawgetp, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)p, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_Unsigned (lua_rawlen) (lua_State *L, int idx) {
  return (lua_Unsigned)luashim_call(ID_lua_rawlen, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_rawset) (lua_State *L, int idx) {
  luashim_call(ID_lua_rawset, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_rawseti) (lua_State *L, int idx, lua_Integer n) {
  luashim_call(ID_lua_rawseti, LUASHIM_HANDLE(L), (jint)idx, (jlong)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_rawsetp) (lua_State *L, int idx, const void *p) {
  luashim_call(ID_lua_rawsetp, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)p, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_resetthread) (lua_State *L) {
  return (int)luashim_call(ID_lua_resetthread, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_rotate) (lua_State *L, int idx, int n) {
  luashim_call(ID_lua_rotate, LUASHIM_HANDLE(L), (jint)idx, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_setcstacklimit) (lua_State *L, unsigned int limit) {
  return (int)luashim_call(ID_lua_setcstacklimit, LUASHIM_HANDLE(L), (jint)limit, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_setfield) (lua_State *L, int idx, const char *k) {
  luashim_call(ID_lua_setfield, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)k, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_setglobal) (lua_State *L, const char *name) {
  luashim_call(ID_lua_setglobal, LUASHIM_HANDLE(L), (intptr_t)name, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_sethook) (lua_State *L, lua_Hook func, int mask, int count) {
  luashim_call(ID_lua_sethook, LUASHIM_HANDLE(L), (intptr_t)func, (jint)mask, (jint)count, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_seti) (lua_State *L, int idx, lua_Integer n) {
  luashim_call(ID_lua_seti, LUASHIM_HANDLE(L), (jint)idx, (jlong)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_setiuservalue) (lua_State *L, int idx, int n) {
  return (int)luashim_call(ID_lua_setiuservalue, LUASHIM_HANDLE(L), (jint)idx, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_setlocal) (lua_State *L, const lua_Debug *ar, int n) {
  return (const char *)(intptr_t)luashim_call(ID_lua_setlocal, LUASHIM_HANDLE(L), (intptr_t)ar, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_setmetatable) (lua_State *L, int objindex) {
  return (int)luashim_call(ID_lua_setmetatable, LUASHIM_HANDLE(L), (jint)objindex, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_settable) (lua_State *L, int idx) {
  luashim_call(ID_lua_settable, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_settop) (lua_State *L, int idx) {
  luashim_call(ID_lua_settop, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_setupvalue) (lua_State *L, int funcindex, int n) {
  return (const char *)(intptr_t)luashim_call(ID_lua_setupvalue, LUASHIM_HANDLE(L), (jint)funcindex, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_setwarnf) (lua_State *L, lua_WarnFunction f, void *ud) {
  luashim_call(ID_lua_setwarnf, LUASHIM_HANDLE(L), (intptr_t)f, (intptr_t)ud, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_status) (lua_State *L) {
  return (int)luashim_call(ID_lua_status, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API size_t (lua_stringtonumber) (lua_State *L, const char *s) {
  return (size_t)luashim_call(ID_lua_stringtonumber, LUASHIM_HANDLE(L), (intptr_t)s, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_toboolean) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_toboolean, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_CFunction (lua_tocfunction) (lua_State *L, int idx) {
  return (lua_CFunction)(intptr_t)luashim_call(ID_lua_tocfunction, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_toclose) (lua_State *L, int idx) {
  luashim_call(ID_lua_toclose, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_Integer (lua_tointegerx) (lua_State *L, int idx, int *isnum) {
  return (lua_Integer)luashim_call(ID_lua_tointegerx, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)isnum, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_tolstring) (lua_State *L, int idx, size_t *len) {
  return (const char *)(intptr_t)luashim_call(ID_lua_tolstring, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)len, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_Number (lua_tonumberx) (lua_State *L, int idx, int *isnum) {
  return (lua_Number)luashim_callD(ID_lua_tonumberx, LUASHIM_HANDLE(L), (jint)idx, (intptr_t)isnum, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const void * (lua_topointer) (lua_State *L, int idx) {
  return (const void *)(intptr_t)luashim_call(ID_lua_topointer, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API lua_State * (lua_tothread) (lua_State *L, int idx) {
  return LUASHIM_STATE(luashim_call(ID_lua_tothread, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0));
}

/* lua.h */
LUA_API void * (lua_touserdata) (lua_State *L, int idx) {
  return (void *)(intptr_t)luashim_call(ID_lua_touserdata, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API int (lua_type) (lua_State *L, int idx) {
  return (int)luashim_call(ID_lua_type, LUASHIM_HANDLE(L), (jint)idx, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API const char * (lua_typename) (lua_State *L, int tp) {
  return (const char *)(intptr_t)luashim_call(ID_lua_typename, LUASHIM_HANDLE(L), (jint)tp, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void * (lua_upvalueid) (lua_State *L, int fidx, int n) {
  return (void *)(intptr_t)luashim_call(ID_lua_upvalueid, LUASHIM_HANDLE(L), (jint)fidx, (jint)n, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_upvaluejoin) (lua_State *L, int fidx1, int n1, int fidx2, int n2) {
  luashim_call(ID_lua_upvaluejoin, LUASHIM_HANDLE(L), (jint)fidx1, (jint)n1, (jint)fidx2, (jint)n2, 0, 0);
}

/* lua.h */
LUA_API lua_Number (lua_version) (lua_State *L) {
  return (lua_Number)luashim_callD(ID_lua_version, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_warning) (lua_State *L, const char *msg, int tocont) {
  luashim_call(ID_lua_warning, LUASHIM_HANDLE(L), (intptr_t)msg, (jint)tocont, 0, 0, 0, 0);
}

/* lua.h */
LUA_API void (lua_xmove) (lua_State *from, lua_State *to, int n) {
  luashim_call(ID_lua_xmove, LUASHIM_HANDLE(from), LUASHIM_HANDLE(to), (jint)n, 0, 0, 0, 0);
}

/* lualib.h */
LUALIB_API void (luaL_openlibs) (lua_State *L) {
  luashim_call(ID_luaL_openlibs, LUASHIM_HANDLE(L), 0, 0, 0, 0, 0, 0);
}
