package test;

import fakescript.fakescript;

public class A
{
	public A()
	{
		
	}
	
	@fakescript
	public void aaa(int a)
	{
		System.out.println("aaa" + a);
	}
	
	@fakescript (name = "bbb")
	public int aaa1()
	{
		System.out.println("aaa1");
		return 1;
	}
	
	@fakescript
	public static void aaa2()
	{
		System.out.println("aaa2");
	}
}
