import com.github.esrrhs.javalua.LuaError;
import com.github.esrrhs.javalua.LuaLibs;
import com.github.esrrhs.javalua.LuaState;
import com.github.esrrhs.javalua.LuaValue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 跑一个 {@code *.lua} 脚本并计时 —— 只用来在本机做量级比较，<b>不是闸门</b>。
 * <p>
 * 用法见本目录的 {@code README.md}。为什么这些数字进不了 CI，也写在那里。
 * <p>
 * 脚本自己的输出走 {@code stdout}：以前这里把 {@code L.out} 换成内存缓冲区，
 * 然后在 {@code close} 前才打印，于是 {@code pcall} 的错误被吞掉、缓冲区里
 * 又什么都没落下来，跑什么都不输出——测出来的时间也就没法信。
 */
public final class RunBenchmark {

    private RunBenchmark() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("用法: java RunBenchmark <script.lua>");
            System.exit(2);
            return;
        }
        Path script = Path.of(args[0]);
        byte[] src;
        try {
            src = Files.readAllBytes(script);
        } catch (Exception e) {
            System.err.println("读不了 " + script + ": " + e);
            System.exit(2);
            return;
        }
        // openAll 之后再测：建状态机和开库的开销不算进脚本时间
        LuaState L = new LuaState();
        LuaLibs.openAll(L);
        long t0 = System.nanoTime();
        int code = 0;
        try {
            LuaValue err = L.runProtected(src, "@" + script);
            if (err != null) {
                System.err.println("脚本以错误结束: " + err);
                code = 1;
            }
        } catch (LuaError e) {
            // runProtected 不抛；抛出来的说明是 JVM 侧的问题（比如堆不够）
            System.err.println("异常: " + e);
            code = 1;
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            /*
             * 只报时间，不报「用了多少内存」。C 那边 collectgarbage("count")
             * 数的是 Lua 分配器手里的字节，这边 JVM 堆由 GC 说了算，两个数
             * 没有换算关系——曾经有个基准测试类就是这么比的，删掉了（见本目录
             * README）。真要量内存请用 -Xmx 逼出上限，或者上 JFR / async-profiler。
             */
            System.err.printf("script=%s elapsed_ms=%d%n", script.getFileName(), ms);
            L.close();
        }
        System.exit(code);
    }
}
