package com.github.esrrhs.test;

import com.github.esrrhs.fakescript.callback;
import com.github.esrrhs.fakescript.fake;
import com.github.esrrhs.fakescript.fk;
import com.github.esrrhs.fakescript.fkconfig;

public class test
{
	public static void main(String[] args)
	{
		System.out.println("fakescript " + fk.version);
		fkconfig config = new fkconfig();
		config.open_debug_log = 0;
		fake f = fk.newfake(config);

		// 多种绑定
		fk.reg(f, "test");
		fk.regall(f, "test");
		fk.regclass(f, A.class);
		fk.regclass(f, B.class);
		fk.regclass(f, java.util.ArrayList.class);

		fk.openprofile(f);
		fk.openbaselib(f);
		fk.openbaselib(f);
		fk.openoptimize(f);
		fk.set_callback(f, new callback() {

			public void on_error(fake f, String file, int lineno, String func, String str)
			{
				System.out.printf("fake error in file(%s:%d) func(%s) : ", file, lineno, func);
				System.out.printf("%s\n", str);
				System.out.printf("call stack :\n%s\n", fk.getcurcallstack(f));
				System.out.printf("cur routine :\n%s\n", fk.getcurroutine(f));
			}

			public void on_print(fake f, String str)
			{
				System.out.println(str);
			}
		});

		boolean b = false;
		int ret = 0;

		b = fk.parse(f, "./src/com/github/esrrhs/test/test.fk");
		if (!b)
		{
			System.out.println("parse fail");
			return;
		}

		b = fk.parse(f, "./src/com/github/esrrhs/test/testop.fk");
		if (!b)
		{
			System.out.println("parse fail");
			return;
		}

		fk.run(f, "cttest.test1");

		A a = new A();
		a = (A) fk.run(f, "testA", a);
		System.out.println("run testA ret " + a);

		a = new B();
		ret = (int) (double) (Double) fk.run(f, "testB", a);
		System.out.println("run testB ret " + ret);

		fk.run(f, "testHotUpdate");
		System.out.println("run testHotUpdate");
		fk.run(f, "testHotUpdate");
		System.out.println("run testHotUpdate");

		long begin = System.currentTimeMillis();
		ret = (int) (double) (Double) fk.run(f, "test_prime");
		System.out.println("run test_prime ret " + ret + " " + (System.currentTimeMillis() - begin));

		System.out.println(fk.dumpprofile(f));
	}
}