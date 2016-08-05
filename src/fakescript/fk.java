package fakescript;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class fk
{
	/**
	 * 版本号
	 */
	public static final String version = "0.1";

	/**
	 * 创建fake对象
	 * <p>
	 * fake为上下文环境<br>
	 * 所有接口在fake中执行
	 * 
	 * @param config
	 *            具体的参数
	 * @return fake对象
	 */
	public static fake newfake(fkconfig config)
	{
		fake f = new fake();
		if (config != null)
		{
			f.cfg = config;
		}
		return f;
	}

	/**
	 * 绑定java函数
	 * <p>
	 * 遍历package下所有类<br>
	 * 绑定标记fakescript的函数
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param packagename
	 *            包的名字
	 * 
	 * @return 无
	 */
	public static void reg(fake f, String packagename)
	{
		List<Class<?>> tmp = packagehelper.getClasses(f, packagename);
		for (Class<?> c : tmp)
		{
			Method[] ms = c.getDeclaredMethods();
			for (Method m : ms)
			{
				if (m.isAnnotationPresent(fakescript.class))
				{
					fakescript fn = (fakescript) m.getAnnotation(fakescript.class);

					String name = fn.name();
					if (name.equals(""))
					{
						name = m.getName();
					}

					fkfunctor fkf = new fkfunctor();
					fkf.m_c = c;
					fkf.m_m = m;
					fkf.m_param = m.getParameterTypes();
					fkf.m_ret = m.getReturnType();
					fkf.m_is_staic = Modifier.isStatic(m.getModifiers());

					if (!fkf.m_is_staic)
					{
						name = c.getName() + name;
					}
					else
					{
						name = c.getSimpleName() + "." + name;
					}

					variant v = new variant();
					v.set_string(name);

					f.fm.add_func(v, fkf);

					types.log(f, "fk reg %s %s", name, fkf);
				}
			}
		}

	}

	/**
	 * 绑定java函数
	 * <p>
	 * 遍历package下所有类<br>
	 * 绑定所有的函数
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param packagename
	 *            包的名字
	 * 
	 * @return 无
	 */
	public static void regall(fake f, String packagename)
	{
		List<Class<?>> tmp = packagehelper.getClasses(f, packagename);
		for (Class<?> c : tmp)
		{
			regclass(f, c);
		}
	}

	/**
	 * 绑定java函数
	 * <p>
	 * 遍历类下所有函数<br>
	 * 绑定类所有的函数
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param packagename
	 *            包的名字
	 * 
	 * @return 无
	 */
	public static void regclass(fake f, Class<?> c)
	{
		Method[] ms = c.getDeclaredMethods();
		for (Method m : ms)
		{
			String name = m.getName();
			if (m.isAnnotationPresent(fakescript.class))
			{
				fakescript fn = (fakescript) m.getAnnotation(fakescript.class);
				if (fn != null && !fn.name().isEmpty())
				{
					name = fn.name();
				}
			}

			fkfunctor fkf = new fkfunctor();
			fkf.m_c = c;
			fkf.m_m = m;
			fkf.m_param = m.getParameterTypes();
			fkf.m_ret = m.getReturnType();
			fkf.m_is_staic = Modifier.isStatic(m.getModifiers());

			if (!fkf.m_is_staic)
			{
				name = c.getName() + name;
			}
			else
			{
				name = c.getSimpleName() + "." + name;
			}

			variant v = new variant();
			v.set_string(name);

			f.fm.add_func(v, fkf);

			types.log(f, "fk reg %s %s", name, fkf);
		}
	}

	/**
	 * 设置回调函数
	 * <p>
	 * 如错误处理<br>
	 * 打印函数
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param cb
	 *            回调类
	 * 
	 * @return 无
	 */
	public static void set_callback(fake f, callback cb)
	{
		f.cb = cb;
	}

	/**
	 * 解析文件
	 * <p>
	 * 解析脚本<br>
	 * 编译成字节码
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param filename
	 *            文件名
	 * 
	 * @return 无
	 */
	public static boolean parse(fake f, String filename)
	{
		f.pa.clear();
		return f.pa.parse(filename);
	}

	/**
	 * 解析代码
	 * <p>
	 * 解析文本字符串代码<br>
	 * 编译成字节码
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param str
	 *            文件名
	 * 
	 * @return 无
	 */
	public static boolean parsestr(fake f, String str)
	{
		f.pa.clear();
		return f.pa.parsestr(str);
	}

	/**
	 * 执行脚本
	 * <p>
	 * 执行指定脚本函数<br>
	 * 结果通过Object返回，注意内部数值都是用double，所以转换时需要注意下
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param func
	 *            函数名
	 * 
	 * @param args
	 *            参数
	 * 
	 * @return 无
	 */
	public static Object run(fake f, String func, Object... args)
	{
		psclear(f);
		for (Object arg : args)
		{
			pspush(f, arg);
		}
		runps(f, func);
		return pspop(f);
	}

	/**
	 * 获取当前文件
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static String getcurfile(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_interpreter().get_running_file_name();
		}
		return "nil";
	}

	/**
	 * 获取当前文件行号
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static int getcurline(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_interpreter().get_running_file_line();
		}
		return 0;
	}

	/**
	 * 获取当前函数
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static String getcurfunc(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_interpreter().get_running_func_name();
		}
		return "nil";
	}

	/**
	 * 获取当前调用堆栈
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static String getcurcallstack(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_interpreter().get_running_call_stack();
		}
		return "nil";
	}

	/**
	 * 获取当前协程信息
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static String getcurroutine(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_routine_info();
		}
		return "nil";
	}

	/**
	 * 打开基本的内置函数
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static void openbaselib(fake f)
	{
		f.bif.openbasefunc();
	}

	/**
	 * 是否有某个函数
	 * <p>
	 * 注意类的非静态成员函数在绑定的时候会在前面加上类名<br>
	 * 如test.A类的aaa函数，他的实际函数名是test.Aaaa
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @param name
	 *            函数名
	 * 
	 * @return 无
	 */
	public static boolean isfunc(fake f, String name)
	{
		variant funcv = new variant();
		funcv.set_string(name);
		return f.fm.get_func(funcv) != null;
	}

	/**
	 * 打开性能监控
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static void openprofile(fake f)
	{
		f.pf.open();
	}

	/**
	 * 关闭性能监控
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static void closeprofile(fake f)
	{
		f.pf.close();
	}

	/**
	 * 打印性能监控数据
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 * 
	 * @return 无
	 */
	public static String dumpprofile(fake f)
	{
		return f.pf.dump();
	}

	protected static void psclear(fake f)
	{
		f.ps.clear();
	}

	protected static void pspush(fake f, Object arg)
	{
		variant v = f.ps.push_and_get();

		if (arg == null)
		{
			v.set_nil();
			return;
		}

		Class<? extends Object> c = arg.getClass();
		if (c == Byte.class)
		{
			Byte b = (Byte) arg;
			v.set_real(b);
		}
		else if (c == Short.class)
		{
			Short b = (Short) arg;
			v.set_real(b);
		}
		else if (c == Integer.class)
		{
			Integer b = (Integer) arg;
			v.set_real(b);
		}
		else if (c == Long.class)
		{
			long b = (long) arg;
			v.set_uuid(b);
		}
		else if (c == Float.class)
		{
			Float b = (Float) arg;
			v.set_real(b);
		}
		else if (c == Double.class)
		{
			Double b = (Double) arg;
			v.set_real(b);
		}
		else if (c == Boolean.class)
		{
			Boolean b = (Boolean) arg;
			v.set_real(b ? 1 : 0);
		}
		else if (c == String.class)
		{
			String b = (String) arg;
			v.set_string(b);
		}
		else
		{
			v.set_pointer(arg);
		}
	}

	protected static Object pspop(fake f)
	{
		if (f.ps.size() == 0)
		{
			return null;
		}

		variant v = f.ps.pop_and_get();
		if (v.m_type == variant_type.NIL)
		{
			return null;
		}
		else if (v.m_type == variant_type.REAL)
		{
			double b = (double) v.m_data;
			return b;
		}
		else if (v.m_type == variant_type.STRING)
		{
			String b = (String) v.m_data;
			return b;
		}
		else if (v.m_type == variant_type.POINTER)
		{
			return v.m_data;
		}
		else if (v.m_type == variant_type.UUID)
		{
			long b = (long) v.m_data;
			return b;
		}
		else
		{
			return null;
		}
	}

	protected static Object trans(Object src, Class<?> c)
	{
		Class<?> srcc = src.getClass();

		if (c == Byte.class || c == Byte.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (byte) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (byte) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (byte) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (byte) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (byte) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (byte) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (byte) 1 : (byte) 0;
			}
			else if (srcc == String.class)
			{
				return Byte.valueOf((String) src);
			}
			else
			{
				return (byte) 0;
			}
		}
		else if (c == Short.class || c == Short.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (short) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (short) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (short) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (short) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (short) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (short) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (short) 1 : (short) 0;
			}
			else if (srcc == String.class)
			{
				return Short.valueOf((String) src);
			}
			else
			{
				return (short) 0;
			}
		}
		else if (c == Integer.class || c == Integer.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (int) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (int) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (int) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (int) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (int) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (int) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (int) 1 : (int) 0;
			}
			else if (srcc == String.class)
			{
				return Integer.valueOf((String) src);
			}
			else
			{
				return (int) 0;
			}
		}
		else if (c == Long.class || c == Long.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (long) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (long) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (long) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (long) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (long) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (long) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (long) 1 : (long) 0;
			}
			else if (srcc == String.class)
			{
				return Long.valueOf((String) src);
			}
			else
			{
				return (long) 0;
			}
		}
		else if (c == Float.class || c == Float.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (float) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (float) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (float) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (float) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (float) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (float) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (float) 1 : (float) 0;
			}
			else if (srcc == String.class)
			{
				return Float.valueOf((String) src);
			}
			else
			{
				return (float) 0;
			}
		}
		else if (c == Double.class || c == Double.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (double) (byte) src;
			}
			else if (srcc == Short.class)
			{
				return (double) (short) src;
			}
			else if (srcc == Integer.class)
			{
				return (double) (int) src;
			}
			else if (srcc == Long.class)
			{
				return (double) (long) src;
			}
			else if (srcc == Float.class)
			{
				return (double) (float) src;
			}
			else if (srcc == Double.class)
			{
				return (double) (double) src;
			}
			else if (srcc == Boolean.class)
			{
				return (boolean) src ? (double) 1 : (double) 0;
			}
			else if (srcc == String.class)
			{
				return Double.valueOf((String) src);
			}
			else
			{
				return (double) 0;
			}
		}
		else if (c == Boolean.class || c == Boolean.TYPE)
		{
			if (srcc == Byte.class)
			{
				return (byte) src != 0;
			}
			else if (srcc == Short.class)
			{
				return (short) src != 0;
			}
			else if (srcc == Integer.class)
			{
				return (int) src != 0;
			}
			else if (srcc == Long.class)
			{
				return (long) src != 0;
			}
			else if (srcc == Float.class)
			{
				return (float) src != 0;
			}
			else if (srcc == Double.class)
			{
				return (double) src != 0;
			}
			else if (srcc == Boolean.class)
			{
				return src;
			}
			else if (srcc == String.class)
			{
				return Integer.valueOf((String) src) != 0;
			}
			else
			{
				return false;
			}
		}
		else if (c == String.class)
		{
			return String.valueOf(src);
		}
		else
		{
			if (srcc == Byte.class)
			{
				return null;
			}
			else if (srcc == Short.class)
			{
				return null;
			}
			else if (srcc == Integer.class)
			{
				return null;
			}
			else if (srcc == Long.class)
			{
				return null;
			}
			else if (srcc == Float.class)
			{
				return null;
			}
			else if (srcc == Double.class)
			{
				return null;
			}
			else if (srcc == Boolean.class)
			{
				return null;
			}
			else if (srcc == String.class)
			{
				return null;
			}
			else
			{
				return src;
			}
		}
	}

	protected static void runps(fake f, String func)
	{
		variant funcv = new variant();
		funcv.set_string(func);

		processor pro = new processor(f);

		try
		{
			routine r = pro.start_routine(funcv, new ArrayList<Integer>());

			f.rn.push_pro(pro);
			pro.run();
			f.rn.pop_pro();

			variant ret = r.get_ret();

			variant v = f.ps.push_and_get();
			v.copy_from(ret);
		}
		catch (Exception e)
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			types.seterror(f, getcurfile(f), getcurline(f), getcurfunc(f), e.toString() + "\n" + sw.toString());
			pw.close();
			f.ps.push_and_get();
		}
	}
}