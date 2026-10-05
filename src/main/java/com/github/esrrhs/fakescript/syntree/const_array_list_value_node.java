package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class const_array_list_value_node extends syntree_node
{
	public ArrayList<syntree_node> m_lists = new ArrayList<syntree_node>();

	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_constarraylist;
	}

	public void add_ele(syntree_node e)
	{
		m_lists.add(e);
	}
}