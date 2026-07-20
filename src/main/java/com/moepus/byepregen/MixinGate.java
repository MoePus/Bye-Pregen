package com.moepus.byepregen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MixinGate {
    String[] requiredMods() default {};

    String[] conflictingMods() default {};

    String config() default "";
}
