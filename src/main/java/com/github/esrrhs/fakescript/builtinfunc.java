package com.github.esrrhs.fakescript;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.Set;

class builtinfunc
{
	private fake m_f;

	public builtinfunc(fake f)
	{
		m_f = f;
	}

	public void openbasefunc()
	{
		reg_func("print", "builtin_print");
		reg_func("format", "builtin_format");
		reg_func("array", "builtin_array");
		reg_func(interpreter.MAP_FUNC_NAME, "builtin_map");
		reg_func(interpreter.GMAP_FUNC_NAME, "builtin_gmap");
		reg_func("size", "builtin_size");
		reg_func("range", "builtin_range");
		reg_func("typeof", "builtin_typeof");
		reg_func("dumpallfunc", "builtin_dumpallfunc");
		reg_func("dumpfunc", "builtin_dumpfunc");
		reg_func("dofile", "builtin_dofile");
		reg_func("dostring", "builtin_dostring");
		reg_func("getcurfile", "builtin_getcurfile");
		reg_func("getcurline", "builtin_getcurline");
		reg_func("getcurfunc", "builtin_getcurfunc");
		reg_func("getcurcallstack", "builtin_getcurcallstack");
		reg_func("isfunc", "builtin_isfunc");
		reg_func("tonumber", "builtin_tonumber");
		reg_func("tostring", "builtin_tostring");
		reg_func("tolong", "builtin_tolong");
		reg_func("getconst", "builtin_getconst");
		reg_func("new", "builtin_new");
		reg_func("abs", "builtin_abs");
		reg_func("floor", "builtin_floor");
		reg_func("ceil", "builtin_ceil");
		reg_func("sqrt", "builtin_sqrt");
		reg_func("pow", "builtin_pow");
		reg_func("random", "builtin_random");
		reg_func("time", "builtin_time");
		reg_func("substr", "builtin_substr");
		reg_func("find", "builtin_find");
		reg_func("upper", "builtin_upper");
		reg_func("lower", "builtin_lower");
		reg_func("trim", "builtin_trim");
		reg_func("replace", "builtin_replace");
		reg_func("split", "builtin_split");
	}

	public static void builtin_new(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);

		// 沙箱:配置了白名单时,只允许实例化匹配前缀的类
		String[] whitelist = f.cfg.new_class_white_list;
		if (whitelist != null && whitelist.length > 0)
		{
			boolean allowed = false;
			for (String prefix : whitelist)
			{
				if (prefix != null && !prefix.isEmpty() && str.startsWith(prefix))
				{
					allowed = true;
					break;
				}
			}
			if (!allowed)
			{
				fk.pspush(f, "class " + str + " not in new_class_white_list");
				return;
			}
		}

