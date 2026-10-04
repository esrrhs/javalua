package com.github.esrrhs.fakescript;

/**
 * 解释器微基准(零依赖,手动运行)
 * <p>
 * 用法:./mvnw -q -Pbench test-compile exec:java -Dexec.classpathScope=test<br>
 * 方法论:每项先预热,再测3轮取中位数。非JMH,数据用于趋势观察而非精确对比。
 */
public class Benchmark {

    public static void main(String[] args) throws Exception {
        System.out.println("fakescript-java benchmark (" + fk.version + ")");
        System.out.println("java: " + System.getProperty("java.version"));
        System.out.println();

        fake f = fk.newfake(new fkconfig());
        fk.openbaselib(f);

        bench("fib(20) recursion", 3, f,
                "func fib(n)\n" +
                "    if n < 2 then\n" +
                "        return n\n" +
                "    end\n" +
                "    return fib(n - 1) + fib(n - 2)\n" +
                "end\n" +
                "func f()\n" +
                "    return fib(20)\n" +
                "end\n", "f", 6765L);

        bench("arithmetic loop x100k", 3, f,
                "func f()\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < 100000, i++ then\n" +
                "        s = s + i * 3\n" +
                "    end\n" +
                "    return s\n" +
                "end\n", "f", 14999850000L);

        bench("string format x20k", 3, f,
                "func f()\n" +
                "    var s = \"\"\n" +
                "    for var i = 0, i < 20000, i++ then\n" +
                "        s = format(\"item:$\", i)\n" +
                "    end\n" +
                "    return size(s)\n" +
                "end\n", "f", 10L);

        bench("map/array ops x50k", 3, f,
                "func f()\n" +
                "    var m = map()\n" +
                "    for var i = 0, i < 50000, i++ then\n" +
                "        m[i] = i * 2\n" +
                "    end\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < 50000, i++ then\n" +
                "        s = s + m[i]\n" +
                "    end\n" +
                "    return s\n" +
                "end\n", "f", 2499950000L);

        bench("json roundtrip x5k", 3, f,
                "func f()\n" +
                "    var m = map()\n" +
                "    for var i = 0, i < 50, i++ then\n" +
                "        m[\"key\" .. i] = i\n" +
                "    end\n" +
                "    var j = tojson(m)\n" +
                "    var b = fromjson(j)\n" +
                "    return size(b)\n" +
                "end\n", "f", 50L);

        bench("script function call x200k", 3, f,
                "func id(x)\n" +
                "    return x\n" +
                "end\n" +
                "func f()\n" +
                "    var s = 0\n" +
                "    for var i = 0, i < 200000, i++ then\n" +
                "        s = id(i)\n" +
                "    end\n" +
                "    return s\n" +
                "end\n", "f", 199999L);
    }

    private static void bench(String name, int rounds, fake f, String script, String entry, long expect) throws Exception {
        fkconfig cfg = new fkconfig();
        cfg.max_run_cmd_num = 0; // 基准不受预算影响
        fake ff = fk.newfake(cfg);
        fk.openbaselib(ff);
        if (!fk.parsestr(ff, script)) {
            System.out.printf("%-28s PARSE FAIL %s%n", name, fk.geterror(ff).split("\n")[0]);
            return;
        }

        Object ret = fk.run(ff, entry);
        if (ret == null || ((Long) ret).longValue() != expect) {
            System.out.printf("%-28s WRONG RESULT %s (expect %d)%n", name, ret, expect);
            return;
        }

        long best = Long.MAX_VALUE;
        for (int i = 0; i < rounds; i++) {
            long t0 = System.nanoTime();
            fk.run(ff, entry);
            long cost = System.nanoTime() - t0;
            if (cost < best) {
                best = cost;
            }
        }

        System.out.printf("%-28s %8.1f ms/op   %,12.0f ops/s%n", name, best / 1e6,
                1e9 / best);
    }
}
