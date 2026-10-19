-- GC 基准测试：构造 1GB 左右的网状对象结构，测量完整 GC 一轮的时间
-- 目标：对比参考 Lua 和 fakescript_java 的 GC 性能

local function now()
    return os.clock()
end

local function format_bytes(n)
    if n >= 1024*1024*1024 then
        return string.format("%.2f GB", n / (1024*1024*1024))
    elseif n >= 1024*1024 then
        return string.format("%.2f MB", n / (1024*1024))
    elseif n >= 1024 then
        return string.format("%.2f KB", n / 1024)
    else
        return string.format("%d B", n)
    end
end

-- 构造阶段：创建大量小对象并建立网状连接
local function build_mesh(target_bytes)
    print("=== 构造阶段 ===")
    print(string.format("目标内存: %s", format_bytes(target_bytes)))
    
    local start = now()
    local nodes = {}
    local total_bytes = 0
    local node_count = 0
    
    -- 每个节点大约占 200-300 字节（表 + 几个字段 + 字符串）
    -- 要凑 1GB 大约需要 400-500 万个节点
    local estimated_node_size = 250
    local target_nodes = math.floor(target_bytes / estimated_node_size)
    
    print(string.format("预计节点数: %d", target_nodes))
    
    -- 分批创建，每批 10000 个，避免一次性占满内存
    local batch_size = 10000
    local last_report = 0
    
    while node_count < target_nodes do
        local batch_end = math.min(node_count + batch_size, target_nodes)
        
        for i = node_count, batch_end - 1 do
            -- 每个节点：一个表 + 几个字段 + 一个字符串
            local node = {
                id = i,
                name = string.format("node_%d_%s", i, string.rep("x", 50)),
                value = math.random() * 1000,
                refs = {}  -- 用于存储对其他节点的引用
            }
            
            -- 随机连接到之前的几个节点（形成网状结构）
            if i > 0 then
                local ref_count = math.random(2, 5)
                for j = 1, ref_count do
                    local ref_idx = math.random(0, i - 1)
                    table.insert(node.refs, nodes[ref_idx + 1])
                end
            end
            
            nodes[i + 1] = node
            node_count = node_count + 1
        end
        
        -- 每 10 万节点报告一次进度
        if node_count - last_report >= 100000 then
            local elapsed = now() - start
            local pct = (node_count / target_nodes) * 100
            collectgarbage("count")  -- 获取当前内存
            local mem = collectgarbage("count") * 1024
            print(string.format("  进度: %.1f%% (%d/%d 节点, %s, %.2fs)",
                pct, node_count, target_nodes, format_bytes(mem), elapsed))
            last_report = node_count
        end
    end
    
    local build_time = now() - start
    collectgarbage("count")
    local final_mem = collectgarbage("count") * 1024
    
    print(string.format("构造完成: %d 节点, %s, 耗时 %.2fs",
        node_count, format_bytes(final_mem), build_time))
    
    return nodes, build_time
end

-- GC 测试阶段：强制完整 GC 并计时
local function test_gc(nodes)
    print("\n=== GC 测试阶段 ===")
    
    -- 先停掉自动 GC，手动控制
    collectgarbage("stop")
    
    -- 强制一次完整 GC，清理构造阶段的垃圾
    print("清理构造垃圾...")
    collectgarbage("collect")
    collectgarbage("collect")
    
    local mem_before = collectgarbage("count") * 1024
    print(string.format("GC 前内存: %s", format_bytes(mem_before)))
    
    -- 计时：强制完整 GC
    print("开始完整 GC...")
    local start = now()
    
    -- 多轮 GC 确保所有可达对象都被扫描
    collectgarbage("collect")
    collectgarbage("collect")
    collectgarbage("collect")
    
    local gc_time = now() - start
    local mem_after = collectgarbage("count") * 1024
    
    print(string.format("GC 后内存: %s", format_bytes(mem_after)))
    print(string.format("GC 耗时: %.4fs", gc_time))
    print(string.format("回收: %s", format_bytes(mem_before - mem_after)))
    
    -- 恢复自动 GC
    collectgarbage("restart")
    
    return gc_time, mem_before, mem_after
end

-- 遍历测试：验证网状结构确实被正确连接
local function verify_mesh(nodes)
    print("\n=== 验证阶段 ===")
    local visited = {}
    local count = 0
    
    local function visit(node)
        if visited[node] then return end
        visited[node] = true
        count = count + 1
        
        for _, ref in ipairs(node.refs) do
            if ref then visit(ref) end
        end
    end
    
    -- 从第一个节点开始遍历
    if nodes[1] then
        visit(nodes[1])
    end
    
    print(string.format("可达节点: %d/%d (%.1f%%)",
        count, #nodes, (count / #nodes) * 100))
    
    return count
end

-- 主流程
local function main()
    print("GC 基准测试")
    print(string.format("Lua 版本: %s", _VERSION))
    print("")
    
    -- 目标 1GB
    local target_bytes = 1024 * 1024 * 1024
    
    -- 构造网状结构
    local nodes, build_time = build_mesh(target_bytes)
    
    -- 验证连接
    local reachable = verify_mesh(nodes)
    
    -- GC 测试
    local gc_time, mem_before, mem_after = test_gc(nodes)
    
    -- 汇总
    print("\n=== 汇总 ===")
    print(string.format("构造耗时: %.2fs", build_time))
    print(string.format("GC 耗时: %.4fs", gc_time))
    print(string.format("节点数: %d", #nodes))
    print(string.format("可达节点: %d", reachable))
    print(string.format("GC 前内存: %s", format_bytes(mem_before)))
    print(string.format("GC 后内存: %s", format_bytes(mem_after)))
    
    -- 清理
    nodes = nil
    collectgarbage("collect")
    collectgarbage("collect")
end

main()
