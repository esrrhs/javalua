package com.github.esrrhs.fakescript;

import java.util.Map;

/**
 * 脚本值与JSON字符串互转
 * <p>
 * write: ARRAY/Map转数组/对象,INT与UUID输出为整数,REAL整数值也输出为整数<br>
 * map键仅支持STRING/INT/REAL/UUID,数值键输出为字符串形式<br>
 * parse: 整数样式解析为INT,含小数/指数解析为REAL,true/false为REAL 1/0<br>
 * 序列化与解析都有深度上限,循环引用与非法JSON以异常报告
 */
class json
{
	private static final int MAX_DEPTH = 64;

	// ==================== 序列化 ====================

	public static String write(variant v) throws Exception
	{
		StringBuilder sb = new StringBuilder();
		write_value(sb, v, 0);
		return sb.toString();
	}

	private static void write_value(StringBuilder sb, variant v, int depth) throws Exception
	{
		if (depth > MAX_DEPTH)
		{
			throw new Exception("json write fail, nested too deep");
		}

		switch (v.get_type())
		{
			case NIL:
			{
				sb.append("null");
			}
				break;
			case INT:
			case UUID:
			{
				sb.append((long) (Long) v.get_data());
			}
				break;
			case REAL:
			{
				double d = (double) (Double) v.get_data();
				if (Double.isNaN(d) || Double.isInfinite(d))
				{
					sb.append("null");
				}
				else if (d == Math.rint(d) && Math.abs(d) < 1.0E15)
				{
					sb.append((long) d);
				}
				else
				{
					sb.append(d);
				}
			}
				break;
			case STRING:
			{
				write_string(sb, (String) v.get_data());
			}
				break;
			case ARRAY:
			{
				variant_array va = (variant_array) v.get_data();
				sb.append('[');
				for (int i = 0; i < va.size(); i++)
				{
					if (i > 0)
					{
						sb.append(',');
					}
					variant e = va.get_by_index(i);
					if (e == null)
					{
						sb.append("null");
					}
					else
					{
						write_value(sb, e, depth + 1);
					}
				}
				sb.append(']');
			}
				break;
			case MAP:
			{
				variant_map vm = (variant_map) v.get_data();
				sb.append('{');
				boolean first = true;
				for (int i = 0; i < vm.size(); i++)
				{
					Map.Entry<variant, variant> e = vm.get_entry_by_index(i);
					variant k = e.getKey();

					if (!first)
					{
						sb.append(',');
					}
					first = false;

					if (k.get_type() == variant_type.STRING)
					{
						write_string(sb, (String) k.get_data());
					}
					else if (k.get_type() == variant_type.INT || k.get_type() == variant_type.UUID)
					{
						write_string(sb, "" + (long) (Long) k.get_data());
					}
					else if (k.get_type() == variant_type.REAL)
					{
						write_string(sb, "" + (double) (Double) k.get_data());
					}
					else
					{
						throw new Exception("json write fail, map key type " + k.get_type() + " not supported");
					}

					sb.append(':');
					write_value(sb, e.getValue(), depth + 1);
				}
				sb.append('}');
			}
				break;
			case POINTER:
			{
				if (v.get_data() == null)
				{
					// EVT_NULL编译为POINTER(null),与NIL等价
					sb.append("null");
				}
				else
				{
					throw new Exception("json write fail, type " + v.get_type() + " not supported");
				}
			}
				break;
			default:
			{
				throw new Exception("json write fail, type " + v.get_type() + " not supported");
			}
		}
	}

