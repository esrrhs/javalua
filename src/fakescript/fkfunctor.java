package fakescript;

import java.lang.reflect.Method;

class fkfunctor
{
	Class<?> m_c;
	Method m_m;
	Class<?>[] m_param;
	Class<?> m_ret;
	boolean m_is_staic;

	public void call(fake f) throws Exception
	{
		Object c = null;
		if (!m_is_staic)
		{
			c = fk.pspop(f);
			if (c == null)
			{
				throw new Exception("call bind class " + m_c.toString() + " " + m_m.toString() + ", ref is null");
			}
			// 检查类型
			if (c.getClass() != m_c)
			{
				throw new Exception("call bind class " + m_c.toString() + " " + m_m.toString()
						+ ", diff class type, give " + c.getClass().toString());
			}
		}

		// 检查返回值数目对不对
		if (f.ps.size() != m_param.length)
		{
			throw new Exception("call bind class " + m_c.toString() + " " + m_m.toString() + ", param not match, give "
					+ f.ps.size() + " need " + m_param.length);
		}

		// 参数
		Object[] param = new Object[m_param.length];
		for (int i = m_param.length - 1; i >= 0; i--)
		{
			param[i] = fk.trans(fk.pspop(f), m_param[i]);
		}

		Object ret = m_m.invoke(c, param);

		// 检查类型
		if (ret != null && ret.getClass().isInstance(m_ret))
		{
			throw new Exception("call bind class " + m_c.toString() + " " + m_m.toString() + ", diff ret type, give "
					+ ret.getClass().toString());
		}

		fk.pspush(f, ret);
	}
}
