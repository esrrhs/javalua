package com.github.esrrhs.fakescript.lua;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code debug} 库的运行期差分 —— 对应 {@code ldblib.c} + {@link DebugLib}。
 * <p>
 * 这批用例全部走 {@link Diff#assertFullMatches}（<b>不</b>剥回溯）：
 * {@code debug.traceback} 的返回值本身就是回溯，走默认的剥回溯规则会把
 * 用例整段抹成空串，然后因「两边都空」而假绿。
 * <p>
 * 三个地方不可比，用例里主动避开：
 * <ul>
 *   <li><b>地址</b>：{@code debug.upvalueid} 返回的是指针，只能比
 *       {@code type} 与「两个 id 是否相等」，不能直接 {@code print}；</li>
 *   <li><b>activelines 的具体内容</b>：依赖每条指令的行号，本实现的行号表
 *       与上游一致所以可写，但一旦改代码生成器就得重校。</li>
 * </ul>
 */
final class DebugTest {

    @BeforeAll
    static void requireRef() {
        assumeTrue(ReferenceLua.find() != null, "参考实现二进制不存在，跳过一致性测试");
    }

    /* ==================== getinfo：层号 ==================== */

    /**
     * 层号从 0 开始，0 是 {@code getinfo} 自己的 C 帧——这一点最容易搞错。
     * <p>
     * 参考实测：{@code what=C, currentline=-1, name=getinfo}。名字是
     * {@code funcnamefromcall} 在<b>上一帧</b>（{@code g}）的调用指令上查出来的，
     * 所以问的是「{@code g} 当时在调谁」，不是「这一帧叫什么」。
     */
    @Test
    void levels() {
        Diff.assertFullMatches(""
                + "local function g()\n"
                + "  for i = 0, 6 do\n"
                + "    local t = debug.getinfo(i)\n"
                + "    print(i, t and (t.what .. \" \" .. tostring(t.currentline)\n"
                + "      .. \" \" .. tostring(t.name) .. \" \" .. t.namewhat))\n"
                + "  end\n"
                + "end\n"
                + "g()\n");
    }

    /** {@code what} / {@code source} / {@code short_src} / 行号范围 */
    @Test
    void sourceAndWhat() {
        Diff.assertFullMatches(""
                + "local function outer()\n"
                + "  local function inner()\n"
                + "    local i = debug.getinfo(1)\n"
                + "    print(i.source, i.short_src, i.linedefined, i.lastlinedefined,\n"
                + "          i.what, i.currentline, i.nups, i.nparams, i.isvararg,\n"
                + "          i.name, i.namewhat, i.istailcall)\n"
                + "    local j = debug.getinfo(2)\n"
                + "    print(j.what, j.currentline, j.name, j.namewhat)\n"
                + "    local k = debug.getinfo(3)\n"
                + "    print(k.what, k.currentline, tostring(k.name), k.namewhat)\n"
                + "  end\n"
                + "  inner()\n"
                + "end\n"
                + "outer()\n");
    }

    /** 以函数为参数（走 {@code >} 分支）：此时没有活动帧，{@code currentline} 是 -1 */
    @Test
    void infoFromFunction() {
        Diff.assertFullMatches(""
                + "local function h(a, b) return a + b end\n"
                + "print(debug.getinfo(h).what, debug.getinfo(h).currentline,\n"
                + "      debug.getinfo(h).nparams, debug.getinfo(h).isvararg,\n"
                + "      debug.getinfo(h).nups)\n"
                + "print(debug.getinfo(print).what, debug.getinfo(print).currentline,\n"
                + "      debug.getinfo(print).nups)\n"
                + "print(debug.getinfo(print).source, debug.getinfo(print).short_src,\n"
                + "      debug.getinfo(print).linedefined)\n"
                + "local i = debug.getinfo(h, \"Slnu\")\n"
                + "print(i.what, i.short_src, i.linedefined, i.lastlinedefined,\n"
                + "      i.currentline, i.nups, i.nparams, i.isvararg)\n");
    }

    /** {@code 'f'} 与 {@code 'L'}：两个要往栈上推东西的选项 */
    @Test
    void infoFuncAndLines() {
        Diff.assertFullMatches(""
                + "local function h(a) return a end\n"
                + "local i = debug.getinfo(h, \"f\")\n"
                + "print(i.func == h)\n"
                + "local j = debug.getinfo(1, \"f\")\n"
                + "print(type(j.func))\n"
                + "local k = debug.getinfo(h, \"L\")\n"
                + "print(type(k.activelines))\n"
                + "local n = 0\n"
                + "for line in pairs(k.activelines) do n = n + 1 end\n"
                + "print(n > 0)\n"
                + "print(debug.getinfo(print, \"L\").activelines)\n");
    }

    /** 选项字符串的边界：空串合法、非法字符报错、{@code >} 不能由脚本给 */
    @Test
    void infoOptions() {
        Diff.assertFullMatches(""
                + "print(type(debug.getinfo(1, \"\")))\n"
                + "print(debug.getinfo(1, \"\").currentline)\n"
                + "print(pcall(debug.getinfo, 1, \"z\"))\n"
                + "print(pcall(debug.getinfo, 1, \">f\"))\n"
                + "print(pcall(debug.getinfo, 99))\n"
                + "print(pcall(debug.getinfo, -1))\n"
                + "print(pcall(debug.getinfo))\n"
                + "print(pcall(debug.getinfo, 1, 5))\n");
    }

    /* ==================== getlocal / setlocal ==================== */

    @Test
    void locals() {
        Diff.assertFullMatches(""
                + "local t = {}\n"
                + "function t.method(a, b, ...)\n"
                + "  local p, q = 1, 2\n"
                + "  local i = debug.getinfo(1)\n"
                + "  print(\"n1\", i.name, i.namewhat, i.nparams, i.isvararg, i.nups)\n"
                + "  print(\"n2\", debug.getinfo(2).name, debug.getinfo(2).namewhat)\n"
                + "  print(\"loc\", debug.getlocal(1, 1))\n"
                + "  print(\"loc\", debug.getlocal(1, 2))\n"
                + "  print(\"loc\", debug.getlocal(1, 3))\n"
                + "  print(\"loc\", debug.getlocal(1, 4))\n"
                + "  print(\"loc\", debug.getlocal(1, 5))\n"
                + "  print(\"loc\", debug.getlocal(1, 6))\n"
                + "  print(\"loc0\", debug.getlocal(1, 0))\n"
                + "  print(\"varg\", debug.getlocal(1, -1))\n"
                + "  print(\"varg\", debug.getlocal(1, -2))\n"
                + "  print(\"varg\", debug.getlocal(1, -3))\n"
                + "  print(\"set\", debug.setlocal(1, 1, 99), p)\n"
                + "  print(\"set\", debug.setlocal(1, 99, 1))\n"
                + "end\n"
                + "t.method(10, 20, 30, 40)\n");
    }

    /**
     * 以函数为参数的 {@code getlocal}：只看入口处还活着的参数，
     * 而且<b>不返回值</b>（{@code db_getlocal} 在这里 {@code return 1}）。
     */
    @Test
    void localFromFunction() {
        Diff.assertFullMatches(""
                + "local function h(a, b, c) return a end\n"
                + "print(debug.getlocal(h, 1))\n"
                + "print(debug.getlocal(h, 2))\n"
                + "print(debug.getlocal(h, 3))\n"
                + "print(debug.getlocal(h, 4))\n"
                + "print(debug.getlocal(print, 1))\n"
                + "print(pcall(debug.getlocal, 1))\n"
                + "print(pcall(debug.getlocal, 99, 1))\n");
    }

    /* ==================== upvalue 四件套 ==================== */

    @Test
    void upvalues() {
        Diff.assertFullMatches(""
                + "local up = 1\n"
                + "local function f() return up end\n"
                + "local function g() up = up + 1 end\n"
                + "print(debug.getupvalue(f, 1))\n"
                + "print(debug.getupvalue(f, 2))\n"
                + "print(debug.getupvalue(print, 1))\n"
                + "print(debug.setupvalue(f, 1, 42), f())\n"
                + "print(debug.setupvalue(f, 9, 1))\n"
                + "print(debug.setupvalue(print, 1, 1))\n"
                + "print(type(debug.upvalueid(f, 1)))\n"
                + "print(debug.upvalueid(f, 1) == debug.upvalueid(g, 1))\n"
                + "print(debug.upvalueid(f, 1) == debug.upvalueid(f, 1))\n"
                + "print(debug.upvalueid(print, 1))\n"
                + "print(debug.upvalueid(f, 9))\n");
    }

    /** {@code _ENV} 是主 chunk 的第 1 个 upvalue，{@code load} 出来的也是 */
    @Test
    void envUpvalue() {
        Diff.assertFullMatches(""
                + "local u = load(\"return 1\")\n"
                + "print(debug.getupvalue(u, 1))\n"
                + "local t = {}\n"
                + "print(debug.setupvalue(u, 1, t))\n"
                + "print(u())\n"
                + "print(debug.getupvalue(u, 1))\n");
    }

    /**
     * {@code upvaluejoin} 换的是「upvalue 指针」而不是值——换完两边共享同一个。
     * <p>
     * 参差：{@code upvaluejoin(print, 1, k, 1)} 报的是 <b>#2</b> 越界
     * （{@code checkupval} 先跑），不是 #1 类型错。
     */
    @Test
    void upvalueJoin() {
        Diff.assertFullMatches(""
                + "local a, b = 10, 20\n"
                + "local function h() return a end\n"
                + "local function k() return b end\n"
                + "print(h(), k())\n"
                + "print(debug.upvalueid(h, 1) == debug.upvalueid(k, 1))\n"
                + "debug.upvaluejoin(h, 1, k, 1)\n"
                + "print(h(), k())\n"
                + "print(debug.upvalueid(h, 1) == debug.upvalueid(k, 1))\n"
                + "print(pcall(debug.upvaluejoin, h, 1, k, 1))\n"
                + "print(pcall(debug.upvaluejoin, print, 1, k, 1))\n"
                + "print(pcall(debug.upvaluejoin, h, 1, print, 1))\n"
                + "print(pcall(debug.upvaluejoin, h, 9, k, 1))\n");
    }

    /* ==================== registry / metatable / userdata ==================== */

    @Test
    void registryAndMetatable() {
        Diff.assertFullMatches(""
                + "print(type(debug.getregistry()))\n"
                + "print(debug.getregistry() == debug.getregistry())\n"
                + "print(debug.getmetatable(1))\n"
                + "print(type(debug.getmetatable(\"x\")))\n"
                + "print(debug.getmetatable({}))\n"
                + "print(debug.setmetatable(1, {__index = function() return \"one\" end}))\n"
                + "print((1).foo)\n"
                + "print(debug.setmetatable(1, nil))\n"
                + "print((1).foo)\n"
                + "print(debug.setmetatable({}, {}))\n"
                + "print(debug.getmetatable(debug.setmetatable({}, {__index = {x = 7}})).__index.x)\n"
                + "print(pcall(debug.setmetatable, 1, 5))\n"
                + "print(pcall(debug.setmetatable))\n"
                + "print(pcall(debug.getmetatable))\n");
    }

    @Test
    void userValues() {
        Diff.assertFullMatches(""
                + "print(debug.getuservalue(1))\n"
                + "print(debug.getuservalue({}, 3))\n"
                + "print(debug.getuservalue(io.stdout))\n"
                + "print(pcall(debug.setuservalue, 1, 5))\n"
                + "print(pcall(debug.setuservalue, {}, 5))\n"
                + "print(pcall(debug.getuservalue, 1, \"x\"))\n");
    }

    /* ==================== traceback ==================== */

    @Test
    void traceback() {
        Diff.assertFullMatches(""
                + "local function a() error(\"boom\") end\n"
                + "local function b() a() end\n"
                + "local function c() b() end\n"
                + "print(xpcall(c, debug.traceback))\n");
    }

    @Test
    void tracebackMessage() {
        Diff.assertFullMatches(""
                + "print(debug.traceback(\"msg\"))\n"
                + "print(debug.traceback(42))\n"
                + "print(debug.traceback({}))\n"
                + "print(debug.traceback(nil))\n");
    }

    /** 协程参数：此时默认 level 是 0，不是 1 */
    @Test
    void tracebackCoroutine() {
        Diff.assertFullMatches(""
                + "local co = coroutine.create(function() error(\"inner\") end)\n"
                + "print(coroutine.resume(co))\n"
                + "print(debug.traceback(co))\n"
                + "print(debug.traceback(co, \"pre\"))\n"
                + "print(debug.traceback(co, \"pre\", 1))\n");
    }

    /** 深栈：{@code LEVELS1 + LEVELS2} 之外的层会被折成 {@code (skipping N levels)} */
    @Test
    void tracebackDeep() {
        Diff.assertFullMatches(""
                + "local function f(n)\n"
                + "  if n == 0 then print(debug.traceback()) else f(n - 1) end\n"
                + "end\n"
                + "f(30)\n");
    }

    /**
     * 消息处理函数的边界。
     * <p>
     * 处理函数<b>自己出错</b>时，C 版会递归进 {@code luaG_errormsg} 直到 C 栈
     * 溢出，被兜成 {@code error in error handling}——不是 {@code 'b'}，
     * 也不是原来的 {@code 'a'}。
     */
    @Test
    void handlerErrors() {
        Diff.assertFullMatches(""
                + "print(xpcall(function() error('a') end,\n"
                + "             function(e) error('b') end))\n"
                + "print(xpcall(function() error('a') end,\n"
                + "             function(e) return e .. '!' end))\n"
                + "print(pcall(function() error(setmetatable({},\n"
                + "  {__tostring = function() return 'TS' end})) end))\n"
                + "print(error(setmetatable({}, {__tostring = function() return 'TS' end})))\n");
    }

    /* ==================== 杂项 ==================== */

    @Test
    void cStackLimit() {
        Diff.assertFullMatches(""
                + "print(debug.setcstacklimit(10))\n"
                + "print(debug.setcstacklimit(1000000))\n"
                + "print(pcall(debug.setcstacklimit))\n"
                + "print(pcall(debug.setcstacklimit, \"x\"))\n");
    }

    /**
     * 键的集合：本轮搬的 14 个。
     * <p>
     * 脚本里<b>显式滤掉</b> {@code sethook} / {@code gethook}：那两个要等解释器
     * 主循环里插好 {@code luaD_hook} 的调用点才能搬，不滤掉这条用例会一直红在
     * 一件已知未做、且不是本轮目标的事情上——而一直红的门禁等于没有门禁。
     */
    @Test
    void keys() {
        Diff.assertFullMatches(""
                + "local ks = {}\n"
                + "for k in pairs(debug) do\n"
                + "  ks[#ks + 1] = k\n"
                + "end\n"
                + "table.sort(ks)\n"
                + "print(#ks)\n"
                + "print(table.concat(ks, \",\"))\n");
    }

    /** 错误对象不是字符串时，{@code traceback} 原样返回 */
    @Test
    void tracebackNonString() {
        Diff.assertFullMatches(""
                + "print(debug.traceback(true))\n"
                + "print(debug.traceback(print))\n"
                + "print(pcall(function() return debug.traceback(setmetatable({}, {})) end))\n");
    }

    /* ==================== 钩子 ==================== */

    /**
     * 调用/返回钩子。
     * <p>
     * 第一个事件是 {@code return} 而不是 {@code call}——那是从
     * {@code debug.sethook} 这个 <b>C 函数</b>返回时报的。它证明调用钩子必须挂在
     * {@code precallC} 里「真正调 C 函数」<b>之前</b>、返回钩子挂在
     * {@code poscall} 里：挂在别处就少了这一条。
     * <p>
     * 末尾那个孤零零的 {@code call} 是 {@code debug.sethook()}（关钩子的那次）
     * <b>自己</b>的调用事件：钩子在它建帧时就报了，它才在里头把钩子关掉。
     */
    @Test
    void hookCallReturn() {
        Diff.assertFullMatches(""
                + "local n = 0\n"
                + "local function f(a) return a + 1 end\n"
                + "debug.sethook(function(ev, ln)\n"
                + "  n = n + 1\n"
                + "  print(n, ev, ln)\n"
                + "end, \"cr\")\n"
                + "local r = f(1)\n"
                + "local g = function() return f(2) end\n"
                + "local r2 = g()\n"
                + "debug.sethook()\n"
                + "print(\"done\", r, r2)\n");
    }

    /** 行钩子：循环里同一行会<b>重复</b>报（往回跳也算换过行） */
    @Test
    void hookLine() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev, ln) print(ev, ln) end, \"l\")\n"
                + "local s = 0\n"
                + "for i = 1, 2 do s = s + i end\n"
                + "debug.sethook()\n"
                + "print(s)\n");
    }

    /** 计数钩子：周期由第 3 个参数给 */
    @Test
    void hookCount() {
        Diff.assertFullMatches(""
                + "local n = 0\n"
                + "debug.sethook(function(ev, ln) n = n + 1 end, \"\", 5)\n"
                + "local s = 0\n"
                + "for i = 1, 20 do s = s + i end\n"
                + "debug.sethook()\n"
                + "print(\"count\", n, s)\n");
    }

    /** 尾调用报 {@code tail call}，不是 {@code call} */
    @Test
    void hookTailCall() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev) print(ev) end, \"c\")\n"
                + "local function a() return b() end\n"
                + "function b() return 3 end\n"
                + "print(a())\n"
                + "debug.sethook()\n");
    }

    /**
     * {@code gethook} 的三个返回值。
     * <p>
     * 没有钩子时它<b>只返回一个</b> {@code nil}——写成返回三个会让
     * {@code print(debug.gethook())} 多打两个空字段。
     */
    @Test
    void hookGetSet() {
        Diff.assertFullMatches(""
                + "print(debug.gethook())\n"
                + "debug.sethook(function() end, \"crl\", 7)\n"
                + "print(debug.gethook())\n"
                + "local h, m, c = debug.gethook()\n"
                + "print(type(h), m, c)\n"
                + "debug.sethook()\n"
                + "print(debug.gethook())\n"
                + "debug.sethook(function() end, \"l\")\n"
                + "print(debug.gethook())\n"
                + "debug.sethook(function() end, \"\", 3)\n"
                + "print(debug.gethook())\n"
                + "debug.sethook()\n");
    }

    /**
     * 钩子函数里出错。
     * <p>
     * 这个错误<b>抓不到</b>：调用钩子是在 {@code pcall} 的 C 帧建起来<b>之前</b>
     * 报的，{@code pcall} 的恢复点还没生效。同时回溯里那一层要印成
     * {@code in hook '?'}——这要求 {@code CIST_HOOKED} 在钩子抛错时<b>不清</b>，
     * 因为 C 版的 {@code luaD_throw} 会把 {@code luaD_hook} 的收尾整段跳过去。
     */
    @Test
    void hookError() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev) error(\"in hook \" .. ev) end, \"c\")\n"
                + "local ok, e = pcall(function() local t = {} return t.x end)\n"
                + "debug.sethook()\n"
                + "print(\"ok\", ok, e)\n");
    }

    /**
     * 钩子函数里 yield 一律报错，主线程上是「不在协程里」。
     * <p>
     * 这条不是「没实现 yield-in-hook」的托辞，而是<b>参考实现的行为</b>：
     * {@code hookf} 用 {@code lua_call} 调钩子函数，走
     * {@code luaD_callnoyield}，「不可 yield」层数加一，于是钩子永远不可 yield。
     */
    @Test
    void hookYieldFails() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev) coroutine.yield() end, \"l\")\n"
                + "print(\"A\")\n"
                + "print(pcall(function() print(\"B\") print(\"C\") end))\n"
                + "debug.sethook()\n"
                + "print(\"D\")\n");
    }

    /** 协程里 yield 报的是另一条：跨 C 调用边界 */
    @Test
    void hookYieldInCoroutineFails() {
        Diff.assertFullMatches(""
                + "local co = coroutine.create(function()\n"
                + "  print(\"in co 1\")\n"
                + "  print(\"in co 2\")\n"
                + "  return \"end\"\n"
                + "end)\n"
                + "debug.sethook(co, function(ev, ln)\n"
                + "  print(\"H\", ev, ln)\n"
                + "  coroutine.yield()\n"
                + "end, \"l\")\n"
                + "print(coroutine.resume(co))\n"
                + "print(coroutine.resume(co))\n"
                + "print(coroutine.resume(co))\n");
    }

    /** 给协程单独设钩子：只跟踪那个协程，且能随时关掉 */
    @Test
    void hookOnCoroutine() {
        Diff.assertFullMatches(""
                + "local co = coroutine.create(function()\n"
                + "  local s = 0\n"
                + "  for i = 1, 3 do s = s + i end\n"
                + "  coroutine.yield(s)\n"
                + "  s = s * 2\n"
                + "  return s\n"
                + "end)\n"
                + "debug.sethook(co, function(ev, ln) print(\"H\", ev, ln) end, \"l\")\n"
                + "print(coroutine.resume(co))\n"
                + "print(debug.gethook(co))\n"
                + "debug.sethook(co)\n"
                + "print(coroutine.resume(co))\n"
                + "print(coroutine.resume(co))\n");
    }

    /**
     * 返回钩子的传递信息：{@code debug.getinfo(2, "r")}。
     * <p>
     * 层号 2 是「引发事件的那一帧」——钩子自己是 1。{@code ftransfer / ntransfer}
     * 只在<b>返回钩子期间</b>非零（{@code CIST_TRAN}），普通代码里一律 0/0。
     */
    @Test
    void hookTransferInfo() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev, ln)\n"
                + "  local t = debug.getinfo(2, \"rSn\")\n"
                + "  print(ev, ln, t.ftransfer, t.ntransfer, t.what, t.name)\n"
                + "end, \"cr\")\n"
                + "local function f() return 1, 2, 3 end\n"
                + "local a, b = f()\n"
                + "debug.sethook()\n"
                + "print(debug.getinfo(1, \"r\").ntransfer)\n");
    }

    /** 嵌套调用：调用与返回必须成对，顺序按「先进后出」 */
    @Test
    void hookNested() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev, ln) print(ev, ln) end, \"cr\")\n"
                + "local function inner() return 7 end\n"
                + "local function outer() return inner() + 1 end\n"
                + "print(outer())\n"
                + "debug.sethook()\n");
    }

    /**
     * 变参函数：调用钩子推迟到 {@code OP_VARARGPREP} 之后才报。
     * <p>
     * 那一步是 {@code luaG_tracecall} 返回 0（关 trap）再由
     * {@code OP_VARARGPREP} 后的 {@code updatetrap} 读回 1 实现的；
     * 少了这个来回，变参函数的调用事件就会凭空多一个或少一个。
     */
    @Test
    void hookVararg() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev, ln) print(ev, ln) end, \"cr\")\n"
                + "local function v(...)\n"
                + "  local n = select('#', ...)\n"
                + "  return n\n"
                + "end\n"
                + "print(v(1, 2, 3))\n"
                + "local function w(a, ...) return a end\n"
                + "print(w(9))\n"
                + "debug.sethook()\n");
    }

    /** 行钩子 + 调用钩子同时开：{@code oldpc} 的更新必须跟得上 */
    @Test
    void hookLineAndCall() {
        Diff.assertFullMatches(""
                + "debug.sethook(function(ev, ln) print(ev, ln) end, \"cl\")\n"
                + "local function f(a)\n"
                + "  local x = a\n"
                + "  return x\n"
                + "end\n"
                + "print(f(1))\n"
                + "debug.sethook()\n");
    }

    /** 钩子函数里再改钩子：{@code allowhook} 为 0 时嵌套的钩子不该触发 */
    @Test
    void hookInsideHook() {
        Diff.assertFullMatches(""
                + "local n = 0\n"
                + "debug.sethook(function(ev)\n"
                + "  if n == 0 then\n"
                + "    n = 1\n"
                + "    debug.sethook(function(e2) print(\"inner\", e2) end, \"cr\")\n"
                + "    local t = {} -- 这几行的内钩子不该来\n"
                + "    t.x = 1\n"
                + "    debug.sethook()\n"
                + "  end\n"
                + "  print(\"outer\", ev)\n"
                + "end, \"cr\")\n"
                + "local function q() return 1 end\n"
                + "q()\n"
                + "debug.sethook(function(ev) print(\"after\", ev) end, \"c\")\n"
                + "q()\n"
                + "debug.sethook()\n");
    }

    /** 执行到一半开/关钩子：{@code settraps} 与 {@code OP_CALL} 后的 updatetrap */
    @Test
    void hookToggleMidRun() {
        Diff.assertFullMatches(""
                + "local function f() return 1 end\n"
                + "f()\n"
                + "debug.sethook(function(ev, ln) print(\"on\", ev, ln) end, \"l\")\n"
                + "local a = 1\n"
                + "local b = 2\n"
                + "debug.sethook()\n"
                + "local c = 3\n"
                + "debug.sethook(function(ev, ln) print(\"again\", ev, ln) end, \"cl\")\n"
                + "f()\n"
                + "debug.sethook()\n"
                + "print(a, b, c)\n");
    }

    /**
     * {@code sethook} 的参数检查顺序。
     * <p>
     * 掩码（第 2 个参数）先于类型（第 1 个参数）检查，所以
     * {@code debug.sethook(1, 2)} 报的是「#2 不是字符串」。
     */
    @Test
    void hookArgErrors() {
        Diff.assertFullMatches(""
                + "print(pcall(debug.sethook))\n"
                + "print(pcall(debug.sethook, 1, 2))\n"
                + "print(pcall(debug.sethook, print))\n"
                + "print(pcall(debug.sethook, print, \"x\"))\n"
                + "print(pcall(debug.sethook, print, \"crl\", \"z\"))\n"
                + "print(pcall(debug.gethook, 1))\n");
    }
}
