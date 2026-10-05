package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class function_call_arglist_node extends syntree_node
{
	public ArrayList<syntree_node> m_arglist = new ArrayList<syntree_node>();
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_call_arglist;
	}

	public void add_arg(syntree_node p)
	{	
		m_arglist.add(p);
	}
}
