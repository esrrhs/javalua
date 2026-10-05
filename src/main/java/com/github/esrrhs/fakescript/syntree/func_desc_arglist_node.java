package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class func_desc_arglist_node extends syntree_node
{
	public ArrayList<String> m_arglist = new ArrayList<String>();
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_arglist;
	}

	
	public void add_arg(syntree_node p)
	{
		identifier_node pi = (identifier_node)(p);
		m_arglist.add(pi.m_str);
	}
}