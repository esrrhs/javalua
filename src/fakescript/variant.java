package fakescript;

class variant
{
	// type
	public variant_type m_type;

	// data
	public Object m_data;

	public variant()
	{
		set_nil();
	}

	public String toString()
	{
		String ss = "";
		if (m_type == variant_type.REAL)
		{
			double real = (double) m_data;
			if (types.isint(real))
			{
				ss = "" + (long) (double) real;
			}
			else
			{
				ss = "" + real;
			}
		}
		else if (m_type == variant_type.STRING)
		{
			ss = (String) (m_data);
		}
		else if (m_type == variant_type.UUID)
		{
			ss = "" + (long) m_data;
		}
		else if (m_type == variant_type.POINTER)
		{
			ss = "" + m_data;
		}
		else if (m_type == variant_type.ARRAY)
		{
			ss = types.arraytoa(m_data);
		}
		else if (m_type == variant_type.MAP)
		{
			ss = types.maptoa(m_data);
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

	public double get_real() throws Exception
	{
		if (m_type != variant_type.REAL && m_type != variant_type.NIL)
		{
			throw new Exception("variant get real fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return m_data == null ? 0 : (double) m_data;
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
		if (m_type != variant_type.STRING && m_type != variant_type.NIL)
		{
			throw new Exception("variant get uuid fail, the variant is " + m_type.toString() + m_data.toString());
		}
		return m_data == null ? 0 : (long) m_data;
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
		if (m_type != variant_type.REAL && m_type != variant_type.NIL)
		{
			throw new Exception("variant can not calculate, the variant is " + m_type.toString() + m_data.toString());
		}
	}

	public void assert_can_divide() throws Exception
	{
		if (((double) m_data) == 0)
		{
			throw new Exception("variant can not be divide, the variant is " + m_type.toString() + m_data.toString());
		}
	}

	public void plus(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data + (double) r.m_data;
		m_type = variant_type.REAL;
	}

	public void minus(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data - (double) r.m_data;
		m_type = variant_type.REAL;
	}

	public void multiply(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data * (double) r.m_data;
		m_type = variant_type.REAL;
	}

	public void divide(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		r.assert_can_divide();
		m_data = (double) l.m_data / (double) r.m_data;
		m_type = variant_type.REAL;
	}

	public void divide_mod(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		r.assert_can_divide();
		m_data = (double) ((long) (double) l.m_data % (long) (double) r.m_data);
		m_type = variant_type.REAL;
	}

	public void string_cat(variant l, variant r) throws Exception
	{
		set_string(l.toString() + r.toString());
	}

	public void and(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (((double) l.m_data != 0) & ((double) r.m_data != 0)) ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void or(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (((double) l.m_data != 0) | ((double) r.m_data != 0)) ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void less(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data < (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void more(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data > (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data == (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void less_equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data <= (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void more_equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data >= (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void not_equal(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = (double) l.m_data != (double) r.m_data ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void not(variant r) throws Exception
	{
		m_data = ((double) r.m_data != 0) ? (double) 0 : (double) 1;
		m_type = variant_type.REAL;
	}

	public static boolean and_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data != 0) & ((double) r.m_data != 0);
	}

	public static boolean or_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data != 0) | ((double) r.m_data != 0);
	}

	public static boolean less_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) < ((double) r.m_data);
	}

	public static boolean more_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) > ((double) r.m_data);
	}

	public static boolean equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) == ((double) r.m_data);
	}

	public static boolean more_equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) >= ((double) r.m_data);
	}

	public static boolean less_equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) <= ((double) r.m_data);
	}

	public static boolean not_equal_jne(variant l, variant r) throws Exception
	{
		l.assert_can_cal();
		r.assert_can_cal();
		return ((double) l.m_data) != ((double) r.m_data);
	}

	public static boolean not_jne(variant r) throws Exception
	{
		r.assert_can_cal();
		return ((double) r.m_data) == 0;
	}

	public boolean bool()
	{
		return ((double) m_data) != 0;
	}

	@Override
	public int hashCode()
	{
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

		if (m_type != r.m_type)
		{
			return false;
		}

		if (m_type == variant_type.REAL)
		{
			return (double) m_data == (double) r.m_data;
		}
		else if (m_type == variant_type.STRING)
		{
			return ((String) m_data).equals(r.m_data);
		}
		else if (m_type == variant_type.UUID)
		{
			return (long) m_data == (long) r.m_data;
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
