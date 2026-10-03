package com.github.esrrhs.fakescript;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

class breakpoint
{
	boolean enable;
	int id;
	String file;
	int line;
}

/**
 * 可编程驱动的调试会话
 * <p>
 * 由fk.debugrun内部的CLI循环使用,宿主与测试也可以直接构造后逐命令驱动:<br>
 * debug_session s = f.dbg.createsession("func", args); s.execute("n"); ...<br>
 * 每条命令返回其产生的全部输出(含断点触发/源码刷新),is_end表示被调程序已结束
 */
class debug_session
{
	private fake m_f;
	private variant m_ret = new variant();
	private ArrayList<breakpoint> m_blist = new ArrayList<breakpoint>();
	private int m_bindex;
	private int m_frame;
	private int m_rid;
	private int m_lastrid;
	private String m_lastfunc = "";
	private ArrayList<String> m_watchvec = new ArrayList<String>();
	private boolean m_firsttime = true;
	private boolean m_isend = false;
	private boolean m_isgoto = false;
	private int m_range = 0;
	private String m_lastcommand = "n";
	private StringBuilder m_out = new StringBuilder();

	debug_session(fake f)
	{
		m_f = f;
		m_rid = fk.getcurroutineid(f);
		m_lastrid = m_rid;
	}

	public boolean is_end()
	{
		return m_isend;
	}

	public variant get_ret()
	{
		return m_ret;
	}

	// 执行一条调试命令,返回该命令产生的全部输出;空命令重复上一条;结束后再执行返回end
	public String execute(String cmdline) throws Exception
	{
		m_out.setLength(0);

		if (m_isend)
		{
			out("end\n");
			return m_out.toString();
		}

		String s = cmdline == null ? "" : cmdline.trim();
		if (s.isEmpty())
		{
			s = m_lastcommand;
		}
		else
		{
			m_lastcommand = s;
		}

		ArrayList<String> paramvec = new ArrayList<String>();
		int command = parse_command(s, paramvec);
		if (command == -2)
		{
			show_debug_help();
			return m_out.toString();
		}
		if (command == -1)
		{
			out("use h to get help\n");
			return m_out.toString();
		}

		if (!m_isgoto)
		{
			refresh_display();
		}
		m_isgoto = false;

		switch (command)
		{
			case debugging.debug_next:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				if (m_firsttime)
				{
					if (check_trigger_breakpoint())
					{
						m_firsttime = false;
						break;
					}
				}
				m_firsttime = false;

				String lastfile = fk.getcurfile(m_f);
				int lastline = fk.getcurline(m_f);
				int laststacklength = fk.getcurcallstacklength(m_f);
				int lastridex = m_rid;
				String curfile = fk.getcurfile(m_f);
				int curline = fk.getcurline(m_f);
				int curstacklength = fk.getcurcallstacklength(m_f);
				int currid = m_rid;
				while (currid == lastridex
						&& (curstacklength > laststacklength || (lastfile.equals(curfile) && lastline == curline)))
				{
					resume();
					if (m_isend)
					{
						break;
					}
					curfile = fk.getcurfile(m_f);
					curline = fk.getcurline(m_f);
					curstacklength = fk.getcurcallstacklength(m_f);
					currid = fk.getcurroutineid(m_f);
					if (check_trigger_breakpoint())
					{
						break;
					}
				}

				m_rid = fk.getcurroutineid(m_f);
			}
				break;
			case debugging.debug_step:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				if (m_firsttime)
				{
					if (check_trigger_breakpoint())
					{
						m_firsttime = false;
						break;
					}
				}
				m_firsttime = false;

				String lastfile = fk.getcurfile(m_f);
				int lastline = fk.getcurline(m_f);
				int lastridex = m_rid;
				String curfile = fk.getcurfile(m_f);
				int curline = fk.getcurline(m_f);
				int currid = m_rid;
				while (currid == lastridex && (lastfile.equals(curfile) && lastline == curline))
				{
					resume();
					if (m_isend)
					{
						break;
					}
					curfile = fk.getcurfile(m_f);
					curline = fk.getcurline(m_f);
					currid = fk.getcurroutineid(m_f);
					if (check_trigger_breakpoint())
					{
						break;
					}
				}

				m_rid = fk.getcurroutineid(m_f);
			}
				break;
			case debugging.debug_next_instruction:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				if (m_firsttime)
				{
					if (check_trigger_breakpoint())
					{
						m_firsttime = false;
						break;
					}
				}
				m_firsttime = false;

