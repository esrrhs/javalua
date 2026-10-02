package com.github.esrrhs.fakescript;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention (RUNTIME)
@Target (METHOD)
/**
 * 绑定Java方法到脚本的注解
 * <p>
 * 标记在静态方法上,通过fk.reg/fk.regclass注册后脚本可直接调用
 * name可指定脚本侧函数名,缺省用方法名
 */
public @interface fakescript
{
	String name() default "";
}
