package com.moepus.byepregen.dfc.runtime;

@FunctionalInterface
public interface CompiledColumnEvaluator {
    void evalColumn(ColumnEvaluationContext context);
}
