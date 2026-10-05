package com.github.esrrhs.fakescript.syntree;

public class while_stmt extends syntree_node
{
	public cmp_stmt m_cmp;
	public block_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_while_stmt;
	}

}