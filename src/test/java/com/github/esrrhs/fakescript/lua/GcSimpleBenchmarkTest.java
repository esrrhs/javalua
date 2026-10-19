package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.Test;

/**
 * GC 基准测试（简化版）：不依赖 collectgarbage("count")
 */
class GcSimpleBenchmarkTest {

    @Test
    void gcSimpleBenchmark() {
        String src = """
            -- GC 基准测试（简化版）：不依赖 collectgarbage("count")
            -- 只测量构造和 GC 的时间

            local function now()
                return os.clock()
            end

            print("GC 基准测试（简化版）")
            print(string.format("Lua 版本: %s", _VERSION))
            print("")

            -- 构造阶段：创建 100 万个节点
            print("=== 构造阶段 ===")
            local target_nodes = 1000000
            print(string.format("目标节点数: %d", target_nodes))

            local start = now()
            local nodes = {}

            for i = 1, target_nodes do
                local node = {
                    id = i,
                    name = string.format("node_%d_%s", i, string.rep("x", 50)),
                    value = math.random() * 1000,
                    refs = {}
                }
                
                -- 随机连接到之前的几个节点
                if i > 1 then
                    local ref_count = math.random(2, 5)
                    for j = 1, ref_count do
                        local ref_idx = math.random(1, i - 1)
                        table.insert(node.refs, nodes[ref_idx])
                    end
                end
                
                nodes[i] = node
                
                -- 每 10 万报告一次
                if i % 100000 == 0 then
                    local elapsed = now() - start
                    print(string.format("  进度: %d/%d (%.1f%%, %.2fs)", i, target_nodes, (i/target_nodes)*100, elapsed))
                end
            end

            local build_time = now() - start
            print(string.format("构造完成: %d 节点, 耗时 %.2fs", target_nodes, build_time))

            -- GC 测试阶段
            print("\\n=== GC 测试阶段 ===")
            collectgarbage("stop")

            print("开始完整 GC...")
            local gc_start = now()
            collectgarbage("collect")
            collectgarbage("collect")
            collectgarbage("collect")
            local gc_time = now() - gc_start

            print(string.format("GC 耗时: %.4fs", gc_time))

            collectgarbage("restart")

            -- 汇总
            print("\\n=== 汇总 ===")
            print(string.format("构造耗时: %.2fs", build_time))
            print(string.format("GC 耗时: %.4fs", gc_time))
            print(string.format("节点数: %d", target_nodes))

            -- 清理
            nodes = nil
            collectgarbage("collect")
            collectgarbage("collect")
            """;
        
        Diff.assertFullMatches(src);
    }
}
