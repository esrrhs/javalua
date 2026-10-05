package com.github.esrrhs.fakescript.syntree;

public class switch_case_node extends syntree_node
{
	public syntree_node m_cmp;
	public syntree_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_switch_case_node;
	}

}
