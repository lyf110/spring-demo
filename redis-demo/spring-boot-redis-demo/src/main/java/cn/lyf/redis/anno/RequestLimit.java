package cn.lyf.redis.anno;

import java.lang.annotation.*;

/**
 * @author lyf
 * @version 1.0
 * @classname RequestLimit
 * @description 使用Redis设计的接口防刷机制
 * @since 2023/4/12 11:45
 */
@Documented
@Inherited
@Target(value = {ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestLimit {
    /**
     * 在second秒内，最大只能请求maxCount次
     *
     * @return second
     */
    int second() default 1;

    /**
     * 在second秒内，最大只能请求maxCount次
     *
     * @return maxCount
     */
    int maxCount() default 1;
}
