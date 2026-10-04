package com.github.esrrhs.fakescript;

public class PerfProbe {
    public static void main(String[] args) throws Exception {
        fake f = fk.newfake(new fkconfig());
        f.cfg.max_run_cmd_num = 0;
        fk.openbaselib(f);
        String script =
                "func fib(n)\n" +
                "    if n < 2 then\n" +
                "        return n\n" +
                "    end\n" +
                "    return fib(n - 1) + fib(n - 2)\n" +
                "end\n" +
                "func f()\n" +
                "    return fib(22)\n" +
                "end\n";
        if (!fk.parsestr(f, script)) {
            System.out.println("parse fail: " + fk.geterror(f));
            return;
        }
        long deadline = System.currentTimeMillis() + 15000;
        long runs = 0;
        while (System.currentTimeMillis() < deadline) {
            Object ret = fk.run(f, "f");
            if (((Long) ret).longValue() != 17711L) {
                System.out.println("wrong result: " + ret);
                return;
            }
            runs++;
        }
        System.out.println("runs=" + runs + " (~" + (runs * 289L / 15000) + "k script-calls/s)");
    }
}
