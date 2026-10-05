package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class return_value_list_node extends syntree_node
{
	public ArrayList<syntree_node> m_returnlist = new ArrayList<syntree_node>();;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_return_value_list;
	}

	
	public void add_arg(syntree_node stmt)
	{
		m_returnlist.add(stmt);
	}
}