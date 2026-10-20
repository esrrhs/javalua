package com.github.esrrhs.fakescript.lua;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 测试小整数缓存的性能提升
 */
class IntegerCacheBenchmarkTest {

    @Test
    void benchmarkIntegerCache() {
        String src = """
            print("=== 小整数缓存性能测试 ===")
            print("Lua 版本: " .. _VERSION)
            print("")
            
            -- 测试 1: 大量小整数创建
            print("测试 1: 创建 100 万个小整数")
            local t = {}
            for i = 1, 1000000 do
                t[i] = i
            end
            print("  完成")
            print("  内存: " .. string.format("%.2f", collectgarbage("count")) .. " KB")
            t = nil
            collectgarbage("collect")
            
            -- 测试 2: 大量大整数创建
            print("")
            print("测试 2: 创建 100 万个大整数")
            t = {}
            for i = 1, 1000000 do
                t[i] = i + 10000
            end
            print("  完成")
            print("  内存: " .. string.format("%.2f", collectgarbage("count")) .. " KB")
            t = nil
            collectgarbage("collect")
            
            print("")
            print("=== 完成 ===")
            """;

        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
        L.out = ps;
        
        byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
        L.pushClosure(L.load(bytes, "benchmark"));
        int func = L.getTop() - 1;
        LuaValue err = L.pcall(func, 0, L.stack[func]);
        if (err != null) {
            System.err.println("Lua error: " + err);
        }
        
        System.out.println(bos.toString(StandardCharsets.UTF_8));
    }
}
