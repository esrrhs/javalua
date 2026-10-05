package com.github.esrrhs.fakescript.syntree;

public class struct_pointer_node extends syntree_node
{
	public String m_str;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_struct_pointer;
	}

}
