package com.github.esrrhs.fakescript;

import java.util.ArrayList;

class variant_array
{
	private ArrayList<variant> m_va = new ArrayList<variant>();
	boolean m_isconst;
	int m_recur;
	private fake m_f;

	public variant_array()
	{
	}

	public variant_array(fake f)
	{
		m_f = f;
	}

	public variant con_array_get(variant kv) throws Exception
	{
		int i = (int) kv.get_real();

		if (i < 0)
		{
			throw new Exception("interpreter get array variant fail, index " + i);
		}

		if (i >= m_va.size())
		{
			if (m_f != null && m_f.cfg.container_max_size > 0 && i >= m_f.cfg.container_max_size)
			{
				throw new Exception("container too big, array index " + i + " max " + m_f.cfg.container_max_size);
			}

			int num = i - m_va.size() + 1;
			for (int j = 0; j < num; j++)
			{
				m_va.add(null);
			}
		}

		variant vv = m_va.get(i);
		if (vv == null)
		{
			vv = new variant();
			m_va.set(i, vv);
		}
		return vv;
	}
	// 尾部追加元素
	public void push_back(variant v)
	{
		m_va.add(v);
	}

	// 弹出尾部元素,空数组返回null
	public variant pop_back()
	{
		if (m_va.isEmpty())
		{
			return null;
		}
		return m_va.remove(m_va.size() - 1);
	}

	// 在i处插入元素,i夹紧到[0,size]
	public void insert_at(int i, variant v)
	{
		if (i < 0)
		{
			i = 0;
		}
		if (i > m_va.size())
		{
			i = m_va.size();
		}
		m_va.add(i, v);
	}

	// 删除i处元素并返回,越界返回null
	public variant remove_at(int i)
	{
		if (i < 0 || i >= m_va.size())
		{
			return null;
		}
		return m_va.remove(i);
	}

	// 排序:数值按double视图比较,字符串按字典序;混用可比较类型以外的东西则报错
	public void sort() throws Exception
	{
		variant_type t = null;
		for (int i = 0; i < m_va.size(); i++)
		{
			variant v = m_va.get(i);
			if (v == null || (v.get_type() != variant_type.REAL && v.get_type() != variant_type.INT
					&& v.get_type() != variant_type.STRING))
			{
				throw new Exception("container sort fail, element " + i + " is not sortable");
			}
			if (t == null)
			{
				t = v.get_type() == variant_type.STRING ? variant_type.STRING : variant_type.REAL;
			}
			else if ((t == variant_type.STRING) != (v.get_type() == variant_type.STRING))
			{
				throw new Exception("container sort fail, can not mix number and string");
			}
		}

		final boolean bystring = t == variant_type.STRING;
		m_va.sort((a, b) -> {
			if (bystring)
			{
				return ((String) a.get_data()).compareTo((String) b.get_data());
			}
			double da = ((Number) a.get_data()).doubleValue();
			double db = ((Number) b.get_data()).doubleValue();
			return Double.compare(da, db);
		});
	}

	// 元素个数,含稀疏生长出的空槽
	public int size()
	{
		return m_va.size();
	}

	// 按下标取元素,越界返回null,稀疏空槽本身也为null
	public variant get_by_index(int i)
	{
		if (i < 0 || i >= m_va.size())
		{
			return null;
		}
		return m_va.get(i);
	}

	public String tostring()
	{
		if (m_recur != 0)
		{
			return "ARRAY IN RECUR";
		}
		m_recur++;

		try
		{
			StringBuilder sb = new StringBuilder();
			sb.append("[");

			for (int i = 0; i < m_va.size(); i++)
			{
				variant n = m_va.get(i);
				if (n != null)
				{
					sb.append(n.toString());
				}
				else
				{
					sb.append(" ");
				}
				sb.append(",");
			}

			sb.append("]");

			return sb.toString();
		}
		finally
		{
			m_recur--;
		}
	}
}