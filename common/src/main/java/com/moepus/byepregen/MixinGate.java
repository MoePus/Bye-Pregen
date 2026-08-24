package com.moepus.byepregen;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MixinGate {
    MixinFeature feature() default MixinFeature.NONE;

    String[] requiredMods() default {};

    String[] conflictingMods() default {};

    ConfigFlag config() default ConfigFlag.ALWAYS;
}
