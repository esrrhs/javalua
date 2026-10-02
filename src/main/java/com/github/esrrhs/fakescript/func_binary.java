package com.github.esrrhs.fakescript;

class func_binary
{
	// 最大栈空间
	private int m_maxstack;
	// 参数个数
	private int m_paramnum;
	// 名字
	private String m_name;
	// 文件名
	private String m_filename;
	// 包名
	private String m_packagename;
	// 二进制缓冲区
	private long[] m_buff;
	// 二进制行号缓冲区
	private int[] m_lineno_buff;
	private int m_end_lineno;
	// 常量
	private variant[] m_const_list;
	// container地址
	private container_addr[] m_container_addr_list;
	// 调试信息，栈变量
	private stack_variant_info[] m_debug_stack_variant_info;
	// 占用标记
	private int m_use;

	public int get_maxstack()
	{
		return m_maxstack;
	}

	public int get_paramnum()
	{
		return m_paramnum;
	}

	public String get_name()
	{
		return m_name;
	}

	public String get_filename()
	{
		return m_filename;
	}

	public String get_packagename()
	{
		return m_packagename;
	}

	public long[] get_buff()
	{
		return m_buff;
	}

	public variant[] get_const_list()
	{
		return m_const_list;
	}

	public container_addr[] get_container_addr_list()
	{
		return m_container_addr_list;
	}

	public stack_variant_info[] get_debug_stack_variant_info()
	{
		return m_debug_stack_variant_info;
	}

	public int get_use()
	{
		return m_use;
	}

	public void inc_use()
	{
		m_use++;
	}

	public void dec_use()
	{
		m_use--;
	}

	public void set_end_lineno(int endlineno)
	{
		m_end_lineno = endlineno;
	}

	public void set_paramnum(int paramnum)
	{
		m_paramnum = paramnum;
	}

	// codegen编译完成后一次性填充
	public void fill(String filename, String packagename, String name, int maxstack, long[] buff, int[] linenobuff,
			variant[] constlist, container_addr[] containeraddrlist, stack_variant_info[] debugstackvariantinfo)
	{
		m_filename = filename;
		m_packagename = packagename;
		m_name = name;
		m_maxstack = maxstack;
		m_buff = buff;
		m_lineno_buff = linenobuff;
		m_const_list = constlist;
		m_container_addr_list = containeraddrlist;
		m_debug_stack_variant_info = debugstackvariantinfo;
	}

	public func_binary clonef()
	{
		func_binary fb = new func_binary();

		fb.m_maxstack = this.m_maxstack;
		fb.m_paramnum = this.m_paramnum;
		fb.m_name = this.m_name;
		fb.m_filename = this.m_filename;
		fb.m_packagename = this.m_packagename;
		fb.m_buff = this.m_buff;
		fb.m_lineno_buff = this.m_lineno_buff;
		fb.m_end_lineno = this.m_end_lineno;
		fb.m_const_list = this.m_const_list;
		fb.m_container_addr_list = this.m_container_addr_list;
		fb.m_debug_stack_variant_info = this.m_debug_stack_variant_info;
		return fb;
	}

	public String dump(int pos)
	{
		String ret = "";

		// 名字
		ret += "\n[";
		ret += m_name;
		ret += "]\n";

		// 最大栈
		ret += "\tmaxstack:\t";
		ret += m_maxstack;
		ret += "\n\n";

		// 常量表
		ret += "\t////// const define ";
		ret += m_const_list.length;
		ret += " //////\n";
		for (int i = 0; i < (int) m_const_list.length; i++)
		{
			ret += "\t[";
			ret += i;
			ret += "]\t";
			ret += m_const_list[i].get_type();
			ret += "\t";
			ret += m_const_list[i].toString();
			ret += "\n";
		}

		// 容器地址表
		ret += "\n\t////// container addr ";
		ret += m_container_addr_list.length;
		ret += " //////\n";
		for (int i = 0; i < (int) m_container_addr_list.length; i++)
		{
			ret += "\t[";
			ret += i;
			ret += "]\t";
			long concmd = m_container_addr_list[i].m_con;
			int concode = command.COMMAND_CODE(concmd);
			ret += "[ CONTAINER ]\t";
			ret += types.dump_addr(concode);
			long keycmd = m_container_addr_list[i].m_key;
			int keycode = command.COMMAND_CODE(keycmd);
			ret += "\t[ KEY ]\t";
			ret += types.dump_addr(keycode);
			ret += "\n";
		}

		// 变量地址
		ret += "\n\t////// stack variant addr ";
		ret += m_debug_stack_variant_info.length;
		ret += " //////\n";
		for (int i = 0; i < (int) m_debug_stack_variant_info.length; i++)
		{
			stack_variant_info info = m_debug_stack_variant_info[i];
			ret += "\t[";
			ret += info.m_pos;
			ret += "]\t";
			ret += info.m_name;
			ret += "\t\tLINE\t";
			ret += info.m_line;
			ret += "\n";
		}

		ret += "\n\t////// byte code ";
		ret += m_buff.length;
		ret += " //////\n";
		// 字节码
		for (int i = 0; i < (int) m_buff.length; i++)
		{
			long cmd = m_buff[i];
			int type = command.COMMAND_TYPE(cmd);
			int code = command.COMMAND_CODE(cmd);
			if (i == pos)
			{
				ret += "->";
			}
			ret += "\t[";
			ret += i;
			ret += "]";
			ret += "[LINE ";
			ret += get_binary_lineno(i);
			ret += "]\t";
			ret += String.format("0x%016x", cmd);
			ret += "\t";
			switch (type)
			{
				case command.COMMAND_OPCODE:
				{
					ret += "[";
					ret += types.OpCodeStr(code);
					ret += "]\t";
				}
					break;
				case command.COMMAND_ADDR:
				{
					ret += "[ ADDR ]\t";
					ret += types.dump_addr(code);
				}
					break;
				case command.COMMAND_POS:
				{
					ret += "[ POS  ]\t";
					ret += code;
				}
					break;
				default:
				{
					ret += "[unknow]\t";
				}
					break;
			}
			ret += "\n";
		}
		ret += "\n";
		return ret;
	}

	public int get_binary_lineno(int pos)
	{
		return (pos >= 0 && pos < (int) m_lineno_buff.length) ? m_lineno_buff[pos]
				: (m_lineno_buff.length > 0 ? m_end_lineno : 0);
	}

}
