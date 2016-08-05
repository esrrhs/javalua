package test;

import fakescript.fakescript;

public class A
{
	public A()
	{

	}

	@fakescript
	public void a1(int a)
	{
		System.out.println("a1 " + a);
	}

	@fakescript(name = "a2ex")
	public double a2()
	{
		System.out.println("a2");
		return 0.2;
	}

	@fakescript
	public static void a3()
	{
		System.out.println("a3");
	}

	public long a4(long uid)
	{
		return uid;
	}

}
