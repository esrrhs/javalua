package com.github.esrrhs.fakescript.syntree;

public class math_assign_stmt extends syntree_node
{
	public syntree_node m_var;
	public String m_oper;
	public syntree_node m_value;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_math_assign_stmt;
	}

}
