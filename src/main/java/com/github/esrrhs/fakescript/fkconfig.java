package com.github.esrrhs.fakescript;

public class fkconfig
{
	public int per_frame_cmd_num = 10; // 每帧执行命令数目
	public int include_deps = 100; // 解析include最大深度
	public int stack_max = 10000; // stack最大尺寸
	public int open_debug_log = 0; // 打印内部调试信息
	public int max_run_cmd_num = 0; // 单次run执行的总命令数上限,超过则以错误结束,0表示不限制,精度受per_frame_cmd_num影响
	public int run_timeout_ms = 0; // 单次run的墙钟时间上限(毫秒),超过则以错误结束,0表示不限制
	public int container_max_size = 0; // 单个容器(array/map)最大元素个数,超过则以错误结束,0表示不限制
}
