package com.github.esrrhs.fakescript;

import java.util.HashMap;
import java.util.Map;

class funcmap
{
	private fake m_f;
	private HashMap<variant, funcunion> m_funcmap = new HashMap<variant, funcunion>();
	// 调用热路径的字符串索引,与m_funcmap同步维护
	private HashMap<String, funcunion> m_funcindex = new HashMap<String, funcunion>();

	public funcmap(fake f)
	{
		m_f = f;
	}

	public funcmap clonef(fake f)
	{
		funcmap ret = new funcmap(f);
		ret.m_funcmap = new HashMap<variant, funcunion>();
		for (Map.Entry<variant, funcunion> e : this.m_funcmap.entrySet())
		{
			funcunion fc = e.getValue();
			ret.m_funcmap.put(e.getKey(), fc.clonef());
		}
		ret.m_funcindex = new HashMap<String, funcunion>();
		for (Map.Entry<String, funcunion> e : this.m_funcindex.entrySet())
		{
			ret.m_funcindex.put(e.getKey(), ret.m_funcmap.get(e.getKey()));
		}
		return ret;
	}

	public String dump()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<variant, funcunion> e : this.m_funcmap.entrySet())
		{
			sb.append(e.getKey().toString());
			sb.append("\n");
		}
		return sb.toString();
	}

	public int size()
	{
		return m_funcmap.size();
	}

	public void add_func(variant name, func_binary fb)
	{
		funcunion f = add_func_union(name);
		f.m_fb = fb;
		f.m_havefb = true;
	}

	public void add_func(variant name, fkfunctor ff)
	{
		funcunion f = add_func_union(name);
		f.m_ff = ff;
		f.m_haveff = true;
	}

	public void add_func(variant name, bifunc bif)
	{
		funcunion f = add_func_union(name);
		f.m_bif = bif;
		f.m_havebif = true;
	}

	public funcunion get_func(variant name)
	{
		return m_funcmap.get(name);
	}

	// 调用热路径:函数名在编译期已知的字符串直接查索引,免variant哈希
	public funcunion get_func_by_name(String name)
	{
		return m_funcindex.get(name);
	}

	public HashMap<variant, funcunion> get_funcmap()
	{
		return m_funcmap;
	}

	private funcunion add_func_union(variant name)
	{
		funcunion p = m_funcmap.get(name);
		if (p != null)
		{
			return p;
		}

		funcunion tmp = new funcunion();
		m_funcmap.put(name, tmp);
		if (name.get_type() == variant_type.STRING)
		{
			m_funcindex.put((String) name.get_data(), tmp);
		}
		return tmp;
	}

}
