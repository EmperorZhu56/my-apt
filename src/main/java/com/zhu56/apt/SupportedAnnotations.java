package com.zhu56.apt;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 支持注解
 *
 * @author zhu56
 * @date 2023/05/28 23:14
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface SupportedAnnotations {
    /**
     * {@return the names of the supported annotation interfaces}
     */
    Class<? extends Annotation> [] value();
}