		try
		{
			Class<?> c = Class.forName(str);
			Object o = c.newInstance();
			fk.pspush(f, o);
		}
		catch (Exception e)
		{
			fk.pspush(f, e.toString());
		}
	}

	public static void builtin_getconst(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);

		variant v = f.ps.push_and_get();

		variant gcv = f.pa.get_const_define(str);
		if (gcv != null)
		{
			v.copy_from(gcv);
		}
		else
		{
			v.set_nil();
		}
	}

	public static void builtin_tostring(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		// container
		variant v = f.ps.pop_and_get();
		if (v != null)
		{
			fk.pspush(f, v.toString());
		}
		else
		{
			fk.pspush(f, "");
		}
	}

	public static void builtin_tonumber(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		variant ret = new variant();
		if (v.get_type() == variant_type.STRING)
		{
			String str = (String) v.get_data();
			try
			{
				// 整数形式的字符串转为INT,其余转REAL
				ret.set_int(Long.valueOf(str));
			}
			catch (NumberFormatException e)
			{
				ret.set_real(Double.valueOf(str));
			}
		}
		else if (v.get_type() == variant_type.REAL)
		{
			ret.set_real((double) (Double) v.get_data());
		}
		else if (v.get_type() == variant_type.INT)
		{
			ret.copy_from(v);
		}
		else if (v.get_type() == variant_type.UUID)
		{
			ret.set_real((double) (long) (Long) v.get_data());
		}
		f.ps.push_and_get().copy_from(ret);
	}

	public static void builtin_tolong(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		long ret = 0;
		if (v.get_type() == variant_type.STRING)
		{
			ret = Long.valueOf((String) v.get_data());
		}
		else if (v.get_type() == variant_type.REAL)
		{
			ret = (long) (double) (Double) v.get_data();
		}
		else if (v.get_type() == variant_type.INT)
		{
			ret = (long) (Long) v.get_data();
		}
		else if (v.get_type() == variant_type.UUID)
		{
			ret = (long) (Long) v.get_data();
		}
		f.ps.push_and_get().set_int(ret);
	}

	public static void builtin_isfunc(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);
		boolean ret = fk.isfunc(f, str);
		fk.pspush(f, ret);
	}

	public static void builtin_getcurcallstack(fake f, interpreter inter)
	{
		String str = fk.getcurcallstack(f);
		fk.pspush(f, str);
	}

	public static void builtin_getcurfunc(fake f, interpreter inter)
	{
		String str = fk.getcurfunc(f);
		fk.pspush(f, str);
	}

	public static void builtin_getcurline(fake f, interpreter inter)
	{
		int line = fk.getcurline(f);
		fk.pspush(f, line);
	}

	public static void builtin_getcurfile(fake f, interpreter inter)
	{
		String str = fk.getcurfile(f);
		fk.pspush(f, str);
	}

	public static void builtin_dofile(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String file = (String) fk.pspop(f);

		// 沙箱:配置为不允许时直接失败
		if (!f.cfg.allow_dofile)
		{
			types.seterror(f, "", 0, "", "dofile %s fail, allow_dofile is false", file);
			fk.pspush(f, false);
			return;
		}

		boolean ret = fk.parse(f, file);
		fk.pspush(f, ret);
	}

	public static void builtin_dostring(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);
		boolean ret = fk.parsestr(f, str);
		fk.pspush(f, ret);
	}

	public static void builtin_dumpfunc(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String func = (String) fk.pspop(f);
		String str = f.bin.dump(func, -1);
		fk.pspush(f, str);
	}

	public static void builtin_dumpallfunc(fake f, interpreter inter)
	{
		String str = f.bin.dump();
		fk.pspush(f, str);
	}

	public static void builtin_typeof(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		String name = v.get_type().name();
		fk.pspush(f, name);
	}

	public static void builtin_range(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 2);

		// pos
		int pos = (int) (Integer) fk.trans(fk.pspop(f), Integer.TYPE);

		// container
		variant v = f.ps.pop_and_get();

		if (v.get_type() == variant_type.STRING)
		{
			if (pos >= 0 && pos < v.get_string().length())
			{
				String ret = v.get_string().substring(pos, pos + 1);
				fk.pspush(f, ret);
			}
			else
			{
				fk.pspush(f, "");
			}
		}
		else if (v.get_type() == variant_type.ARRAY)
		{
			variant ele = v.get_array().get_by_index(pos);
			if (ele != null || (pos >= 0 && pos < v.get_array().size()))
			{
				variant ret = f.ps.push_and_get();
				if (ele != null)
				{
					ret.copy_from(ele);
				}
				else
				{
					ret.set_nil();
				}
			}
			else
			{
				fk.pspush(f, false);
			}
		}
		else if (v.get_type() == variant_type.MAP)
		{
			Map.Entry<variant, variant> e = v.get_map().get_entry_by_index(pos);
			if (e != null)
			{
				variant key = f.ps.push_and_get();

				variant value = f.ps.push_and_get();

				key.copy_from(e.getKey());
				value.copy_from(e.getValue());
			}
			else
			{
				fk.pspush(f, false);
				fk.pspush(f, false);
			}
		}
		else
		{
			fk.pspush(f, false);
		}
	}

	public static void builtin_size(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		int len = 0;
		if (v.get_type() == variant_type.STRING)
		{
			len = v.get_string().length();
		}
		else if (v.get_type() == variant_type.ARRAY)
		{
			len = v.get_array().size();
		}
		else if (v.get_type() == variant_type.MAP)
		{
			len = v.get_map().size();
		}
		fk.pspush(f, len);
	}

	public static void builtin_map(fake f, interpreter inter)
	{
		variant_map m = new variant_map(f);
		variant v = f.ps.push_and_get();
		v.set_map(m);
	}

	public static void builtin_gmap(fake f, interpreter inter)
	{
		variant_map m = f.rn.get_gmap();
		variant v = f.ps.push_and_get();
		v.set_map(m);
	}

	public static void builtin_array(fake f, interpreter inter)
	{
		variant_array a = new variant_array(f);
		variant v = f.ps.push_and_get();
		v.set_array(a);
	}

	public static void builtin_format(fake f, interpreter inter)
	{
		String formatstr = "";
		if (f.ps.size() > 0)
		{
			formatstr = f.ps.get(0).toString();
		}

		StringBuilder sb = new StringBuilder();
		int j = 1;
		for (int i = 0; i < (int) formatstr.length(); i++)
		{
			if (formatstr.charAt(i) == '$')
			{
				if (i + 1 < (int) formatstr.length() && formatstr.charAt(i + 1) == '$')
				{
					sb.append('$');
					i++;
				}
				else
				{
					if (j < (int) f.ps.size())
					{
						sb.append(f.ps.get(j).toString());
						j++;
					}
				}
			}
			else
			{
				sb.append(formatstr.charAt(i));
			}
		}

		f.ps.clear();
		// ret
		fk.pspush(f, sb.toString());
	}

	public static void builtin_print(fake f, interpreter inter)
	{
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < (int) f.ps.size(); i++)
		{
			sb.append(f.ps.get(i).toString());
		}

		// printf
		f.cb.on_print(f, sb.toString());

		f.ps.clear();

		// ret
		fk.pspush(f, 1);
	}

	public static void BIF_CHECK_ARG_NUM(fake f, int n) throws Exception
	{
		if (f.ps.size() != n)
		{
			throw new Exception("buildin func param not match, give " + f.ps.size() + " need " + n);
		}
	}

	public void reg_func(String regname, String funcname)
	{
		synchronized (fk.class)
		{
			variant v = null;
			bifunc bif = null;
			if (fk.regName.get(regname) != null)
			{
				v = fk.regName.get(regname);
				bif = fk.regBindFunc.get(regname);
			}
			else
			{
				v = new variant();
				v.set_string(regname);

				bif = new bifunc();

				try
				{
					bif.m_m = this.getClass().getDeclaredMethod(funcname, fake.class, interpreter.class);
				}
				catch (Exception e)
				{
					types.seterror(m_f, "", 0, "", "reg buildin func %s fail %s", regname, types.show_exception(e));
				}

				fk.regName.put(regname, v);
				fk.regBindFunc.put(regname, bif);
			}

			m_f.fm.add_func(v, bif);
		}
	}

	// ==================== 标准库:数学与时间 ====================

	public static void builtin_abs(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		variant ret = f.ps.push_and_get();
		if (v.get_type() == variant_type.INT)
		{
			ret.set_int(Math.abs((long) (Long) v.get_data()));
		}
		else
		{
			ret.set_real(Math.abs(v.get_real()));
		}
	}

	public static void builtin_floor(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_int((long) Math.floor(v.get_real()));
	}

	public static void builtin_ceil(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_int((long) Math.ceil(v.get_real()));
	}

	public static void builtin_sqrt(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_real(Math.sqrt(v.get_real()));
	}

	public static void builtin_pow(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 2);

		variant e = f.ps.pop_and_get();
		variant b = f.ps.pop_and_get();
		f.ps.push_and_get().set_real(Math.pow(b.get_real(), e.get_real()));
	}

	// random()返回[0,1)的REAL;random(n)返回[0,n)的INT
	public static void builtin_random(fake f, interpreter inter) throws Exception
	{
		if (f.ps.size() > 0)
		{
			BIF_CHECK_ARG_NUM(f, 1);

			variant v = f.ps.pop_and_get();
			long n = v.get_int();
			if (n <= 0)
			{
				f.ps.push_and_get().set_int(0);
			}
			else
			{
				f.ps.push_and_get().set_int(ThreadLocalRandom.current().nextLong(n));
			}
		}
		else
		{
			f.ps.push_and_get().set_real(ThreadLocalRandom.current().nextDouble());
		}
	}

	// 当前毫秒时间戳
	public static void builtin_time(fake f, interpreter inter) throws Exception
	{
		f.ps.push_and_get().set_int(System.currentTimeMillis());
	}

	// ==================== 标准库:字符串 ====================

	// substr(s, start, len):从start(0起)截取len个字符,自动夹紧
	public static void builtin_substr(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 3);

		variant vlen = f.ps.pop_and_get();
		variant vstart = f.ps.pop_and_get();
		variant vs = f.ps.pop_and_get();
		String s = vs.get_string();
		int start = (int) vstart.get_real();
		int len = (int) vlen.get_real();

		if (start < 0)
		{
			start = 0;
		}
		if (start >= s.length() || len <= 0)
		{
			f.ps.push_and_get().set_string("");
			return;
		}
		int end = Math.min(s.length(), start + len);
		f.ps.push_and_get().set_string(s.substring(start, end));
	}

	// find(s, sub):子串位置,0起,不存在为-1
	public static void builtin_find(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 2);

		variant vsub = f.ps.pop_and_get();
		variant vs = f.ps.pop_and_get();
		f.ps.push_and_get().set_int(vs.get_string().indexOf(vsub.get_string()));
	}

	public static void builtin_upper(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_string(v.get_string().toUpperCase());
	}

	public static void builtin_lower(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_string(v.get_string().toLowerCase());
	}

	public static void builtin_trim(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		f.ps.push_and_get().set_string(v.get_string().trim());
	}

	// replace(s, from, to):字面量替换,非正则
	public static void builtin_replace(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 3);

		variant vto = f.ps.pop_and_get();
		variant vfrom = f.ps.pop_and_get();
		variant vs = f.ps.pop_and_get();
		String s = vs.get_string();
		String from = vfrom.get_string();
		if (from.isEmpty())
		{
			f.ps.push_and_get().set_string(s);
			return;
		}
		f.ps.push_and_get().set_string(s.replace(from, vto.get_string()));
	}

	// split(s, sep):按字面量分隔符切分为字符串数组
	public static void builtin_split(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 2);

		variant vsep = f.ps.pop_and_get();
		variant vs = f.ps.pop_and_get();
		String s = vs.get_string();
		String sep = vsep.get_string();

		variant_array va = new variant_array(f);
		if (sep.isEmpty())
		{
			variant kv0 = new variant();
			kv0.set_real(0);
			va.con_array_get(kv0).set_string(s);
		}
		else
		{
			int i = 0;
			int pos = 0;
			while (true)
			{
				int next = s.indexOf(sep, pos);
				String part = next == -1 ? s.substring(pos) : s.substring(pos, next);

				variant kv = new variant();
				kv.set_real(i);
				va.con_array_get(kv).set_string(part);
				i++;

				if (next == -1)
				{
					break;
				}
				pos = next + sep.length();
			}
		}

		variant ret = f.ps.push_and_get();
		ret.set_array(va);
	}
}