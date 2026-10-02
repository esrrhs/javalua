package com.github.esrrhs.fakescript;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

class variant_map
{
	private HashMap<variant, variant> m_vm = new HashMap<variant, variant>();
	boolean m_isconst;
	int m_recur;
	private fake m_f;

	public variant_map()
	{
	}

	public variant_map(fake f)
	{
		m_f = f;
	}

	public variant con_map_get(variant kv) throws Exception
	{
		variant vv = m_vm.get(kv);
		if (vv == null)
		{
			if (m_f != null && m_f.cfg.container_max_size > 0 && m_vm.size() >= m_f.cfg.container_max_size)
			{
				throw new Exception(
						"container too big, map size " + m_vm.size() + " max " + m_f.cfg.container_max_size);
			}

			variant newkv = new variant();
			newkv.copy_from(kv);
			vv = new variant();
			m_vm.put(newkv, vv);
		}
		return vv;
	}
	// 键值对个数
	public int size()
	{
		return m_vm.size();
	}

	// 按遍历序取第pos个键值对,越界返回null
	public Map.Entry<variant, variant> get_entry_by_index(int pos)
	{
		if (pos < 0 || pos >= m_vm.size())
		{
			return null;
		}
		Set<Map.Entry<variant, variant>> set = m_vm.entrySet();
		return (Map.Entry<variant, variant>) set.toArray()[pos];
	}

	public String tostring()
	{
		if (m_recur != 0)
		{
			return "MAP IN RECUR";
		}
		m_recur++;

		try
		{
			StringBuilder sb = new StringBuilder();
			sb.append("{");
			int i = 0;
			Iterator<Entry<variant, variant>> it = m_vm.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<variant, variant> entry = it.next();
				variant kv = entry.getKey();
				variant vv = entry.getValue();
				if (i == 0)
				{
					sb.append("(");
				}
				else
				{
					sb.append(",(");
				}

				sb.append(kv.toString());
				sb.append(",");
				sb.append(vv.toString());
				sb.append(")");

				i++;
			}

			sb.append("}");

			return sb.toString();
		}
		finally
		{
			m_recur--;
		}
	}
}