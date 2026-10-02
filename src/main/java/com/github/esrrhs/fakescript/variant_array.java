package com.github.esrrhs.fakescript;

import java.util.ArrayList;

class variant_array
{
	public ArrayList<variant> m_va = new ArrayList<variant>();
	public boolean m_isconst;
	public int m_recur;
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
}
