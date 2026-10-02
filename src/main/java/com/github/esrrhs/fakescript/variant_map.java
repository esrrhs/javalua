package com.github.esrrhs.fakescript;

import java.util.HashMap;

class variant_map
{
	public HashMap<variant, variant> m_vm = new HashMap<variant, variant>();
	public boolean m_isconst;
	public int m_recur;
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
}
