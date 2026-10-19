package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.Test;

/**
 * GC 基准测试（高精度版）：用毫秒级计时，多次 GC 取平均
 */
class GcPreciseBenchmarkTest {

    @Test
    void gcPreciseBenchmark() {
        String src = """
            -- GC 基准测试（高精度版）：用毫秒级计时
            local function now_ms()
                return os.clock() * 1000  -- 转为毫秒
            end

            print("GC 基准测试（高精度版）")
            print(string.format("Lua 版本: %s", _VERSION))
            print("")

            -- 构造阶段
            print("=== 构造阶段 ===")
            local target_nodes = 1000000
            print(string.format("目标节点数: %d", target_nodes))

            local start = now_ms()
            local nodes = {}

            for i = 1, target_nodes do
                nodes[i] = {
                    id = i,
                    name = "node_" .. i,
                    refs = {}
                }
                
                if i > 1 then
                    local ref_count = math.random(2, 5)
                    for j = 1, ref_count do
                        local ref_idx = math.random(1, i - 1)
                        table.insert(nodes[i].refs, nodes[ref_idx])
                    end
                end
            end

            local build_time = now_ms() - start
            print(string.format("构造完成: %d 节点, 耗时 %.0f ms", target_nodes, build_time))

            -- 内存报告
            print("\\n=== 内存报告 ===")
            collectgarbage("collect")
            local mem_before = collectgarbage("count")
            print(string.format("GC 前内存: %.2f KB", mem_before))

            -- GC 测试（多次取平均）
            print("\\n=== GC 测试阶段 ===")
            local gc_times = {}
            for run = 1, 5 do
                local gc_start = now_ms()
                collectgarbage("collect")
                local gc_time = now_ms() - gc_start
                table.insert(gc_times, gc_time)
                print(string.format("  第 %d 次 GC: %.2f ms", run, gc_time))
            end

            local mem_after = collectgarbage("count")
            print(string.format("GC 后内存: %.2f KB", mem_after))
            print(string.format("回收: %.2f KB", mem_before - mem_after))

            -- 计算平均 GC 时间
            local total_gc = 0
            for _, t in ipairs(gc_times) do
                total_gc = total_gc + t
            end
            local avg_gc = total_gc / #gc_times

            -- 汇总
            print("\\n=== 汇总 ===")
            print(string.format("构造耗时: %.0f ms", build_time))
            print(string.format("平均 GC 耗时: %.2f ms", avg_gc))
            print(string.format("节点数: %d", target_nodes))

            -- 清理
            nodes = nil
            collectgarbage("collect")
            """;
        
        Diff.assertFullMatches(src);
    }
}
