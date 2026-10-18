package com.github.esrrhs.fakescript.lua;

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
        case 0: /* luaL_addgsub */
            Api.luaL_addgsub(a0, a1, a2, a3);
            return 0;
        case 1: /* luaL_addlstring */
            Api.luaL_addlstring(a0, a1, a2);
            return 0;
        case 2: /* luaL_addstring */
            Api.luaL_addstring(a0, a1);
            return 0;
        case 3: /* luaL_addvalue */
            Api.luaL_addvalue(a0);
            return 0;
        case 4: /* luaL_argerror */
            return Api.luaL_argerror(a0, a1, a2);
        case 5: /* luaL_buffinit */
            Api.luaL_buffinit(a0, a1);
            return 0;
        case 6: /* luaL_buffinitsize */
            return Api.luaL_buffinitsize(a0, a1, a2);
        case 7: /* luaL_callmeta */
            return Api.luaL_callmeta(a0, a1, a2);
        case 8: /* luaL_checkany */
            Api.luaL_checkany(a0, a1);
            return 0;
        case 9: /* luaL_checkinteger */
            return Api.luaL_checkinteger(a0, a1);
        case 10: /* luaL_checklstring */
            return Api.luaL_checklstring(a0, a1, a2);
        case 11: /* luaL_checknumber */
            throw new IllegalStateException("double 返回走 dispatchD");
        case 12: /* luaL_checkoption */
            return Api.luaL_checkoption(a0, a1, a2, a3, a4);
        case 13: /* luaL_checkstack */
            Api.luaL_checkstack(a0, a1, a2);
            return 0;
        case 14: /* luaL_checktype */
            Api.luaL_checktype(a0, a1, a2);
            return 0;
        case 15: /* luaL_checkudata */
            return Api.luaL_checkudata(a0, a1, a2);
        case 16: /* luaL_checkversion_ */
            Api.luaL_checkversion_(a0, d0, a1);
            return 0;
        case 17: /* luaL_error */
            return Api.luaL_error(a0, a1, a2);
        case 18: /* luaL_execresult */
            return Api.luaL_execresult(a0, a1);
        case 19: /* luaL_fileresult */
            return Api.luaL_fileresult(a0, a1, a2);
        case 20: /* luaL_getmetafield */
            return Api.luaL_getmetafield(a0, a1, a2);
        case 21: /* luaL_getsubtable */
            return Api.luaL_getsubtable(a0, a1, a2);
        case 22: /* luaL_gsub */
            return Api.luaL_gsub(a0, a1, a2, a3);
        case 23: /* luaL_len */
            return Api.luaL_len(a0, a1);
        case 24: /* luaL_loadbufferx */
            return Api.luaL_loadbufferx(a0, a1, a2, a3, a4);
        case 25: /* luaL_loadfilex */
            return Api.luaL_loadfilex(a0, a1, a2);
        case 26: /* luaL_loadstring */
            return Api.luaL_loadstring(a0, a1);
        case 27: /* luaL_newmetatable */
            return Api.luaL_newmetatable(a0, a1);
        case 28: /* luaL_newstate */
            return Api.luaL_newstate();
        case 29: /* luaL_optinteger */
            return Api.luaL_optinteger(a0, a1, a2);
        case 30: /* luaL_optlstring */
            return Api.luaL_optlstring(a0, a1, a2, a3);
        case 31: /* luaL_optnumber */
            throw new IllegalStateException("double 返回走 dispatchD");
        case 32: /* luaL_prepbuffsize */
            return Api.luaL_prepbuffsize(a0, a1);
        case 33: /* luaL_pushresult */
            Api.luaL_pushresult(a0);
            return 0;
        case 34: /* luaL_pushresultsize */
            Api.luaL_pushresultsize(a0, a1);
            return 0;
        case 35: /* luaL_ref */
            return Api.luaL_ref(a0, a1);
        case 36: /* luaL_requiref */
            Api.luaL_requiref(a0, a1, a2, a3);
            return 0;
        case 37: /* luaL_setfuncs */
            Api.luaL_setfuncs(a0, a1, a2);
            return 0;
        case 38: /* luaL_setmetatable */
            Api.luaL_setmetatable(a0, a1);
            return 0;
        case 39: /* luaL_testudata */
            return Api.luaL_testudata(a0, a1, a2);
        case 40: /* luaL_tolstring */
            return Api.luaL_tolstring(a0, a1, a2);
        case 41: /* luaL_traceback */
            Api.luaL_traceback(a0, a1, a2, a3);
            return 0;
        case 42: /* luaL_typeerror */
            return Api.luaL_typeerror(a0, a1, a2);
        case 43: /* luaL_unref */
            Api.luaL_unref(a0, a1, a2);
            return 0;
        case 44: /* luaL_where */
            Api.luaL_where(a0, a1);
            return 0;
        case 45: /* lua_absindex */
            return Api.lua_absindex(a0, a1);
        case 46: /* lua_arith */
            Api.lua_arith(a0, a1);
            return 0;
        case 47: /* lua_atpanic */
            return Api.lua_atpanic(a0, a1);
        case 48: /* lua_callk */
            Api.lua_callk(a0, a1, a2, a3, a4);
            return 0;
        case 49: /* lua_checkstack */
            return Api.lua_checkstack(a0, a1);
        case 50: /* lua_close */
            Api.lua_close(a0);
            return 0;
        case 51: /* lua_closeslot */
            Api.lua_closeslot(a0, a1);
            return 0;
        case 52: /* lua_closethread */
            return Api.lua_closethread(a0, a1);
        case 53: /* lua_compare */
            return Api.lua_compare(a0, a1, a2, a3);
        case 54: /* lua_concat */
            Api.lua_concat(a0, a1);
            return 0;
        case 55: /* lua_copy */
            Api.lua_copy(a0, a1, a2);
            return 0;
        case 56: /* lua_createtable */
            Api.lua_createtable(a0, a1, a2);
            return 0;
        case 57: /* lua_dump */
            return Api.lua_dump(a0, a1, a2, a3);
        case 58: /* lua_error */
            return Api.lua_error(a0);
        case 59: /* lua_gc */
            return Api.lua_gc(a0, a1, a2);
        case 60: /* lua_getallocf */
            return Api.lua_getallocf(a0, a1);
        case 61: /* lua_getfield */
            return Api.lua_getfield(a0, a1, a2);
        case 62: /* lua_getglobal */
            return Api.lua_getglobal(a0, a1);
        case 63: /* lua_gethook */
            return Api.lua_gethook(a0);
        case 64: /* lua_gethookcount */
            return Api.lua_gethookcount(a0);
        case 65: /* lua_gethookmask */
            return Api.lua_gethookmask(a0);
        case 66: /* lua_geti */
            return Api.lua_geti(a0, a1, a2);
        case 67: /* lua_getinfo */
            return Api.lua_getinfo(a0, a1, a2);
        case 68: /* lua_getiuservalue */
            return Api.lua_getiuservalue(a0, a1, a2);
        case 69: /* lua_getlocal */
            return Api.lua_getlocal(a0, a1, a2);
        case 70: /* lua_getmetatable */
            return Api.lua_getmetatable(a0, a1);
        case 71: /* lua_getstack */
            return Api.lua_getstack(a0, a1, a2);
        case 72: /* lua_gettable */
            return Api.lua_gettable(a0, a1);
        case 73: /* lua_gettop */
            return Api.lua_gettop(a0);
        case 74: /* lua_getupvalue */
            return Api.lua_getupvalue(a0, a1, a2);
        case 75: /* lua_iscfunction */
            return Api.lua_iscfunction(a0, a1);
        case 76: /* lua_isinteger */
            return Api.lua_isinteger(a0, a1);
        case 77: /* lua_isnumber */
            return Api.lua_isnumber(a0, a1);
        case 78: /* lua_isstring */
            return Api.lua_isstring(a0, a1);
        case 79: /* lua_isuserdata */
            return Api.lua_isuserdata(a0, a1);
        case 80: /* lua_isyieldable */
            return Api.lua_isyieldable(a0);
        case 81: /* lua_len */
            Api.lua_len(a0, a1);
            return 0;
        case 82: /* lua_load */
            return Api.lua_load(a0, a1, a2, a3, a4);
        case 83: /* lua_newstate */
            return Api.lua_newstate(a0, a1);
        case 84: /* lua_newthread */
            return Api.lua_newthread(a0);
        case 85: /* lua_newuserdatauv */
            return Api.lua_newuserdatauv(a0, a1, a2);
        case 86: /* lua_next */
            return Api.lua_next(a0, a1);
        case 87: /* lua_pcallk */
            return Api.lua_pcallk(a0, a1, a2, a3, a4, a5);
        case 88: /* lua_pushboolean */
            Api.lua_pushboolean(a0, a1);
            return 0;
        case 89: /* lua_pushcclosure */
            Api.lua_pushcclosure(a0, a1, a2);
            return 0;
        case 90: /* lua_pushfstring */
            return Api.lua_pushfstring(a0, a1, a2);
        case 91: /* lua_pushinteger */
            Api.lua_pushinteger(a0, a1);
            return 0;
        case 92: /* lua_pushlightuserdata */
            Api.lua_pushlightuserdata(a0, a1);
            return 0;
        case 93: /* lua_pushlstring */
            return Api.lua_pushlstring(a0, a1, a2);
        case 94: /* lua_pushnil */
            Api.lua_pushnil(a0);
            return 0;
        case 95: /* lua_pushnumber */
            Api.lua_pushnumber(a0, d0);
            return 0;
        case 96: /* lua_pushstring */
            return Api.lua_pushstring(a0, a1);
        case 97: /* lua_pushthread */
            return Api.lua_pushthread(a0);
        case 98: /* lua_pushvalue */
            Api.lua_pushvalue(a0, a1);
            return 0;
        case 99: /* lua_pushvfstring */
            return Api.lua_pushvfstring(a0, a1, a2);
        case 100: /* lua_rawequal */
            return Api.lua_rawequal(a0, a1, a2);
        case 101: /* lua_rawget */
            return Api.lua_rawget(a0, a1);
        case 102: /* lua_rawgeti */
            return Api.lua_rawgeti(a0, a1, a2);
        case 103: /* lua_rawgetp */
            return Api.lua_rawgetp(a0, a1, a2);
        case 104: /* lua_rawlen */
            return Api.lua_rawlen(a0, a1);
        case 105: /* lua_rawset */
            Api.lua_rawset(a0, a1);
            return 0;
        case 106: /* lua_rawseti */
            Api.lua_rawseti(a0, a1, a2);
            return 0;
        case 107: /* lua_rawsetp */
            Api.lua_rawsetp(a0, a1, a2);
            return 0;
        case 108: /* lua_resetthread */
            return Api.lua_resetthread(a0);
        case 109: /* lua_resume */
            return Api.lua_resume(a0, a1, a2, a3);
        case 110: /* lua_rotate */
            Api.lua_rotate(a0, a1, a2);
            return 0;
        case 111: /* lua_setallocf */
            Api.lua_setallocf(a0, a1, a2);
            return 0;
        case 112: /* lua_setcstacklimit */
            return Api.lua_setcstacklimit(a0, a1);
        case 113: /* lua_setfield */
            Api.lua_setfield(a0, a1, a2);
            return 0;
        case 114: /* lua_setglobal */
            Api.lua_setglobal(a0, a1);
            return 0;
        case 115: /* lua_sethook */
            Api.lua_sethook(a0, a1, a2, a3);
            return 0;
        case 116: /* lua_seti */
            Api.lua_seti(a0, a1, a2);
            return 0;
        case 117: /* lua_setiuservalue */
            return Api.lua_setiuservalue(a0, a1, a2);
        case 118: /* lua_setlocal */
            return Api.lua_setlocal(a0, a1, a2);
        case 119: /* lua_setmetatable */
            return Api.lua_setmetatable(a0, a1);
        case 120: /* lua_settable */
            Api.lua_settable(a0, a1);
            return 0;
        case 121: /* lua_settop */
            Api.lua_settop(a0, a1);
            return 0;
        case 122: /* lua_setupvalue */
            return Api.lua_setupvalue(a0, a1, a2);
        case 123: /* lua_setwarnf */
            Api.lua_setwarnf(a0, a1, a2);
            return 0;
        case 124: /* lua_status */
            return Api.lua_status(a0);
        case 125: /* lua_stringtonumber */
            return Api.lua_stringtonumber(a0, a1);
        case 126: /* lua_toboolean */
            return Api.lua_toboolean(a0, a1);
        case 127: /* lua_tocfunction */
            return Api.lua_tocfunction(a0, a1);
        case 128: /* lua_toclose */
            Api.lua_toclose(a0, a1);
            return 0;
        case 129: /* lua_tointegerx */
            return Api.lua_tointegerx(a0, a1, a2);
        case 130: /* lua_tolstring */
            return Api.lua_tolstring(a0, a1, a2);
        case 131: /* lua_tonumberx */
            throw new IllegalStateException("double 返回走 dispatchD");
        case 132: /* lua_topointer */
            return Api.lua_topointer(a0, a1);
        case 133: /* lua_tothread */
            return Api.lua_tothread(a0, a1);
        case 134: /* lua_touserdata */
            return Api.lua_touserdata(a0, a1);
        case 135: /* lua_type */
            return Api.lua_type(a0, a1);
        case 136: /* lua_typename */
            return Api.lua_typename(a0, a1);
        case 137: /* lua_upvalueid */
            return Api.lua_upvalueid(a0, a1, a2);
        case 138: /* lua_upvaluejoin */
            Api.lua_upvaluejoin(a0, a1, a2, a3, a4);
            return 0;
        case 139: /* lua_version */
            throw new IllegalStateException("double 返回走 dispatchD");
        case 140: /* lua_warning */
            Api.lua_warning(a0, a1, a2);
            return 0;
        case 141: /* lua_xmove */
            Api.lua_xmove(a0, a1, a2);
            return 0;
        case 142: /* lua_yieldk */
            return Api.lua_yieldk(a0, a1, a2, a3);
        case 143: /* luaL_openlibs */
            Api.luaL_openlibs(a0);
            return 0;
        case 144: /* luaopen_base */
            return Api.luaopen_base(a0);
        case 145: /* luaopen_coroutine */
            return Api.luaopen_coroutine(a0);
        case 146: /* luaopen_debug */
            return Api.luaopen_debug(a0);
        case 147: /* luaopen_io */
            return Api.luaopen_io(a0);
        case 148: /* luaopen_math */
            return Api.luaopen_math(a0);
        case 149: /* luaopen_os */
            return Api.luaopen_os(a0);
        case 150: /* luaopen_package */
            return Api.luaopen_package(a0);
        case 151: /* luaopen_string */
            return Api.luaopen_string(a0);
        case 152: /* luaopen_table */
            return Api.luaopen_table(a0);
        case 153: /* luaopen_utf8 */
            return Api.luaopen_utf8(a0);
        default:
            throw unimplemented("id=" + id);
        }
    }

    /** 返回 double 的那些（{@code lua_tonumberx} 一类）走这里 */
    static double dispatchD(int id, long a0, long a1, long a2,
            long a3, long a4, long a5, double d0) {
        switch (id) {
        case 11: /* luaL_checknumber */
            return Api.luaL_checknumber(a0, a1);
        case 31: /* luaL_optnumber */
            return Api.luaL_optnumber(a0, a1, d0);
        case 131: /* lua_tonumberx */
            return Api.lua_tonumberx(a0, a1, a2);
        case 139: /* lua_version */
            return Api.lua_version(a0);
        default:
            throw unimplemented("id=" + id);
        }
    }
}
