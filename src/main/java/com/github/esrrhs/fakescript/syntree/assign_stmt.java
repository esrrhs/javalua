package com.github.esrrhs.fakescript.syntree;

public class assign_stmt extends syntree_node
{
	public syntree_node m_var;
	public syntree_node m_value;
	public boolean m_isnew;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_assign_stmt;
	}

}
