package com.github.esrrhs.javalua;

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

    /* ==================== debug.debug：那个读 stdin 的循环 ==================== */

    /**
     * {@code ldblib.c: db_debug} 的三条硬规矩，少一条逐字节比对就红。
     * <p>
     * ①<b>每次读之前先往 stderr 打 {@code "lua_debug> "}</b>（无换行）。漏了它，
     * 参考实现的输出里就凭空多十一字节。②<b>是循环不是读一行就返回</b>：一直读到
     * EOF 或某一行正好是 {@code "cont\n"}，中间每行都当 chunk 执行、出错也继续。
     * ③命令的 chunk 名是 {@code =(debug command)}，报错里显示成
     * {@code (debug command):1:}。
     * <p>
     * 顺带记一条<b>流顺序</b>上的事：C 的 {@code print} 走
     * {@code lua_writeline}，它<b>每次都 fflush(stdout)</b>，所以 stdout 与
     * stderr 在合并捕获时是严格按程序顺序交错的 —— 不是「stdout 攒到最后」。
     * 本实现的 {@code L.out} 与 {@code L.err} 指到同一条缓冲上，顺序天然一致。
     * <p>
     * 这条用例必须走 {@code Diff.assertMatchesWithStdin}：只喂
     * {@code /dev/null} 的话循环体、出错分支、{@code cont} 三条路一条也走不到。
     */
    @Test
    void interactiveDebuggerMatchesReference() {
        // 空 stdin：打一个提示符，第一次 fgets 就 EOF，直接返回
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n", "");
        // 只一行 cont：打一个提示符就退出
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n", "cont\n");
        // 完整循环：执行 / 语法错 / 再执行 / cont 退出
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n",
                "print(\"one\")\n1+\nprint(\"two\")\ncont\n");
        // 末尾没有换行也照样执行，然后 EOF 退出
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n", "print(\"nl\")");
        // 运行期错误同样打到 stderr 并继续
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n",
                "error(\"boom\")\nprint(\"ok\")\ncont\n");
        // fgets 的 250 字节缓冲：超长一行会被切成多段，每段各自当 chunk 跑
        Diff.assertMatchesWithStdin("debug.debug()\nprint(\"after\")\n",
                "print(\"\" .. string.rep(\"x\", 300))\ncont\n");
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
     * 钩子的<b>掩码 × count × 线程</b>格子。
     * <p>
     * 已有的 {@link #hookCallReturn} / {@link #hookLine} / {@link #hookCount} /
     * {@link #hookTailCall} 是<b>一个掩码一片</b>，这片是八个掩码的<b>全组合</b>
     * 配上 count 与协程 —— 因为好几个语义只有在格子里才看得出来：
     * <ul>
     *   <li>{@code makemask} 用 {@code strchr} 扫，<b>只认 c / r / l</b>：
     *       {@code "x"} 是空掩码（一个事件都没有），{@code "CC"} 也是空的
     *       （大小写敏感）。单看 {@code "crl"} 永远不会发现。</li>
     *   <li>{@code count} 只有 {@code > 0} 才并进 {@code LUA_MASKCOUNT}：
     *       传 0 与传 -1 都<b>没有</b> count 事件，而掩码 {@code ""} + count 1
     *       是<b>只有</b> count 事件。</li>
     *   <li>钩子是<b>按线程</b>装的：给主线程装的行钩子，协程里一行都不触发；
     *       反过来 {@code debug.sethook(co, ...)} 只管那个协程。</li>
     *   <li>{@code gethook} 的掩码<b>不含</b> count（{@code unmakemask} 只输出
     *       c / r / l），count 是第三个返回值单独给的。</li>
     * </ul>
     * <p>
     * 形状上有一样必须照做：事件<b>收集起来最后统一打印</b>。钩子开着的时候每一个
     * {@code print} 都会自己产生一对 call / return 事件，混进结果里就什么也看不出了。
     * 钩子体本身跨好几行、还调函数 —— 重入（{@code allowhook}）失效的话这里就是
     * 无限递归，用例会直接炸而不是比对失败。
     */
    @Test
    void hookMaskMatrix() {
        Diff.assertFullMatches("""
                local out = {}
                local function say(s) out[#out + 1] = s end

                local function collect(tag, mask, count, body)
                  local ev = {}
                  local on = true
                  local function h(what, line)
                    if not on then return end
                    -- 钩子体跨多行且调函数：重入的话这里就是无限递归
                    local t = what
                    if what == "line" then t = "l" .. tostring(line) end
                    ev[#ev + 1] = t
                  end
                  debug.sethook(h, mask, count or 0)
                  local ok, err = pcall(body)
                  on = false
                  debug.sethook()
                  say(tag .. " ok=" .. tostring(ok) .. " err=" .. tostring(err)
                      .. " [" .. table.concat(ev, " ") .. "]")
                end

                local function b_empty() end
                local function b_two() local x = 1 x = x + 1 return x end
                local function b_call() local r = b_empty() return r end
                local function b_tail() return b_two() end
                local function b_err() error("BOOM") end
                local function b_loop() local s = 0 for i = 1, 3 do s = s + i end return s end
                local function b_pc() return pcall(function() return 1 end) end

                -- 1) 八个掩码
                collect("m0", "", 0, b_two)
                collect("mc", "c", 0, b_two)
                collect("mr", "r", 0, b_two)
                collect("ml", "l", 0, b_two)
                collect("mcr", "cr", 0, b_two)
                collect("mcl", "cl", 0, b_two)
                collect("mrl", "rl", 0, b_two)
                collect("mcrl", "crl", 0, b_two)
                -- 2) 未知字母与大写：makemask 只认 c / r / l
                collect("mx", "x", 0, b_two)
                collect("mcrlx", "crlx", 0, b_two)
                collect("mCC", "CC", 0, b_two)
                -- 3) 尾调用 vs 普通调用
                collect("t1", "crl", 0, b_tail)
                collect("t2", "c", 0, b_tail)
                collect("t3", "crl", 0, b_call)
                -- 4) 出错与 pcall
                collect("e1", "crl", 0, b_err)
                collect("e2", "crl", 0, b_pc)
                -- 5) count：0 与负数都不并进 LUA_MASKCOUNT
                collect("n1", "", 1, b_loop)
                collect("n2", "l", 2, b_loop)
                collect("n3", "", 0, b_loop)
                collect("n4", "", -1, b_loop)
                collect("n5", "crl", 3, b_loop)
                -- 6) gethook：掩码里<b>不含</b> count
                say("g1 " .. tostring(debug.gethook()))
                local h1 = function() end
                debug.sethook(h1, "cl", 7)
                local a, b, c = debug.gethook()
                say("g2 " .. tostring(a == h1) .. " '" .. tostring(b) .. "' " .. tostring(c))
                debug.sethook(h1, "", 0)
                a, b, c = debug.gethook()
                say("g3 " .. tostring(a == h1) .. " '" .. tostring(b) .. "' " .. tostring(c))
                debug.sethook()
                say("g4 " .. tostring(debug.gethook()))
                -- 7) 报错顺序：mask（第 2 参）在 function（第 1 参）之前查
                say("s1 " .. tostring(pcall(debug.sethook, 42, "l")))
                say("s2 " .. tostring(pcall(debug.sethook, print)))
                say("s3 " .. tostring(pcall(debug.sethook, print, "l", "x")))
                say("s4 " .. tostring(pcall(debug.sethook, print, {})))
                say("s5 " .. tostring(pcall(debug.sethook, print, 42)))
                -- 8) 钩子按线程装：主线程的行钩子管不到协程里
                local co = coroutine.create(function()
                  local s = 0
                  for i = 1, 3 do s = s + i coroutine.yield(s) end
                  return s
                end)
                local coev = {}
                local on2 = true
                local function ch(what, line)
                  if not on2 then return end
                  coev[#coev + 1] = (what == "line") and ("l" .. tostring(line)) or what
                end
                debug.sethook(ch, "l", 0)
                say("c1 " .. tostring(coroutine.resume(co)))
                say("c2 [" .. table.concat(coev, " ") .. "]")
                on2 = false
                debug.sethook()
                -- 反过来：装到协程上，只管那个协程
                local co2 = coroutine.create(function() return 1 end)
                debug.sethook(co2, ch, "cl", 0)
                say("c4 " .. tostring(debug.gethook(co2) ~= nil))
                on2 = true
                say("c5 " .. tostring(coroutine.resume(co2)))
                say("c6 [" .. table.concat(coev, " ") .. "]")
                on2 = false
                debug.sethook(co2)
                print(table.concat(out, "\\n"))
                """);
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

    /**
     * {@code debug.getinfo} 的 <b>name / namewhat</b> 网格（77 行）。
     * <p>
     * {@code name} 不是存在函数里的，而是<b>从调用点反推</b>出来的
     * （{@code funcnamefromcall} → {@code funcnamefromcode} → {@code getobjname}）：
     * 看调用者那条指令是 {@code OP_GETFIELD} / {@code OP_GETTABUP} /
     * {@code OP_GETUPVAL} / {@code OP_MOVE} / {@code OP_SELF} 里的哪一种，
     * 再从常量表或寄存器里把名字抠出来。所以同一个函数在不同语法位置上被调用，
     * {@code name} 与 {@code namewhat} 都不同 —— 只能铺成矩阵逐个比对。
     * <p>
     * 网格覆盖九种 {@code namewhat}：{@code global} / {@code local} /
     * {@code field} / {@code method} / {@code upvalue} / {@code metamethod} /
     * {@code for iterator} / {@code ""}（推不出来）/ 以及取不到时的 {@code nil}。
     * 几个不看参考实现想不到的形状：
     * <ul>
     *   <li><b>{@code t[1]()}</b> 的名字是 {@code "integer index"}，
     *       <b>不是</b> {@code "?"}。整数键在 {@code getobjname} 里是单独一支；
     *       只有浮点键与布尔键才落到 {@code "?"}。</li>
     *   <li><b>{@code "?"} 的 {@code namewhat} 仍然是 {@code "field"}</b> ——
     *       名字推不出来，调用方式推得出来，两者不挂钩。</li>
     *   <li><b>metamethod</b> 那一路的 {@code name} 是<b>去掉 {@code __}</b> 的
     *       元方法名（{@code index} / {@code newindex} / {@code add} / {@code len} /
     *       {@code close} …），而 {@code __call} 走的是普通调用，给的是
     *       {@code name="a1", namewhat="local"}。</li>
     *   <li><b>{@code __call}</b> 那一路给 {@code name="t", namewhat="local"}，
     *       不是 {@code "method"}。</li>
     *   <li>取不到名字时 {@code name} 是 {@code nil} 而 {@code namewhat} 是
     *       <b>空串</b>，不是 {@code nil}。</li>
     * </ul>
     * 这一片第一跑就逐字节一致（除脚本路径），所以敏感性是量过的：把
     * {@code "integer index"} 改成 {@code "?"} 红 5 行；把 {@code namewhat} 的
     * {@code "field"} 改大小写红 1 行。
     */
    @Test
    void functionNameShapes() {
        Diff.assertMatches("""
        
        -- debug.getinfo 的 name / namewhat 网格：让同一个函数在各种语法位置上被调用
        local function p()
            local i = debug.getinfo(1, "n")
            print(tostring(i.name), "[" .. tostring(i.namewhat) .. "]")
        end
        local function q()
            local i = debug.getinfo(1, "nS")
            print(tostring(i.name), "[" .. tostring(i.namewhat) .. "]",
                  i.what, i.short_src, i.linedefined, i.lastlinedefined)
        end
        
        -- 全局
        g = p
        g()
        -- 局部
        local l = p
        l()
        do local l2 = p l2() end
        -- 表字段（字符串键）
        local t = {}
        t.x = p
        t.x()
        t["y z"] = p
        t["y z"]()
        -- 表字段（非字符串键）：C 里 getobjname 给 "?"
        t[1] = p
        t[1]()
        t[1.5] = p
        t[1.5]()
        t[true] = p
        t[true]()
        -- 方法
        local obj = {}
        function obj:m() return p() end
        obj:m()
        local mt = { __index = { mm = p } }
        setmetatable(obj, mt)
        obj:mm()
        -- upvalue
        do
            local up = p
            local function inner() return up() end
            inner()
        end
        -- 直接调用、作为参数、返回值
        p()
        local function callit(f) return f() end
        callit(p)
        local function retit() return p end
        retit()()
        -- 尾调用
        local function tail() return p() end
        tail()
        -- 主 chunk / getinfo 的其它层
        do
            local i = debug.getinfo(1, "n")
            print(tostring(i.name), "[" .. tostring(i.namewhat) .. "]")
        end
        print(tostring(debug.getinfo(p).name), "[" .. tostring(debug.getinfo(p).namewhat) .. "]")
        print(tostring(debug.getinfo(print).name), "[" .. tostring(debug.getinfo(print).namewhat) .. "]")
        print(debug.getinfo(p).what)
        print(debug.getinfo(p).source, debug.getinfo(p).short_src)
        print(debug.getinfo(p).nups, debug.getinfo(p).nparams, debug.getinfo(p).isvararg)
        print(debug.getinfo(1).currentline)
        
        -- 匿名函数的名字
        local anon = function() end
        print(tostring(debug.getinfo(anon).name), "[" .. tostring(debug.getinfo(anon).namewhat) .. "]")
        local mt2 = {}
        mt2.__call = function(self) return debug.getinfo(1, "n") end
        setmetatable(t, mt2)
        local r = t()
        print(tostring(r.name), "[" .. tostring(r.namewhat) .. "]")
        
        -- getlocal / getupvalue 的名字
        local function upfn()
            local a, b, c = 1, 2, 3
            return function()
                local names = {}
                for i = 1, 3 do
                    local n = debug.getupvalue(debug.getinfo(1, "f").func, i)
                    names[#names + 1] = tostring(n)
                end
                print(table.concat(names, ","))
                local ln = {}
                for i = 1, 3 do
                    ln[#ln + 1] = tostring(debug.getlocal(1, i))
                end
                print(table.concat(ln, ","))
            end
        end
        upfn()()
        
        -- 协程里的名字
        local co = coroutine.create(function()
            local i = debug.getinfo(1, "n")
            print(tostring(i.name), "[" .. tostring(i.namewhat) .. "]")
            coroutine.yield()
        end)
        coroutine.resume(co)
        
        -- namewhat 在 C 函数上
        print(tostring(debug.getinfo(2, "n").name), "[" .. tostring(debug.getinfo(2, "n").namewhat) .. "]")
        
        -- 全局表上的字段/方法：走 OP_GETTABUP + 常量键，也就是 kName 那条路
        G = {}
        G.f = p
        G.f()
        G["g h"] = p
        G["g h"]()
        G[1] = p
        G[1]()
        G[1.5] = p
        G[1.5]()
        G[true] = p
        G[true]()
        G[0] = p
        G[0]()
        G[-3] = p
        G[-3]()
        G[2^53] = p
        G[2^53]()
        function G:m() return p() end
        G:m()
        -- upvalue 上的表：OP_GETTABUP 但基址是 upvalue 不是 _ENV
        do
            local u = {}
            u.f = p
            u.m = function(self) return p() end
            local function inner()
                u.f()
                u:m()
                u[1]()
                u[1.5]()
            end
            u[1] = p
            u[1.5] = p
            inner()
        end
        -- 通过 metamethod 与迭代器调用
        local mm = setmetatable({}, { __index = function() return p end })
        mm.anything()
        local co2 = coroutine.wrap(p)
        co2()
        for kv in p do break end
        print(pcall(p))
        print(select(1, p()))
        -- 函数在 C 边界上：debug.getinfo 取 C 函数与 Lua 函数的名
        print(tostring(debug.getinfo(math.max, "n").name),
              "[" .. tostring(debug.getinfo(math.max, "n").namewhat) .. "]")
        print(debug.getinfo(math.max).what, debug.getinfo(math.max).nups)
        print(pcall(debug.getupvalue, math.max, 1))
        print(pcall(debug.getlocal, math.max, 1))
        -- upvalue / local 名字的边界
        local function f2(x, y, ...)
            local z = 1
            return function()
                local a = {}
                for i = 1, 4 do
                    local ok, n = pcall(debug.getupvalue, debug.getinfo(1, "f").func, i)
                    a[#a + 1] = ok and tostring(n) or "ERR"
                end
                print(table.concat(a, ","))
                local b = {}
                for i = 1, 4 do
                    local ok, n = pcall(debug.getlocal, 1, i)
                    b[#b + 1] = ok and tostring(n) or "ERR"
                end
                print(table.concat(b, ","))
            end
        end
        f2(1, 2, 3)()
        -- 越界的 upvalue 下标：返回空（tostring 会报 value expected），所以用 pcall 兜
        print(pcall(debug.getupvalue, p, 0))
        print(pcall(debug.getupvalue, p, 99))
        print(pcall(debug.getupvalue, p, -1))
        print(pcall(debug.getlocal, 1, 0))
        print(pcall(debug.getlocal, 1, 99))
        print(pcall(debug.getlocal, 99, 1))
        print(pcall(debug.getupvalue, print, 1))
        
        
        -- namewhat 的另外三种：for iterator / hook / metamethod
        local function pm()
            local i = debug.getinfo(1, "n")
            print(tostring(i.name), "[" .. tostring(i.namewhat) .. "]")
            return 1
        end
        -- metamethod：name 是去掉 "__" 的元方法名
        local mmt = {
            __index = pm, __newindex = pm, __add = pm, __concat = pm, __len = pm,
            __eq = pm, __lt = pm, __le = pm, __unm = pm, __bnot = pm, __call = pm,
        }
        local a1 = setmetatable({}, mmt)
        local a2 = setmetatable({}, mmt)
        local ok1 = a1.x
        a1.x = 1
        local ok3 = a1 + 1
        local ok4 = a1 .. "z"
        local ok5 = #a1
        local ok6 = (a1 == a2)
        local ok7 = (a1 < a2)
        local ok8 = (a1 <= a2)
        local ok9 = -a1
        local ok10 = ~a1
        a1()
        -- 全局表访问触发 __index：走 OP_GETTABUP 那条路
        GM = setmetatable({}, mmt)
        local ok11 = GM.x
        -- __close
        do
            local cv <close> = setmetatable({}, { __close = pm })
        end
        -- for iterator
        for k in pm do break end
        -- hook：在钩子内部调用的函数
        do
            local once = false
            debug.sethook(function()
                if not once then once = true pm() end
            end, "c")
            local function zz() return 1 end
            zz()
            debug.sethook()
        end
        
        print("done")
                """);
    }
}
