package com.github.esrrhs.fakescript.syntree;

public class switch_stmt extends syntree_node
{
	public syntree_node m_cmp;
	public syntree_node m_caselist;
	public syntree_node m_def;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_switch_stmt;
	}

}
