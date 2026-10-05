package com.github.esrrhs.fakescript.syntree;

public class else_stmt extends syntree_node
{
	public block_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_else_stmt;
	}

}
