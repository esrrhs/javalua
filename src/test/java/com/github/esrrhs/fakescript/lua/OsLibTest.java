package com.github.esrrhs.fakescript.lua;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * os 库的差分用例 —— 阶段 7。
 * <p>
 * os 库天生平台相关，所以选例的原则是「只比对平台无关的那部分」：
 * 所有 {@code os.date} / {@code os.time} 用例都用<b>固定的时间戳</b>，
 * 且尽量走 {@code !}（UTC）；用到本地时区的地方，参考实现与本实现跑在同一台
 * 机器上、同一个 {@code TZ}，所以结果也必须一致。
 * <p>
 * 几处专门盯着写：
 * <ul>
 *   <li><b>{@code strftime} 的每个转换符</b>逐个比对，包括 {@code E} / {@code O}
 *       修饰、{@code %U} / {@code %W} / {@code %V} 三种「第几周」的差别、
 *       {@code %e} 的空格补位、{@code %I} 的 0 点算 12。</li>
 *   <li><b>{@code os.time} 的归一化</b>：{@code month=13, day=32, hour=25} 会被
 *       正规化，而且结果<b>回写进传入的表</b>（{@code wday} / {@code yday} /
 *       {@code isdst} 会被填出来）。</li>
 *   <li><b>{@code luaL_fileresult} 的三元组</b>：{@code os.remove} 失败时给
 *       {@code nil, "<路径>: No such file or directory", 2}，成功时<b>只</b>给
 *       {@code true}（不是三元组）。</li>
 *   <li><b>{@code os.execute} 成功也返回三个值</b>：{@code true, "exit", 0}。</li>
 * </ul>
 * <p>
 * 不比对的：{@code os.clock} 的数值（只比类型）、{@code os.tmpname} 的路径、
 * 无参 {@code os.time} / {@code os.date} 的当前时刻。
 */
class OsLibTest {

