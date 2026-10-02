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