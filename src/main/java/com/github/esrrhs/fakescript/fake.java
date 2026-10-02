package com.github.esrrhs.fakescript;

public class fake
{
	protected boolean error = false;
	protected String errorstr = "";
	protected callback cb = new default_callback();

	// 配置
	protected fkconfig cfg = new fkconfig();

	// 停止标记,由fk.stop设置,在下一个命令边界生效
	protected volatile boolean stopflag = false;

	// 解析
	protected parser pa = new parser(this);

	// 参数栈
	protected paramstack ps = new paramstack(this);

	// 二进制
	protected binary bin = new binary(this);

	// 函数索引
	protected funcmap fm = new funcmap(this);

	// 性能检测
	protected profile pf = new profile(this);

	// 内建的函数集合
	protected builtinfunc bif = new builtinfunc(this);

	// 当前运行状态
	protected running rn = new running(this);

	// debug容器
	protected debugging dbg = new debugging(this);

	protected fake clonef()
	{
		fake nf = new fake();

		nf.cfg = this.cfg;
		nf.cb = this.cb;
		nf.pa = this.pa.clonef(this);
		nf.ps = new paramstack(this);
		nf.bin = new binary(this);
		nf.fm = this.fm.clonef(this);
		nf.pf = new profile(this);
		nf.bif = new builtinfunc(this);
		nf.rn = new running(this);
		nf.dbg = new debugging(this);

		return nf;
	}

	public void clearerr()
	{
		error = false;
		errorstr = "";
	}

	// 未通过fk.set_callback设置回调时的默认行为:print输出到stdout,错误只记录在errorstr中
	static class default_callback implements callback
	{
		@Override
		public void on_error(fake f, String file, int lineno, String func, String str)
		{
		}

		@Override
		public void on_print(fake f, String str)
		{
			System.out.print(str);
		}
	}
}
