package com.github.esrrhs.fakescript.syntree;

public class for_loop_stmt extends syntree_node
{
	public syntree_node m_var;
	public syntree_node m_begin;
	public syntree_node m_end;
	public syntree_node m_add;
	public block_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_for_loop_stmt;
	}

}
