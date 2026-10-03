package com.github.esrrhs.fakescript;

/**
 * 脚本万能变量类型
 * <p>
 * 类似lua的Variant,通过m_type区分实际类型
 * 内部数值统一用double存储,UUID类型用long
 */
class variant
{
	// type
	private variant_type m_type;

	// data
	private Object m_data;

	// 类型
	public variant_type get_type()
	{
		return m_type;
	}

	// 原始数据,调用方自行按类型转换
	public Object get_data()
	{
		return m_data;
	}

	public variant()
	{
		set_nil();
	}

	public String toString()
	{
		String ss = "";
		if (m_type == variant_type.REAL)
		{
			double real = (double) (Double) m_data;
			if (types.isint(real))
			{
				ss = "" + (long) (double) real;
			}
			else
			{
				ss = "" + real;
			}
		}
		else if (m_type == variant_type.INT)
		{
			ss = "" + (long) (Long) m_data;
		}
		else if (m_type == variant_type.STRING)
		{
			ss = (String) (m_data);
		}
		else if (m_type == variant_type.UUID)
		{
			ss = "" + (long) (Long) m_data;
		}
		else if (m_type == variant_type.POINTER)
		{
			ss = "" + m_data;
		}
		else if (m_type == variant_type.ARRAY)
		{
			ss = ((variant_array) m_data).tostring();
		}
		else if (m_type == variant_type.MAP)
		{
			ss = ((variant_map) m_data).tostring();
		}
		else if (m_type == variant_type.NIL)
		{
			ss = "nil";
		}
		else
		{
			ss = "ERROR";
		}
		return ss;
	}

	public void set_nil()
	{
		m_type = variant_type.NIL;
		m_data = null;
	}

	// 解释器把栈槽当原始存储复用时使用(ip/bp/fb等机器值),不构成脚本可见值
	void set_slot(Object data)
	{
		m_type = variant_type.NIL;
		m_data = data;
	}

	public void set_pointer(Object o)
	{
		m_type = variant_type.POINTER;
		m_data = o;
	}

	public void set_real(double d)
	{
		m_type = variant_type.REAL;
		m_data = d;
	}

	public void set_string(String s)
	{
		m_type = variant_type.STRING;
		m_data = s;
	}

	public void set_uuid(long l)
	{
		m_type = variant_type.UUID;
		m_data = l;
	}

	public void set_int(long l)
	{
		m_type = variant_type.INT;
		m_data = l;
	}

	public void set_array(variant_array va)
	{
		m_type = variant_type.ARRAY;
		m_data = va;
	}

	public void set_map(variant_map vm)
	{
		m_type = variant_type.MAP;
		m_data = vm;
	}

