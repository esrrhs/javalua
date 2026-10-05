package com.github.esrrhs.fakescript.syntree;

public class func_desc_node extends syntree_node
{
	public String m_funcname;
	public func_desc_arglist_node m_arglist;
	public block_node m_block;
	public int m_endline;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_func_desc;
	}

}
