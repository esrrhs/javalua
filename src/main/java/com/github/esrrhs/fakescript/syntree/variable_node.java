package com.github.esrrhs.fakescript.syntree;

public class variable_node extends syntree_node
{
	public String m_str;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_variable;
	}

}
