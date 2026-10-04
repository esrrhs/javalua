package com.github.esrrhs.fakescript;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class fk
{
	/**
	 * 版本号
	 */
	public static final String version = "1.0.15";

	// 节省内存
	protected static final HashMap<String, variant> regName = new HashMap<String, variant>();
	protected static final HashMap<String, fkfunctor> regFunctor = new HashMap<String, fkfunctor>();
	protected static final HashMap<String, bifunc> regBindFunc = new HashMap<String, bifunc>();

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
	 * 复制fake对象
	 * <p>
	 * fake为上下文环境<br>
	 * 所有接口在fake中执行
	 *
	 * @param f
	 *            fake对象
	 * @return fake对象
	 */
	public static fake clone(fake f)
	{
		return f.clonef();
	}

	/**
	 * 上一次执行是否失败
	 * <p>
	 * fake为上下文环境<br>
	 * 所有接口在fake中执行
	 *
	 * @param f
	 *            fake对象
	 * @return 是否失败
	 */
	public static boolean error(fake f)
	{
		return f.error;
	}

	/**
	 * 获取上一次错误信息
	 * 
	 * @param f
	 *            fake对象
	 * @return 错误信息
	 */
	public static String geterror(fake f)
	{
		return f.errorstr;
	}

	/**
	 * 获取上一次错误的结构化信息
	 * <p>
	 * 按字段返回文件、行号、函数与完整信息,无错误时为null<br>
	 * fk.clearerr及下次run/parse开始时清空
	 *
	 * @param f
	 *            fake对象
	 * @return 结构化错误,无错误时为null
	 */
	public static fkerror getlasterror(fake f)
	{
		return f.lasterror;
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
	 */
	public static void reg(fake f, String packagename)
	{
		List<Class<?>> tmp = packagehelper.getClasses(f, packagename);
		for (Class<?> c : tmp)
		{
			Method[] ms = c.getMethods();
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

					reg_method(f, name, c, m);
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
	 * @param c
	 *            class
	 *
	 */
	public static void regclass(fake f, Class<?> c)
	{
		Class<?>[] cs = c.getDeclaredClasses();
		for (Class<?> cc : cs)
		{
			regclass(f, cc);
		}

		Method[] ms = c.getMethods();
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

			reg_method(f, name, c, m);
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
	 */
	public static void set_callback(fake f, callback cb)
	{
		if (cb == null)
		{
			f.cb = new fake.default_callback();
		}
		else
		{
			f.cb = cb;
		}
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
		if (!enter(f))
		{
			return false;
		}
		try
		{
			f.clearerr();
			f.pa.clear();
			return f.pa.parse(filename);
		}
		finally
		{
			f.exit();
		}
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
		if (!enter(f))
		{
			return false;
		}
		try
		{
			f.clearerr();
			f.pa.clear();
			return f.pa.parsestr(str);
		}
		finally
		{
			f.exit();
		}
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
		Object[] ret = runmulti(f, func, args);
		return ret.length > 0 ? ret[0] : null;
	}

	/**
	 * 执行脚本,取回全部返回值
	 * <p>
	 * 脚本函数可以return多个值<br>
	 * 结果按顺序通过Object数组返回<br>
	 * 注意内部数值都是用double，所以转换时需要注意下
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
	 * @return 返回值数组
	 */
	public static Object[] runmulti(fake f, String func, Object... args)
	{
		psclear(f);
		for (Object arg : args)
		{
			pspush(f, arg);
		}
		runps(f, func);

		int num = f.ps.size();
		Object[] ret = new Object[num];
		for (int i = 0; i < num; i++)
		{
			ret[i] = psget(f, i);
		}
		return ret;
	}

	/**
	 * 分帧执行脚本
	 * <p>
	 * 与run不同,每次调用最多执行per_frame_cmd_num条命令后返回,适合宿主按帧调度的场景<br>
	 * 首次调用自动启动指定脚本函数,后续调用继续执行,func与args被忽略<br>
	 * 协程未全部结束时返回null;全部结束后返回返回值数组(与runmulti一致)<br>
	 * 结束或出错后可以重新用新的func和args再次启动
	 *
	 * @param f
	 *            上下文环境
	 *
	 * @param func
	 *            函数名,仅在首次调用时生效
	 *
	 * @param args
	 *            参数,仅在首次调用时生效
	 *
	 * @return 未结束返回null,结束返回返回值数组
	 */
	public static Object[] resume(fake f, String func, Object... args)
	{
		if (!enter(f))
		{
			return new Object[] { null };
		}
		try
		{
			return resume_inner(f, func, args);
		}
		catch (Exception e)
		{
			types.seterror(f, "", 0, "", "resume fail " + types.show_exception(e));
			return new Object[] { null };
		}
		finally
		{
			f.exit();
		}
	}

	private static Object[] resume_inner(fake f, String func, Object... args) throws Exception
	{
		processor pro = f.rn.cur_pro();
		if (pro == null)
		{
			// 启动
			f.clearerr();
			f.stopflag = false;
			psclear(f);
			for (Object arg : args)
			{
				pspush(f, arg);
			}
			variant funcv = new variant();
			funcv.set_string(func);

			pro = new processor(f);
			pro.set_max_runcmd(f.cfg.per_frame_cmd_num);
			try
			{
				pro.start_routine(funcv, new ArrayList<Integer>());
				f.rn.push_pro(pro);
			}
			catch (Exception e)
			{
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				types.seterror(f, getcurfile(f), getcurline(f), getcurfunc(f), e.toString() + "\n" + sw.toString());
				pw.close();
				return new Object[] { null };
			}
		}

		try
		{
			pro.run();

			if (pro.get_routine_num() != 0)
			{
				// 未结束
				return null;
			}

			// 结束了,取返回值
			ArrayList<variant> rets = pro.get_entrycurroutine().get_interpreter().get_ret_all();
			Object[] ret = new Object[rets.isEmpty() ? 1 : rets.size()];
			if (rets.isEmpty())
			{
				ret[0] = null;
			}
			else
			{
				for (int i = 0; i < rets.size(); i++)
				{
					ret[i] = variant_to_object(rets.get(i));
				}
			}
			f.rn.pop_pro();
			return ret;
		}
		catch (Exception e)
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String msg = e.toString() + "\n" + sw.toString();
			String callstack = getcurcallstack(f);
			if (!callstack.equals("nil"))
			{
				msg += "\ncall stack:\n" + callstack;
			}
			types.seterror(f, getcurfile(f), getcurline(f), getcurfunc(f), msg);
			pw.close();
			f.rn.pop_pro();
			return new Object[] { null };
		}
	}

	/**
	 * 停止脚本执行
	 * <p>
	 * 在下一条命令边界生效<br>
	 * 当前run会以错误结束
	 *
	 * @param f
	 *            上下文环境
	 *
	 */
	public static void stop(fake f)
	{
		f.stopflag = true;
	}

	public static Object debugrun(fake f, String func, Object... args)
	{
		psclear(f);
		for (Object arg : args)
		{
			pspush(f, arg);
		}
		openstepmod(f);
		rundebugps(f, func);
		closestepmod(f);
		return pspop(f);
	}

	public static void openstepmod(fake f)
	{
		f.rn.set_stepmod(true);
	}

	public static void closestepmod(fake f)
	{
		f.rn.set_stepmod(false);
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

	public static int getcurroutineid(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_id();
		}
		return 0;
	}

	public static String getcurvariantbyroutinebyframe(fake f, int rid, int frame, String name, int line)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			wrapper ret = new wrapper(new String());
			wrapper retline = new wrapper(Integer.valueOf(0));
			p.get_routine_by_id(rid).get_interpreter().get_running_variant(frame, name, line, ret, retline);
			return (String) ret.d;
		}
		return "";
	}

	public static void setcurvariantbyroutinebyframe(fake f, int rid, int frame, String name, String value, int line)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			p.get_routine_by_id(rid).get_interpreter().set_running_variant(frame, name, line, value);
		}
	}

	/**
	 * @deprecated 拼写错误,请使用{@link #getcurvariantbyroutinebyframe}
	 */
	@Deprecated
	public static String getcurvaiantbyroutinebyframe(fake f, int rid, int frame, String name, int line)
	{
		return getcurvariantbyroutinebyframe(f, rid, frame, name, line);
	}

	/**
	 * @deprecated 拼写错误,请使用{@link #setcurvariantbyroutinebyframe}
	 */
	@Deprecated
	public static void setcurvaiantbyroutinebyframe(fake f, int rid, int frame, String name, String value, int line)
	{
		setcurvariantbyroutinebyframe(f, rid, frame, name, value, line);
	}

	public static String getcurfuncbyroutinebyframe(fake f, int rid, int frame)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			wrapper stackinfo = new wrapper(new String());
			wrapper func = new wrapper(new String());
			wrapper file = new wrapper(new String());
			wrapper line = new wrapper(Integer.valueOf(0));
			p.get_routine_by_id(rid).get_interpreter().get_running_call_stack_frame_info(frame, stackinfo, func, file,
					line);
			return (String) func.d;
		}
		return "nil";
	}

	public static String getcurroutinebyid(fake f, int rid)
	{
		processor p = f.rn.cur_pro();
		if (p != null)
		{
			return p.get_routine_info_by_id(rid);
		}
		return "";
	}

	public static int getcurcallstacklength(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_curroutine() != null)
		{
			return p.get_curroutine().get_interpreter().get_running_call_stack_length();
		}
		return 0;
	}

	public static String getcurfilebyroutinebyframe(fake f, int rid, int frame)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			wrapper stackinfo = new wrapper(new String());
			wrapper func = new wrapper(new String());
			wrapper file = new wrapper(new String());
			wrapper line = new wrapper(Integer.valueOf(0));
			p.get_routine_by_id(rid).get_interpreter().get_running_call_stack_frame_info(frame, stackinfo, func, file,
					line);
			return (String) file.d;
		}
		return "nil";
	}

	public static int getcurlinebyroutinebyframe(fake f, int rid, int frame)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			wrapper stackinfo = new wrapper(new String());
			wrapper func = new wrapper(new String());
			wrapper file = new wrapper(new String());
			wrapper line = new wrapper(Integer.valueOf(0));
			p.get_routine_by_id(rid).get_interpreter().get_running_call_stack_frame_info(frame, stackinfo, func, file,
					line);
			return (int) (Integer) line.d;
		}
		return 0;
	}

	public static String getcurcallstackbyroutinebyframe(fake f, int rid, int frame)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			wrapper stackinfo = new wrapper(new String());
			wrapper func = new wrapper(new String());
			wrapper file = new wrapper(new String());
			wrapper line = new wrapper(Integer.valueOf(0));
			p.get_routine_by_id(rid).get_interpreter().get_running_call_stack_frame_info(frame, stackinfo, func, file,
					line);
			return (String) stackinfo.d;
		}
		return "nil";
	}

	public static String getfuncfile(fake f, String func)
	{
		variant funcv = new variant();
		funcv.set_string(func);
		funcunion ff = f.fm.get_func(funcv);
		if (ff != null && ff.m_havefb)
		{
			return ff.m_fb.get_filename();
		}
		return "";
	}

	public static int getfuncstartline(fake f, String func)
	{
		variant funcv = new variant();
		funcv.set_string(func);
		funcunion ff = f.fm.get_func(funcv);
		if (ff != null && ff.m_havefb)
		{
			return ff.m_fb.get_binary_lineno(0);
		}
		return 0;
	}

	public static int getcurroutinenum(fake f)
	{
		processor p = f.rn.cur_pro();
		if (p != null)
		{
			return p.get_routine_num();
		}
		return 0;
	}

	public static int getroutineidbyindex(fake f, int index)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_index(index) != null)
		{
			return p.get_routine_by_index(index).get_id();
		}
		return 0;
	}

	public static String getcurroutinebyindex(fake f, int index)
	{
		processor p = f.rn.cur_pro();
		if (p != null)
		{
			return p.get_routine_info_by_index(index);
		}
		return "";
	}

	public static int getcurcallstacklengthbyroutine(fake f, int rid)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			return p.get_routine_by_id(rid).get_interpreter().get_running_call_stack_length();
		}
		return 0;
	}

	public static int getcurbytecodeposbyroutine(fake f, int rid)
	{
		processor p = f.rn.cur_pro();
		if (p != null && p.get_routine_by_id(rid) != null)
		{
			return p.get_routine_by_id(rid).get_interpreter().get_running_bytecode_pos();
		}
		return -1;
	}

	public static String dumpfunc(fake f, String func, int pos)
	{
		return f.bin.dump(func, pos);
	}

	public static boolean ishaveroutine(fake f, int rid)
	{
		processor p = f.rn.cur_pro();
		if (p != null)
		{
			return p.get_routine_by_id(rid) != null;
		}
		return false;
	}

	public static String getfilecode(fake f, String filename, int line)
	{
		if (filename.isEmpty() || line <= 0)
		{
			return "";
		}

		try
		{
			String encoding = "utf-8";
			Reader reader = new InputStreamReader(new FileInputStream(filename), encoding);
			BufferedReader bufferedReader = new BufferedReader(reader);

			int i = 0;
			String ret = "";
			while (true)
			{
				String str = bufferedReader.readLine();
				if (str == null)
				{
					break;
				}

				i++;
				if (i >= line)
				{
					ret = str;
					break;
				}
			}
			return ret;
		}
		catch (Exception e)
		{
			types.log(f, "getfilecode %s:%d fail %s", filename, line, types.show_exception(e));
			return "";
		}
	}

	/**
	 * 打开基本的内置函数
	 * <p>
	 * 
	 * @param f
	 *            上下文环境
	 *
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
	 * @return dump
	 */
	public static String dumpprofile(fake f)
	{
		return f.pf.dump();
	}

	// 并发访问检测入口:占用失败返回false并记录错误(错误状态在并发误用下为last-writer-wins)
	private static boolean enter(fake f)
	{
		if (f.try_enter())
		{
			return true;
		}
		types.seterror(f, "", 0, "", "fake is busy, concurrent access from another thread detected");
		return false;
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
			v.set_pointer(null);
			return;
		}

		Class<? extends Object> c = arg.getClass();
		if (c == Byte.class || c == Short.class || c == Integer.class)
		{
			// 宿主整数统一映射为脚本INT
			Number b = (Number) arg;
			v.set_int(b.longValue());
		}
		else if (c == Long.class)
		{
			long b = (long) (Long) arg;
			if (f.cfg.long_as_int)
			{
				// 不使用UUID的宿主可开启,Long参数直接以64位整数进入脚本
				v.set_int(b);
			}
			else
			{
				// 历史约定:Long映射为UUID,不参与计算
				v.set_uuid(b);
			}
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

	// variant转宿主Object:REAL转Double,UUID转Long,STRING/POINTER原样,其余为null
	protected static Object variant_to_object(variant v)
	{
		if (v.get_type() == variant_type.NIL)
		{
			return null;
		}
		else if (v.get_type() == variant_type.INT)
		{
			return (long) (Long) v.get_data();
		}
		else if (v.get_type() == variant_type.REAL)
		{
			return (double) (Double) v.get_data();
		}
		else if (v.get_type() == variant_type.STRING)
		{
			return v.get_data();
		}
		else if (v.get_type() == variant_type.POINTER)
		{
			return v.get_data();
		}
		else if (v.get_type() == variant_type.UUID)
		{
			return (long) (Long) v.get_data();
		}
		else
		{
			return null;
		}
	}

	protected static Object psget(fake f, int i)
	{
		if (f.ps.size() == 0)
		{
			return null;
		}

		return variant_to_object(f.ps.get(i));
	}

	protected static Object pspop(fake f)
	{
		if (f.ps.size() == 0)
		{
			return null;
		}

		return variant_to_object(f.ps.pop_and_get());
	}

	/**
	 * 把脚本侧的Object转换为绑定时要求的目标类型
	 * <p>
	 * 数值之间按目标类型窄化/加宽;布尔转数值为1/0,数值转布尔为非0即真<br>
	 * 字符串按目标数值类型解析;字符串转布尔按整数解析<br>
	 * 其他对象(如绑定的Java对象)对primitive目标转为零值,对特定类型目标直接透传
	 *
	 * @param src
	 *            源对象
	 *
	 * @param c
	 *            目标类型
	 *
	 * @return 转换结果
	 */
	protected static Object trans(Object src, Class<?> c)
	{
		if (src == null)
		{
			return null;
		}

		// 这种一般是模板，直接转过去
		if (c == Object.class)
		{
			return src;
		}

		if (c == String.class)
		{
			return String.valueOf(src);
		}

		if (c == Boolean.class || c == Boolean.TYPE)
		{
			if (src instanceof Boolean)
			{
				return src;
			}
			if (src instanceof Number)
			{
				return ((Number) src).doubleValue() != 0;
			}
			if (src instanceof String)
			{
				return Integer.valueOf((String) src) != 0;
			}
			return false;
		}

		if (c == Byte.class || c == Byte.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).byteValue();
			}
			return (byte) 0;
		}

		if (c == Short.class || c == Short.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).shortValue();
			}
			return (short) 0;
		}

		if (c == Integer.class || c == Integer.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).intValue();
			}
			return (int) 0;
		}

		if (c == Long.class || c == Long.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).longValue();
			}
			return (long) 0;
		}

		if (c == Float.class || c == Float.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).floatValue();
			}
			return (float) 0;
		}

		if (c == Double.class || c == Double.TYPE)
		{
			if (src instanceof Number)
			{
				return ((Number) src).doubleValue();
			}
			return (double) 0;
		}

		// 这些就是特定的类型了
		if (src instanceof Number || src instanceof Boolean || src instanceof String)
		{
			return null;
		}
		return src;
	}

	protected static boolean canTrans(Object src, Class<?> c)
	{
		if (src == null)
		{
			return true;
		}

		Class<?> srcc = src.getClass();

		if ((c == Byte.class || c == Byte.TYPE) || (c == Short.class || c == Short.TYPE)
				|| (c == Integer.class || c == Integer.TYPE) || (c == Long.class || c == Long.TYPE)
				|| (c == Float.class || c == Float.TYPE) || (c == Double.class || c == Double.TYPE)
				|| (c == Boolean.class || c == Boolean.TYPE))
		{
			if (srcc == Byte.class)
			{
				return true;
			}
			else if (srcc == Short.class)
			{
				return true;
			}
			else if (srcc == Integer.class)
			{
				return true;
			}
			else if (srcc == Long.class)
			{
				return true;
			}
			else if (srcc == Float.class)
			{
				return true;
			}
			else if (srcc == Double.class)
			{
				return true;
			}
			else if (srcc == Boolean.class)
			{
				return true;
			}
			else if (srcc == String.class)
			{
				return false;
			}
			else
			{
				return false;
			}
		}
		else if (c == String.class)
		{
			if (srcc == String.class)
			{
				return true;
			}
			else
			{
				return false;
			}
		}
		// 这种一般是模板，直接转过去
		else if (c == Object.class)
		{
			return true;
		}
		// 这些就是特定的类型了
		else
		{
			return c.isInstance(src);
		}
	}

	private static void rundebugps(fake f, String func)
	{
		if (!enter(f))
		{
			return;
		}
		try
		{
			rundebugps_inner(f, func);
		}
		catch (Exception e)
		{
			types.seterror(f, "", 0, "", "debug run fail " + types.show_exception(e));
		}
		finally
		{
			f.exit();
		}
	}

	private static void rundebugps_inner(fake f, String func) throws Exception
	{
		variant funcv = new variant();
		funcv.set_string(func);

		processor pro = new processor(f);

		try
		{
			routine r = pro.start_routine(funcv, new ArrayList<Integer>());

			f.rn.push_pro(pro);

			f.dbg.debug();
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

	private static void runps(fake f, String func)
	{
		if (!enter(f))
		{
			// 占用失败不触碰共享的参数栈,run/runmulti据空栈返回null
			return;
		}
		try
		{
			runps_inner(f, func);
		}
		catch (Exception e)
		{
			// 理论不可达:runps_inner内部已捕获;兜底保证契约
			types.seterror(f, "", 0, "", "run fail " + types.show_exception(e));
			f.ps.push_and_get();
		}
		finally
		{
			f.exit();
		}
	}

	private static void runps_inner(fake f, String func) throws Exception
	{
		variant funcv = new variant();
		funcv.set_string(func);

		f.clearerr();
		f.stopflag = false;
		processor pro = new processor(f);

		try
		{
			routine r = pro.start_routine(funcv, new ArrayList<Integer>());

			f.rn.push_pro(pro);
			pro.run();

			ArrayList<variant> rets = r.get_interpreter().get_ret_all();
			if (rets.isEmpty())
			{
				// 保持旧行为:无返回值时也压一个nil,保证pspop返回null
				variant v = f.ps.push_and_get();
				v.copy_from(r.get_ret());
			}
			else
			{
				for (int i = 0; i < rets.size(); i++)
				{
					variant v = f.ps.push_and_get();
					v.copy_from(rets.get(i));
				}
			}
		}
		catch (Exception e)
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String msg = e.toString() + "\n" + sw.toString();
			String callstack = getcurcallstack(f);
			if (!callstack.equals("nil"))
			{
				msg += "\ncall stack:\n" + callstack;
			}
			types.seterror(f, getcurfile(f), getcurline(f), getcurfunc(f), msg);
			pw.close();
			f.ps.push_and_get();
		}
		finally
		{
			f.rn.pop_pro();
		}
	}

	private static void reg_method(fake f, String name, Class<?> c, Method m)
	{
		boolean isstatic = Modifier.isStatic(m.getModifiers());
		if (!isstatic)
		{
			name = c.getName() + name;
		}
		else
		{
			name = c.getSimpleName() + "." + name;
		}

		synchronized (fk.class)
		{
			variant v = null;
			fkfunctor fkf = null;

			if (regName.get(name) != null)
			{
				v = regName.get(name);
				fkf = regFunctor.get(name);

				for (fkmethod fm : fkf.m_ms)
				{
					if (fm.m_param.length == m.getParameterTypes().length)
					{
						boolean equal = true;
						for (int i = 0; i < fm.m_param.length; i++)
						{
							if (fm.m_param[i] != m.getParameterTypes()[i])
							{
								equal = false;
								break;
							}
						}
						if (equal)
						{
							f.fm.add_func(v, fkf);
							types.log(f, "fk reg %s %s from cache", name, fkf);
							return;
						}
					}
				}
			}
			else
			{
				v = new variant();
				v.set_string(name);

				fkf = new fkfunctor();
				fkf.m_c = c.getName();
				fkf.m_is_static = isstatic;

				f.fm.add_func(v, fkf);

				regName.put(name, v);
				regFunctor.put(name, fkf);
			}

			fkmethod fm = new fkmethod();
			fm.m_m = m;
			fm.m_param = m.getParameterTypes();
			fm.m_ret = m.getReturnType();

			if (fkf.m_ms == null)
			{
				fkf.m_ms = new fkmethod[1];
				fkf.m_ms[0] = fm;
			}
			else
			{
				fkmethod[] newarray = new fkmethod[fkf.m_ms.length + 1];
				for (int i = 0; i < fkf.m_ms.length; i++)
				{
					newarray[i] = fkf.m_ms[i];
				}
				newarray[fkf.m_ms.length] = fm;
				fkf.m_ms = newarray;
			}

			types.log(f, "fk reg %s %s", name, fkf);
		}
	}

	protected static boolean resumeps(fake f, boolean isend) throws Exception
	{
		if (!enter(f))
		{
			return false;
		}
		try
		{
			return resumeps_inner(f, isend);
		}
		finally
		{
			f.exit();
		}
	}

	private static boolean resumeps_inner(fake f, boolean isend) throws Exception
	{
		// 上次的processor
		processor pro = f.rn.cur_pro();
		if (pro == null)
		{
			variant ret = f.ps.push_and_get();
			ret.set_nil();
			return false;
		}

		pro.run();
		if (pro.get_routine_num() != 0)
		{
			variant ret = f.ps.push_and_get();
			ret.set_nil();
			return false;
		}

		// 结束了
		variant ret = f.ps.push_and_get();
		ret.copy_from(pro.get_entrycurroutine().get_ret());

		f.rn.pop_pro();

		return true;
	}
}