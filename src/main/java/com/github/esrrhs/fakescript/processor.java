package com.github.esrrhs.fakescript;

import java.util.ArrayList;

class processor
{
	private fake m_f;
	private routine m_entryroutine;
	private int m_genid;
	private routine m_curroutine;
	private ArrayList<routine> m_routines = new ArrayList<routine>();
	int m_lastroutine;
	int m_lastroutine_runnum;

	public processor(fake f)
	{
		m_f = f;
	}

	public routine get_curroutine()
	{
		return m_curroutine;
	}

	public routine get_entrycurroutine()
	{
		return m_entryroutine;
	}

	public routine start_routine(variant func, ArrayList<Integer> retpos) throws Exception
	{
		routine r = new routine(m_f);

		if (m_entryroutine == null)
		{
			m_entryroutine = r;
		}

		r.set_id(m_genid);
		m_genid++;
		r.set_processor(this);
		r.entry(func, retpos);

		if (m_curroutine == null)
		{
			m_curroutine = r;
		}

		m_routines.add(r);

		return r;
	}

	public void run() throws Exception
	{
		if (m_f.rn.is_stepmod())
		{
			if (m_routines.isEmpty())
			{
				return;
			}

			routine n = m_routines.get(m_lastroutine);
			n.run(1);
			m_lastroutine_runnum++;
			boolean needupdate = false;
			if (n.is_end())
			{
				m_routines.remove(m_lastroutine);
				needupdate = true;
			}
			else if (m_lastroutine_runnum >= m_f.cfg.per_frame_cmd_num)
			{
				m_lastroutine_runnum = 0;
				needupdate = true;
			}
			if (needupdate)
			{
				for (int i = 0; i < m_routines.size(); i++)
				{
					int index = m_lastroutine + i + 1;
					if (index >= m_routines.size())
					{
						index = index % m_routines.size();
					}

					routine nex = m_routines.get(index);
					if (nex == null)
					{
						continue;
					}

					m_curroutine = n;
					m_lastroutine = index;
					break;
				}
			}
		}
		else
		{
			long starttime = System.currentTimeMillis();
			long totalcmd = 0;
			while (!m_routines.isEmpty())
			{
				if (m_f.stopflag)
				{
					throw new Exception("run stopped by fk.stop");
				}

				if (m_f.cfg.max_run_cmd_num > 0 && totalcmd >= m_f.cfg.max_run_cmd_num)
				{
					throw new Exception("run exceed max_run_cmd_num " + m_f.cfg.max_run_cmd_num
							+ ", maybe dead loop script");
				}

				if (m_f.cfg.run_timeout_ms > 0
						&& System.currentTimeMillis() - starttime >= m_f.cfg.run_timeout_ms)
				{
					throw new Exception("run exceed run_timeout_ms " + m_f.cfg.run_timeout_ms
							+ " ms, maybe dead loop script");
				}

				for (int i = 0; i < (int) m_routines.size(); i++)
				{
					routine r = m_routines.get(i);
					m_curroutine = r;
					// 注意:此函数内部可能会调用到add接口
					int cmdnum = m_f.cfg.per_frame_cmd_num;
					if (m_f.cfg.max_run_cmd_num > 0)
					{
						cmdnum = (int) Math.min(cmdnum, Math.max(1, m_f.cfg.max_run_cmd_num - totalcmd));
					}
					totalcmd += r.run(cmdnum);
					if (r.is_end())
					{
						m_routines.remove(i);
					}
				}
			}
		}
	}

	public String get_routine_info()
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < m_routines.size(); i++)
		{
			routine r = m_routines.get(i);

			sb.append("#").append(i).append("\tId:").append(r.get_id()).append("\t")
					.append(r.get_interpreter().get_running_func_name()).append("(")
					.append(r.get_interpreter().get_running_file_name()).append(":")
					.append(r.get_interpreter().get_running_file_line()).append(")\t")
					.append(r.is_end() ? "Dead" : "Alive").append("\n");
		}
		return sb.toString();
	}

	public routine get_routine_by_id(int id)
	{
		for (int i = 0; i < m_routines.size(); i++)
		{
			routine r = m_routines.get(i);
			if (r == null)
			{
				continue;
			}

			if (r.get_id() == id)
			{
				return r;
			}
		}
		return null;
	}

	public String get_routine_info_by_id(int id)
	{
		int j = 0;
		for (int i = 0; i < m_routines.size(); i++)
		{
			routine r = m_routines.get(i);
			if (r == null)
			{
				continue;
			}

			if (r.get_id() == id)
			{
				String ret = "";

				ret += "#";
				ret += j;
				ret += "\tId:";
				ret += id;
				ret += "\t";
				ret += r.get_interpreter().get_running_func_name();
				ret += "(";
				ret += r.get_interpreter().get_running_file_name();
				ret += ":";
				ret += r.get_interpreter().get_running_file_line();
				ret += ")\t";
				ret += r.is_end() ? "Dead" : "Alive";
				ret += "\n";
				return ret;
			}
			j++;
		}
		return "";
	}

	public int get_routine_num()
	{
		return m_routines.size();
	}

	public routine get_routine_by_index(int index)
	{
		int j = 0;
		for (int i = 0; i < m_routines.size(); i++)
		{
			routine r = m_routines.get(i);
			if (r == null)
			{
				continue;
			}

			if (j >= index)
			{
				return r;
			}
			j++;
		}
		return null;
	}

	String get_routine_info_by_index(int index)
	{
		int j = 0;
		for (int i = 0; i < m_routines.size(); i++)
		{
			routine r = m_routines.get(i);
			if (r == null)
			{
				continue;
			}

			if (j >= index)
			{
				return get_routine_info_by_id(r.get_id());
			}
			j++;
		}
		return "";
	}
}
