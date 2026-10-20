package com.github.esrrhs.fakescript.lua;

/**
 * 最简烟雾测试
 */
public class MinimalSmokeTest {
    public static void main(String[] args) {
        System.out.println("开始测试...");

        try {
            LuaState L = new LuaState();
            System.out.println("LuaState 创建成功");

            LuaLibs.openAll(L);
            System.out.println("库加载成功");

            // 测试 1: 简单赋值
            String src1 = "x = 1 + 2";
            runScript(L, src1, "测试 1");

            // 测试 2: 表创建
            String src2 = "t = {}";
            runScript(L, src2, "测试 2");

            // 测试 3: 表字段赋值
            String src3 = "t.x = 10";
            runScript(L, src3, "测试 3");

            // 测试 4: 表字段读取
            String src4 = "y = t.x";
            runScript(L, src4, "测试 4");

            System.out.println("\n所有测试通过！");
        } catch (Exception e) {
            System.err.println("异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runScript(LuaState L, String src, String name) {
        byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        L.pushClosure(L.load(bytes, name));
        int func = L.getTop() - 1;
        LuaValue err = L.pcall(func, 0, L.stack[func]);
        if (err != null) {
            System.err.println(name + " 失败: " + err);
            System.exit(1);
        }
        System.out.println(name + " 通过");
    }
}
