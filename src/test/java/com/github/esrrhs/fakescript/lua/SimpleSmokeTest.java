package com.github.esrrhs.fakescript.lua;

/**
 * 简单烟雾测试
 */
public class SimpleSmokeTest {
    public static void main(String[] args) {
        System.out.println("开始测试...");

        try {
            LuaState L = new LuaState();
            System.out.println("LuaState 创建成功");

            LuaLibs.openAll(L);
            System.out.println("库加载成功");

            String src = "print('Hello from Lua')";
            byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("脚本准备加载");

            L.pushClosure(L.load(bytes, "test"));
            System.out.println("脚本加载成功");

            int func = L.getTop() - 1;
            System.out.println("调用脚本...");

            LuaValue err = L.pcall(func, 0, L.stack[func]);
            if (err != null) {
                System.err.println("脚本执行失败: " + err);
            } else {
                System.out.println("脚本执行成功");
            }
        } catch (Exception e) {
            System.err.println("异常: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("测试完成");
    }
}