	private static void write_string(StringBuilder sb, String s)
	{
		sb.append('"');
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			switch (c)
			{
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\f':
					sb.append("\\f");
					break;
				default:
					if (c < 0x20)
					{
						sb.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}

	// ==================== 解析 ====================

	private String m_src;
	private int m_pos;

	private json(String s)
	{
		m_src = s;
		m_pos = 0;
	}

	public static variant parse(String s) throws Exception
	{
		if (s == null)
		{
			throw new Exception("json parse fail, input is null");
		}
		json p = new json(s);
		variant v = p.parse_value(0);
		p.skip_ws();
		if (p.m_pos < p.m_src.length())
		{
			throw new Exception("json parse fail, unexpected char at " + p.m_pos);
		}
		return v;
	}

	private void skip_ws()
	{
		while (m_pos < m_src.length())
		{
			char c = m_src.charAt(m_pos);
			if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
			{
				break;
			}
			m_pos++;
		}
	}

	private variant parse_value(int depth) throws Exception
	{
		if (depth > MAX_DEPTH)
		{
			throw new Exception("json parse fail, nested too deep");
		}

		skip_ws();
		if (m_pos >= m_src.length())
		{
			throw new Exception("json parse fail, unexpected end");
		}

		char c = m_src.charAt(m_pos);
		if (c == '{')
		{
			return parse_object(depth);
		}
		else if (c == '[')
		{
			return parse_array(depth);
		}
		else if (c == '"')
		{
			variant v = new variant();
			v.set_string(parse_string());
			return v;
		}
		else if (c == 't' && m_src.startsWith("true", m_pos))
		{
			m_pos += 4;
			variant v = new variant();
			v.set_real(1);
			return v;
		}
		else if (c == 'f' && m_src.startsWith("false", m_pos))
		{
			m_pos += 5;
			variant v = new variant();
			v.set_real(0);
			return v;
		}
		else if (c == 'n' && m_src.startsWith("null", m_pos))
		{
			m_pos += 4;
			return new variant();
		}
		else
		{
			return parse_number();
		}
	}

	private variant parse_object(int depth) throws Exception
	{
		m_pos++; // {
		variant_map vm = new variant_map();

		skip_ws();
		if (m_pos < m_src.length() && m_src.charAt(m_pos) == '}')
		{
			m_pos++;
			variant v = new variant();
			v.set_map(vm);
			return v;
		}

		while (true)
		{
			skip_ws();
			if (m_pos >= m_src.length() || m_src.charAt(m_pos) != '"')
			{
				throw new Exception("json parse fail, expect object key at " + m_pos);
			}
			variant key = new variant();
			key.set_string(parse_string());

			skip_ws();
			if (m_pos >= m_src.length() || m_src.charAt(m_pos) != ':')
			{
				throw new Exception("json parse fail, expect ':' at " + m_pos);
			}
			m_pos++;

			variant value = parse_value(depth + 1);
			vm.con_map_get(key).copy_from(value);

			skip_ws();
			if (m_pos >= m_src.length())
			{
				throw new Exception("json parse fail, unexpected end in object");
			}
			char c = m_src.charAt(m_pos);
			if (c == ',')
			{
				m_pos++;
				continue;
			}
			if (c == '}')
			{
				m_pos++;
				variant v = new variant();
				v.set_map(vm);
				return v;
			}
			throw new Exception("json parse fail, unexpected char at " + m_pos);
		}
	}

	private variant parse_array(int depth) throws Exception
	{
		m_pos++; // [
		variant_array va = new variant_array();
		int i = 0;

		skip_ws();
		if (m_pos < m_src.length() && m_src.charAt(m_pos) == ']')
		{
			m_pos++;
			variant v = new variant();
			v.set_array(va);
			return v;
		}

		while (true)
		{
			variant value = parse_value(depth + 1);
			variant kv = new variant();
			kv.set_real(i);
			va.con_array_get(kv).copy_from(value);
			i++;

			skip_ws();
			if (m_pos >= m_src.length())
			{
				throw new Exception("json parse fail, unexpected end in array");
			}
			char c = m_src.charAt(m_pos);
			if (c == ',')
			{
				m_pos++;
				continue;
			}
			if (c == ']')
			{
				m_pos++;
				variant v = new variant();
				v.set_array(va);
				return v;
			}
			throw new Exception("json parse fail, unexpected char at " + m_pos);
		}
	}

	private String parse_string() throws Exception
	{
		m_pos++; // "
		StringBuilder sb = new StringBuilder();
		while (m_pos < m_src.length())
		{
			char c = m_src.charAt(m_pos);
			if (c == '"')
			{
				m_pos++;
				return sb.toString();
			}
			if (c == '\\')
			{
				m_pos++;
				if (m_pos >= m_src.length())
				{
					throw new Exception("json parse fail, unexpected end in string escape");
				}
				char e = m_src.charAt(m_pos);
				switch (e)
				{
					case '"':
					case '\\':
					case '/':
						sb.append(e);
						m_pos++;
						break;
					case 'n':
						sb.append('\n');
						m_pos++;
						break;
					case 'r':
						sb.append('\r');
						m_pos++;
						break;
					case 't':
						sb.append('\t');
						m_pos++;
						break;
					case 'b':
						sb.append('\b');
						m_pos++;
						break;
					case 'f':
						sb.append('\f');
						m_pos++;
						break;
					case 'u':
					{
						if (m_pos + 4 >= m_src.length())
						{
							throw new Exception("json parse fail, bad unicode escape at " + m_pos);
						}
						String hex = m_src.substring(m_pos + 1, m_pos + 5);
						sb.append((char) Integer.parseInt(hex, 16));
						m_pos += 5;
						break;
					}
					default:
						throw new Exception("json parse fail, bad escape at " + m_pos);
				}
				continue;
			}
			sb.append(c);
			m_pos++;
		}
		throw new Exception("json parse fail, string not closed");
	}

	private variant parse_number() throws Exception
	{
		int start = m_pos;
		if (m_pos < m_src.length() && m_src.charAt(m_pos) == '-')
		{
			m_pos++;
		}
		while (m_pos < m_src.length())
		{
			char c = m_src.charAt(m_pos);
			if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-')
			{
				m_pos++;
			}
			else
			{
				break;
			}
		}
		if (m_pos == start)
		{
			throw new Exception("json parse fail, unexpected char at " + m_pos);
		}

		String num = m_src.substring(start, m_pos);
		variant v = new variant();
		if (num.indexOf('.') == -1 && num.indexOf('e') == -1 && num.indexOf('E') == -1)
		{
			try
			{
				v.set_int(Long.parseLong(num));
				return v;
			}
			catch (NumberFormatException e)
			{
				// 超出long范围,退化为浮点
			}
		}
		v.set_real(Double.valueOf(num));
		return v;
	}
}
