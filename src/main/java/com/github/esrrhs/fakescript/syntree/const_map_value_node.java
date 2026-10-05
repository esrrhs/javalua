package com.github.esrrhs.fakescript.syntree;

public class const_map_value_node extends syntree_node
{
	public syntree_node m_k;
	public syntree_node m_v;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_constmapvalue;
	}

}
