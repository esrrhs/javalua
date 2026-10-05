package com.github.esrrhs.fakescript.syntree;

public class yield_stmt extends syntree_node
{
	public syntree_node m_time;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_yield;
	}

}

