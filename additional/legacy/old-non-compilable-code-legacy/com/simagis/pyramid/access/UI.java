package com.simagis.pyramid.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
@interface UI {
    String caption() default "";
    String[] category() default {};
    String defaultValue() default "";
    String description() default "";
    Class<? extends Exception> exceptionUIClass () default Exception.class;
    Enum[] enums() default {};
}
