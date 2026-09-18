package com.moepus.byepregen.dfc.runtime;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Objects;

/** Generated code and immutable native sampler/parameter bindings; no chunk state is captured. */
public final class ColumnTemplate {
    private final CompiledColumnEvaluator evaluator;
    private final boolean yIndependent;

    public ColumnTemplate(MethodHandle constructor, List<Binding> bindings, boolean yIndependent) {
        this.yIndependent = yIndependent;
        Object[] values = bindings.stream().map(Binding::value).toArray();
        try {
            this.evaluator = (CompiledColumnEvaluator) constructor.invoke((Object) values);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot instantiate generated density column evaluator", failure);
        }
    }

    public CompiledColumnEvaluator evaluator() { return this.evaluator; }
    public boolean yIndependent() { return this.yIndependent; }

    public record Binding(Object value) {
        public Binding { Objects.requireNonNull(value); }
    }
}
