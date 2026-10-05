package com.github.esrrhs.fakescript.syntree;

public class elseif_stmt extends syntree_node
{
	public cmp_stmt m_cmp;
	public syntree_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_elseif_stmt;
	}

}
