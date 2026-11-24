package com.github.esrrhs.javalua;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dependency-free diagnostic harness, NOT a replacement for JMH or a timing gate. */
public final class PerformanceHarness {
    private static volatile long sink;
    private static final com.sun.management.ThreadMXBean ALLOC = allocationBean();

    private PerformanceHarness() { }

    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean extended
                && extended.isThreadAllocatedMemorySupported()) {
            try {
                if (!extended.isThreadAllocatedMemoryEnabled()) extended.setThreadAllocatedMemoryEnabled(true);
                return extended;
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // Allocation telemetry is optional; never substitute heap occupancy for it.
            }
        }
        return null;
    }

    private static long allocated() {
        return ALLOC == null ? -1 : ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private static long gcCount() {
        long count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value >= 0) count += value;
        }
        return count;
    }

    private static long heapUsed() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static long expected(String name, int n) {
        long sum = (long) n * (n + 1) / 2;
        return switch (name) {
            case "arithmetic", "tables" -> 3 * sum;
            case "allocations", "lua-to-java", "java-to-lua" -> sum + n;
            case "strings" -> 52L * n + 45L * (n / 10) + (long) (n % 10) * (n % 10 + 1) / 2;
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static long invoke(LuaState state, LuaValue function, int n) {
        int base = state.top;
        state.push(function);
        state.push(LuaValue.ofInteger(n));
        state.call(base, 1);
        LuaValue result = state.at(base);
        if (!result.isInteger()) throw new AssertionError("non-integer result: " + result);
        long value = result.i;
        state.top = base;
        return value;
    }

    private static long batch(LuaState state, LuaValue function, String name, int n, int batch) {
        long checksum = 0;
        long want = expected(name, n);
        for (int b = 0; b < batch; b++) {
            long value = 0;
            if (name.equals("java-to-lua")) {
                for (int i = 1; i <= n; i++) value += invoke(state, function, i);
            } else {
                value = invoke(state, function, n);
            }
            if (value != want) throw new AssertionError(name + ": " + value + " != " + want);
            checksum += value;
        }
        sink = checksum;
        return checksum;
    }

    private static void workload(Path directory, String name, int n, int batch, int warmup, int samples, int warmupMs)
            throws Exception {
        LuaState state = new LuaState();
        try {
            LuaLibs.openAll(state);
            state.getGlobals().set("bench_host", LuaValue.ofFunction(s -> {
                s.push(LuaValue.ofInteger(s.arg(0).i + 1));
                return 1;
            }));
            LuaValue function = LuaValue.ofClosure(state.load(Files.readAllBytes(directory.resolve(name + ".lua")),
                    "@benchmark/" + name + ".lua"));
            // Exclude file I/O, compilation, state/library setup and warmup from samples.
            long warmStart = System.nanoTime();
            int warmBatches = 0;
            while (warmBatches < warmup || System.nanoTime() - warmStart < warmupMs * 1_000_000L) {
                batch(state, function, name, n, batch);
                warmBatches++;
            }
            long warmElapsed = System.nanoTime() - warmStart;
            for (int i = 0; i < samples; i++) {
                long gc0 = gcCount();
                long heap0 = heapUsed();
                long bytes0 = allocated();
                long start = System.nanoTime();
                long checksum = batch(state, function, name, n, batch);
                long elapsed = System.nanoTime() - start;
                long bytes1 = allocated();
                long heap1 = heapUsed();
                long collections = gcCount() - gc0;
                long bytes = bytes0 < 0 || bytes1 < 0 ? -1 : bytes1 - bytes0;
                System.out.printf(java.util.Locale.ROOT,
                        "{\"workload\":\"%s\",\"sample\":%d,\"elapsed_ns\":%d,"
                        + "\"allocated_bytes\":%d,\"gc_collections\":%d,\"heap_before_bytes\":%d,"
                        + "\"heap_after_bytes\":%d,\"checksum\":%d,\"warmup_batches\":%d,"
                        + "\"warmup_elapsed_ns\":%d}%n",
                        name, i, elapsed, bytes, collections, heap0, heap1, checksum, warmBatches, warmElapsed);
            }
        } finally {
            state.close();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("startup")) {
            long start = System.nanoTime();
            LuaState state = new LuaState();
            try {
                LuaLibs.openAll(state);
                LuaValue function = LuaValue.ofClosure(state.load("return 42".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "=startup"));
                if (invoke(state, function, 0) != 42) throw new AssertionError("startup");
            } finally {
                state.close();
            }
            System.out.println("{\"setup_execute_close_ns\":" + (System.nanoTime() - start) + ",\"checksum\":42}");
            return;
        }
        if (args.length != 7) throw new IllegalArgumentException("workload-dir n batch warmup samples order-offset warmup-ms");
        int n = Integer.parseInt(args[1]);
        int batch = Integer.parseInt(args[2]);
        int warmup = Integer.parseInt(args[3]);
        int samples = Integer.parseInt(args[4]);
        int warmupMs = Integer.parseInt(args[6]);
        if (warmupMs < 0) throw new IllegalArgumentException("warmup-ms must be non-negative");
        if (n < 1 || n > 1000000 || batch < 1 || warmup < 0 || samples < 1)
            throw new IllegalArgumentException("invalid workload bounds");
        System.out.println("{\"native_available\":" + NativeBridge.AVAILABLE + "}");
        List<String> names = new ArrayList<>(List.of("arithmetic", "tables", "strings", "java-to-lua",
                "lua-to-java", "allocations"));
        Collections.rotate(names, Integer.parseInt(args[5]) % names.size());
        for (String name : names) workload(Path.of(args[0]), name, n, batch, warmup, samples, warmupMs);
    }
}
