package com.github.esrrhs.fakescript;

/**
 * fake为上下文环境
 * <p>
 * 所有脚本接口在fake中执行:解析、编译、运行、调试都围绕它进行
 * 不是线程安全的,请在单一线程中驱动同一个fake实例
 */
public class fake
{
	protected boolean error = false;
	protected String errorstr = "";
	// 最近一次错误的结构化信息,clearerr时清空
	protected fkerror lasterror = null;

	// 并发访问检测:同一时刻只允许一个线程进入执行/解析,同线程可重入
	// 误用从"静默状态损坏"变为明确报错;顺序的跨线程使用不受影响
	private final java.util.concurrent.atomic.AtomicReference<Thread> m_holder = new java.util.concurrent.atomic.AtomicReference<Thread>();
	private int m_holddepth;

	boolean try_enter()
	{
		Thread cur = Thread.currentThread();
		if (m_holder.get() == cur)
		{
			m_holddepth++;
			return true;
		}
		if (m_holder.compareAndSet(null, cur))
		{
			m_holddepth = 1;
			return true;
		}
		return false;
	}

	void exit()
	{
		m_holddepth--;
		if (m_holddepth <= 0)
		{
			m_holder.set(null);
		}
	}
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
		lasterror = null;
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
