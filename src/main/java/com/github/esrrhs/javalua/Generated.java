package com.github.esrrhs.javalua;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「不是手写的实现」，JaCoCo 会把它跳出行覆盖统计。
 * <p>
 * 用在这里有两处，性质不同但要分开说清楚：
 * <ul>
 *   <li><b>生成物</b>：{@link CApi} 整个类由 {@code native/gen_capi.py} 产出，
 *       测它等于测生成器；</li>
 *   <li><b>未实现的占位桩</b>：{@link Api} 里那些还是
 *       {@code throw unimplemented(...)} 的方法。它们是「C API 还差哪些符号」
 *       的清单，不是实现——实现时把注解摘掉，注解的个数就是欠账的条数。</li>
 * </ul>
 * <p>
 * 之所以不靠「降低门禁值」来消化这批行：门禁一降就再也看不出真下降。而摘
 * 注解的动作与「这条语义真的落地了」是同一个动作，不会脱节。
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Generated {
}
