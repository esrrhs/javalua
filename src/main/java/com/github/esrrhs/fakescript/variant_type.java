package com.github.esrrhs.fakescript;

enum variant_type
{
	NIL,
	REAL,	   	// 浮点数值,参与计算
	INT,		// 64位整数值,参与计算,整数字面量与整数运算的结果
	STRING,	 	// 字符串
	POINTER,	// 指针
	UUID,	   	// int64的uuid，不参与计算，为了效率
	ARRAY,	  	// 数组
	MAP,		// 集合
}
