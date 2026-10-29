package com.github.esrrhs.javalua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 表结构快照差分 —— 数组部分的「按位精确」。
 * <p>
 * 与 {@link RuntimeTest#arrayBookkeepingMatchesReference()} 的分工：那边比的是
 * {@code #t} / {@code rawlen}，也就是表结构的<b>可观测</b>投影；这里比的是
 * <b>结构体本身</b>——数组真实尺寸、{@code alimit}、提示位（{@code BITRAS}）、
 * 哈希节点的分配尺寸。
 * <p>
 * 为什么这层证据不能省：两份表可以给出相同的 {@code #t}，而 {@code alimit} 与
 * 真实尺寸已经分岔。C 5.4.7 的 {@code luaH_getn}（{@code ltable.c}）从
 * {@code alimit} 起步、又会在 (1a)/(1b) 两条出口把 {@code alimit} 改写成<b>提示</b>
 * 并置 {@code setnorealasize}；{@code luaH_getint} 读键时还会把提示抬到该键。
 * 起步点与提示位不同，落在的合法边界就可能不同——那是真实程序看得见的差别，
 * 但只在「先构造、再删填、过一阵才读 {@code #t}」的历史里露头。可观测投影比不出
 * 这一步是「一样」还是「凑巧一样」。
 * <p>
 * 参考侧是 {@code src/test/resources/tblstruct.c}（与 {@code fkdump.c} 同一类，
 * CI 跟参考实现一起编）。语料见 {@link #CHUNK}：构造器各档尺寸、构造后补/删键、
 * 提示位会被 {@code luaH_getn} 改写的那几条路径、{@code table.insert}/{@code remove}，
 * 外加一段<b>确定性</b> LCG 驱动的随机构造历史（参考实现没有 {@code T} 模块，
 * 不能用 {@code math.random}：它按时间播种，两个进程不可能一样）。
 */
final class TableStructTest {

    /** 落到 {@link Diff#SCRATCH} 下的文件名（每个 JVM 一份，见 {@link Diff#SCRATCH}） */
    private static final String FILE_NAME = "tblstruct.lua";

    @BeforeAll
    static void requireDumper() {
        assumeTrue(ReferenceLua.findTblstruct() != null,
                "找不到 tblstruct 转储器，跳过表结构快照差分（设 TBLSTRUCT_BIN 指向它）");
    }

    /**
     * 语料。用局部计数 {@code n} 而不是 {@code #R}：读长度会改写被读表的
     * {@code alimit} 提示，那会把「快照」变成「快照前先动过手脚」。
     * <p>
     * 每条都把 {@code t} 交给 {@code add} 存进 {@code R}，最后两个全局变量
     * （{@code TBLSTRUCT_COUNT} / {@code TBLSTRUCT_RESULT}）是 {@code tblstruct.c}
     * 认的交接口。
     */
    private static final String CHUNK = """
            local R, n = {}, 0
            local function add(t) n = n + 1; R[n] = t end

            -- 1) 构造器路径：数组部分的各档尺寸
            add({})
            add({1})
            add({1, 2})
            add({1, 2, 3})
            add({1, 2, 3, 4, 5, 6, 7, 8})
            add({1, 2, 3, 4, 5, 6, 7, 8, 9})
            add({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16})
            add({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17})
            add({x = 1})
            add({1, 2, x = 3})
            add({[1] = 1, [2] = 2, [3] = 3})
            add({[1] = 1, [3] = 3})
            add({[10] = 10})
            add({a = 1, b = 2, c = 3, d = 4, e = 5})
            add(table.pack(1, 2, 3))
            add(table.pack(1, nil, 3))
            add(table.pack())

            -- 2) 构造之后往更大 / 更小的键上写
            do local t = {1, 2, 3}; t[4] = 4; add(t) end
            do local t = {1, 2, 3}; t[5] = 5; add(t) end
            do local t = {1, 2, 3}; t[10] = 10; add(t) end
            do local t = {1, 2, 3}; t[3] = nil; add(t) end
            do local t = {1, 2, 3}; t[1] = nil; add(t) end
            do local t = {1, 2, 3}; t[3] = nil; t[4] = 4; t[5] = 5; add(t) end

            -- 3) 提示位会被 luaH_getn 改写的路径：先动表，再读 #t，再快照
            do local t = {1, 2, 3, 4}; t[4] = nil; add(t) end
            do local t = {1, 2, 3, 4}; t[4] = nil; local _ = #t; add(t) end
            do local t = {1, 2, 3, 4}; t[3] = nil; local _ = #t; add(t) end
            do local t = {1,2,3,4,5,6,7,8}; t[8] = nil; t[7] = nil; local _ = #t; add(t) end
            do local t = {1,2,3,4,5,6,7,8}; t[8] = nil; local _ = #t; add(t) end
            do local t = {1,2,3,4,5,6,7,8}; t[7] = nil; local _ = #t; add(t) end
            do local t = {1,2,3,4,5,6,7,8,9}; t[9] = nil; local _ = #t; add(t) end

            -- 4) 逆序挖空 / 反序补键（顺序不同，alimit 会被顶到不同的位置）
            do local t = {1,2,3,4}; t[1]=nil; t[2]=nil; t[3]=nil; t[4]=nil; add(t) end
            do local t = {1,2,3,4}; t[4]=nil; t[3]=nil; t[2]=nil; t[1]=nil; add(t) end

            -- 5) table.insert / table.remove 走的 resize 路径
            do local t = {}; for i = 1, 17 do table.insert(t, i) end; add(t) end
            do local t = {}; for i = 1, 17 do table.insert(t, i) end
               for i = 1, 9 do table.remove(t) end; add(t) end
            do local t = {1,2,3}; table.remove(t, 1); add(t) end

            -- 6) 确定性 LCG：随机构造历史（构造器 + 写 + 删 + #t 打点）
            do
              local s = 12345
              local function rnd(m) s = (s * 1103515245 + 12345) % 2147483648; return s % m end
              for k = 1, 60 do
                local t = {}
                local sz = rnd(13)
                for i = 1, sz do t[i] = i end
                local steps = rnd(24)
                for j = 1, steps do
                  local key = 1 + rnd(20)
                  if rnd(3) == 0 then t[key] = nil else t[key] = key end
                end
                if rnd(2) == 0 then local _ = #t end
                add(t)
              end
            end

            TBLSTRUCT_COUNT = n
            TBLSTRUCT_RESULT = R
            """;

    @Test
    void tableStructsMatchReference() {
        byte[] bytes = CHUNK.getBytes(StandardCharsets.UTF_8);
        String path = Diff.SCRATCH + "/" + FILE_NAME;

        String oracle = ReferenceLua.dumpTableStructs(bytes, path);
        assertNotNull(oracle, "tblstruct 没有输出");
        assertTrue(!oracle.isEmpty(), "tblstruct 输出了空串，等于没测");

        String mine = mineStructs(bytes, path);
        /*
         * 逐行比，失败时把两份都带上——几百行的快照塞进 AssertionError 看不清
         * 从哪一行起不一样，所以顺手落到 SCRATCH 下供 diff。
         */
        if (!oracle.equals(mine)) {
            dump(path + ".ref", oracle);
            dump(path + ".mine", mine);
        }
        assertEquals(oracle, mine, "表结构快照与参考实现不一致；两份输出见 "
                + Diff.SCRATCH + "/" + FILE_NAME + ".ref 与 .mine");
    }

    /**
     * 在本实现上跑同一段语料，把 {@code TBLSTRUCT_RESULT} 里每张表的内部字段
     * 按 {@code tblstruct.c} 的格式打出来。
     * <p>
     * 读的是 {@code array.length} / {@code alimit} / {@code alimitIsReal} /
     * {@code nodeSize} 这几个字段本身，<b>不</b>走 {@link LuaTable#length()}
     * ——后者会像 C 的 {@code luaH_getn} 一样改写 {@code alimit} 提示，
     * 那就不是在快照，而是在快照前先动过手脚。
     */
    private static String mineStructs(byte[] bytes, String path) {
        LuaState L = new LuaState();
        try {
            LuaLibs.openAll(L);
            L.run(bytes, "@" + path);
            LuaValue countV = L.globals.get("TBLSTRUCT_COUNT");
            assertTrue(countV.isInteger(), "语料没有交出 TBLSTRUCT_COUNT");
            long n = countV.asInteger();
            LuaValue resV = L.globals.get("TBLSTRUCT_RESULT");
            assertTrue(resV.isTable(), "语料没有交出 TBLSTRUCT_RESULT");
            LuaTable result = resV.asTable();

            StringBuilder sb = new StringBuilder();
            for (long i = 1; i <= n; i++) {
                LuaValue v = result.get(i);
                if (!v.isTable()) {
                    // 与 tblstruct.c 的占位符同一串，让这一行当场对不上
                    sb.append("nontable\n");
                    continue;
                }
                LuaTable t = v.asTable();
                sb.append(Integer.toUnsignedString(t.array.length)).append(' ')
                        .append(Integer.toUnsignedString(t.alimit)).append(' ')
                        .append(t.alimitIsReal ? 1 : 0).append(' ')
                        .append(Integer.toUnsignedString(t.nodeSize)).append('\n');
            }
            return sb.toString();
        } finally {
            L.close();
        }
    }

    /** 落一份输出到 {@link Diff#SCRATCH}；只是调试产物，写失败不影响判定 */
    private static void dump(String name, String content) {
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(name),
                    content.getBytes(StandardCharsets.ISO_8859_1));
        } catch (java.io.IOException ignored) {
            // 落不下去就算了
        }
    }
}