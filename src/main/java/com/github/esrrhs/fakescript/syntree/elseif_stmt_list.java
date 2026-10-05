package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

public class elseif_stmt_list extends syntree_node
{
	public ArrayList<syntree_node> m_stmtlist = new ArrayList<syntree_node>();
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_elseif_stmt_list;
	}

	
	public void add_stmt(syntree_node stmt)
	{
		m_stmtlist.add(stmt);
	}
}
