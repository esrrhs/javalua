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
			// Lua 5.4: %.14g + 整数形态补 .0,float 永不打印成整数样子
			ss = types.format_lua_float((double) (Double) m_data);
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

	// Lua 5.4 算术语义:数值字符串参与算术时按 Lua 词法强转
	// ("10"+1 == 11," 10 "+1 == 11,"0x10"+0 == 16,"10.0"+1 == 11.0);
	// 非数值字符串仍抛原风格错误。位运算与比较不强转(与 Lua 一致)
	private static variant coerce_arith(variant v) throws Exception
	{
		if (v.m_type != variant_type.STRING)
		{
			return v;
		}
		variant n = types.parse_lua_number(types.trim_lua((String) v.m_data), null);
		if (n == null)
		{
			throw new Exception("variant can not calculate, the variant is STRING " + v.m_data);
		}
		return n;
	}

	public void plus(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		// INT×INT快路径:类型已知可算,跳过断言调用
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data + (long) (Long) r.m_data;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = l.get_real() + r.get_real();
		m_type = variant_type.REAL;
	}

	public void minus(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		// INT×INT快路径:类型已知可算,跳过断言调用
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data - (long) (Long) r.m_data;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = l.get_real() - r.get_real();
		m_type = variant_type.REAL;
	}

	public void multiply(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		// INT×INT快路径:类型已知可算,跳过断言调用
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = (long) (Long) l.m_data * (long) (Long) r.m_data;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		m_data = l.get_real() * r.get_real();
		m_type = variant_type.REAL;
	}

	// 除法恒为浮点,4/2得到2.0,1/2得到0.5
	// Lua 5.4:整数除零抛错;浮点除零按 IEEE 754 得 inf/-inf/nan
	public void divide(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT
				&& (long) (Long) r.m_data == 0)
		{
			throw new Exception("variant can not be divide, the variant is INT 0");
		}
		m_data = l.get_real() / r.get_real();
		m_type = variant_type.REAL;
	}

	// Lua 5.4 floor 取模:结果符号与除数一致,-1 % 3 == 2,1 % -3 == -2
	public void divide_mod(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			long a = (long) (Long) l.m_data;
			long b = (long) (Long) r.m_data;
			if (b == 0)
			{
				throw new Exception("variant can not be mod, the variant is INT 0");
			}
			// 与 Lua lvm.c 一致:截断余数在异号且非0时补一次除数
			// (MIN % -1 余数为0,天然不溢出,结果为0)
			long m = a % b;
			if (m != 0 && (a ^ b) < 0)
			{
				m += b;
			}
			m_data = m;
			m_type = variant_type.INT;
		}
		else
		{
			double a = l.get_real();
			double b = r.get_real();
			// fmod(Java double % 的 C 语义)后按 floor 取模校正;
			// 整除时零的符号与 Lua 实测一致,跟随被除数(-5.0%5 == -0.0);
			// 浮点 % 0 按 IEEE 得 nan,不报错
			double m = a % b;
			if (m != 0.0 && ((m < 0.0) != (b < 0.0)))
			{
				m += b;
			}
			m_data = m;
			m_type = variant_type.REAL;
		}
	}

	public void string_cat(variant l, variant r) throws Exception
	{
		set_string(l.toString() + r.toString());
	}

	// Lua 5.4 // floor 除法:int//int 为整数 floor 除(除零报错,MIN//-1 环绕为 MIN);
	// 任一操作数为浮点则结果恒为浮点 floor(a/b),除零按 IEEE 754 得 inf/nan
	public void floor_divide(variant l, variant r) throws Exception
	{
		l = coerce_arith(l);
		r = coerce_arith(r);
		l.assert_can_cal();
		r.assert_can_cal();
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			long a = (long) (Long) l.m_data;
			long b = (long) (Long) r.m_data;
			if (b == 0)
			{
				throw new Exception("variant can not be divide, the variant is INT 0");
			}
			if (a == Long.MIN_VALUE && b == -1)
			{
				m_data = Long.MIN_VALUE;
				m_type = variant_type.INT;
				return;
			}
			// q = (a - floor_mod(a,b)) / b,与 Lua floor 除法等价且不溢出
			long m = a % b;
			if (m != 0 && (a ^ b) < 0)
			{
				m += b;
			}
			m_data = (a - m) / b;
			m_type = variant_type.INT;
		}
		else
		{
			m_data = Math.floor(l.get_real() / r.get_real());
			m_type = variant_type.REAL;
		}
	}

	// Lua 5.4 位运算取整数:INT 直接用;NIL 沿用本语言算术惯例按 0;
	// REAL 仅接受落在 int64 内的精确整数,否则报错(number has no integer representation)
	private long bit_integer(variant v) throws Exception
	{
		if (v.m_type == variant_type.INT)
		{
			return (long) (Long) v.m_data;
		}
		if (v.m_type == variant_type.NIL)
		{
			return 0;
		}
		if (v.m_type == variant_type.REAL)
		{
			double d = (double) (Double) v.m_data;
			if (!Double.isNaN(d) && d >= (double) Long.MIN_VALUE
					&& d < 9.2233720368547758E18 && types.isint(d))
			{
				return (long) d;
			}
			throw new Exception("variant can not bit oper, number has no integer representation");
		}
		throw new Exception("variant can not bit oper, the variant is " + v.m_type.toString());
	}

	public void bit_and(variant l, variant r) throws Exception
	{
		m_data = bit_integer(l) & bit_integer(r);
		m_type = variant_type.INT;
	}

	public void bit_or(variant l, variant r) throws Exception
	{
		m_data = bit_integer(l) | bit_integer(r);
		m_type = variant_type.INT;
	}

	public void bit_xor(variant l, variant r) throws Exception
	{
		m_data = bit_integer(l) ^ bit_integer(r);
		m_type = variant_type.INT;
	}

	public void bit_not(variant l) throws Exception
	{
		m_data = ~bit_integer(l);
		m_type = variant_type.INT;
	}

	// Lua 5.4 移位:|位移量|>=64 结果为 0;负数反向移位;右移按无符号补 0
	public void shift_left(variant l, variant r) throws Exception
	{
		long a = bit_integer(l);
		long d = bit_integer(r);
		if (d >= 64 || d <= -64)
		{
			m_data = 0L;
		}
		else if (d >= 0)
		{
			m_data = a << d;
		}
		else
		{
			m_data = a >>> (-d);
		}
		m_type = variant_type.INT;
	}

	public void shift_right(variant l, variant r) throws Exception
	{
		long a = bit_integer(l);
		long d = bit_integer(r);
		if (d >= 64 || d <= -64)
		{
			m_data = 0L;
		}
		else if (d >= 0)
		{
			m_data = a >>> d;
		}
		else
		{
			m_data = a << (-d);
		}
		m_type = variant_type.INT;
	}

	// Lua 5.4 一元负号:INT MIN 取负超过 int64,按 Lua 结果为浮点 9.2233720368548e+18
	public void negate(variant l) throws Exception
	{
		l = coerce_arith(l);
		if (l.m_type == variant_type.INT)
		{
			long a = (long) (Long) l.m_data;
			if (a == Long.MIN_VALUE)
			{
				m_data = -((double) Long.MIN_VALUE);
				m_type = variant_type.REAL;
				return;
			}
			m_data = -a;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		m_data = -l.get_real();
		m_type = variant_type.REAL;
	}

	public void less(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ((long) (Long) l.m_data < (long) (Long) r.m_data) ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, cmp_num(l, r) < 0);
	}

	public void more(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ((long) (Long) l.m_data > (long) (Long) r.m_data) ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, cmp_num(l, r) > 0);
	}

	public void equal(variant l, variant r) throws Exception
	{
		m_data = l.equals(r) ? (double) 1 : (double) 0;
		m_type = variant_type.REAL;
	}

	public void less_equal(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ((long) (Long) l.m_data <= (long) (Long) r.m_data) ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, cmp_num(l, r) <= 0);
	}

	public void more_equal(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			m_data = ((long) (Long) l.m_data >= (long) (Long) r.m_data) ? (long) 1 : (long) 0;
			m_type = variant_type.INT;
			return;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		set_cmp_ret(l, r, cmp_num(l, r) >= 0);
	}

	// Lua 5.4 跨类型数值比较:INT 与 REAL 精确比较,不经 double 中转,
	// 避免 2^53 以上丢精度(9007199254740993 == 9007199254740993.0 为假,
	// math.maxinteger < math.maxinteger + 0.0 为真)。返回 2 表示不可比较
	// (涉及 NaN,所有关系运算为假)
	private static final double TWO_POW_63 = 9223372036854775808.0;

	// 三路比较 INT i 与 REAL f,语义对齐 Lua lvm.c(LTintfloat/LEintfloat)
	private static int cmp_int_real(long i, double f)
	{
		if (Double.isNaN(f))
		{
			return 2;
		}
		// f 具备精确整数表示:按整数比较(Lua 的 F2Ieq 路径)
		if (types.isint(f))
		{
			return Long.compare(i, (long) f);
		}
		// |i| 在 double 精确表示范围内(double 比较无损)
		if (i >= -9007199254740992L && i <= 9007199254740992L)
		{
			return Double.compare((double) i, f);
		}
		// i 超出 double 精度:|f| >= 2^53 时 double 必为整数,不会落到这里,
		// 落此仅剩越界情形:f >= 2^63 大于任何 int64,f < -2^63 小于任何 int64
		if (f >= TWO_POW_63)
		{
			return -1;
		}
		return 1;
	}

	// 数值三路比较:-1/0/1,NaN 不可比较返回 2
	private static int cmp_num(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return Long.compare((long) (Long) l.m_data, (long) (Long) r.m_data);
		}
		if (l.m_type == variant_type.INT && r.m_type == variant_type.REAL)
		{
			return cmp_int_real((long) (Long) l.m_data, (double) (Double) r.m_data);
		}
		if (l.m_type == variant_type.REAL && r.m_type == variant_type.INT)
		{
			int c = cmp_int_real((long) (Long) r.m_data, (double) (Double) l.m_data);
			return c == 2 ? 2 : -c;
		}
		double a = l.get_real();
		double b = r.get_real();
		if (Double.isNaN(a) || Double.isNaN(b))
		{
			return 2;
		}
		return Double.compare(a, b);
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
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data != 0 && (long) (Long) r.m_data != 0;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() != 0 && r.get_real() != 0;
	}

	public static boolean or_jne(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data != 0 || (long) (Long) r.m_data != 0;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return l.get_real() != 0 || r.get_real() != 0;
	}

	public static boolean less_jne(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data < (long) (Long) r.m_data;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return cmp_num(l, r) < 0;
	}

	public static boolean more_jne(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data > (long) (Long) r.m_data;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return cmp_num(l, r) > 0;
	}

	public static boolean equal_jne(variant l, variant r) throws Exception
	{
		return l.equals(r);
	}

	public static boolean more_equal_jne(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data >= (long) (Long) r.m_data;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return cmp_num(l, r) >= 0;
	}

	public static boolean less_equal_jne(variant l, variant r) throws Exception
	{
		if (l.m_type == variant_type.INT && r.m_type == variant_type.INT)
		{
			return (long) (Long) l.m_data <= (long) (Long) r.m_data;
		}
		l.assert_can_cal();
		r.assert_can_cal();
		return cmp_num(l, r) <= 0;
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

		// 数值跨类型按值比较(1与1.0相等),与hashCode的数值规范化配套,保证map键跨类型一致。
		// Lua 5.4 精确语义:仅当 REAL 具备精确整数表示且等于 INT 时相等
		// (9007199254740993 != 9007199254740993.0,math.maxinteger != math.maxinteger+0.0)
		if ((m_type == variant_type.INT && r.m_type == variant_type.REAL)
				|| (m_type == variant_type.REAL && r.m_type == variant_type.INT))
		{
			long i = m_type == variant_type.INT ? (long) (Long) m_data : (long) (Long) r.m_data;
			double d = m_type == variant_type.REAL ? (double) (Double) m_data : (double) (Double) r.m_data;
			return types.isint(d) && (long) d == i;
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
