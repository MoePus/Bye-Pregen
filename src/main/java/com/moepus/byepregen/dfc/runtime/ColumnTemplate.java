package com.moepus.byepregen.dfc.runtime;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Shared generated class and immutable object-slot layout for one RandomState. */
public final class ColumnTemplate {
    private final MethodHandle constructor;
    private final List<Binding> bindings;
    private final boolean yIndependent;
    private final String disabledReason;

    public ColumnTemplate(
            MethodHandle constructor,
            List<Binding> bindings,
            boolean yIndependent
    ) {
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.bindings = List.copyOf(bindings);
        this.yIndependent = yIndependent;
        this.disabledReason = null;
    }

    private ColumnTemplate(String reason) {
        this.constructor = null;
        this.bindings = List.of();
        this.yIndependent = false;
        this.disabledReason = Objects.requireNonNull(reason, "reason");
    }

    public static ColumnTemplate disabled(String reason) {
        return new ColumnTemplate(reason);
    }

    public boolean available() {
        return this.constructor != null;
    }

    public String disabledReason() {
        return this.disabledReason;
    }

    public boolean yIndependent() {
        return this.yIndependent;
    }

    public CompiledColumnEvaluator bind(Function<DensityFunction, DensityFunction> resolver) {
        if (!this.available()) throw new IllegalStateException("Column compiler is disabled: " + this.disabledReason);
        Objects.requireNonNull(resolver, "resolver");
        Object[] values = new Object[this.bindings.size()];
        for (int i = 0; i < values.length; ++i) {
            Binding binding = this.bindings.get(i);
            Object value = binding.kind() == BindingKind.DIRECT
                    ? binding.value() : resolver.apply((DensityFunction) binding.value());
            values[i] = Objects.requireNonNull(value, "Column binding resolver returned null");
        }
        return this.instantiate(values);
    }

    public CompiledColumnEvaluator bindResolved(BindingResolver resolver) {
        if (!this.available()) throw new IllegalStateException("Column compiler is disabled: " + this.disabledReason);
        Objects.requireNonNull(resolver, "resolver");
        Object[] values = new Object[this.bindings.size()];
        for (int i = 0; i < values.length; ++i) {
            Binding binding = this.bindings.get(i);
            Object value = switch (binding.kind()) {
                case DIRECT -> binding.value();
                case DENSITY -> resolver.resolveDensity((DensityFunction) binding.value());
                case INTERPOLATED -> resolver.resolveInterpolated(
                        (DensityFunction) binding.value(), binding.interpolatorSlot());
            };
            values[i] = Objects.requireNonNull(value, "Column binding resolver returned null");
        }
        return this.instantiate(values);
    }

    private CompiledColumnEvaluator instantiate(Object[] values) {
        try {
            return (CompiledColumnEvaluator) this.constructor.invoke((Object) values);
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Cannot instantiate generated density column evaluator", throwable);
        }
    }

    public enum BindingKind {
        DIRECT,
        DENSITY,
        INTERPOLATED
    }

    public interface BindingResolver {
        DensityFunction resolveDensity(DensityFunction source);

        DensityFunction resolveInterpolated(DensityFunction source, int ordinal);
    }

    public record Binding(Object value, BindingKind kind, int interpolatorSlot) {
        public Binding {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(kind, "kind");
            if (kind != BindingKind.DIRECT && !(value instanceof DensityFunction)) {
                throw new IllegalArgumentException("Resolved column binding is not a DensityFunction");
            }
            if ((kind == BindingKind.INTERPOLATED) != (interpolatorSlot >= 0)) {
                throw new IllegalArgumentException("Invalid interpolated binding slot");
            }
        }
    }
}
