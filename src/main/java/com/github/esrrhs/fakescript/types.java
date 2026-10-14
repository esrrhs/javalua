package com.github.esrrhs.fakescript;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

class types
{
	// Lua 5.4 数值转字符串规则(lobject.c::tostringbuff, LUAI_NUMFFORMAT "%.14g"):
	// 1) 特殊值输出 nan / inf / -inf(C sprintf 形态,而非Java的 NaN/Infinity);
	// 2) 14 位有效数字,舍入模式与 C printf 默认一致为 ties-to-even(Java 的 %g 是
	//    HALF_UP,123456789012345.0 会进位成 ...35,而 Lua 为 ...34),因此用
	//    BigDecimal(HALF_EVEN) 自行舍入;
	// 3) %g 风格选择:指数 < -4 或 >= 14 用科学记数法,否则定点;尾数去尾随零,
	//    指数至少两位(e+05/e+20);
	// 4) 结果仅由符号与数字构成时补 ".0",保证 float 永不以整数形态输出。
	public static String format_lua_float(double d)
	{
		if (Double.isNaN(d))
		{
			return "nan";
		}
		if (d == Double.POSITIVE_INFINITY)
		{
			return "inf";
		}
		if (d == Double.NEGATIVE_INFINITY)
		{
			return "-inf";
		}
		// BigDecimal 不保留负零,单独处理(tostring(-0.0) == "-0.0")
		if (d == 0.0)
		{
			return Double.doubleToRawLongBits(d) < 0 ? "-0.0" : "0.0";
		}

		// new BigDecimal(double) 取 double 的精确十进制值,避免最短表示丢失舍入信息
		BigDecimal bd = new BigDecimal(d).round(new MathContext(14, RoundingMode.HALF_EVEN));
		bd = bd.stripTrailingZeros();

		boolean neg = bd.signum() < 0;
		String digits = bd.unscaledValue().abs().toString();
		int precision = digits.length();
		// value = digits * 10^exp
		int exp = precision - 1 - bd.scale();

		StringBuilder sb = new StringBuilder();
		if (neg)
		{
			sb.append('-');
		}
		if (exp < -4 || exp >= 14)
		{
			// 科学记数法:第一位数字 [. 其余数字] e 符号 至少两位指数
			sb.append(digits.charAt(0));
			if (precision > 1)
			{
				sb.append('.');
				sb.append(digits, 1, precision);
			}
			sb.append('e');
			sb.append(exp >= 0 ? '+' : '-');
			int ax = Math.abs(exp);
			if (ax < 10)
			{
				sb.append('0');
			}
			sb.append(ax);
		}
		else
		{
			// 定点:按 scale 放小数点
			String plain = bd.toPlainString();
			sb.append(plain.charAt(0) == '-' ? plain.substring(1) : plain);
		}

		// 整数形态补 .0(等价 Lua buff[strspn(buff, "-0123456789")] == '\0')
		int i = 0;
		if (i < sb.length() && sb.charAt(i) == '-')
		{
			i++;
		}
		while (i < sb.length() && sb.charAt(i) >= '0' && sb.charAt(i) <= '9')
		{
			i++;
		}
		if (i == sb.length())
		{
			sb.append(".0");
		}
		return sb.toString();
	}

