package com.github.esrrhs.fakescript.syntree;

public class for_stmt extends syntree_node
{
	public block_node m_beginblock;
	public cmp_stmt m_cmp;
	public block_node m_endblock;
	public block_node m_block;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_for_stmt;
	}

}