				int laststacklength = fk.getcurcallstacklength(m_f);
				int lastridex = m_rid;
				int curstacklength = fk.getcurcallstacklength(m_f);
				int currid = m_rid;
				do
				{
					resume();
					if (m_isend)
					{
						break;
					}
					curstacklength = fk.getcurcallstacklength(m_f);
					currid = fk.getcurroutineid(m_f);
					if (check_trigger_breakpoint())
					{
						break;
					}
				}
				while (currid == lastridex && curstacklength > laststacklength);

				m_rid = fk.getcurroutineid(m_f);
			}
				break;
			case debugging.debug_step_instruction:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				if (m_firsttime)
				{
					if (check_trigger_breakpoint())
					{
						m_firsttime = false;
						break;
					}
				}
				m_firsttime = false;

				resume();

				m_rid = fk.getcurroutineid(m_f);
				if (check_trigger_breakpoint())
				{
					break;
				}
			}
				break;
			case debugging.debug_continue:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				if (m_firsttime)
				{
					if (check_trigger_breakpoint())
					{
						m_firsttime = false;
						break;
					}
				}
				m_firsttime = false;

				while (true)
				{
					resume();
					m_rid = fk.getcurroutineid(m_f);
					if (check_trigger_breakpoint())
					{
						break;
					}
					if (m_isend)
					{
						break;
					}
				}
			}
				break;
			case debugging.debug_breakpoint:
			{
				breakpoint tmp = new breakpoint();
				if (paramvec.isEmpty())
				{
					tmp.file = fk.getcurfilebyroutinebyframe(m_f, m_rid, m_frame);
					tmp.line = fk.getcurlinebyroutinebyframe(m_f, m_rid, m_frame);
				}
				else
				{
					String str = paramvec.get(0);
					int subpos = str.indexOf(':');
					if (subpos != -1)
					{
						String filestr = str.substring(0, subpos);
						String linestr = str.substring(subpos + 1);

						tmp.file = filestr;
						tmp.line = Integer.parseInt(linestr);
					}
					else
					{
						boolean isnumber = true;
						for (int i = 0; i < (int) str.length(); i++)
						{
							if (!Character.isDigit(str.charAt(i)))
							{
								isnumber = false;
								break;
							}
						}

						if (isnumber)
						{
							tmp.file = fk.getcurfilebyroutinebyframe(m_f, m_rid, m_frame);
							tmp.line = Integer.parseInt(str);
						}
						else
						{
							if (!fk.isfunc(m_f, str))
							{
								out(String.format("%s is not func\n", str));
								return finish_output();
							}

							tmp.file = fk.getfuncfile(m_f, str);
							tmp.line = fk.getfuncstartline(m_f, str);
						}
					}
				}
				tmp.enable = true;
				tmp.id = m_bindex;

				if ((int) tmp.file.lastIndexOf('/') != -1)
				{
					tmp.file = tmp.file.substring(tmp.file.lastIndexOf('/') + 1);
				}

				m_blist.add(tmp);
				m_bindex++;
				out(String.format("Breakpoint %d at file %s, line %d total %d\n", tmp.id, tmp.file, tmp.line,
						(int) m_blist.size()));
			}
				break;
			case debugging.debug_enable:
			{
				if (paramvec.isEmpty())
				{
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						tmp.enable = true;
					}
				}
				else
				{
					int id = Integer.parseInt(paramvec.get(0));
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						if (tmp.id == id)
						{
							tmp.enable = true;
						}
					}
				}
			}
				break;
			case debugging.debug_disable:
			{
				if (paramvec.isEmpty())
				{
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						tmp.enable = false;
					}
				}
				else
				{
					int id = Integer.parseInt(paramvec.get(0));
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						if (tmp.id == id)
						{
							tmp.enable = false;
						}
					}
				}
			}
				break;
			case debugging.debug_delete:
			{
				if (paramvec.isEmpty())
				{
					m_blist.clear();
					m_watchvec.clear();
					return finish_output();
				}
				else
				{
					int id = Integer.parseInt(paramvec.get(0));
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						if (tmp.id == id)
						{
							m_blist.remove(i);
						}
					}
				}
			}
				break;
			case debugging.debug_info:
			{
				if (paramvec.isEmpty())
				{
					out("need arg, useage: i b, i r\n");
					return finish_output();
				}

				if (paramvec.get(0).equals("b"))
				{
					out("Id\tEnb\twhere\n");
					for (int i = 0; i < (int) m_blist.size(); i++)
					{
						breakpoint tmp = m_blist.get(i);
						out(String.format("%d\t%s\tfile %s, line %d\n", tmp.id, tmp.enable ? "y" : "n", tmp.file,
								tmp.line));
					}
				}
				else if (paramvec.get(0).equals("r"))
				{
					for (int i = 0; i < (int) fk.getcurroutinenum(m_f); i++)
					{
						out(String.format("%s%s%s\n", fk.getroutineidbyindex(m_f, i) == m_rid ? "*" : "",
								fk.getroutineidbyindex(m_f, i) == fk.getcurroutineid(m_f) ? "->" : "",
								fk.getcurroutinebyindex(m_f, i)));
					}
				}

				out("\n");
			}
				break;
			case debugging.debug_finish:
			{
				m_frame = 0;
				m_rid = fk.getcurroutineid(m_f);

				int laststacklength = fk.getcurcallstacklength(m_f);
				int curstacklength = fk.getcurcallstacklength(m_f);
				do
				{
					resume();
					if (m_isend)
					{
						break;
					}
					curstacklength = fk.getcurcallstacklength(m_f);
				}
				while (curstacklength >= laststacklength);
			}
				break;
			case debugging.debug_list:
			{
				int listrange = 3;
				if (!paramvec.isEmpty())
				{
					listrange = Integer.parseInt(paramvec.get(0));
				}
				show_debug_code(listrange);
				m_isgoto = true;
			}
				break;
			case debugging.debug_print:
			{
				if (paramvec.isEmpty())
				{
					out("need arg, useage: p variant\n");
					return finish_output();
				}

				String name = paramvec.get(0);
				out(String.format("%s\n", fk.getcurvariantbyroutinebyframe(m_f, m_rid, m_frame, name, -1)));
			}
				break;
			case debugging.debug_set:
			{
				if (paramvec.size() < 2)
				{
					out("need arg, useage: set variant value\n");
					return finish_output();
				}

				String name = paramvec.get(0);
				String value = paramvec.get(1);
				fk.setcurvariantbyroutinebyframe(m_f, m_rid, m_frame, name, value, -1);
				out(String.format("%s\n", fk.getcurvariantbyroutinebyframe(m_f, m_rid, m_frame, name, -1)));
			}
				break;
			case debugging.debug_watch:
			{
				if (paramvec.isEmpty())
				{
					out("need arg, useage: wa variant\n");
					return finish_output();
				}

				String name = paramvec.get(0);
				m_watchvec.add(name);
			}
				break;
			case debugging.debug_backtrace:
			{
				int length = fk.getcurcallstacklengthbyroutine(m_f, m_rid);
				for (int i = 0; i < length; i++)
				{
					out(String.format("%s%s\n", i == m_frame ? "*" : " ",
							fk.getcurcallstackbyroutinebyframe(m_f, m_rid, i)));
				}
				m_isgoto = true;
			}
				break;
			case debugging.debug_frame:
			{
				if (paramvec.isEmpty())
				{
					m_frame = 0;
				}
				else
				{
					int theframe = Integer.parseInt(paramvec.get(0));
					if (theframe < 0 || theframe >= fk.getcurcallstacklengthbyroutine(m_f, m_rid))
					{
						out(String.format("%d is invalid\n", theframe));
					}
					m_frame = theframe;
				}
			}
				break;
			case debugging.debug_disa:
			{
				int pos = fk.getcurbytecodeposbyroutine(m_f, m_rid);
				String func = fk.getcurfuncbyroutinebyframe(m_f, m_rid, m_frame);
				out(String.format("%s\n", fk.dumpfunc(m_f, func, pos)));
			}
				break;
			case debugging.debug_routine:
			{
				if (paramvec.isEmpty())
				{
					out("need arg, useage: r rid\n");
					return finish_output();
				}

				int id = Integer.parseInt(paramvec.get(0));
				if (!fk.ishaveroutine(m_f, id))
				{
					out(String.format("no routine %d\n", id));
					return finish_output();
				}

				m_rid = id;
			}
				break;
			default:
				return finish_output();
		}

		return finish_output();
	}

	// 命令结束后统一收尾:程序结束则输出end并把返回值压回参数栈
	private String finish_output()
	{
		if (m_isend)
		{
			out("end\n");
			fk.psclear(m_f);
			variant ret = m_f.ps.push_and_get();
			ret.copy_from(m_ret);
		}
		return m_out.toString();
	}

	private void resume() throws Exception
	{
		fk.psclear(m_f);
		m_isend = fk.resumeps(m_f, m_isend);
		variant ret = m_f.ps.pop_and_get();
		m_ret.copy_from(ret);
	}

	private void refresh_display()
	{
		show_watch_variant();
		check_show_func_header();
		show_debug_code(m_range);
	}

	private void out(String s)
	{
		m_out.append(s);
	}

	private void show_debug_code(int range)
	{
		int curline = fk.getcurlinebyroutinebyframe(m_f, m_rid, m_frame);
		for (int i = curline - range; i <= curline + range; i++)
		{
			if (i > 0)
			{
				String code = fk.getfilecode(m_f, fk.getcurfilebyroutinebyframe(m_f, m_rid, m_frame), i);
				if (!code.isEmpty())
				{
					out(String.format("%s%d\t%s\n", curline == i ? "*" : "", i, code));
				}
			}
		}
		out("\n");
	}

	private void show_watch_variant()
	{
		for (int i = 0; i < (int) m_watchvec.size(); i++)
		{
			out(String.format("%s\n", fk.getcurvariantbyroutinebyframe(m_f, m_rid, m_frame, m_watchvec.get(i), -1)));
		}
	}

	private void check_show_func_header()
	{
		String curfunc = fk.getcurfuncbyroutinebyframe(m_f, m_rid, m_frame);
		if (m_rid != m_lastrid)
		{
			out(String.format("routine %s\n", fk.getcurroutinebyid(m_f, m_rid)));
			m_lastrid = m_rid;
		}
		if (!curfunc.equals(m_lastfunc))
		{
			out(String.format("file %s, line %d, func %s\n", fk.getcurfilebyroutinebyframe(m_f, m_rid, m_frame),
					fk.getcurlinebyroutinebyframe(m_f, m_rid, m_frame), curfunc));
			m_lastfunc = curfunc;
		}
	}

	private boolean check_trigger_breakpoint()
	{
		String curfile = fk.getcurfile(m_f);
		if ((int) curfile.lastIndexOf('/') != -1)
		{
			curfile = curfile.substring(curfile.lastIndexOf('/') + 1);
		}
		int line = fk.getcurline(m_f);
		for (int i = 0; i < (int) m_blist.size(); i++)
		{
			breakpoint tmp = m_blist.get(i);
			if (tmp.enable && tmp.line == line && tmp.file.equals(curfile))
			{
				out(String.format("Trigger Breakpoint %d at file %s, line %d\n", tmp.id, tmp.file, tmp.line));
				return true;
			}
		}

		return false;
	}

	private void show_debug_help()
	{
		out("h help\n" + "n\tnext\n" + "s\tstep\n" + "ni\tnext bytecode\n" + "si\tstep bytecode\n"
				+ "c\tcontinue\n" + "l\tlist\n" + "p\tprint\n" + "set\tset\n" + "wa\twatch\n" + "b\tbreakpoint\n"
				+ "en\tenable\n" + "dis\tdisable\n" + "d\tdelete\n" + "i\tinfo\n" + "bt\tbacktrace\n" + "f\tframe\n"
				+ "fin\tfinish\n" + "r\troutine\n" + "disa\tview bytecode\n");
	}

	// 解析命令行,返回命令id并填充参数;未知命令-1,帮助-2
	private static int parse_command(String s, ArrayList<String> paramvec)
	{
		paramvec.clear();
		String[] pv = s.split(" ");
		for (String p : pv)
		{
			if (!p.isEmpty())
			{
				paramvec.add(p);
			}
		}

		if (paramvec.isEmpty())
		{
			return -1;
		}

		String strcommand = paramvec.get(0);
		paramvec.remove(0);

		if (strcommand.equals("n"))
		{
			return debugging.debug_next;
		}
		else if (strcommand.equals("s"))
		{
			return debugging.debug_step;
		}
		else if (strcommand.equals("ni"))
		{
			return debugging.debug_next_instruction;
		}
		else if (strcommand.equals("si"))
		{
			return debugging.debug_step_instruction;
		}
		else if (strcommand.equals("c"))
		{
			return debugging.debug_continue;
		}
		else if (strcommand.equals("b"))
		{
			return debugging.debug_breakpoint;
		}
		else if (strcommand.equals("dis"))
		{
			return debugging.debug_disable;
		}
		else if (strcommand.equals("en"))
		{
			return debugging.debug_enable;
		}
		else if (strcommand.equals("d"))
		{
			return debugging.debug_delete;
		}
		else if (strcommand.equals("i"))
		{
			return debugging.debug_info;
		}
		else if (strcommand.equals("fin"))
		{
			return debugging.debug_finish;
		}
		else if (strcommand.equals("l"))
		{
			return debugging.debug_list;
		}
		else if (strcommand.equals("wa"))
		{
			return debugging.debug_watch;
		}
		else if (strcommand.equals("p"))
		{
			return debugging.debug_print;
		}
		else if (strcommand.equals("set"))
		{
			return debugging.debug_set;
		}
		else if (strcommand.equals("bt"))
		{
			return debugging.debug_backtrace;
		}
		else if (strcommand.equals("f"))
		{
			return debugging.debug_frame;
		}
		else if (strcommand.equals("disa"))
		{
			return debugging.debug_disa;
		}
		else if (strcommand.equals("r"))
		{
			return debugging.debug_routine;
		}
		else if (strcommand.equals("h"))
		{
			return -2;
		}

		return -1;
	}
}

