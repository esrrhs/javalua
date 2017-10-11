package fakescript;

import java.util.Map;
import java.util.Set;

class buildinfunc
{
	private fake m_f;

	public buildinfunc(fake f)
	{
		m_f = f;
	}

	public void openbasefunc()
	{
		reg_func("print", "buildin_print");
		reg_func("format", "buildin_format");
		reg_func("array", "buildin_array");
		reg_func(interpreter.MAP_FUNC_NAME, "buildin_map");
		reg_func(interpreter.GMAP_FUNC_NAME, "buildin_gmap");
		reg_func("size", "buildin_size");
		reg_func("range", "buildin_range");
		reg_func("typeof", "buildin_typeof");
		reg_func("dumpallfunc", "buildin_dumpallfunc");
		reg_func("dumpfunc", "buildin_dumpfunc");
		reg_func("dofile", "buildin_dofile");
		reg_func("dostring", "buildin_dostring");
		reg_func("getcurfile", "buildin_getcurfile");
		reg_func("getcurline", "buildin_getcurline");
		reg_func("getcurfunc", "buildin_getcurfunc");
		reg_func("getcurcallstack", "buildin_getcurcallstack");
		reg_func("isfunc", "buildin_isfunc");
		reg_func("tonumber", "buildin_tonumber");
		reg_func("tostring", "buildin_tostring");
		reg_func("tolong", "buildin_tolong");
		reg_func("getconst", "buildin_getconst");
	}

	public static void buildin_getconst(fake f, interpreter inter) throws Exception
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

	public static void buildin_tostring(fake f, interpreter inter) throws Exception
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

	public static void buildin_tonumber(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		double ret = 0;
		if (v.m_type == variant_type.STRING)
		{
			ret = Double.valueOf((String) v.m_data);
		}
		else if (v.m_type == variant_type.REAL)
		{
			ret = (double) v.m_data;
		}
		else if (v.m_type == variant_type.UUID)
		{
			ret = (double) v.m_data;
		}
		fk.pspush(f, ret);
	}

	public static void buildin_tolong(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		long ret = 0;
		if (v.m_type == variant_type.STRING)
		{
			ret = Long.valueOf((String) v.m_data);
		}
		else if (v.m_type == variant_type.REAL)
		{
			ret = (long) v.m_data;
		}
		else if (v.m_type == variant_type.UUID)
		{
			ret = (long) v.m_data;
		}
		fk.pspush(f, ret);
	}

	public static void buildin_isfunc(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);
		boolean ret = fk.isfunc(f, str);
		fk.pspush(f, ret);
	}

	public static void buildin_getcurcallstack(fake f, interpreter inter)
	{
		String str = fk.getcurcallstack(f);
		fk.pspush(f, str);
	}

	public static void buildin_getcurfunc(fake f, interpreter inter)
	{
		String str = fk.getcurfunc(f);
		fk.pspush(f, str);
	}

	public static void buildin_getcurline(fake f, interpreter inter)
	{
		int line = fk.getcurline(f);
		fk.pspush(f, line);
	}

	public static void buildin_getcurfile(fake f, interpreter inter)
	{
		String str = fk.getcurfile(f);
		fk.pspush(f, str);
	}

	public static void buildin_dofile(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String file = (String) fk.pspop(f);
		boolean ret = fk.parse(f, file);
		fk.pspush(f, ret);
	}

	public static void buildin_dostring(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String str = (String) fk.pspop(f);
		boolean ret = fk.parsestr(f, str);
		fk.pspush(f, ret);
	}

	public static void buildin_dumpfunc(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		String func = (String) fk.pspop(f);
		String str = f.bin.dump(func, -1);
		fk.pspush(f, str);
	}

	public static void buildin_dumpallfunc(fake f, interpreter inter)
	{
		String str = f.bin.dump();
		fk.pspush(f, str);
	}

	public static void buildin_typeof(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		String name = v.m_type.name();
		fk.pspush(f, name);
	}

	public static void buildin_range(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 2);

		// pos
		int pos = (int) fk.trans(fk.pspop(f), Integer.TYPE);

		// container
		variant v = f.ps.pop_and_get();

		if (v.m_type == variant_type.STRING)
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
		else if (v.m_type == variant_type.ARRAY)
		{
			if (pos >= 0 && pos < v.get_array().m_va.size())
			{
				variant ret = f.ps.push_and_get();
				if (v.get_array().m_va.get(pos) != null)
				{
					ret.copy_from(v.get_array().m_va.get(pos));
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
		else if (v.m_type == variant_type.MAP)
		{
			if (pos >= 0 && pos < v.get_map().m_vm.size())
			{
				variant key = f.ps.push_and_get();

				variant value = f.ps.push_and_get();

				Set<Map.Entry<variant, variant>> set = v.get_map().m_vm.entrySet();

				Map.Entry<variant, variant> e = (Map.Entry<variant, variant>) set.toArray()[pos];
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

	public static void buildin_size(fake f, interpreter inter) throws Exception
	{
		BIF_CHECK_ARG_NUM(f, 1);

		variant v = f.ps.pop_and_get();
		int len = 0;
		if (v.m_type == variant_type.STRING)
		{
			len = v.get_string().length();
		}
		else if (v.m_type == variant_type.ARRAY)
		{
			len = v.get_array().m_va.size();
		}
		else if (v.m_type == variant_type.MAP)
		{
			len = v.get_map().m_vm.size();
		}
		fk.pspush(f, len);
	}

	public static void buildin_map(fake f, interpreter inter)
	{
		variant_map m = new variant_map();
		variant v = f.ps.push_and_get();
		v.set_map(m);
	}

	public static void buildin_gmap(fake f, interpreter inter)
	{
		variant_map m = f.rn.get_gmap();
		variant v = f.ps.push_and_get();
		v.set_map(m);
	}

	public static void buildin_array(fake f, interpreter inter)
	{
		variant_array a = new variant_array();
		variant v = f.ps.push_and_get();
		v.set_array(a);
	}

	public static void buildin_format(fake f, interpreter inter)
	{
		String formatstr = "";
		if (f.ps.size() > 0)
		{
			formatstr = f.ps.get(0).toString();
		}

		String str = "";
		int j = 1;
		for (int i = 0; i < (int) formatstr.length(); i++)
		{
			if (formatstr.getBytes()[i] == '$')
			{
				if (i + 1 < (int) formatstr.length() && formatstr.getBytes()[i + 1] == '$')
				{
					str += formatstr.substring(i, i + 1);
					i++;
				}
				else
				{
					if (j < (int) f.ps.size())
					{
						str += f.ps.get(j).toString();
						j++;
					}
				}
			}
			else
			{
				str += formatstr.substring(i, i + 1);
			}
		}

		f.ps.clear();
		// ret
		fk.pspush(f, str);
	}

	public static void buildin_print(fake f, interpreter inter)
	{
		String str = "";

		for (int i = 0; i < (int) f.ps.size(); i++)
		{
			str += f.ps.get(i).toString();
		}

		// printf
		f.cb.on_print(f, str);

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
					System.out.println(e);
				}

				fk.regName.put(regname, v);
				fk.regBindFunc.put(regname, bif);
			}

			m_f.fm.add_func(v, bif);
		}
	}

}
