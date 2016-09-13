package fakescript;

public class fake
{
	protected String errorstr = "";
	protected callback cb = null;

	// 配置
	protected fkconfig cfg = new fkconfig();

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
	protected buildinfunc bif = new buildinfunc(this);

	// 当前运行状态
	protected running rn = new running(this);

	// debug容器
	protected debuging dbg = new debuging(this);

	// 优化
	protected optimizer opt = new optimizer(this);
}
