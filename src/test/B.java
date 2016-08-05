package test;

public class B extends A
{
	@Override
	public void a1(int a)
	{
		System.out.println("b1 " + a);
	}

	public static void a3()
	{
		System.out.println("b3");
	}

	public B b1()
	{
		return this;
	}
}
