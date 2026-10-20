package com.github.esrrhs.javalua;

/**
 * 快速烟雾测试：验证 Node 内联优化后表操作正常
 */
public class NodeInlineSmokeTest {
    public static void main(String[] args) {
        System.out.println("=== Node 内联优化烟雾测试 ===");

        LuaState L = new LuaState();
        LuaLibs.openAll(L);

        // 测试 1: 基本表操作
        String test1 = """
            local t = {}
            t.x = 1
            t.y = 2
            t.z = 3
            assert(t.x == 1)
            assert(t.y == 2)
            assert(t.z == 3)
            print("测试 1 通过: 基本表操作")
            """;
        runScript(L, test1);

        // 测试 2: 哈希冲突
        String test2 = """
            local t = {}
            for i = 1, 100 do
                t["key" .. i] = i
            end
            for i = 1, 100 do
                assert(t["key" .. i] == i)
            end
            print("测试 2 通过: 哈希冲突")
            """;
        runScript(L, test2);

        // 测试 3: rehash
        String test3 = """
            local t = {}
            for i = 1, 1000 do
                t[i] = i * 2
            end
            for i = 1, 1000 do
                assert(t[i] == i * 2)
            end
            print("测试 3 通过: rehash")
            """;
        runScript(L, test3);

        // 测试 4: pairs 遍历
        String test4 = """
            local t = {a=1, b=2, c=3}
            local count = 0
            for k, v in pairs(t) do
                count = count + 1
            end
            assert(count == 3)
            print("测试 4 通过: pairs 遍历")
            """;
        runScript(L, test4);

        // 测试 5: 弱表
        String test5 = """
            local t = setmetatable({}, {__mode = "v"})
            local v = {1, 2, 3}
            t[1] = v
            v = nil
            collectgarbage()
            assert(t[1] == nil)
            print("测试 5 通过: 弱表")
            """;
        runScript(L, test5);

        System.out.println("\n=== 所有测试通过 ===");
    }

    private static void runScript(LuaState L, String src) {
        byte[] bytes = src.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        L.pushClosure(L.load(bytes, "test"));
        int func = L.getTop() - 1;
        LuaValue err = L.pcall(func, 0, L.stack[func]);
        if (err != null) {
            System.err.println("脚本执行失败: " + err);
            System.exit(1);
        }
    }
}
