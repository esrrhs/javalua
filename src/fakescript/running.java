package fakescript;

import java.util.ArrayList;

class running
{
	private fake m_f;
	private ArrayList<processor> m_processes = new ArrayList<processor>();
	private boolean stepmod;

	public running(fake f)
	{
		m_f = f;
	}

	public processor cur_pro()
	{
		return m_processes.isEmpty() ? null : m_processes.get(m_processes.size() - 1);
	}

	public void push_pro(processor pro)
	{
		m_processes.add(pro);
	}

	public void pop_pro()
	{
		m_processes.remove(m_processes.size() - 1);
	}

	public boolean is_stepmod()
	{
		return stepmod;
	}

	public void set_stepmod(boolean stepmod)
	{
		this.stepmod = stepmod;
	}

}
