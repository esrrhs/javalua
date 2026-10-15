package com.github.esrrhs.fakescript.syntree;

import java.util.ArrayList;

/**
 * Lua 5.4 风格的运行期表构造式 {@code {1, 2, 3}} / {@code {a = 1, b = 2}}。
 * <p>
 * 与编译期常量表（{@code const} 里的 {@code [1, 2]}、{@code {k : v}}，走
 * {@link explicit_value_node} 的 EVT_ARRAY/EVT_MAP）是两回事:这里的元素是
 * 任意表达式,必须在运行期求值。
 * <p>
 * 编译方式是**纯 AST 层脱糖**,不新增 opcode:
 * <pre>
 *   __fktable_0 = array()        // 或 map()
 *   __fktable_0[0] = e1          // 数组式:下标从 0 起
 *   __fktable_0["a"] = e2        // map 式:键为标识符
 *   &lt;值&gt; = __fktable_0
 * </pre>
 * 全部复用既有的 var / assign / container_get / function_call 编译路径。
 */
public class table_constructor_node extends syntree_node
{
	/** true 为数组式 {@code {e1, e2}};false 为 map 式 {@code {k = e}} */
	public boolean m_is_array = true;

	/** 数组式的元素表达式;map 式不用 */
	public ArrayList<syntree_node> m_values = new ArrayList<syntree_node>();

	/** map 式的键(当前只支持标识符键);与 m_values 一一对应 */
	public ArrayList<String> m_keys = new ArrayList<String>();

	@Override
	public esyntreetype gettype()
	{
		return esyntreetype.est_table_constructor;
	}
}
