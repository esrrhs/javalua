package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
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
}