    @TestFactory
    List<DynamicTest> osScriptsMatchReference() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "fmt", FORMAT);
        add(tests, "time", TIME);
        add(tests, "file", FILES);
        add(tests, "err", ERRORS);
        add(tests, "errno", ERRNO);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String tag, String[] srcs) {
        for (int i = 0; i < srcs.length; i++) {
            String src = srcs[i];
            int n = i;
            tests.add(DynamicTest.dynamicTest(tag + "[" + n + "]", () -> Diff.assertMatches(src)));
        }
    }

    private static final String[] FORMAT = {

            """
            local t = 1700000000  -- 2023-11-14 22:13:20 UTC
            local one = {"a","A","b","B","c","C","d","D","e","F","g","G","h","H","I","j"}
            local r = {}
            for _,o in ipairs(one) do r[#r+1] = o.."=["..os.date("!%"..o, t).."]" end
            print(table.concat(r, " "))
            local two = {"m","M","n","p","r","R","S","t","T","u","U","V","w","W","x","X"}
            r = {}
            for _,o in ipairs(two) do r[#r+1] = o.."=["..os.date("!%"..o, t).."]" end
            print(table.concat(r, " "))
            -- %z 不带 '!' 才比：macOS 的 strftime 在 gmtime 下给的 %z 是本地
            -- 标准偏移而不是 +0000（实测 TZ=America/New_York 时 !%z 给 -0500
            -- 而 %z 给 -0400），那是平台 bug，本实现按 POSIX 给 +0000
            local three = {"y","Y","Z","%"}
            r = {}
            for _,o in ipairs(three) do r[#r+1] = o.."=["..os.date("!%"..o, t).."]" end
            print(table.concat(r, " "))
            """,

            """
            local t = 1700000000
            local eo = {"Ec","EC","Ex","EX","Ey","EY","Od","Oe","OH","OI","Om","OM","OS"}
            local r = {}
            for _,o in ipairs(eo) do r[#r+1] = o.."=["..os.date("!%"..o, t).."]" end
            print(table.concat(r, " "))
            local eo2 = {"Ou","OU","OV","Ow","OW","Oy"}
            r = {}
            for _,o in ipairs(eo2) do r[#r+1] = o.."=["..os.date("!%"..o, t).."]" end
            print(table.concat(r, " "))
            """,

            """
            print(os.date("!%c", 0))
            print(os.date("!%F %T", 0))
            print(os.date("!%F %T", 86399))
            print(os.date("!%F %T", 951782400))
            print(os.date("!%F %T", -1))
            print(os.date("!%F %T", -86400))
            print(os.date("!%e", os.time{year=2023, month=1, day=5}))
            print(os.date("!%j", os.time{year=2023, month=1, day=1}))
            print(os.date("!%j", os.time{year=2020, month=12, day=31}))
            """,

            """
            -- %I 在 0 点与 12 点的表现，以及 %p 的翻转点
            local r = {}
            for h = 0, 23, 3 do
              local t = os.time{year=2023, month=6, day=15, hour=h, min=5, sec=9}
              r[#r+1] = os.date("!%H=%I%p", t)
            end
            print(table.concat(r, " "))
            print(os.date("!%I %p", os.time{year=2023, month=6, day=15, hour=12}))
            """,

            """
            -- U / W / V 三种周编号在跨年处各不相同
            local days = {{2023,1,1},{2021,1,1},{2016,1,3},{2019,12,30},{2020,12,31},{2024,2,29}}
            for _, d in ipairs(days) do
              local t = os.time{year=d[1], month=d[2], day=d[3], hour=12}
              print(d[1].."-"..d[2].."-"..d[3], os.date("!%U %W %V %G %g", t))
            end
            """,

            """
            print(os.date("!*t", 0).year, os.date("!*t", 0).month, os.date("!*t", 0).day)
            print(os.date("!*t", 0).wday, os.date("!*t", 0).yday, os.date("!*t", 0).isdst)
            local d = os.date("!*t", 1700000000)
            print(d.year, d.month, d.day, d.hour, d.min, d.sec, d.wday, d.yday, d.isdst)
            local n = 0
            for k in pairs(d) do n = n + 1 end
            print(n)
            """,

            """
            -- 本地时区：本实现与参考实现跑在同一台机器上，结果必须一致
            local t = 1700000000
            print(os.date("%Y-%m-%d %H:%M:%S", t))
            print(os.date("%z", t), os.date("%Z", t))
            local d = os.date("*t", t)
            print(d.year, d.month, d.day, d.hour, d.min, d.sec, d.wday, d.yday, d.isdst)
            """,

            """
            -- 数字会被当成格式串；非转换字符原样输出
            print(os.date(42, 0))
            print(os.date("abc", 0))
            print(os.date("", 0))
            print(os.date("100%%", 0))
            print(os.date("!%Y-%m-%dT%H:%M:%S", 1700000000))
            """,
    };

    private static final String[] TIME = {

            """
            local tt = {year=2023, month=11, day=14, hour=22, min=13, sec=20}
            local e = os.time(tt)
            print(e, math.type(e))
            print(tt.year, tt.month, tt.day, tt.hour, tt.min, tt.sec, tt.wday, tt.yday, tt.isdst)
            """,

            """
            -- 归一化：13 月 32 日 25 点 61 分 61 秒
            local t = {year=2023, month=13, day=32, hour=25, min=61, sec=61}
            print(os.time(t))
            print(t.year, t.month, t.day, t.hour, t.min, t.sec, t.wday, t.yday, t.isdst)
            """,

            """
            local t = {year=2020, month=2, day=29, hour=12}
            print(os.time(t), t.month, t.day, t.yday)
            local u = {year=2023, month=2, day=28, hour=12}
            print(os.time(u), u.yday)
            local v = {year=2024, month=12, day=31, hour=23, min=59, sec=59}
            print(os.time(v), v.yday)
            """,

            """
            -- hour/min/sec 有默认值：12:00:00
            print(os.time{year=2023, month=11, day=14} == os.time{year=2023, month=11, day=14, hour=12})
            print(os.time{year=2023, month=11, day=14, hour=0})
            print(math.type(os.time{year=1970, month=1, day=1, hour=0, min=0, sec=0}))
            """,

            """
            print(os.difftime(100, 50), math.type(os.difftime(100, 50)))
            print(os.difftime(50, 100))
            print(os.difftime(0, 0))
            print(math.type(os.time()))
            print(os.time() > 1600000000)
            print(math.type(os.clock()))
            print(os.clock() >= 0)
            """,

            """
            -- 本时区没有夏令时，isdst=false 与自动判定应当一致
            local a = os.time{year=2023, month=11, day=14, hour=22, min=13, sec=20}
            local b = os.time{year=2023, month=11, day=14, hour=22, min=13, sec=20, isdst=false}
            print(a == b)
            """,
    };

    private static final String[] FILES = {

            """
            print(os.execute())
            print(os.execute("true"))
            print(os.execute("false"))
            print(os.execute("exit 7"))
            """,

            """
            print(os.getenv("PATH") ~= nil)
            print(os.getenv("__NO_SUCH_ENV_VAR_XYZ__"))
            print(pcall(os.getenv))
            print(pcall(os.getenv, 42))
            """,

            """
            -- tmpname 建出来的文件是存在的；路径本身含随机部分，只比目录前缀
            local n = os.tmpname()
            print(n:sub(1, 5))
            print(os.remove(n))
            print(select(3, os.remove(n)))   -- 只比 errno，消息里含随机路径
            """,

            """
            local a = os.tmpname()
            local b = a .. ".moved"
            print(os.rename(a, b))
            print(os.remove(b))
            print(os.rename(a, b))   -- 源已经不在了
            """,

            """
            local n = os.tmpname()
            print(type(n), #n > 0)
            """,

            """
            print(os.setlocale())
            print(os.setlocale("C"))
            print(os.setlocale("POSIX"))
            print(os.setlocale("xx_XX"))
            print(os.setlocale(nil, "time"))
            print(os.setlocale(nil, "all"))
            """,
    };

    private static final String[] ERRORS = {

            """
            print(pcall(os.date, "!%Q", 0))
            print(pcall(os.date, "%", 0))
            print(pcall(os.date, "%QQ", 0))
            print(pcall(os.date, "!%Z%Q", 0))
            print(pcall(os.date, 42, 2^62))
            """,

            """
            print(pcall(os.time, "x"))
            print(pcall(os.time, 42))
            print(pcall(os.time, {}))
            print(pcall(os.time, {year="x"}))
            print(pcall(os.time, {year=2023, month="y"}))
            print(pcall(os.time, {year=2023, month=1}))
            print(pcall(os.time, {year=2023, month=1, day=1, hour=1.5}))
            print(pcall(os.time, {year=2^62, month=1, day=1}))
            """,

            """
            print(pcall(os.difftime, 1))
            print(pcall(os.rename, "/tmp/__fk_only_one__"))
            print(pcall(os.remove))
            print(pcall(os.setlocale, nil, "bogus"))
            print(pcall(os.execute, 42))
            """,

            """
            -- 元方法也要生效：字段读走 __index、回写走 __newindex
            local store = {year=2023, month=11, day=14, hour=22, min=13, sec=20}
            local written = {}
            local t = setmetatable({}, {
                __index = function(_, k) return store[k] end,
                __newindex = function(_, k, v) written[k] = v end,
            })
            print(os.time(t))
            local keys = {}
            for k in pairs(written) do keys[#keys+1] = k end
            table.sort(keys)
            print(table.concat(keys, ","))
            print(written.year, written.month, written.wday ~= nil, written.yday ~= nil)
            """,
    };

    /**
     * {@code os.remove} / {@code os.rename} 的 <b>errno 形状表</b>（22 行）。
     * 也是照着 {@code LuaAux} 的未覆盖行点名打的 —— 那些
     * {@code errnoOf} / {@code strError} 里 {@code ENOTEMPTY} / {@code EACCES}
     * 之类的分支从来没走到过。
     * <p>
     * 咬出<b>五处</b>：
     * <ul>
     *   <li><b>{@code ENOTEMPTY} 的数值是操作系统定的</b>：Linux 39、macOS/BSD 66。
     *       这里写死 39，于是 {@code os.remove(非空目录)} 消息对了、数字差 27。
     *       改成按 {@code os.name} 取值（{@code LuaAux.ENOTEMPTY}）。</li>
     *   <li><b>{@code strError} 不认 66</b>：只补了数字的话会「数字 66 对、
     *       消息却是 Input/output error」—— 数值与文本得一起改。</li>
     *   <li><b>{@code os.remove("")} </b>：C 给 {@code ENOENT}(2)，我们给
     *       {@code EIO}(5)。与 {@code loadfile("")} 同一类「C 与 Java 判路径
     *       不一致」的角落。</li>
     *   <li><b>{@code os.rename} 的形状表整个没对</b>：文件 → 已存在的目录是
     *       {@code EISDIR}(21)、目录 → 已存在的文件是 {@code ENOTDIR}(20)、
     *       目录 → <b>空</b>目录是<b>成功</b>（替换）、文件 → 已存在的文件也是
     *       替换。之前一律走不带 {@code REPLACE_EXISTING} 的 {@code Files.move}，
     *       于是「目标已存在」全报 {@code EEXIST}。</li>
     * </ul>
     * 一处不比：{@code os.remove("/etc")} 在本机是 {@code EPERM}(1)，取决于
     * SIP 与这台机器的权限，不是 Lua 的事实。改用 {@code chmod 000} 的目录来走
     * {@code EACCES}(13) 那条路。
     */
    private static final String[] ERRNO = {"""
        -- os.remove / os.rename 的 errno 分支：ENOTEMPTY / EACCES / EEXIST / ENOENT
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

        local D = "/tmp/fkerm"
        os.execute("rm -rf " .. D .. " && mkdir -p " .. D)
        local function put(p, body)
          local f = assert(io.open(p, "w")); f:write(body or "x"); f:close()
        end

        -- remove
        os.execute("mkdir -p " .. D .. "/empty " .. D .. "/full && touch " .. D .. "/full/inner")
        probe("rm_missing", os.remove, D .. "/nope_xyz")
        probe("rm_file", function() put(D .. "/f1"); return os.remove(D .. "/f1") end)
        probe("rm_emptydir", os.remove, D .. "/empty")
        probe("rm_fulldir", os.remove, D .. "/full")          -- ENOTEMPTY(39)
        probe("rm_root", os.remove, "/etc/hosts")             -- EACCES(13)
        -- 目录权限不足（chmod 000）时删它里面的文件：EACCES(13)。
        -- 不用 os.remove("/etc")：那条在本机是 EPERM(1)，取决于 SIP 与这台机器的权限，
        -- 不是 Lua 的事实，无法建模也不该进比对。
        probe("rm_noperm", function()
          os.execute("mkdir -p " .. D .. "/np && touch " .. D .. "/np/f && chmod 000 " .. D .. "/np")
          local r = table.pack(os.remove(D .. "/np/f"))
          os.execute("chmod 755 " .. D .. "/np")
          return table.unpack(r, 1, r.n)
        end)
        probe("rm_noname", os.remove)
        probe("rm_nil", os.remove, nil)
        probe("rm_num", os.remove, 42)
        probe("rm_empty", os.remove, "")

        -- rename
        put(D .. "/a")
        probe("rn_ok", function() return os.rename(D .. "/a", D .. "/b") end)
        -- 文件 -> 已存在的文件：C 的 rename 是替换
        probe("rn_over_file", function()
          put(D .. "/x1", "1")
          put(D .. "/x2", "2")
          local ok = os.rename(D .. "/x1", D .. "/x2")
          local g = io.open(D .. "/x2")
          local c = g and g:read("a") or "gone"
          if g then g:close() end
          local still = io.open(D .. "/x1")
          if still then still:close() end
          return ok, c, still ~= nil
        end)
        put(D .. "/c")
        os.execute("mkdir -p " .. D .. "/d")
        probe("rn_file_over_dir", os.rename, D .. "/c", D .. "/d")     -- EISDIR / ENOTDIR?
        put(D .. "/e")
        os.execute("mkdir -p " .. D .. "/f/g")
        probe("rn_dir_over_fulldir", os.rename, D .. "/f", D .. "/d")  -- ENOTEMPTY(39)
        probe("rn_missing", os.rename, D .. "/nope_xyz", D .. "/z")
        probe("rn_same", function() put(D .. "/s"); return os.rename(D .. "/s", D .. "/s") end)
        probe("rn_to_root", function() put(D .. "/t"); return os.rename(D .. "/t", "/etc/nope_xyz") end)
        probe("rn_noargs", os.rename)
        probe("rn_nil", os.rename, nil, nil)
        probe("rn_num", os.rename, 42, 43)
        probe("rn_empty", os.rename, "", "")
        probe("rn_dir_to_file", function()
          os.execute("mkdir -p " .. D .. "/dd")
          put(D .. "/ff")
          return os.rename(D .. "/dd", D .. "/ff")
        end)

        os.execute("rm -rf " .. D)
        print(table.concat(out, "\\n"))
            """ };

    /**
     * {@code os.date} / {@code os.time} 的形状矩阵（337 行）。
     * <p>
     * 已有的 {@code FORMAT} 组只跑了 {@code !}（UTC）那一半；这里补齐另外半边：
     * <b>本地时区</b>的每个转换符，以及同一批转换符在<b>多个时间点</b>上的值
     * （{@code 0} / 2038 边界 / 1900 / {@code -1} / 公元 1 年），外加
     * 非法转换符的报错形状、{@code os.time} / {@code os.difftime} /
     * {@code os.setlocale} / {@code os.getenv} 的边角。
     * <p>
     * 咬出<b>一处真 bug</b>：{@code *t} 写成了「以 {@code *t} 开头」而 C 是
     * {@code strcmp(s, "*t") == 0}。于是 {@code os.date("*t x")} 本该走
     * strftime 那条路、把 {@code *} {@code t} 空格 {@code x} 全当字面量返回
     * {@code "*t x"}，本实现却返回了表。修的时候又带出第二个细节：
     * {@code strcmp} 见到 {@code \0} 就停，所以 {@code os.date("*t\0x")} 在 C 里
     * <b>仍然是取表</b> —— 只比长度不算完，得先截到第一个 NUL。
     * <p>
     * 敏感性：把 {@code LUA_STRFTIMEOPTIONS} 里 {@code E} / {@code O} 两字符那组
     * 删掉红 38 行；把 {@code *t} 退回前缀匹配红 1 行；把 {@code *t} 的长度
     * 改成用 {@code f.length}（不截 NUL）红 2 行。
     * <p>
     * 不进矩阵的：{@code !%z}（macOS 的 strftime 在 gmtime 下给的是<b>本地</b>
     * 偏移，glibc 给 {@code +0000}；本实现按 POSIX 给 {@code +0000}，只能在
     * Linux 上对得上）、以及无参的 {@code os.date} / {@code os.time}（墙上时钟）。
     */
    @Test
    void dateShapeMatrixMatchesReference() {
        Diff.assertMatches("""
                
                local T0 = 0
                local T1 = 1700000000
                local T2 = -946080000        -- 1940 前后
                local T3 = 2147483647        -- 2038 边界
                local T4 = -2208988800       -- 1900-01-01
                local T5 = -1
                
                local function show(r)
                    if type(r) == "table" then
                        return string.format("%d-%d-%d %d:%d:%d yday=%d wday=%d isdst=%s",
                            r.year, r.month, r.day, r.hour, r.min, r.sec, r.yday, r.wday,
                            tostring(r.isdst))
                    end
                    return tostring(r)
                end
                
                local function d(tag, fmt, t)
                    local ok, r = pcall(os.date, fmt, t)
                    print(tag, ok and show(r) or ("ERR " .. tostring(r)))
                end
                
                local function e(tag, f, ...)
                    local ok, r = pcall(f, ...)
                    print(tag, ok and show(r) or ("ERR " .. tostring(r)))
                end
                
                -- 单字符转换符：C99 那一组（照 LUA_STRFTIMEOPTIONS 抄）
                local singles = "aAbBcCdDeFgGhHIjmMnprRStTuUVwWxXyYzZ%"
                for i = 1, #singles do
                    local c = singles:sub(i, i)
                    d("S_" .. c, "%" .. c, T1)
                end
                -- 同一批时间戳下的 UTC（! 前缀）
                for i = 1, #singles do
                    local c = singles:sub(i, i)
                    -- %z / %Z 在 '!' 下两边 libc 不一致：macOS 的 strftime 用全局 timezone
                    -- （给本地偏移），glibc 用 tm_gmtoff（给 +0000 / GMT）。不进矩阵。
                    if c ~= "z" and c ~= "Z" then
                        d("U_" .. c, "!%" .. c, T1)
                    end
                end
                -- 另一个时间点：1970 与 2038 边界
                for i = 1, #singles do
                    local c = singles:sub(i, i)
                    d("Z_" .. c, "%" .. c, T0)
                    d("Y_" .. c, "%" .. c, T3)
                end
                -- 1900 与负数秒：zeroPad 的负数分支
                for i = 1, #singles do
                    local c = singles:sub(i, i)
                    if c ~= "z" and c ~= "Z" then
                        d("N_" .. c, "!%" .. c, T4)
                        d("M_" .. c, "!%" .. c, T5)
                    end
                end
                
                -- 两字符转换符（E / O 修饰）
                local doubles = { "Ec", "EC", "Ex", "EX", "Ey", "EY", "Od", "Oe", "OH", "OI",
                                  "Om", "OM", "OS", "Ou", "OU", "OV", "Ow", "OW", "Oy" }
                for _, c in ipairs(doubles) do
                    d("D_" .. c, "%" .. c, T1)
                    d("E_" .. c, "!%" .. c, T1)
                end
                
                -- *t 与 !*t
                d("t1", "*t", T1)
                d("t2", "!*t", T1)
                d("t3", "*t", T0)
                d("t4", "!*t", T0)
                d("t5", "*t", T2)
                d("t6", "!*t", T5)
                d("t7", "*t x", T1)
                d("t8", "%*t", T1)
                
                -- 非法转换符
                d("x1", "%q", T1)
                d("x2", "%E", T1)
                d("x3", "%O", T1)
                d("x4", "%", T1)
                d("x5", "abc%", T1)
                d("x6", "%Ez", T1)
                d("x7", "%Oq", T1)
                d("x8", "%s", T1)
                d("x9", "%k", T1)
                d("xa", "%l", T1)
                d("xb", "%%", T1)
                d("xc", "", T1)
                d("xd", "hello %Y world", T1)
                d("xe", "!", T1)
                d("xf", "!*", T1)
                
                -- 时间的形状
                d("v1", "%Y", "1700000000")
                d("v2", "%Y", 1700000000.0)
                d("v3", "%Y", 1700000000.5)
                d("v4", "%Y", true)
                d("v5", "%Y", {})
                d("v6", "%Y", 2^62)
                d("v7", "%Y", -2^62)
                d("v8", "%Y", 1e300)
                d("v9", "%Y", 0/0)
                e("vb", os.date, "%Y")
                d("vc", "%Y", -62135596800)   -- 公元 1 年
                d("vd", "!%C", -62135596800)
                
                -- os.time
                local function tt(tag, t)
                    e(tag, os.time, t)
                end
                tt("m1", { year = 2023, month = 11, day = 14, hour = 22, min = 13, sec = 20 })
                tt("m2", { year = 2023, month = 11, day = 14 })
                tt("m3", { year = 2023, month = 11, day = 14, isdst = true })
                tt("m4", { year = 2023, month = 11, day = 14, isdst = false })
                tt("m5", { year = 1970, month = 1, day = 1, hour = 0 })
                tt("m6", { year = 1900, month = 1, day = 1 })
                tt("m7", { year = 2023, month = 13, day = 1 })
                tt("m8", { year = 2023, month = 0, day = 1 })
                tt("m9", { year = 2023, month = 1, day = 32 })
                tt("ma", { year = 2023, month = 1 })
                tt("mb", { month = 1, day = 1 })
                tt("mc", { year = 2023, month = 1, day = "1" })
                tt("md", { year = 2023, month = 1, day = 1.5 })
                tt("me", { year = 2023, month = 1, day = true })
                tt("mf", { year = 2^40, month = 1, day = 1 })
                tt("mg", {})
                tt("mh", { year = 2023, month = 1, day = 1, hour = 25 })
                e("mj", os.time, 42)
                
                -- os.difftime / os.clock 之外的杂项
                e("n1", os.difftime, 100, 50)
                e("n2", os.difftime, 50, 100)
                e("n3", os.difftime, 0, 0)
                e("n4", os.difftime, "100", 50)
                e("n5", os.difftime)
                e("n6", os.difftime, 1)
                
                -- os.setlocale
                e("l1", os.setlocale)
                e("l2", os.setlocale, "C")
                e("l3", os.setlocale, "C", "all")
                e("l4", os.setlocale, "xx")
                e("l5", os.setlocale, nil, "bogus")
                e("l6", os.setlocale, "C", "bogus")
                e("l7", os.setlocale, 42)
                e("l8", os.setlocale, "C", "time")
                
                -- os.getenv
                e("g1", os.getenv, "NO_SUCH_VAR_XYZ_12345")
                e("g2", os.getenv, "PATH")
                e("g3", os.getenv, 42)
                e("g4", os.getenv, true)
                e("g5", os.getenv)
                
                -- 内嵌 \\0：C 的 strcmp 见到 \\0 就停
                d("w1", "*t\\0x", T1)
                d("w2", "!*t\\0x", T1)
                d("w3", "%Y\\0abc", T1)
                d("w4", "\\0*t", T1)
                print("done")
                """);
    }
}
