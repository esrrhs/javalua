package com.github.esrrhs.fakescript.syntree;

public class return_stmt extends syntree_node
{
	public return_value_list_node m_returnlist;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_return_stmt;
	}

}
