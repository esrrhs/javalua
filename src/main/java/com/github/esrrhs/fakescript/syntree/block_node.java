package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class block_node extends syntree_node
{
	public ArrayList<syntree_node> m_stmtlist = new ArrayList<syntree_node>();
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_block;
	}

	
	public void add_stmt(syntree_node stmt)
	{
		m_stmtlist.add(stmt);
	}

}