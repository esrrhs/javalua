package com.github.esrrhs.fakescript.syntree;

public class if_stmt extends syntree_node
{
	public cmp_stmt m_cmp;
	public block_node m_block;
	public elseif_stmt_list m_elseifs;
	public else_stmt m_elses;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_if_stmt;
	}

}