	// Lua 5.4 tonumber 字符串解析(base10 标准路径)。
	// 十进制:整数超 int64 回落浮点;0x 前缀:整数按 uint64 重解释为 int64,
	// 含小数点/p 指数走十六进制浮点。无法解析返回 null(对应 Lua 的 nil)。
	public static variant parse_lua_number(String trimmed, Integer base)
	{
		if (trimmed.isEmpty())
		{
			return null;
		}
		// 与 Lua strtod 路径对齐:拒绝 inf/nan/infinity 等字面量
		// ("inf" 本身含 'n',一次扫描全部覆盖;十六进制数字不含 n/N)
		for (int i = 0; i < trimmed.length(); i++)
		{
			char c = trimmed.charAt(i);
			if (c == 'n' || c == 'N')
			{
				return null;
			}
		}

		int p = 0;
		boolean negative = false;
		char c0 = trimmed.charAt(0);
		if (c0 == '+' || c0 == '-')
		{
			negative = c0 == '-';
			p = 1;
		}

		boolean hex = p + 1 < trimmed.length() && trimmed.charAt(p) == '0'
				&& (trimmed.charAt(p + 1) == 'x' || trimmed.charAt(p + 1) == 'X');

		if (base == null)
		{
			if (hex)
			{
				// Lua: hex 整数按 uint64 解析并重解释为 int64(0xffff... -> -1),
				// 仅当含小数点/p 指数时才回落十六进制浮点
				Long wrapped = parse_uint64_wrapped(trimmed, p + 2, 16);
				if (wrapped != null)
				{
					return make_int(negative, wrapped);
				}
				Double hv = parse_hex_float(trimmed, p, negative);
				if (hv != null)
				{
					variant v = new variant();
					v.set_real(hv);
					return v;
				}
				return null;
			}
			// 十进制:先严格 int64,超界回落浮点(1e999 溢出为 inf,与 Lua ERANGE 一致)
			variant iv = parse_signed_decimal(trimmed);
			if (iv != null)
			{
				return iv;
			}
			// Double.parseDouble 接受 Java 特有的 f/d 后缀("10f" -> 10.0),
			// Lua strtod 不接受;先过一遍 Lua 十进制浮点词法再解析
			if (!is_lua_decimal_float(trimmed))
			{
				return null;
			}
			try
			{
				double dv = Double.parseDouble(trimmed);
				variant v = new variant();
				v.set_real(dv);
				return v;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}

		// 显式 base(2..36):Lua 不跳过 0x 前缀('x' 即非法字符),
		// 整数按 uint64 重解释(ffffffffffffffff base16 -> -1),不回落浮点
		Long wrapped = parse_uint64_wrapped(trimmed, p, base);
		if (wrapped == null)
		{
			return null;
		}
		return make_int(negative, wrapped);
	}

	private static variant make_int(boolean negative, long unsignedBits)
	{
		// 二补码取负,Java long 减法自然环绕(0x800..001 取负 -> MAX)
		long result = negative ? (0L - unsignedBits) : unsignedBits;
		variant v = new variant();
		v.set_int(result);
		return v;
	}

	// 十进制严格 int64 解析,仅接受 [-2^63, 2^63-1],其余(含超界)返回 null
	private static variant parse_signed_decimal(String s)
	{
		try
		{
			long iv = Long.parseLong(s);
			variant v = new variant();
			v.set_int(iv);
			return v;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	// Lua strtod 十进制浮点词法:[+-]?(digits[.digits?]|.digits)([eE][+-]?digits)?
	// 用于在 Double.parseDouble 前拦截 Java 特有的 f/d/F/D 后缀与其它非法形态
	private static boolean is_lua_decimal_float(String s)
	{
		int i = 0;
		int n = s.length();
		if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
		{
			i++;
		}
		int digits = 0;
		while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9')
		{
			i++;
			digits++;
		}
		if (i < n && s.charAt(i) == '.')
		{
			i++;
			while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9')
			{
				i++;
				digits++;
			}
		}
		if (digits == 0)
		{
			return false;
		}
		if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E'))
		{
			i++;
			if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
			{
				i++;
			}
			int expDigits = 0;
			while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9')
			{
				i++;
				expDigits++;
			}
			if (expDigits == 0)
			{
				return false;
			}
		}
		return i == n;
	}

	// 从 digitsStart 起按 base 解析整数,语义同 Lua 5.4 l_str2int:
	// 数字串任意长,unsigned 累加自然按 2^64 环绕(Java long 运算本身就是
	// 二补码 wrap),返回的 long 持有 uint64 位模式,符号由调用方 wrap 取负。
	// 仅在无数字或出现非法字符时返回 null。
	private static Long parse_uint64_wrapped(String s, int digitsStart, int base)
	{
		if (digitsStart >= s.length())
		{
			return null;
		}
		long acc = 0;
		for (int i = digitsStart; i < s.length(); i++)
		{
			int d = digit_value(s.charAt(i));
			if (d < 0 || d >= base)
			{
				return null;
			}
			acc = acc * base + d;
		}
		return acc;
	}

	private static int digit_value(char c)
	{
		if (c >= '0' && c <= '9')
		{
			return c - '0';
		}
		if (c >= 'a' && c <= 'z')
		{
			return c - 'a' + 10;
		}
		if (c >= 'A' && c <= 'Z')
		{
			return c - 'A' + 10;
		}
		return -1;
	}

	// 解析 C99 十六进制浮点:[sign] 0x hexdigits[.hexdigits][p[+-]decimals]
	private static Double parse_hex_float(String s, int signPos, boolean negative)
	{
		int i = signPos + 2;
		// 尾数允许超过 64 位,用 double 累积,超出精度的位按浮点舍入自然丢弃
		double mant = 0;
		int totalDigits = 0;
		int fracDigits = 0;
		while (i < s.length())
		{
			char c = s.charAt(i);
			int d = digit_value(c);
			if (d < 0 || d > 15)
			{
				break;
			}
			mant = mant * 16 + d;
			totalDigits++;
			i++;
		}
		if (i < s.length() && s.charAt(i) == '.')
		{
			i++;
			while (i < s.length())
			{
				char c = s.charAt(i);
				int d = digit_value(c);
				if (d < 0 || d > 15)
				{
					break;
				}
				mant = mant * 16 + d;
				totalDigits++;
				fracDigits++;
				i++;
			}
		}
		if (totalDigits == 0)
		{
			return null;
		}
		int exp = 0;
		if (i < s.length() && (s.charAt(i) == 'p' || s.charAt(i) == 'P'))
		{
			i++;
			int es = i;
			if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-'))
			{
				i++;
			}
			int ev = 0;
			boolean any = false;
			while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9')
			{
				ev = ev * 10 + (s.charAt(i) - '0');
				i++;
				any = true;
			}
			if (!any)
			{
				return null;
			}
			if (es < s.length() && s.charAt(es) == '-')
			{
				ev = -ev;
			}
			exp = ev;
		}
		if (i != s.length() || totalDigits == 0)
		{
			return null;
		}
		double value = Math.scalb(mant, exp - 4 * fracDigits);
		return negative ? -value : value;
	}

	// Lua 5.4 tonumber 允许的前后空白
	public static String trim_lua(String s)
	{
		int b = 0;
		int e = s.length();
		while (b < e && is_lua_space(s.charAt(b)))
		{
			b++;
		}
		while (e > b && is_lua_space(s.charAt(e - 1)))
		{
			e--;
		}
		return s.substring(b, e);
	}

	private static boolean is_lua_space(char c)
	{
		return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\u000b';
	}

	public static void seterror(fake f, String file, int lineno, String func, String errorstr, Object... args)
	{
		f.error = true;
		f.errorstr = String.format(errorstr, args);

		// 局部变量构建后原子赋值,防止并发clearerr导致的NPE
		fkerror err = new fkerror();
		err.file = file;
		err.lineno = lineno;
		err.funcname = func;
		err.message = f.errorstr;
		f.lasterror = err;

		if (f.cb != null)
		{
			f.cb.on_error(f, file, lineno, func, f.errorstr);
		}
	}

	public static void log(fake f, String str, Object... args)
	{
		if (f.cfg.open_debug_log != 0)
		{
			if (args.length == 0)
			{
				System.out.println(str);
			}
			else
			{
				System.out.printf(str, args);
				System.out.println("");
			}
		}
	}

	public static String show_exception(Exception e)
	{
		StringBuilder sb = new StringBuilder();
		for (StackTraceElement se : e.getStackTrace())
		{
			sb.append(se.toString());
			sb.append("\n");
		}
		sb.append(e.toString());
		return sb.toString();
	}

	public static String gen_package_name(String p, String n)
	{
		if (p.isEmpty())
		{
			return n;
		}
		else
		{
			return p + "." + n;
		}
	}

public static boolean isint(double d)
{
	// 必须先做范围检查:(long) 强转对 |d| >= 2^63 饱和到 MIN/MAX,
	// 饱和值提升回 double 后恰等于 ±2^63,会把 2^63 误判为可表示整数
	// (MAXINT == MAXINT + 0.0 会错误成立)
	if (!(d >= -9223372036854775808.0 && d < 9223372036854775808.0))
	{
		return false;
	}
	long l = (long) d;
	return l == d;
}

	public static String pointertoa(Object o)
	{
		if (o == null)
		{
			return "";
		}
		return o.toString();
	}

	public static String dump_addr(int code)
	{
		String ret = "";
		int addrtype = command.HIINT16(code);
		int pos = command.LOINT16(code);
		switch (addrtype)
		{
			case command.ADDR_STACK:
				ret += "STACK";
				break;
			case command.ADDR_CONST:
				ret += "CONST";
				break;
			case command.ADDR_CONTAINER:
				ret += "CONTAINER";
				break;
			default:
				ret += "unknown ";
				ret += addrtype;
		}
		ret += "\t";
		ret += pos;
		return ret;
	}

	public static String OpCodeStr(int opcode)
	{
		switch (opcode)
		{
			case command.OPCODE_ASSIGN:
				return "ASSIGN";
			case command.OPCODE_PLUS:
				return "PLUS";
			case command.OPCODE_MINUS:
				return "MINUS";
			case command.OPCODE_MULTIPLY:
				return "MULTIPLY";
			case command.OPCODE_DIVIDE:
				return "DIVIDE";
			case command.OPCODE_DIVIDE_MOD:
				return "DIVIDE_MOD";
			case command.OPCODE_STRING_CAT:
				return "STRING_CAT";
			case command.OPCODE_PLUS_ASSIGN:
				return "PLUS_ASSIGN";
			case command.OPCODE_MINUS_ASSIGN:
				return "MINUS_ASSIGN";
			case command.OPCODE_MULTIPLY_ASSIGN:
				return "MULTIPLY_ASSIGN";
			case command.OPCODE_DIVIDE_ASSIGN:
				return "DIVIDE_ASSIGN";
			case command.OPCODE_DIVIDE_MOD_ASSIGN:
				return "DIVIDE_MOD_ASSIGN";
			case command.OPCODE_RETURN:
				return "RETURN";
			case command.OPCODE_JNE:
				return "JNE";
			case command.OPCODE_JMP:
				return "JMP";
			case command.OPCODE_FORBEGIN:
				return "FORBEGIN";
			case command.OPCODE_FORLOOP:
				return "FORLOOP";
			case command.OPCODE_AND:
				return "AND";
			case command.OPCODE_OR:
				return "OR";
			case command.OPCODE_LESS:
				return "LESS";
			case command.OPCODE_MORE:
				return "MORE";
			case command.OPCODE_EQUAL:
				return "EQUAL";
			case command.OPCODE_MOREEQUAL:
				return "MOREEQUAL";
			case command.OPCODE_LESSEQUAL:
				return "LESSEQUAL";
			case command.OPCODE_NOTEQUAL:
				return "NOTEQUAL";
			case command.OPCODE_NOT:
				return "NOT";
			case command.OPCODE_AND_JNE:
				return "AND_JNE";
			case command.OPCODE_OR_JNE:
				return "OR_JNE";
			case command.OPCODE_LESS_JNE:
				return "LESS_JNE";
			case command.OPCODE_MORE_JNE:
				return "MORE_JNE";
			case command.OPCODE_EQUAL_JNE:
				return "EQUAL_JNE";
			case command.OPCODE_MOREEQUAL_JNE:
				return "MOREEQUAL_JNE";
			case command.OPCODE_LESSEQUAL_JNE:
				return "LESSEQUAL_JNE";
			case command.OPCODE_NOTEQUAL_JNE:
				return "NOTEQUAL_JNE";
			case command.OPCODE_NOT_JNE:
				return "NOT_JNE";
			case command.OPCODE_CALL:
				return "CALL";
			case command.OPCODE_SLEEP:
				return "SLEEP";
			case command.OPCODE_YIELD:
				return "YIELD";
			case command.OPCODE_NEGATE:
				return "NEGATE";
			case command.OPCODE_DIVIDE_FLOOR:
				return "DIVIDE_FLOOR";
			case command.OPCODE_DIVIDE_FLOOR_ASSIGN:
				return "DIVIDE_FLOOR_ASSIGN";
			case command.OPCODE_BIT_AND:
				return "BIT_AND";
			case command.OPCODE_BIT_OR:
				return "BIT_OR";
			case command.OPCODE_BIT_XOR:
				return "BIT_XOR";
			case command.OPCODE_BIT_NOT:
				return "BIT_NOT";
			case command.OPCODE_SHIFT_LEFT:
				return "SHIFT_LEFT";
			case command.OPCODE_SHIFT_RIGHT:
				return "SHIFT_RIGHT";
		}
		return "unknown";
	}

}
