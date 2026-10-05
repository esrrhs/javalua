package com.github.esrrhs.fakescript.syntree;

public class math_expr_node extends syntree_node
{
	public String m_oper;
	public syntree_node m_left;
	public syntree_node m_right;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_math_expr;
	}

}