	public Object get_pointer() throws Exception
	{
		if (m_type != variant_type.POINTER && m_type != variant_type.NIL)
		{
			throw new Exception("variant get pointer fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return m_data;
	}

	// 取数值(double视图),接受REAL/INT/NIL
	public double get_real() throws Exception
	{
		if (m_type != variant_type.REAL && m_type != variant_type.INT && m_type != variant_type.NIL)
		{
			throw new Exception("variant get real fail, the variant is " + m_type.toString() + m_data.toString());
		}
		if (m_type == variant_type.INT)
		{
			return (long) (Long) m_data;
		}
		return m_data == null ? 0 : (double) (Double) m_data;
	}

	// 取数值(long视图),接受INT/REAL/NIL,REAL按C语义截断
	public long get_int() throws Exception
	{
		if (m_type != variant_type.REAL && m_type != variant_type.INT && m_type != variant_type.NIL)
		{
			throw new Exception("variant get int fail, the variant is " + m_type.toString() + m_data.toString());
		}
		if (m_type == variant_type.INT)
		{
			return (long) (Long) m_data;
		}
		return m_data == null ? 0 : (long) (double) (Double) m_data;
	}

	// 是否参与计算的数值类型
	private boolean is_num()
	{
		return m_type == variant_type.REAL || m_type == variant_type.INT;
	}

	public String get_string() throws Exception
	{
		if (m_type != variant_type.STRING && m_type != variant_type.NIL)
		{
			throw new Exception("variant get string fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return m_data == null ? "" : (String) m_data;
	}

	public long get_uuid() throws Exception
	{
		if (m_type != variant_type.UUID && m_type != variant_type.NIL)
		{
			throw new Exception("variant get uuid fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return m_data == null ? 0 : (long) (Long) m_data;
	}

	public variant_map get_map() throws Exception
	{
		if (m_type != variant_type.MAP)
		{
			throw new Exception("variant get map fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return (variant_map) m_data;
	}

	public variant_array get_array() throws Exception
	{
		if (m_type != variant_type.ARRAY)
		{
			throw new Exception("variant get array fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return (variant_array) m_data;
	}

	public void assert_can_cal() throws Exception
	{
		if (m_type != variant_type.REAL && m_type != variant_type.INT && m_type != variant_type.NIL)
		{
			throw new Exception("variant can not calculate, the variant is " + m_type.toString() + " "
					+ (m_data != null ? m_data.toString() : "null"));
		}
	}

	public void assert_can_divide() throws Exception
	{
		if ((m_type == variant_type.INT ? (long) (Long) m_data : (double) (Double) m_data) == 0)
		{
			throw new Exception(
					"variant can not be divide, the variant is " + m_type.toString() + " " + m_data.toString());
		}
	}

	public void plus(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data + (long) (Long) r.m_data;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = l.get_real() + r.get_real();
			m_type = variant_type.REAL;
		}
	}

	public void minus(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data - (long) (Long) r.m_data;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = l.get_real() - r.get_real();
			m_type = variant_type.REAL;
		}
	}

	public void multiply(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data * (long) (Long) r.m_data;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = l.get_real() * r.get_real();
			m_type = variant_type.REAL;
		}
	}

	// 除法恒为浮点,4/2得到2.0,1/2得到0.5
	public void divide(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		r.assert_can_divide();
		m_data = l.get_real() / r.get_real();
		m_type = variant_type.REAL;
	}

	public void divide_mod(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		r.assert_can_divide();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data % (long) (Long) r.m_data;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = (double) (l.get_int() % r.get_int());
			m_type = variant_type.REAL;
		}
	}

	public void string_cat(variant l, variant r) throws Exception
	{
		set_string(l.toString() + r.toString());
	}

	public void and(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		boolean ret = l.get_real() != 0 && r.get_real() != 0;
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ret ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = ret ? (double) 1 : (double) 0;
			m_type = variant_type.REAL;
		}
	}

	public void or(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		boolean ret = l.get_real() != 0 || r.get_real() != 0;
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ret ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = ret ? (double) 1 : (double) 0;
			m_type = variant_type.REAL;
		}
	}

	public void less(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, l.get_real() < r.get_real());
	}

	public void more(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, l.get_real() > r.get_real());
	}

	public void equal(variant l, variant r) throws Exception
	{
		m_data = l.equals(r) ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void less_equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, l.get_real() <= r.get_real());
	}

	public void more_equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, l.get_real() >= r.get_real());
	}

	// 比较结果1/0,两个INT操作数时结果保持INT
	private void set_cmp_ret(variant l, variant r, boolean ret)
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ret ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = ret ? (double) 1 : (double) 0;
			m_type = variant_type.REAL;
		}
	}

	public void not_equal(variant l, variant r) throws Exception
	{
		m_data = l.equals(r) ? (double) 0 : (double) 1;
		m_type = variant_type.REAL;
	}

	public void not(variant r) throws Exception
	{
		boolean ret = r.get_real() != 0;
		if (r.m_type == variant_type.INT)
		{
			m_data = ret ? (long) 0 : (long) 1;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = ret ? (double) 0 : (double) 1;
			m_type = variant_type.REAL;
		}
	}

	public static boolean and_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() != 0 && r.get_real() != 0;
	}

	public static boolean or_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() != 0 || r.get_real() != 0;
	}

	public static boolean less_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() < r.get_real();
	}

	public static boolean more_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() > r.get_real();
	}

	public static boolean equal_jne(variant l, variant r) throws Exception
	{
		return l.equals(r);
	}

	public static boolean more_equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() >= r.get_real();
	}

	public static boolean less_equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() <= r.get_real();
	}

	public static boolean not_equal_jne(variant l, variant r) throws Exception
	{
		return !l.equals(r);
	}

	public static boolean not_jne(variant r) throws Exception
	{
		r.assert_can_cal();
		return r.get_real() == 0;
	}

	public boolean bool()
	{
		// NIL或其他非数值类型视为false,避免比较判断时对无效类型崩溃
		if (m_type == variant_type.INT)
		{
			return (long) (Long) m_data != 0;
		}
		if (m_type == variant_type.REAL)
		{
			return (double) (Double) m_data != 0;
		}
		return false;
	}

	@Override
	public int hashCode()
	{
		// 数值键统一按double视图哈希,使INT与REAL的等值键落在同一桶
		if (m_type == variant_type.INT)
		{
			return Double.hashCode((double) (long) (Long) m_data);
		}
		return m_data != null ? m_data.hashCode() : 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}

		if (o == null || getClass() != o.getClass())
		{
			return false;
		}

		variant r = (variant) o;

		if ((m_type == variant_type.NIL && r.m_type == variant_type.POINTER && r.m_data == null)
				|| (m_type == variant_type.POINTER && m_data == null && r.m_type == variant_type.NIL))
		{
			return true;
		}

		// 数值跨类型按值比较(1与1.0相等),与hashCode的数值规范化配套,保证map键跨类型一致
		if ((m_type == variant_type.INT && r.m_type == variant_type.REAL)
				|| (m_type == variant_type.REAL && r.m_type == variant_type.INT))
		{
			double a = m_type == variant_type.INT ? (long) (Long) m_data : (double) (Double) m_data;
			double b = r.m_type == variant_type.INT ? (long) (Long) r.m_data : (double) (Double) r.m_data;
			return a == b;
		}

		if (m_type != r.m_type)
		{
			return false;
		}

		if (m_type == variant_type.REAL)
		{
			return (double) (Double) m_data == (double) (Double) r.m_data;
		}
		else if (m_type == variant_type.INT)
		{
			return (long) (Long) m_data == (long) (Long) r.m_data;
		}
		else if (m_type == variant_type.STRING)
		{
			return ((String) m_data).equals(r.m_data);
		}
		else if (m_type == variant_type.UUID)
		{
			return (long) (Long) m_data == (long) (Long) r.m_data;
		}
		else if (m_type == variant_type.POINTER)
		{
			return m_data == r.m_data;
		}
		else if (m_type == variant_type.ARRAY)
		{
			return m_data == r.m_data;
		}
		else if (m_type == variant_type.MAP)
		{
			return m_data == r.m_data;
		}
		else if (m_type == variant_type.NIL)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public void copy_from(variant r)
	{
		m_type = r.m_type;
		m_data = r.m_data;
	}
}
