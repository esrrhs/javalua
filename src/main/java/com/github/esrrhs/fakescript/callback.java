package com.github.esrrhs.fakescript;

/**
 * 宿主回调接口
 * <p>
 * 通过fk.set_callback设置,用于接收脚本print输出与错误通知
 * 未设置时print默认输出到stdout,错误只记录在errorstr中
 */
public interface callback
{
	public void on_error(fake f, String file, int lineno, String func,
			String str);
	
	public void on_print(fake f, String str);
}
