package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class var_list_node extends syntree_node
{
	public ArrayList<syntree_node> m_varlist = new ArrayList<syntree_node>();;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_var_list;
	}

	
	public void add_arg(syntree_node stmt)
	{
		m_varlist.add(stmt);
	}
}
