package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * io 库的差分用例 —— 阶段 7。
 * <p>
 * 选例的原则是「只比对平台无关的那部分」：
 * <ul>
 *   <li><b>不比对地址</b>。{@code print(io.stdout)} 给的是
 *       {@code file (0x...)}，两边不可能相同，差分比对会把它抹成
 *       {@code 0xADDR}——所以这种用例只验证「形状」，验证不了数值。</li>
 *   <li><b>不比对 {@code io.popen} 拿 errno 的路径</b>：Java 起进程失败的
 *       errno 与 C 的 {@code popen} 不同。</li>
 *   <li><b>临时文件用固定路径</b>：两边跑在同一台机器上，同一个路径对两边
 *       都可见；每个用例开头先 {@code os.remove} 掉，避免相互干扰。</li>
 * </ul>
 * <p>
 * 真正盯住的是这几处容易翻车的地方：
 * <ul>
 *   <li><b>{@code io.write} 的返回值是句柄</b>（不是 true），
 *       {@code io.read} 同理；</li>
 *   <li><b>{@code luaL_fileresult} 的三元组</b>：{@code io.open} 失败时给
 *       {@code nil, "<路径>: No such file or directory", 2}；</li>
 *   <li><b>{@code f:close()} 对标准文件</b>给
 *       {@code nil, "cannot close standard file"} 两个值，不是报错；</li>
 *   <li><b>已关闭的句柄</b>再用报 {@code attempt to use a closed file}；</li>
 *   <li><b>{@code io.lines(文件)}</b> 返回四个值（第四个是待关闭变量），
 *       迭代结束会把文件关掉，之后 {@code io.type} 应是 {@code closed file}；</li>
 *   <li><b>{@code f:read("n")}</b> 的「读一个合法数字前缀」行为：
 *       {@code "3x"} 读成 {@code 3} 且把 {@code x} 留在流里。</li>
 * </ul>
 */
class IoLibTest {

