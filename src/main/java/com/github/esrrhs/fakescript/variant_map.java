package com.github.esrrhs.fakescript;

import java.util.HashMap;

class variant_map
{
	public HashMap<variant, variant> m_vm = new HashMap<variant, variant>();
	public boolean m_isconst;
	public int m_recur;

	public variant con_map_get(variant kv)
	{
		variant vv = m_vm.get(kv);
		if (vv == null)
		{
			vv = new variant();
			m_vm.put(kv, vv);
		}
		return vv;
	}
}
