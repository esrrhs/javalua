package com.github.esrrhs.fakescript.syntree;

public class continue_stmt extends syntree_node
{
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_continue;
	}

}
