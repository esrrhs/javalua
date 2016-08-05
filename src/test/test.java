package test;

import fakescript.callback;
import fakescript.fake;
import fakescript.fk;

public class test
{
	public static void main(String[] args)
	{
		System.out.println("fakescript " + fk.version);
		fake f = fk.newfake(null);

		// 多种绑定
		fk.reg(f, "test");
		fk.regall(f, "test");
		fk.regclass(f, A.class);
		fk.regclass(f, B.class);

		fk.openbaselib(f);
		fk.set_callback(f, new callback() {
			@Override
			public void on_error(fake f, String file, int lineno, String func, String str)
			{
				System.out.printf("fake error in file(%s:%d) func(%s) : ", file, lineno, func);
				System.out.printf("%s\n", str);
				System.out.printf("call stack :\n%s\n", fk.getcurcallstack(f));
				System.out.printf("cur routine :\n%s\n", fk.getcurroutine(f));
			}

			@Override
			public void on_print(fake f, String str)
			{
				System.out.println(str);
			}
		});

		boolean b = fk.parse(f, "./src/test/test.fk");
		if (!b)
		{
			System.out.println("parse fail");
			return;
		}

		A a = new A();
		int ret = (int) (double) fk.run(f, "testA", a);
		System.out.println("run testA ret " + ret);

		a = new B();
		ret = (int) (double) fk.run(f, "testB", a);

		System.out.println("run testB ret " + ret);

		long begin = System.currentTimeMillis();
		ret = (int) (double) fk.run(f, "test_prime");
		System.out.println("run test_prime ret " + ret + " " + (System.currentTimeMillis() - begin));

	}
}