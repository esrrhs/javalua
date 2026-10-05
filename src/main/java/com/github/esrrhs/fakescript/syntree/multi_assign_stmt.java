package com.github.esrrhs.fakescript.syntree;

public class multi_assign_stmt extends syntree_node
{
	public var_list_node m_varlist;
	public syntree_node m_value;
	public boolean m_isnew;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_multi_assign_stmt;
	}

}
