package com.github.esrrhs.fakescript.syntree;

public class container_get_node extends syntree_node
{
	public String m_container;
	public syntree_node m_key;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_container_get;
	}

}
