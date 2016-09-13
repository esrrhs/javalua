package test;

import java.util.ArrayList;

public class B extends A
{
	@Override
	public int a1(int a)
	{
		System.out.println("b1 " + a);
		return 0;
	}

	public static void a3()
	{
		System.out.println("b3");
	}

	public B b1()
	{
		return this;
	}

	public <T> T b2()
	{
		return (T) "b2";
	}

	public <T> void b3(T t)
	{
		System.out.println(t);
	}

	public ArrayList<String> b4()
	{
		return new ArrayList<String>();
	}
}
