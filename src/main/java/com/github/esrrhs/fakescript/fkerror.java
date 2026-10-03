package com.github.esrrhs.fakescript;

/**
 * 结构化的错误信息
 * <p>
 * 通过fk.getlasterror获取,记录最近一次错误的定位与内容<br>
 * 宿主可按字段读取而无需解析错误字符串
 */
public class fkerror
{
	// 出错的文件,字符串解析时为空
	public String file;

	// 出错的行号,未知为0
	public int lineno;

	// 出错时的函数,未知为nil
	public String funcname;

	// 完整错误信息,运行时错误时附带Java堆栈与脚本调用栈
	public String message;

	@Override
	public String toString()
	{
		return file + ":" + lineno + " " + funcname + " " + message;
	}
}
