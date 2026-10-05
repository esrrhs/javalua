package com.github.esrrhs.fakescript.syntree;

public class cmp_stmt extends syntree_node
{
	public String m_cmp;
	public syntree_node m_left;
	public syntree_node m_right;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_cmp_stmt;
	}

}