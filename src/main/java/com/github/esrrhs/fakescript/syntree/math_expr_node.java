package com.github.esrrhs.fakescript.syntree;

public class math_expr_node extends syntree_node
{
	public String m_oper;
	public syntree_node m_left;
	public syntree_node m_right;
	// 一元运算(如 -x、~x),此时 m_left 为 null,仅使用 m_right
	public boolean m_unary = false;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_math_expr;
	}

}
