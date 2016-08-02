package test;

import fakescript.fakescript;

public class B extends A
{
	@fakescript
	@Override
	public void aaa(int a)
	{
		System.out.println("bbb" + a);
	}

	@fakescript(name = "bbb")
	@Override
	public int aaa1()
	{
		System.out.println("bbb1");
		return 1;
	}

	@fakescript
	public static void aaa2()
	{
		System.out.println("bbb2");
	}

	@fakescript
	public A aaa3()
	{
		return this;
	}

	public String aaa4()
	{
		return "test";
	}
}
