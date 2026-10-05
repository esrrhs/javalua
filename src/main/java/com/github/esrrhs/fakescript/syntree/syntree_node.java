package com.github.esrrhs.fakescript.syntree;

public class syntree_node 
{
	public int m_lno = 0;

	public syntree_node()
	{
	}
	
	public esyntreetype gettype()
	{
		return esyntreetype.est_nil;
	}
	
	public String gettypename()
	{
		return gettype().toString();
	}
	
	public int lineno()
	{
		return m_lno;
	}

}