class debugging
{
	fake m_f;

	static final int debug_next = 0;
	static final int debug_step = 1;
	static final int debug_next_instruction = 2;
	static final int debug_step_instruction = 3;
	static final int debug_continue = 4;
	static final int debug_breakpoint = 5;
	static final int debug_enable = 6;
	static final int debug_disable = 7;
	static final int debug_delete = 8;
	static final int debug_info = 9;
	static final int debug_finish = 10;
	static final int debug_list = 11;
	static final int debug_print = 12;
	static final int debug_set = 13;
	static final int debug_watch = 14;
	static final int debug_backtrace = 15;
	static final int debug_frame = 16;
	static final int debug_disa = 17;
	static final int debug_routine = 18;

	public debugging(fake f)
	{
		m_f = f;
	}

	// 创建非交互调试会话:等价于fk.debugrun的启动部分,由调用方逐命令驱动
	debug_session createsession(String func, Object... args) throws Exception
	{
		variant funcv = new variant();
		funcv.set_string(func);

		fk.openstepmod(m_f);

		fk.psclear(m_f);
		for (Object arg : args)
		{
			fk.pspush(m_f, arg);
		}

		processor pro = new processor(m_f);
		pro.start_routine(funcv, new ArrayList<Integer>());
		m_f.rn.push_pro(pro);

		return new debug_session(m_f);
	}

	void debug() throws Exception
	{
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		debug_session s = new debug_session(m_f);
		while (true)
		{
			System.out.printf("(fake) ");

			String line = "";
			try
			{
				String read = in.readLine();
				line = read == null ? "" : read;
			}
			catch (Exception e)
			{
			}

			System.out.print(s.execute(line));
			if (s.is_end())
			{
				break;
			}
		}
	}
}
