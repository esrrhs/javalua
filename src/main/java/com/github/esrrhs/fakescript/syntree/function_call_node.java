package com.github.esrrhs.fakescript.syntree;

public class function_call_node extends syntree_node
{
	public boolean m_fakecall;
	public boolean m_classmem_call;
	public String m_fuc;
	public function_call_arglist_node m_arglist;
	public syntree_node m_prefuc;

	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_function_call;
	}

}