    @TestFactory
    List<DynamicTest> ioScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "type", TYPES);
        add(tests, "open", OPEN);
        add(tests, "read", READ);
        add(tests, "write", WRITE);
        add(tests, "lines", LINES);
        add(tests, "seek", SEEK);
        add(tests, "err", ERRORS);
        add(tests, "zero", ZERO);
        add(tests, "proc", PROC);
        add(tests, "mode", MODE);
        add(tests, "gaps", GAPS);
        add(tests, "errpath", ERRPATH);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    /* ==================== 类型与句柄 ==================== */

    private static final String[] TYPES = {
            """
            print(type(io.stdout), type(io.stdin), type(io.stderr))
            print(io.type(io.stdout), io.type(io.stdin), io.type(io.stderr))
            print(io.type(42), io.type("x"), io.type({}), io.type(nil), io.type(print))
            local mt = getmetatable(io.stdout)
            print(mt ~= nil, type(mt.__index), type(mt.__gc), type(mt.__close), type(mt.__tostring))
            print(mt == getmetatable(io.stdin))
            """,

            """
            -- io.write / io.read 返回的是句柄本身，不是 true
            local r = io.write("")
            print(io.type(r))
            print(io.stdout == r)
            """,

            """
            -- 标准文件关不掉：给两个值，不报错
            print(io.stdout:close())
            print(io.stdin:close())
            print(io.type(io.stdout))
            print(io.stdout:close())
            """,

            """
            -- 方法表里没有的东西索引出来是 nil；userdata 不能取长度
            print(io.stdout.nosuchmethod)
            print(io.stdout.read == io.stdout.read)
            print(pcall(function() return #io.stdout end))
            print(pcall(function() io.stdout.x = 1 end))
            """, };

    /* ==================== 打开 ==================== */

    private static final String[] OPEN = {
            """
            local p = "/tmp/fkio_open1.txt"
            os.remove(p)
            local f = io.open(p, "w")
            print(io.type(f))
            f:write("hello\\n")
            f:close()
            print(io.type(f))
            local g = io.open(p, "r")
            print(g:read("l"))
            g:close()
            os.remove(p)
            """,

            """
            -- 打不开：三元组 nil, "<路径>: No such file or directory", 2
            print(io.open("/nonexistent_dir_xyz/zzz.txt"))
            print(io.open("/nonexistent_dir_xyz/zzz.txt", "w"))
            local ok, e = io.open("/etc/hostname", "wb")
            print(ok ~= nil)
            """,

            """
            -- 模式校验：只认 [rwa]%+?[b]*
            print(pcall(io.open, "/tmp/fkio_open3.txt", "bogus"))
            print(pcall(io.open, "/tmp/fkio_open3.txt", ""))
            print(pcall(io.open, "/tmp/fkio_open3.txt", "rw"))
            print(pcall(io.open, "/tmp/fkio_open3.txt", "rb+"))
            print(pcall(io.open))
            """,

            """
            -- tmpfile 能读能写
            local f = io.tmpfile()
            print(io.type(f))
            f:write("abc")
            print(f:seek("set", 0))
            print(f:read("a"))
            print(f:close())
            """,

            """
            -- io.input / io.output 换默认流
            local p = "/tmp/fkio_open5.txt"
            os.remove(p)
            local o = io.output(p)
            print(io.type(o))
            o:write("line1\\nline2\\n")
            io.output(io.stdout)
            print("to stdout")
            local i = io.input(p)
            print(io.type(i), i:read("l"))
            io.input(io.stdin)
            os.remove(p)
            """,

            """
            -- popen
            local f = io.popen("echo hi")
            print(io.type(f))
            print(f:read("l"))
            print(f:close())
            print(io.popen("echo hi", "x"))
            """, };

    /* ==================== 读 ==================== */

    private static final String[] READ = {
            """
            local p = "/tmp/fkio_read1.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("a\\nb\\nc\\n")
            f:close()
            f = io.open(p, "r")
            print(f:read("l"), f:read("l"), f:read("l"))
            print(f:read("l"))
            print(f:read("a"))
            f:close()
            os.remove(p)
            """,

            """
            local p = "/tmp/fkio_read2.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("a\\nb\\nc\\n")
            f:close()
            f = io.open(p, "r")
            -- 'L' 保留换行，'a' 读到尾
            print(f:read("L"))
            print(f:read("a"))
            f:close()
            print(f:read("a"))
            os.remove(p)
            """,

            """
            local p = "/tmp/fkio_read3.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("3.5 -7 0x10 1e3 3x -  .5")
            f:close()
            f = io.open(p, "r")
            for i = 1, 8 do print(f:read("n")) end
            print(f:read("n"))
            print(f:read("a"))
            f:close()
            os.remove(p)
            """,

            """
            local p = "/tmp/fkio_read4.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("0123456789")
            f:close()
            f = io.open(p, "r")
            print(f:read(0))    -- 空串表示还没到 EOF
            print(f:read(4))
            print(f:read(100))
            print(f:read(0))    -- nil 表示到 EOF 了
            print(f:read(1))
            f:close()
            os.remove(p)
            """,

            """
            -- 无参 io.read 从 stdin 读；stdin 是 /dev/null，直接 EOF
            print(io.read())
            print(io.read("a"))
            print(io.read("l"))
            print(io.read("n"))
            print(io.read(0))
            print(io.read(3))
            """,

            """
            -- 多个读格式一次返回多个结果
            local p = "/tmp/fkio_read6.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("12 abc\\n34\\n")
            f:close()
            f = io.open(p, "r")
            print(f:read("n", "l", "n"))
            f:close()
            os.remove(p)
            """,

            """
            -- 读格式带 '*' 前缀（兼容写法）
            local p = "/tmp/fkio_read7.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("hi\\n")
            f:close()
            f = io.open(p, "r")
            print(f:read("*l"))
            f:close()
            os.remove(p)
            """, };

    /* ==================== 写 ==================== */

    private static final String[] WRITE = {
            """
            -- 数字按 %.14g 写；整数不带 .0
            io.write("a", 1, "b", 1.5, " ", -0.0, " ", 1e300, "\\n")
            io.write(7, "\\n")
            print(io.write("x", "\\n"))
            """,

            """
            local p = "/tmp/fkio_write2.txt"
            os.remove(p)
            local f = io.open(p, "w")
            print(f:write("one\\n"))
            print(f:write("two\\n"))
            f:close()
            local g = io.open(p, "r")
            print(g:read("a"))
            g:close()
            os.remove(p)
            """,

            """
            -- 'a' 是追加：重写不会截断
            local p = "/tmp/fkio_write3.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("base\\n")
            f:close()
            f = io.open(p, "a")
            f:write("more\\n")
            f:close()
            f = io.open(p, "r")
            print(f:read("a"))
            f:close()
            -- 'w' 会截断
            f = io.open(p, "w")
            f:write("new\\n")
            f:close()
            f = io.open(p, "r")
            print(f:read("a"))
            f:close()
            os.remove(p)
            """,

            """
            -- 写字节：Lua 字符串可以含 \\0
            local p = "/tmp/fkio_write4.txt"
            os.remove(p)
            local f = io.open(p, "wb")
            f:write("a\\0b")
            f:close()
            f = io.open(p, "rb")
            local s = f:read("a")
            print(#s, s:byte(1), s:byte(2), s:byte(3))
            f:close()
            os.remove(p)
            """, };

    /* ==================== lines ==================== */

    private static final String[] LINES = {
            """
            local p = "/tmp/fkio_lines1.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("x\\ny\\nz\\n")
            f:close()
            f = io.open(p, "r")
            local r = {}
            for l in f:lines() do r[#r+1] = l end
            print(table.concat(r, ","))
            print(io.type(f))
            f:close()
            os.remove(p)
            """,

            """
            -- io.lines(文件) 返回四个值，迭代结束自动关文件
            local p = "/tmp/fkio_lines2.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("1\\n2\\n3\\n")
            f:close()
            local g = io.open(p, "r")
            local r = {}
            for l in g:lines("l") do r[#r+1] = l end
            print(table.concat(r, ","))
            g:close()
            local it, s, c, cl = io.lines(p)
            print(type(it), s, c, io.type(cl))
            f = io.open(p, "r")
            local h = f
            for l in io.lines(p) do r[#r+1] = l end
            print(table.concat(r, ","))
            print(io.type(h))
            os.remove(p)
            """,

            """
            -- lines 的读格式会传给每次迭代
            local p = "/tmp/fkio_lines3.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("10 20\\n30 40\\n")
            f:close()
            local r = {}
            for a, b in io.lines(p, "n", "n") do r[#r+1] = a .. "/" .. b end
            print(table.concat(r, " "))
            os.remove(p)
            """,

            """
            -- 文件不存在时 io.lines 直接报错（opencheck），不是返回 nil
            print(pcall(io.lines, "/nonexistent_dir_xyz/nope.txt"))
            """, };

    /* ==================== seek / setvbuf ==================== */

    private static final String[] SEEK = {
            """
            local p = "/tmp/fkio_seek1.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("0123456789")
            f:close()
            f = io.open(p, "r")
            print(f:seek("set", 3))
            print(f:read(2))
            print(f:seek("cur", 0))
            print(f:seek("cur", -2))
            print(f:seek("end"))
            print(f:seek("end", -3))
            print(f:read(3))
            print(f:seek("set"))
            f:close()
            os.remove(p)
            """,

            """
            -- 管道不能定位。只测 stdout：参考实现的 stdin 被定向到 /dev/null，
            -- 而 /dev/null 是<b>可定位</b>的字符设备（lseek 恒返回 0），
            -- 本实现的 stdin 是内存流，必然 ESPIPE——那是流的来源不同，
            -- 不是 io 库的行为差异，所以这一条不比。
            print(io.stdout:seek())
            print(io.stdout:seek("set", 0))
            print(io.stdout:seek("end"))
            """,

            """
            local p = "/tmp/fkio_seek3.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("hello")
            f:close()
            f = io.open(p, "r+")
            print(f:read(2))
            print(f:seek("set", 0))
            print(f:write("HE"))
            print(f:seek("set", 0))
            print(f:read("a"))
            f:close()
            os.remove(p)
            """,

            """
            -- setvbuf 只认模式名，总是成功
            local p = "/tmp/fkio_seek4.txt"
            os.remove(p)
            local f = io.open(p, "w")
            print(f:setvbuf("no"))
            print(f:setvbuf("full", 4096))
            print(f:setvbuf("line"))
            print(pcall(f.setvbuf, f, "bogus"))
            print(pcall(f.setvbuf, f))
            f:close()
            os.remove(p)
            """,

            """
            print(io.flush())
            local p = "/tmp/fkio_seek5.txt"
            os.remove(p)
            local f = io.open(p, "w")
            print(f:flush())
            f:close()
            os.remove(p)
            """, };

    /* ==================== 报错 ==================== */

    private static final String[] ERRORS = {
            """
            -- 已关闭的句柄
            local p = "/tmp/fkio_err1.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("x")
            f:close()
            print(pcall(f.write, f, "y"))
            print(pcall(f.read, f, "l"))
            print(pcall(f.lines, f))
            print(pcall(f.seek, f))
            print(pcall(f.close, f))
            os.remove(p)
            """,

            """
            -- 参数类型
            print(pcall(io.type))
            print(pcall(io.open, 42))
            print(pcall(io.open, "x", 42))
            print(pcall(io.write, {}))     -- 表不能 tostring 成字符串
            print(pcall(io.write, true))
            """,

            """
            -- 读格式不认识
            local p = "/tmp/fkio_err3.txt"
            os.remove(p)
            local f = io.open(p, "w")
            f:write("hi\\n")
            f:close()
            f = io.open(p, "r")
            print(pcall(f.read, f, "z"))
            print(pcall(f.read, f, ""))
            print(pcall(f.read, f, "*z"))
            print(f:read("l"))
            f:close()
            os.remove(p)
            """,

            """
            -- 默认流被关掉之后再用
            local p = "/tmp/fkio_err4.txt"
            os.remove(p)
            local i = io.input(p)
            print(io.type(i))
            i:close()
            print(pcall(io.read))
            print(pcall(io.lines))
            os.remove(p)
            """,

            """
            -- 错误里的函数名：方法调用时参数号要减一
            local p = "/tmp/fkio_err5.txt"
            os.remove(p)
            local f = io.open(p, "w")
            print(pcall(f.read, f, 42))
            print(pcall(f.seek, f, "bogus"))
            print(pcall(io.open, nil))
            f:close()
            os.remove(p)
            """, };

    /**
     * 零长度 I/O。
     * <p>
     * 咬出来的是 {@code f:write("")}：<b>写 0 个字节在 C 里一定成功</b> ——
     * {@code fwrite(s, 1, 0, f)} 返回 0、正好等于 {@code l}，于是 {@code g_write}
     * 走成功分支，把<b>文件自己</b>返回来。我们的 {@code LuaFile.write} 是先判
     * {@code !writable} 再看长度，于是往<b>只读</b>文件写空串也报了
     * {@code Bad file descriptor}。
     * <p>
     * 能把它逼出来的只有这一个组合：往可写文件写空串两边都对，往只读文件写
     * <b>非空</b>串两边都报 EBADF。这也说明为什么「只看返回值对不对」不够 ——
     * 得把「可写 × 只读 × 空 × 非空」四个格子全铺开。
     * <p>
     * 读那一侧 {@code f:read(0)} 走的是<b>另一条路</b>：{@code g_read} 对数字 0
     * 调的不是 {@code read_chars} 而是 {@code test_eof}（先 {@code getc} 再
     * {@code ungetc}），所以在只写文件上照样失败、给三元组。我们的 {@code read}
     * 本来就是先 {@code done >= len} 再判 {@code readable}，因此没踩到 ——
     * 但这两条路不对称，写那边补的这一刀别照着挪到读这边来。
     */
    private static final String[] ZERO = {
            """
            -- 往<b>只读</b>文件写空串是成功的（fwrite 的 l == 0）
            local p = "/tmp/fkio_zero.txt"
            os.remove(p)
            local w = io.open(p, "w")
            w:write("hello")
            w:close()
            local ro = io.open(p)
            print("W1", ro:write("") == ro)
            print("W2", select("#", ro:write("")))
            print("W3", ro:write() == ro)
            print("W4", select("#", ro:write("a")))
            print("W5", ro:write("a") == ro)
            print("W6", pcall(function() return ro:write("a") end))
            ro:close()
            os.remove(p)
            """,

            """
            -- 标准流：stdin 是只读的，写空串照样成功
            print("S1", io.stdin:write("") == io.stdin)
            print("S2", io.stdout:write("") == io.stdout)
            print("S3", io.stderr:write("") == io.stderr)
            print("S4", io.write("") == io.stdout)
            print("S5", io.write() == io.stdout)
            print("S6", select("#", io.write("")))
            print("S7", select("#", io.write("x", "")))
            """,

            """
            -- 读 0 字节：g_read 对 0 走 test_eof（getc + ungetc），不是 fread
            local p = "/tmp/fkio_zero2.txt"
            os.remove(p)
            local w = io.open(p, "w")
            w:write("abc")
            w:close()
            local ro = io.open(p)
            print("R1", ro:read(0) == "")
            print("R2", select("#", ro:read(0)))
            local wo = io.open(p, "a")
            print("R3", select("#", wo:read(0)))
            print("R4", wo:read(0))
            print("R5", pcall(function() return wo:read("l") end))
            wo:close()
            ro:close()
            os.remove(p)
            """,

            """
            -- 多参数里混空串，以及关闭之后
            local p = "/tmp/fkio_zero3.txt"
            os.remove(p)
            local w = io.open(p, "w")
            print("M1", w:write("", "", "") == w)
            print("M2", w:write("a", "", "b") == w)
            print("M3", w:write(1, "", 2.5) == w)
            print("M4", w:close())
            print("M5", io.open(p):read("a"))
            print("M6", pcall(function() return w:write("") end))
            print("M7", pcall(function() return w:write() end))
            os.remove(p)
            """, };


    /**
     * {@code io.popen} / {@code io.tmpfile} / {@code setvbuf}。
     * <p>
     * 这一组之前只有 1~2 个用例，是 io 库里最薄的一块。铺开之后咬出<b>一处真 bug</b>：
     * {@code io.popen("exit 3"):close()} 参考实现给 {@code nil, "exit", 3}，我们给的是
     * {@code false, "exit", 3} —— {@code luaL_execresult} 失败时压的是
     * {@code luaL_pushfail}，而 5.4 里它<b>就是 nil</b>
     * （{@code lauxlib.h: #define luaL_pushfail(L) lua_pushnil(L)}）。
     * 只有 {@code io.popen} 的收尾走 {@code LuaAux.execResult}，
     * {@code os.execute} 有自己一份、本来就是对的，所以这个 bug 在 os 那边看不出来。
     * <p>
     * 三样东西<b>不比</b>，每一条都是实测出来的，不是托辞：
     * <ul>
     *   <li><b>子进程的输出/错误必须在命令里重定向掉。</b>继承来的 stdout / stderr
     *       不进本实现的捕获缓冲（Java 的子进程继承的是 JVM 真那个 stderr），
     *       两边对不齐。所以这里写的是 {@code cat > /dev/null} 与
     *       {@code 2>/dev/null}，而不是 {@code cat} 与裸命令。</li>
     *   <li><b>不排空管道就 {@code close}</b>：参考实现拿到的是
     *       {@code nil, "signal", 13}（{@code echo} 被 SIGPIPE 杀掉），
     *       我们给 {@code true, "exit", 0}。这是<b>竞态</b>——取决于子进程有没有
     *       先写完，逐次运行结果不稳定，不能进比对。</li>
     *   <li><b>{@code setvbuf} 的负的 / 极大的 size。</b> C 把它转成
     *       {@code size_t} 交给 libc 的 {@code setvbuf}，能不能分配出来是 libc
     *       自己定的：同一台机器上 {@code 2^31} 失败而 {@code 2^40} 成功，
     *       {@code "no"} 配 {@code -1} 成功而 {@code "line"} 配 {@code -1} 失败。
     *       Java 侧没法合理建模。</li>
     * </ul>
     */
    private static final String[] PROC = {
            """
            -- 这一片刻意把子进程的输出/错误都在命令里重定向掉：它只关心「读到了
            -- 什么、退出码是多少」。继承来的 stdout/stderr 怎么落另有 errpath 段
            -- （那边是能对的 —— 本实现把它们转到了 L.out / L.err，正是捕获缓冲）。
            local out = {}
            local function c(tag, f, ...)
              local r = table.pack(pcall(f, ...))
              local s = tag
              for i = 1, r.n do s = s .. "\t" .. tostring(r[i]) end
              out[#out + 1] = s
            end
            local function say(s) out[#out + 1] = s end
            local p = io.popen("echo hi")
            say("p1 " .. io.type(p) .. " " .. tostring(p:read("l"))
                .. " " .. tostring(p:read("a")))
            c("p2", p.close, p)
            p = io.popen("printf abc")
            say("p3 " .. tostring(p:read("a")))
            c("p4", p.close, p)
            local w = io.popen("cat > /dev/null", "w")
            say("w1 " .. tostring(w:write("abc") == w))
            c("w2", w.close, w)
            -- 非零退出码是 nil 不是 false（luaL_pushfail 在 5.4 里就是 lua_pushnil）
            for _, code in ipairs({ 0, 3, 7 }) do
              local f = io.popen("exit " .. code)
              c("e" .. code, f.close, f)
            end
            local ef = io.popen("false")
            c("ef", ef.close, ef)
            local nx = io.popen("no_such_cmd_xyz_12345 2>/dev/null")
            say("n1 " .. tostring(nx ~= nil))
            if nx then
              say("n2 [" .. tostring(nx:read("a")) .. "]")
              c("n3", nx.close, nx)
            end
            print(table.concat(out, "\n"))
            """,


            """
            -- popen 的模式检查，以及管道上能做什么
            local out = {}
            local function c(tag, f, ...)
              local r = table.pack(pcall(f, ...))
              local s = tag
              for i = 1, r.n do s = s .. "\t" .. tostring(r[i]) end
              out[#out + 1] = s
            end
            local function say(s) out[#out + 1] = s end
            c("m1", io.popen, "echo hi", "r")
            c("m3", io.popen, "echo hi", "rw")
            c("m4", io.popen, "echo hi", "")
            c("m5", io.popen, "echo hi", "x")
            c("m6", io.popen)
            c("m7", io.popen, nil)
            c("m8", io.popen, "echo hi", "r", 99)
            local m2ok, m2 = pcall(io.popen, "cat > /dev/null", "w")
            say("m2 " .. tostring(m2ok) .. " " .. tostring(io.type(m2)))
            if m2ok then c("m2c", m2.close, m2) end
            local s1 = io.popen("echo hi")
            c("s1", s1.seek, s1, "set", 0)
            c("s2", s1.setvbuf, s1, "no")
            say("s3 " .. tostring(type(s1:lines())))
            say("s4 " .. tostring(s1:read("a")))
            c("s5", s1.close, s1)
            c("s6", s1.close, s1)
            say("s7 " .. tostring(io.type(s1)))
            print(table.concat(out, "\n"))
            """,


            """
            local out = {}
            local function c(tag, f, ...)
              local r = table.pack(pcall(f, ...))
              local s = tag
              for i = 1, r.n do s = s .. "\t" .. tostring(r[i]) end
              out[#out + 1] = s
            end
            local function say(s) out[#out + 1] = s end
            local t = io.tmpfile()
            say("t1 " .. tostring(t ~= nil) .. " " .. tostring(io.type(t)))
            say("t2 " .. tostring(t:write("hello") == t))
            say("t3 " .. tostring(t:seek("set", 0)))
            say("t4 " .. tostring(t:read("a")))
            c("t5", t.close, t)
            say("t6 " .. tostring(io.type(t)))
            local t2 = io.tmpfile()
            c("t7", t2.close, t2)
            local t3 = io.tmpfile(1, 2)
            say("t8 " .. tostring(t3 ~= nil))
            if t3 then t3:close() end
            print(table.concat(out, "\n"))
            """,


            """
            local out = {}
            local function c(tag, f, ...)
              local r = table.pack(pcall(f, ...))
              local s = tag
              for i = 1, r.n do s = s .. "\t" .. tostring(r[i]) end
              out[#out + 1] = s
            end
            local function say(s) out[#out + 1] = s end
            local p = "/tmp/fkio_vbuf.txt"
            os.remove(p)
            for _, mode in ipairs({ "no", "full", "line" }) do
              local f = io.open(p, "w")
              c("v_" .. mode, f.setvbuf, f, mode)
              f:write("xyz")
              c("vc_" .. mode, f.close, f)
              local g = io.open(p)
              say("vr_" .. mode .. " " .. tostring(g:read("a")))
              g:close()
            end
            local f2 = io.open(p, "w")
            c("v_sz", f2.setvbuf, f2, "full", 4096)
            c("v_sz0", f2.setvbuf, f2, "full", 0)
            -- 负的 / 极大的 size 不比：C 转成 (size_t) 后交给 libc 的 setvbuf，
            -- 能不能分配出来是 libc 自己定的（同一台机器上 2^31 失败、2^40 成功，
            -- "no" 配 -1 成功、"line" 配 -1 失败），Java 侧没法合理建模
            c("v_bad", f2.setvbuf, f2, "nope")
            c("v_none", f2.setvbuf, f2)
            c("v_num", f2.setvbuf, f2, 42)
            c("v_nil", f2.setvbuf, f2, nil)
            f2:close()
            c("v_stdout", io.stdout.setvbuf, io.stdout, "no")
            c("v_stdin", io.stdin.setvbuf, io.stdin, "full")
            os.remove(p)
            print(table.concat(out, "\n"))
            """, };

    /**
     * {@code io.open} 的<b>模式矩阵</b>：{@code l_checkmode} 认的是
     * {@code [rwa]%+?b*}，形状很容易写错（{@code "rbb"} 合法而 {@code "rb+"}
     * 不合法 —— 后缀是 {@code strspn(mode, "b")} 与全长比，不是「一个 b」）。
     * 34 种模式 ×「读 / 定位 / 写 / 位置 / 关 / 最终内容」，79 行。
     * <p>
     * 这一片咬出<b>三处</b>，其中两处是 C stdio 定的、本实现不打算建模，
     * 第三处就是上面那句「写了之后位置是多少」——三处都量过才下的结论：
     * <ul>
     *   <li><b>更新模式（{@code r+}/{@code w+}/{@code a+}）上「读完不定位直接写」
     *       </b>的落点由 stdio 的<b>预读缓冲</b>决定，ISO C 里这本就是未定义行为。
     *       实测：12 字节的文件写到 13（EOF），6002 字节的写到 4097（BUFSIZ+1），
     *       而 {@code read(5000)} 又快又准地写到 5001（大块读绕过缓冲）。
     *       所以每次写之前都显式 {@code seek} 一次，把这条 UB 路径绕开 ——
     *       绕开之后（{@code read → seek → write}）两边<b>逐字节一致</b>。</li>
     *   <li><b>append 档上写完的位置</b>：{@code seek("set",0)} 再写 1 字节，
     *       参考给 <b>1</b>（{@code base+n}），我们给 13（{@code EOF+n}，
     *       这一边才符合 POSIX）。再量下去发现它也是缓冲定的：
     *       {@code n<=4095} 给 {@code base+n}、{@code n>=4096} 给 {@code EOF+n}。
     *       所以这里在量位置之前先 {@code flush} 把缓冲压掉，位置就落到 POSIX
     *       那个唯一确定的答案上，两边才可比。<b>写进去的内容两边始终一致</b>，
     *       分歧只在 {@code seek("cur",0)} 报的数。</li>
     *   <li><b>标准流上的 {@code seek}</b>取决于真实 fd 可不可定位：重定向到文件
     *       时给 0、{@code /dev/null} 给 0、管道与 tty 给 {@code "Illegal seek"}。
     *       本实现拿不到那个 fd，无法建模，故不比。</li>
     * </ul>
     */
    private static final String[] MODE = {"""

        -- io.open 的模式矩阵：l_checkmode 认的是 [rwa]%+?b*，形状很容易写错
        -- 两样东西不比，都是实测出来的，理由见 README：
        --   1. 更新模式（r+/w+/a+）上「读完不定位直接写」的落点由 C stdio 的**预读缓冲**
        --      决定（ISO C 里这本就是未定义行为）：12 字节的文件写到 13（EOF），
        --      6002 字节的写到 4097（BUFSIZ+1），而 read(5000) 又写到 5001（大块读绕过缓冲）。
        --      所以每个写之前都显式 seek 一次，把这条 UB 路径绕开。
        --   2. 标准流上的 seek 取决于真实 fd 可不可定位：文件 / /dev/null 给 0，
        --      管道 / tty 给 "Illegal seek"。本实现拿不到那个 fd，无法建模。
        local out = {}
        local function d(v)
          local t = type(v)
          if t == "nil" then return "nil" end
          if t == "boolean" then return tostring(v) end
          if t == "number" then return math.type(v) .. ":" .. tostring(v) end
          if t == "string" then return string.format("q(%d)[%s]", #v, v) end
          return t
        end
        local function probe(name, f, ...)
          local r = table.pack(pcall(f, ...))
          local s = name .. " [" .. r.n .. "]"
          for i = 2, r.n do s = s .. " " .. d(r[i]) end
          if not r[1] then s = s .. " ERR " .. tostring(r[2]) end
          out[#out + 1] = s
        end

        local P = "/tmp/fkmd.txt"
        local function seed()
          local f = assert(io.open(P, "w"))
          f:write("hello\\nworld\\n")
          f:close()
        end

        local modes = {
          "r", "w", "a", "r+", "w+", "a+",
          "rb", "wb", "ab", "r+b", "w+b", "a+b",
          "rbb", "wbb", "abb", "r+bb", "w+bb", "a+bb",
          "rb+", "wb+", "r b", "br", "rw", "ar", "r++", "", "x", "+r", "++r",
          "rB", "R", "r\\t", "r\\n", "r ", " r",
        }
        for _, m in ipairs(modes) do
          seed()
          probe("open[" .. m:gsub("%c", "?") .. "]", function()
            local f, e, en = io.open(P, m)
            if f == nil then return f, e, en end
            local r = {}
            r[#r + 1] = io.type(f)
            -- 追加 + 更新（a+ / a+b / a+bb）打开时的<b>位置在哪</b>是
            -- implementation-defined：glibc 给 0，于是读得到内容；Apple libc 给
            -- EOF，于是读出来是 nil。读这一步在这三种模式上不比（约定 #57）。
            if m:sub(1, 1) == "a" and m:find("+", 1, true) then
              r[#r + 1] = "skip"
            else
              local ok1, v1 = pcall(function() return f:read("l") end)
              r[#r + 1] = tostring(ok1) .. "/" .. tostring(v1)
            end
            -- 显式定位：把「读之后直接写」那条 UB 路径绕开
            local okp, vp, ep = pcall(function() return f:seek("set", 0) end)
            r[#r + 1] = tostring(okp) .. "/" .. tostring(vp) .. "/" .. tostring(ep)
            local ok2, v2 = pcall(function() return (f:write("Z") == f) end)
            r[#r + 1] = tostring(ok2) .. "/" .. tostring(v2)
            -- append 档上「写了之后位置是多少」是 stdio 缓冲定的：小写入给 base+n
            -- （n<=4095），大写入给 EOF+n（n>=4096）。先 flush 把缓冲压掉，位置就落到
            -- POSIX 那个唯一确定的答案上，两边才可比。
            if m:sub(1, 1) == "a" then
              local okf, vf = pcall(function() return f:flush() end)
              r[#r + 1] = "flush=" .. tostring(okf) .. "/" .. tostring(vf)
            end
            local ok3, v3, e3 = pcall(function() return f:seek("cur", 0) end)
            r[#r + 1] = tostring(ok3) .. "/" .. tostring(v3) .. "/" .. tostring(e3)
            local ok4, v4 = pcall(function() return f:close() end)
            r[#r + 1] = tostring(ok4) .. "/" .. tostring(v4) .. "/" .. tostring(io.type(f))
            -- 写完之后文件里到底是什么：这才是落点的真凭实据
            local g = io.open(P, "r")
            r[#r + 1] = (g == nil) and "gone" or ("[" .. tostring(g:read("a")) .. "]")
            if g then g:close() end
            return table.concat(r, ";")
          end)
        end

        -- 模式参数的其它形状：不是字符串、缺失、数字
        seed()
        probe("m_nil", io.open, P, nil)
        probe("m_none", io.open, P)
        probe("m_num", io.open, P, 42)
        probe("m_bool", io.open, P, true)
        probe("m_tbl", io.open, P, {})
        probe("m_nilfirst", io.open, nil, "r")
        probe("m_noname", io.open)
        probe("m_numname", io.open, 42, "r")

        seed()
        probe("r_write", function()
          local f = assert(io.open(P, "r"))
          local ok, e, en = pcall(function() f:write("x") end)
          f:close()
          return ok, e, en
        end)
        probe("w_read", function()
          local f = assert(io.open(P, "w"))
          local ok, v, en = pcall(function() return f:read("l") end)
          f:close()
          return ok, v, en
        end)
        probe("w_seek_read", function()
          local f = assert(io.open(P, "w"))
          f:write("abc")
          local p = f:seek("set", 0)
          local ok, v = pcall(function() return f:read("a") end)
          f:close()
          return p, ok, v
        end)
        probe("a_seek_write", function()
          local f = assert(io.open(P, "a"))
          local p = f:seek("cur", 0)
          f:write("END")
          f:close()
          local g = assert(io.open(P, "r"))
          local c = g:read("a")
          g:close()
          return p, c
        end)
        probe("rplus", function()
          local f = assert(io.open(P, "r+"))
          local a = f:read("l")
          f:seek("cur", 0)
          local b = (f:write("Q") == f)
          f:seek("set", 0)
          local c = f:read("a")
          f:close()
          return a, b, c
        end)
        probe("wplus", function()
          local f = assert(io.open(P, "w+"))
          f:write("XY")
          f:seek("set", 0)
          local a = f:read("a")
          f:close()
          return a
        end)
        -- 打开瞬间的位置（p）不比：追加模式下它是 implementation-defined，
        -- glibc 给 0、Apple libc 给 EOF（约定 #57）。只比写完定位后读回来的内容。
        probe("aplus", function()
          local f = assert(io.open(P, "a+"))
          f:write("Z")
          f:seek("set", 0)
          local a = f:read("a")
          f:close()
          return a
        end)

        -- 标准流（seek 不比：见文件头第 2 条）
        probe("close_stdout", function() return io.stdout:close() end)
        probe("close_stdin", function() return io.stdin:close() end)
        probe("write_stdout", function() return (io.write("") == io.stdout) end)
        probe("stdin_write", function() local ok, v, e = pcall(function() return (io.stdin:write("x") == io.stdin) end); return ok, v, e end)
        probe("stdout_type", function() return io.type(io.stdout), io.type(io.stderr), io.type(io.stdin) end)

        os.remove(P)
        print(table.concat(out, "\\n"))
            """ };

    /**
     * 照着 JaCoCo 的<b>未覆盖行点名打</b>的一批（39 行）。
     * <p>
     * 上一篇矩阵的选题是「按类排序挑缺口最大的」，这一篇再往下走一层：
     * 打开 {@code target/site/jacoco/<包>/IoLib.java.html}（{@code jacoco.xml}
     * 里没有行级信息，HTML 有），把 {@code class="nc"} 的行号抄出来，
     * 逐条问「这一行能不能从 Lua 里走到」。63 行未覆盖里大半是
     * {@code catch (IOException e)} —— 那些确实走不到；剩下这些是<b>能走到
     * 却从没走到</b>的，也就是真正漏测的行为：
     * <ul>
     *   <li>已关闭句柄的 {@code tostring} 是 {@code "file (closed)"}
     *       （不是 {@code io.type} 的 {@code "closed file"}，两个字符串不一样）；</li>
     *   <li>{@code io.lines()} <b>无参</b>与显式 {@code nil}：走注册表里的
     *       {@code IO_INPUT}；</li>
     *   <li>{@code f:read("n")} 的各形状 —— 指数 {@code 1e5} / {@code 1e999}
     *       （给 inf）、十六进制 {@code 0x10} / {@code 0x1p4} / {@code 0x.8p1}、
     *       前导零 {@code 0777}（十进制不是八进制）、{@code 0x} 与 {@code 1e}
     *       这种残缺前缀给 nil、超长数字串给 nil；</li>
     *   <li>迭代途中把文件关掉：迭代器再调用报 {@code "file is already closed"}；</li>
     *   <li>大块写（5000 字节）绕开 {@code wbuf} 直接落盘的那条分支。</li>
     * </ul>
     * 39 行逐字节一致 —— 也就是说这些分支<b>本来就是对的，只是没人跑过</b>。
     * 它们的价值在回归：以后谁改了 {@code read("n")} 的前缀判断或
     * {@code wbuf} 的阈值，这里立刻红。
     */
    private static final String[] GAPS = {"""
        -- 照着 JaCoCo 未覆盖行点名打：闭文件的 tostring、lines 无参、read("n") 各形状、
        -- 迭代途中关文件、大块写
        local out = {}
        local function d(v)
          local t = type(v)
          if t == "nil" then return "nil" end
          if t == "boolean" then return tostring(v) end
          if t == "number" then return math.type(v) .. ":" .. tostring(v) end
          if t == "string" then return string.format("q(%d)[%s]", #v, (v:gsub("\\n", "\\\\n"))) end
          return t
        end
        local function probe(name, f, ...)
          local r = table.pack(pcall(f, ...))
          local s = name .. " [" .. r.n .. "]"
          for i = 2, r.n do s = s .. " " .. d(r[i]) end
          if not r[1] then s = s .. " ERR " .. d(r[2]) end
          out[#out + 1] = s
        end

        local P = "/tmp/fkio2.txt"
        local function put(s)
          local f = assert(io.open(P, "w"))
          f:write(s)
          f:close()
        end

        -- 1. 已关闭句柄的形状：tostring 给 "file (closed)"，io.type 给 "closed file"
        probe("ts_closed", function()
          local f = io.open(P, "w")
          f:close()
          return tostring(f), io.type(f)
        end)
        probe("ts_open", function()
          local f = io.open(P, "w")
          local s = tostring(f)
          f:close()
          -- 只比形状：%p 打的是指针，长度也对不上（已知的 file (%p) 偏离）
          return s:match("^file %(0x%x+%)$") ~= nil
        end)
        probe("ts_closed_concat", function()
          local f = io.open(P, "w")
          f:close()
          return "x" .. tostring(f)
        end)

        -- 2. lines 无参 / 显式 nil：走 registry 里的 IO_INPUT
        put("a\\nb\\nc\\n")
        probe("lines_noarg", function()
          local f = assert(io.open(P, "r"))
          io.input(f)
          local r = {}
          for l in io.lines() do r[#r + 1] = l end
          io.input(io.stdin)
          f:close()
          return table.concat(r, ",")
        end)
        probe("lines_nil", function()
          local f = assert(io.open(P, "r"))
          io.input(f)
          local r = {}
          for l in io.lines(nil) do r[#r + 1] = l end
          io.input(io.stdin)
          f:close()
          return table.concat(r, ",")
        end)
        probe("lines_noarg_fmt", function()
          local f = assert(io.open(P, "r"))
          io.input(f)
          local r = {}
          for l in io.lines("a") do r[#r + 1] = l end
          io.input(io.stdin)
          f:close()
          return r[1]
        end)
        probe("lines_noarg_closed_input", function()
          io.input(assert(io.open(P, "r")))
          io.input():close()
          local ok, e = pcall(function() for l in io.lines() do end end)
          io.input(io.stdin)
          return ok, e
        end)

        -- 3. read("n") 的各形状：指数、十六进制、前导零、超长
        local nums = {
          "1e5", "1E5", "1e+5", "1e-5", "0x10", "0X10", "0x1p4", "0x.8p1",
          "0777", "0z", "0", "0x", "1e", ".5", "5.", "- 5", "  -3.5e-2  ",
          "1e999", "0x1p999", "inf", "nan", "3abc", "--3", "+3",
        }
        for _, s in ipairs(nums) do
          put(s .. "\\n")
          probe("n[" .. s:gsub("%%", "%%%%") .. "]", function()
            local f = assert(io.open(P, "r"))
            local a, b = f:read("n")
            f:close()
            return a, b
          end)
        end
        put(string.rep("1", 250) .. "\\n")
        probe("n_250digits", function()
          local f = assert(io.open(P, "r"))
          local a = f:read("n")
          f:close()
          return a
        end)

        -- 4. 迭代途中把文件关掉：迭代器再调用报 "file is already closed"
        put("a\\nb\\nc\\n")
        probe("lines_close_mid", function()
          local f = assert(io.open(P, "r"))
          local it = f:lines()
          local first = it()
          f:close()
          local ok, e = pcall(it)
          return first, ok, e
        end)
        probe("lines_close_mid_named", function()
          local f = assert(io.open(P, "r"))
          local first
          for l in f:lines() do
            first = l
            f:close()
          end
          return first
        end)
        probe("lines_readerr", function()
          local f = io.open(P, "w")   -- 只写档：lines 读不出来
          local ok, e = pcall(function() for l in f:lines() do end end)
          f:close()
          return ok, e
        end)
        probe("lines_multi_fmt", function()
          local r = {}
          local f = assert(io.open(P, "r"))
          for a, b in f:lines("l", "l") do r[#r + 1] = tostring(a) .. "/" .. tostring(b) end
          f:close()
          return table.concat(r, ";")
        end)

        -- 5. 大块写：绕过 wbuf 直接落盘（wbuf 是 4096）
        probe("write_big", function()
          local f = assert(io.open(P, "w"))
          f:write(string.rep("x", 5000))
          f:close()
          local g = assert(io.open(P, "r"))
          local s = g:read("a")
          g:close()
          return #s
        end)
        probe("write_mixed", function()
          local f = assert(io.open(P, "w"))
          f:write(string.rep("a", 4000))
          f:write(string.rep("b", 4000))
          f:write("c")
          f:close()
          local g = assert(io.open(P, "r"))
          local s = g:read("a")
          g:close()
          return #s, s:sub(3999, 4002)
        end)
        probe("read_partial", function()
          put("abc")
          local f = assert(io.open(P, "r"))
          local a = f:read(100)
          local b = f:read(100)
          f:close()
          return a, b
        end)

        os.remove(P)
        print(table.concat(out, "\\n"))
            """ };

    /**
     * io 的<b>出错路径</b>：管道上的 {@code seek}、错模式的 EBADF、
     * {@code popen} 子进程的流继承、{@code os.remove} / {@code os.rename} 的 errno。
     * <p>
     * 起因是覆盖率报告里 {@code IoLib} 有 54 行没走到，几乎全是
     * {@code catch (IOException)} —— 也就是「真出 I/O 错时给什么 errno」这一片
     * 从来没被扫过。顺着缺口点名咬出了三个真分歧：
     * <ul>
     *   <li><b>{@code io.open(不存在的文件, "r+") 必须失败</b>。C 的
     *       {@code fopen("r+")} 走 {@code open(path, O_RDWR)}，<b>没带
     *       {@code O_CREAT}</b>；Java 的 {@code RandomAccessFile(f, "rw")}
     *       一定会建。修法是先用只读模式探一次存在性（{@code "r"} 模式本身
     *       不创建），errno 照旧交给 {@code errnoOf} 反查；用
     *       {@code File.exists()} 会把「父目录不可搜索」的 EACCES 错报成
     *       ENOENT。</li>
     *   <li><b>{@code popen} 只接管一条流，其余继承</b>。读模式接管子进程的
     *       {@code stdout}，写模式接管它的 {@code stdin}，<b>另外两条要原样
     *       落到 lua 的 stdout / stderr</b>。Java 的 {@code ProcessBuilder}
     *       默认三条全接成管道，于是全被吞掉；更糟的是读模式下子进程的
     *       {@code stdin} 也是管道且没人关 —— {@code io.popen("cat", "r")}
     *       会一直等下去。</li>
     *   <li>上面那条还顺带决定了<b>转发线程要 {@code join}</b>：不 join 的话
     *       子进程的最后几行可能还没落进捕获缓冲，比对时好时坏。</li>
     * </ul>
     * <p>
     * 一处探针写法上的坑：<b>文件句柄上没有 {@code tell} 方法</b>（只有
     * {@code seek}），写 {@code f.tell} 得到的是
     * {@code attempt to call a nil value} —— 那是探针写错，不是实现的行为。
     */
    private static final String[] ERRPATH = {"""
-- io 的「出错路径」探针：管道上的 seek/tell、错的打开模式、popen/tempfile 的
-- errno、close 的三元组。起因是覆盖率报告里 IoLib 有 54 行没走到，几乎全是
-- catch (IOException) —— 也就是「真出 I/O 错时给什么 errno」这一片从来没扫过。
local D = "target/rt"
os.execute("rm -rf " .. D .. " && mkdir -p " .. D)

local function put(name, body)
  local f = assert(io.open(D .. "/" .. name, "wb"))
  f:write(body)
  f:close()
end
put("a.txt", "line1\\nline2\\nline3\\n")
put("empty.txt", "")

local function show(v)
  local t = type(v)
  if t == "nil" then return "nil" end
  if t == "boolean" then return tostring(v) end
  if t == "number" then return math.type(v) .. ":" .. tostring(v) end
  if t == "string" then return string.format("q(%d)[%s]", #v, v) end
  return t
end
local function probe(name, f, ...)
  local r = table.pack(pcall(f, ...))
  local s = name .. " [" .. r.n .. "]"
  for i = 2, r.n do s = s .. " " .. show(r[i]) end
  if not r[1] then s = s .. " ERR " .. show(r[2]) end
  print(s)
end

-- ① 管道上的 seek / tell：C 的 fseek/ftell 在管道上给 ESPIPE(29)
--    io.popen 出来的句柄不是 RandomAccessFile，走的是 raf == null 那条路
print("== 管道 seek/tell ==")
local pr = io.popen("echo hello", "r")
if pr then
  print("R1", pr:read("a"))
  probe("R.seek_set", pr.seek, pr, "set", 0)
  probe("R.seek_cur", pr.seek, pr, "cur", 0)
  probe("R.seek_end", pr.seek, pr, "end", 0)
  probe("R.tell", pr.tell, pr)
  probe("R.lines", pr.lines, pr)
  probe("R.read_a2", pr.read, pr, "a")
  probe("R.close", pr.close, pr)
end

print("== 管道 write 侧 ==")
local pw = io.popen("cat > " .. D .. "/pw.out", "w")
if pw then
  probe("W.write", pw.write, pw, "xyz\\n")
  probe("W.seek", pw.seek, pw, "set", 0)
  probe("W.tell", pw.tell, pw)
  probe("W.flush", pw.flush, pw)
  probe("W.read", pw.read, pw, "a")
  probe("W.close", pw.close, pw)
end
do
  local f = io.open(D .. "/pw.out", "rb")
  print("W.result", f and f:read("a") or "NOFILE")
  if f then f:close() end
end

-- ② 错模式：只读文件上写、只写文件上读（EBADF 9）
--    f 上没有 tell 方法（seek("cur",0) 才是），用 f.tell 会得到
--    "attempt to call a nil value" —— 那是探针写错，不是实现的行为
print("== 错模式 ==")
local function modes()
  for _, m in ipairs({ "r", "w", "a", "r+", "w+", "a+" }) do
    local f, msg, en = io.open(D .. "/m.txt", m)
    if f then
      probe(m .. ".read1", f.read, f, 1)
      probe(m .. ".read0", f.read, f, 0)
      probe(m .. ".write_empty", f.write, f, "")
      probe(m .. ".write_x", f.write, f, "x")
      probe(m .. ".seek", f.seek, f, "set", 0)
      probe(m .. ".seekcur", f.seek, f, "cur", 0)
      probe(m .. ".flush", f.flush, f)
      probe(m .. ".lines", f.lines, f)
      probe(m .. ".close", f.close, f)
    else
      -- 打不开时连消息和 errno 一起比：r+ 在文件不存在时必须失败
      print(m .. " OPENFAIL " .. show(msg) .. " " .. show(en))
    end
    os.remove(D .. "/m.txt")
  end
end
put("m.txt", "abc")
modes()

-- ②之二 文件<b>不存在</b>时逐个模式开一遍：C 的 fopen 只有 r / r+ 要求文件在
print("== 不存在的文件 × 各模式 ==")
for _, m in ipairs({ "r", "w", "a", "r+", "w+", "a+", "rb", "r+b", "wb", "ab" }) do
  local f, msg, en = io.open(D .. "/gone_" .. m, m)
  if f then
    print("gone " .. m .. " OK")
    f:close()
  else
    -- 消息里的路径含模式名，两边一样；errno 才是要钉的
    print("gone " .. m .. " FAIL " .. show(en))
  end
  os.remove(D .. "/gone_" .. m)
end

-- ③ popen 的退出状态三元组
print("== popen 退出状态 ==")
for _, cmd in ipairs({ "true", "false", "exit 7", "exit 255" }) do
  local p = io.popen(cmd, "r")
  if p then
    probe("exit[" .. cmd .. "]", p.close, p)
  end
end
probe("popen_badmode", io.popen, "true", "x")
probe("popen_nocmd", io.popen)
probe("popen_noname", io.popen, nil)
-- 42 会被 sh 当成命令名，子进程的 stderr 是继承的 —— 不 close 就等不到它，
-- 那行 "command not found" 出不出是竞态。必须 close 才确定。
local pn = io.popen(42)
if pn then probe("popen_num_close", pn.close, pn) end

-- ④ tmpfile / close 的 errno
print("== tmpfile ==")
local tf = io.tmpfile()
if tf then
  probe("tf.write", tf.write, tf, "hello")
  probe("tf.seek0", tf.seek, tf, "set", 0)
  probe("tf.read", tf.read, tf, "a")
  probe("tf.close", tf.close, tf)
end
probe("tmpfile_arg", io.tmpfile, "x")

-- ⑤ 关掉的句柄再操作
print("== 已关句柄 ==")
local f = io.open(D .. "/a.txt", "r")
f:close()
probe("closed.read", f.read, f, "a")
probe("closed.write", f.write, f, "a")
probe("closed.seek", f.seek, f, "set", 0)
probe("closed.close", f.close, f)
probe("closed.lines", f.lines, f)
probe("closed_tostring", tostring, f)

-- ⑥ io.lines 的三种调用形状（无参 / nil / 文件名）
print("== io.lines 形状 ==")
probe("lines_nil", io.lines, nil)
probe("lines_file_argc", function()
  local r = table.pack(io.lines(D .. "/a.txt"))
  return r.n, type(r[1]), type(r[2]), type(r[3]), type(r[4])
end)
probe("lines_noarg", function() return select("#", io.lines()) end)
probe("lines_missing", io.lines, D .. "/nope.txt")

-- ⑦ os.remove / os.rename 的 errno
print("== os.remove/rename errno ==")
probe("rm_missing", os.remove, D .. "/nope.txt")
probe("rm_dir", os.remove, D)
probe("rm_file", os.remove, D .. "/empty.txt")
probe("rm_noname", os.remove)
probe("rm_num", os.remove, 42)
os.execute("mkdir -p " .. D .. "/nonempty && touch " .. D .. "/nonempty/x")
probe("rm_nonempty", os.remove, D .. "/nonempty")
put("r1.txt", "a")
probe("mv_ok", os.rename, D .. "/r1.txt", D .. "/r2.txt")
probe("mv_missing", os.rename, D .. "/nope1", D .. "/nope2")
probe("mv_dir", os.rename, D, D .. "/r3")
probe("mv_self", os.rename, D .. "/r2.txt", D .. "/r2.txt")
-- 类型冲突的 rename 只比「失败了」：errno 是<b>内核</b>定的，不由 POSIX 统一
-- —— macOS 给 "Is a directory" 21，Linux 的「文件 -> 非空目录」给
-- "Directory not empty" 39。两边都是各自内核的答案（约定 #98），连消息带
-- errno 一起归掉，但仍然比「它失败了、给了三元组」。
local function probe_xdir(name, ...)
  local r = table.pack(pcall(os.rename, ...))
  out[#out + 1] = name .. " ok=" .. tostring(r[1]) .. " [" .. r.n .. "] " .. show(r[2])
end
probe_xdir("mv_into_dir", D .. "/r2.txt", D)

-- ⑧ io.open 的失败 errno
print("== io.open 失败 ==")
probe("open_missing", io.open, D .. "/nope.txt", "r")
probe("open_nodir", io.open, D .. "/nodir/x", "w")
probe("open_badmode", io.open, D .. "/a.txt", "q")
probe("open_nomode", io.open, D .. "/a.txt")
probe("open_nilname", io.open, nil, "r")
probe("open_num", io.open, 42, "r")
probe("open_dir_w", io.open, D, "w")
probe("open_dir_r", io.open, D, "r")

print("DONE")
            """ };

    /**
     * 文件方法 × 打开模式 × 每种参数：1262 行矩阵。
     * <p>
     * 九块内容：①六个方法（{@code flush lines read seek setvbuf write}）× 六个打开
     * 模式 × 21 种参数，外加「关掉之后再用」的第二遍；②{@code seek} 的<b>双参数</b>
     * 矩阵 —— 单参数时第二个值是 option，负数压根到不了偏移这条路，所以第一块对
     * 偏移是瞎的；③写完立刻定位；④{@code read} 的多格式 / 多参数 / {@code lines}
     * 迭代；⑤{@code write} 的多参数；⑥目录句柄；⑦<b>另开一个句柄</b>去看
     * {@code flush} 到底有没有把字节推出去；⑧{@code ungetc} 退回来的那个字节要能被
     * 下一次 {@code read} 拿到；⑨{@code popen} 句柄的关闭（{@code closeRaw} 的
     * {@code in} / {@code out} 两支）。
     * <p>
     * 咬出来的两个真分歧：
     * <ul>
     *   <li><b>{@code f:read(-1)}</b>：C 是 {@code size_t l = (size_t)luaL_checkinteger}，
     *       负数转成天文数字后 {@code luaL_prepbuffsize} 分配失败，抛的是
     *       {@code "not enough memory"}，不是整数越界。我们原来直接
     *       {@code new byte[(int)n]}，是 {@code NegativeArraySizeException}——
     *       一个漏出 {@code pcall} 的 Java 异常。</li>
     *   <li><b>成功的 {@code fseek} 会丢掉 {@code ungetc} 退回来的字节</b>：
     *       {@code f:read("n")} 在 {@code "three"} 上失败时把 {@code 't'} 退了回去，
     *       接着 {@code f:seek("set",0)} 再 {@code f:read(3)} —— 参考给
     *       {@code "1 2"}，我们给 {@code "t1 "}。</li>
     * </ul>
     * <p>
     * 实测灵敏度（单点破坏后红几行）：{@code seek} 负位置不报 EINVAL 46、
     * {@code read} 不判目录 整片崩、{@code append} 不回末尾 5、{@code flush} 不
     * {@code flushW} 4、{@code seek} 不清 pushback 3、{@code seek} 目录基准恒 0 2、
     * {@code write} 不特判 0 字节 1。<b>{@code tell} 里的 {@code +wlen} 是 0</b>
     * —— {@code f:seek} 先 {@code flushW} 再 {@code tell}，缓冲恒为空，那一刀
     * 观测不到，别当成已验证。⑧⑨两块是<b>只补覆盖、不产生红</b>的：去掉
     * {@code done >= len} 那条短路、或者不关 {@code popen} 的 {@code in}，矩阵照样
     * 全绿 —— 它们的作用是让 JaCoCo 少说两句谎，不是多钉一条判定。
     */
    /**
     * {@code io.lines()} <b>一个参数都不给</b>的那条分支 —— 它读的是
     * {@code io.input} 指向的默认输入，所以必须喂 stdin 才走得到。
     * <p>
     * 覆盖率报告里 {@code IoLib} 的 {@code ioLines} 有两行（「一个参数都没有」
     * 那支）一直是白的：以前所有 {@code io.lines} 用例要么给文件名、要么显式给
     * {@code nil}，两条走的都是另一支。
     * <p>
     * 顺带钉两件事：{@code io.input(f)} 换掉默认输入之后 {@code io.lines()}
     * 读的是<b>那个文件</b>而不是 stdin；以及不带 {@code "l"} 时
     * {@code io.read} 的默认格式。
     */
    @Test
    void defaultInputLinesMatchesReference() {
        String src = """
                local out = {}
                local function say(s) out[#out + 1] = s end
                -- ① 无参 io.lines() 读默认输入（= stdin）
                local n = 0
                for l in io.lines() do n = n + 1 say("A" .. n .. " [" .. l .. "]") end
                say("A n=" .. n)
                -- ② io.read 的几种默认/显式格式
                say("B " .. tostring(io.read()))
                say("C " .. tostring(io.read("l")))
                say("D " .. tostring(io.read("n")))
                say("E " .. tostring(io.read("a")))
                say("F " .. tostring(select('#', io.read("l", "l"))))
                -- ③ 换掉默认输入之后，无参 io.lines() 读的是那个文件
                local p = "target/rt/lines_in.txt"
                os.remove(p)
                local f = assert(io.open(p, "w"))
                f:write("f1\\nf2\\nf3\\n")
                f:close()
                io.input(p)
                for l in io.lines() do say("G [" .. l .. "]") end
                say("H " .. tostring(io.read("l")))
                io.input(io.stdin)
                os.remove(p)
                print(table.concat(out, "\\n"))
                """;
        Diff.assertMatchesWithStdin(src, "s1\ns2\n42\nrest\n");
        Diff.assertMatchesWithStdin(src, "");
        Diff.assertMatchesWithStdin(src, "only-one-line\n");
    }

    @Test
    void fileMethodGridMatchesReference() {
        Diff.assertMatches("""
-- 文件方法 × 打开模式 × 每种参数：顺着 getmetatable(io.stdout).__index 往下挖
local function norm(e) return (tostring(e):gsub("0x%x+", "PTR")) end
local function show(...)
    local n = select('#', ...)
    local t = {}
    for i = 1, n do t[i] = norm(tostring((select(i, ...)))) end
    return table.concat(t, ",")
end
local function s(tag, f, ...)
    local r = table.pack(pcall(f, ...))
    if not r[1] then
        print(tag, norm(tostring(r[2])))
    else
        print(tag, "OK", show(table.unpack(r, 2, r.n)))
    end
end

local P = "/tmp/fkio_fm.txt"
os.remove(P)                     -- 开头先删一次：上一趟崩掉留下的残骸会翻盘
do
    local f = io.open(P, "w")
    f:write("abc\\n")
    f:close()
end

-- 第 1 项就是 nil，# 与 ipairs 会在第一格就停（踩过：只出 110 行）
local VALS = {}
local NV = 0
local function add(v) NV = NV + 1 VALS[NV] = v end
for _, v in ipairs({ false, true, 0, 1, -1, 1.5, "s", "", {},
                     "set", "cur", "end", "full", "line", "no",
                     "n", "a", "l", "L", 2 }) do add(v) end
add(nil)
local METHODS = { "flush", "lines", "read", "seek", "setvbuf", "write" }
-- a+ 不进这张表：打开时的位置（0 还是 EOF）是 implementation-defined，
-- glibc 与 Apple libc 相反，于是 read / lines 的每一步答案都相反（约定 #57）。
local MODES = { "r", "w", "a", "r+", "w+" }

for _, m in ipairs(MODES) do
    local f = io.open(P, m)
    print("mode", m, io.type(f))
    if f ~= nil then
        for _, meth in ipairs(METHODS) do
            s(m .. "|" .. meth .. "|a0", function() return f[meth](f) end)
            for i = 1, NV do
                local v = VALS[i]
                s(m .. "|" .. meth .. "|" .. i, function() return f[meth](f, v) end)
            end
        end
        -- 关掉之后再用：attempt to use a closed file
        print(m .. "|close", f:close())
        print(m .. "|type", io.type(f))
        for _, meth in ipairs(METHODS) do
            s(m .. "|closed|" .. meth, function() return f[meth](f) end)
            s(m .. "|closed|" .. meth .. "|v", function() return f[meth](f, 1) end)
        end
        print(m .. "|close2", pcall(function() return f:close() end))
    end
end

-- io.type 与 io.open 的返回值
print("type_nil", io.type(nil))
print("type_num", io.type(1))
print("type_std", io.type(io.stdout))
-- 打不开的文件：各模式
print("open_dir", pcall(io.open, "/tmp", "r"))
print("open_noent", pcall(io.open, "/tmp/fkio_no_such_file", "r"))
print("open_badmode", pcall(io.open, P, "rw"))
print("open_badmode2", pcall(io.open, P, ""))
print("open_badmode3", pcall(io.open, P, "rb"))
print("open_badmode4", pcall(io.open, P, "r+b"))


-- ============ 第二块：seek 的双参数矩阵 ============
-- 单参数时第二个值是 option，负数压根到不了偏移这条路
local OFFS = { -2, -1, 0, 1, 3, 5, 100, 2^31, -2^31, 1.5, "0", "x" }
for _, m in ipairs(MODES) do
    local f = io.open(P, m)
    if f ~= nil then
        for _, w in ipairs({ "set", "cur", "end" }) do
            for _, off in ipairs(OFFS) do
                s(m .. "|sk2|" .. w .. "|" .. tostring(off), function() return f:seek(w, off) end)
            end
        end
        f:close()
    end
end

-- ============ 第三块：写完立刻定位（缓冲里的字节算不算位置） ============
for _, m in ipairs({ "w", "a", "r+", "w+", "a+" }) do
    local f = io.open(P, m)
    if f ~= nil then
        s(m .. "|w|seekcur0", function() f:write("hello") return f:seek("cur", 0) end)
        s(m .. "|w|seekend0", function() f:write("hello") return f:seek("end", 0) end)
        s(m .. "|w|seekset", function() f:write("hello") return f:seek("set") end)
        s(m .. "|w|flush", function() f:write("hello") return f:flush() end)
        s(m .. "|w|seekcur0b", function() f:write("hello") f:flush() return f:seek("cur", 0) end)
        s(m .. "|w|reada", function() f:write("hello") f:flush() f:seek("set", 0) return f:read("a") end)
        s(m .. "|w|readn", function() f:write("12") f:flush() f:seek("set", 0) return f:read("n") end)
        f:close()
    end
end

-- ============ 第四块：read 的多格式 / 多参数 / lines 迭代 ============
do
    local w = io.open(P, "w")
    w:write("1 2\\nthree\\n")
    w:close()
    local f = io.open(P, "r")
    if f then
        s("rd|a", function() return f:read("a") end)
        f:seek("set", 0)
        s("rd|l", function() return f:read("l") end)
        s("rd|L", function() return f:read("L") end)
        f:seek("set", 0)
        s("rd|n", function() return f:read("n") end)
        s("rd|n2", function() return f:read("n") end)
        s("rd|n3", function() return f:read("n") end)
        f:seek("set", 0)
        s("rd|num", function() return f:read(3) end)
        s("rd|num0", function() return f:read(0) end)
        s("rd|eof0", function() f:read("a") return f:read(0) end)
        s("rd|multi", function() f:seek("set", 0) return f:read("l", "L", "a", 2) end)
        s("rd|bad", function() return f:read("z") end)
        s("rd|star", function() f:seek("set", 0) return f:read("*l") end)
        s("rd|empt", function() return f:read("") end)
        s("rd|lines", function() f:seek("set", 0) local t = {} for l in f:lines() do t[#t + 1] = l end return table.concat(t, "|") end)
        s("rd|linesL", function() f:seek("set", 0) local t = {} for l in f:lines("L") do t[#t + 1] = l end return table.concat(t, "|") end)
        s("rd|linesn", function() f:seek("set", 0) local t = {} for l in f:lines("n") do t[#t + 1] = tostring(l) end return table.concat(t, "|") end)
        s("rd|lines3", function() f:seek("set", 0) local t = {} for l in f:lines(3) do t[#t + 1] = l end return table.concat(t, "|") end)
        s("rd|linesbad", function() f:seek("set", 0) local ok, e = pcall(function() for l in f:lines("z") do end end) return ok, e end)
        f:close()
    end
end

-- ============ 第五块：write 的多参数 ============
for _, m in ipairs({ "w", "a", "r+", "w+", "a+" }) do
    local f = io.open(P, m)
    if f then
        s(m .. "|wr|multi", function() return f:write("a", 1, 2.5, "b") end)
        s(m .. "|wr|num", function() return f:write(1) end)
        s(m .. "|wr|bool", function() return f:write("a", true) end)
        s(m .. "|wr|nil", function() return f:write("a", nil) end)
        s(m .. "|wr|empty", function() return f:write("") end)
        s(m .. "|wr|nada", function() return f:write() end)
        f:close()
    end
end


-- ============ 第六块：目录句柄 ============
-- io.open 目录在两边都成功（C 的 fopen 不拦），真正现形的是读/定位/写
local d = io.open("/tmp", "r")
if d then
    print("dir|type", io.type(d))
    s("dir|reada", function() return d:read("a") end)
    s("dir|readl", function() return d:read("l") end)
    s("dir|readn", function() return d:read("n") end)
    s("dir|read3", function() return d:read(3) end)
    s("dir|read0", function() return d:read(0) end)
    s("dir|seekcur0", function() return d:seek("cur", 0) end)
    s("dir|seekset0", function() return d:seek("set", 0) end)
    s("dir|seekneg", function() return d:seek("set", -1) end)
    s("dir|seekcur1", function() return d:seek("cur", 1) end)
    s("dir|seekcur7", function() return d:seek("cur", 7) end)
    s("dir|seekneg2", function() return d:seek("cur", -1) end)
    s("dir|flush", function() return d:flush() end)
    s("dir|write", function() return d:write("x") end)
    s("dir|setvbuf", function() return d:setvbuf("no") end)
    s("dir|lines", function() local ok, e = pcall(function() for l in d:lines() do end end) return ok, e end)
    s("dir|close", function() return d:close() end)
    print("dir|type2", io.type(d))
end


-- ============ 第七块：另开一个句柄去看 flush 到底有没有把字节推出去 ============
-- 只有这一个办法能看见 flush 里的 flushW：seek 自己也 flush，会把痕迹抹掉
for _, m in ipairs({ "w", "a", "w+", "r+" }) do
    local f = io.open(P, m)
    if f then
        f:write("hello")
        s(m .. "|x|nof", function() return (io.open(P, "r")):read("a") end)
        s(m .. "|x|fl", function() f:flush() return (io.open(P, "r")):read("a") end)
        f:close()
    end
    local f2 = io.open(P, m)
    if f2 then
        f2:write("XY")
        s(m .. "|x|nof2", function() return (io.open(P, "r")):read("a") end)
        s(m .. "|x|sk", function() f2:seek("cur", 0) return (io.open(P, "r")):read("a") end)
        f2:close()
    end
end


-- ============ 第八块：ungetc 退回来的那个字节要能被下一次 read 拿到 ============
do
    local w = io.open(P, "w")
    w:write("three\\n")
    w:close()
    local f = io.open(P, "r")
    if f then
        s("pb|n", function() return f:read("n") end)
        s("pb|1", function() return f:read(1) end)
        s("pb|1b", function() return f:read(1) end)
        s("pb|1c", function() return f:read(1) end)
        s("pb|rest", function() return f:read("a") end)
        f:close()
    end
end

-- ============ 第九块：popen 句柄也要真关（closeRaw 的 in / out 两支） ============
do
    local f = io.popen("echo hi", "r")
    if f then
        print("po|type", io.type(f))
        s("po|reada", function() return f:read("a") end)
        s("po|readl", function() return f:read("l") end)
        s("po|seek", function() return f:seek("set", 0) end)
        s("po|flush", function() return f:flush() end)
        s("po|write", function() return f:write("x") end)
        s("po|setvbuf", function() return f:setvbuf("full") end)
        print("po|close", f:close())
        print("po|type2", io.type(f))
    end
    local g = io.popen("cat > /dev/null", "w")
    if g then
        print("po2|type", io.type(g))
        s("po2|write", function() return g:write("abc") end)
        s("po2|read", function() return g:read("l") end)
        s("po2|seek", function() return g:seek("cur", 0) end)
        s("po2|flush", function() return g:flush() end)
        print("po2|close", g:close())
        print("po2|type2", io.type(g))
    end
end

os.remove(P)
print("done")
            """);
    }
}
