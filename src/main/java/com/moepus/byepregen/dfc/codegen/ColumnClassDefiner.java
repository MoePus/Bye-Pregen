package com.moepus.byepregen.dfc.codegen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class ColumnClassDefiner {
    private ColumnClassDefiner() {
    }

    public static MethodHandle defineConstructor(byte[] classBytes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(
                    classBytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            return lookup.findConstructor(lookup.lookupClass(),
                    MethodType.methodType(void.class, Object[].class));
        } catch (IllegalAccessException | NoSuchMethodException throwable) {
            throw new IllegalStateException("Cannot define density column hidden class", throwable);
        }
    }
}
