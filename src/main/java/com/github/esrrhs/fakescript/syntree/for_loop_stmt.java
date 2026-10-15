package com.github.esrrhs.fakescript.syntree;

public class for_loop_stmt extends syntree_node
{
	public syntree_node m_var;
	public syntree_node m_begin;
	public syntree_node m_end;
	public syntree_node m_add;
	public block_node m_block;
	/* true 表示闭区间 [begin, end]。既有写法 `for local i = a -> b, s do`
	   是半开区间 [a, b),保持 false;Lua 数值 for `for i = a, b[, s] do`
	   置 true,编译成 *_CLOSED 变体。 */
	public boolean m_closed = false;
	
	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_for_loop_stmt;
	}

}
