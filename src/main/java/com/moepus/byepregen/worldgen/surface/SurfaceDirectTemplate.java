package com.moepus.byepregen.worldgen.surface;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

final class SurfaceDirectTemplate {
    private final SurfaceBindingLayout bindings;
    private final MethodHandle constructor;
    private final Statistics statistics;

    SurfaceDirectTemplate(
            SurfaceBindingLayout bindings,
            MethodHandle constructor,
            Statistics statistics
    ) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    Object bind(Object context) throws Throwable {
        Object[] values = this.bindings.bind(context);
        return this.constructor.invokeExact(context, values);
    }

    Statistics statistics() {
        return this.statistics;
    }

    record Statistics(ClassShape generated, BindingCounts bindings, ValueCounts values) {
        int classBytes() {
            return this.generated.bytes();
        }

        int bindingSlots() {
            return this.bindings.fields();
        }

        int bindingEvents() {
            return this.bindings.events();
        }

        int regions() {
            return this.generated.regions();
        }

        int noiseOccurrences() {
            return this.values.noisePredicates();
        }

        int noiseSamples() {
            return this.values.noiseSamples();
        }

        int biomeValues() {
            return this.values.biomeValues();
        }

        String regionShape() {
            return this.generated.regionShape();
        }
    }

    record ClassShape(int bytes, int regions, String regionShape) {
    }

    record BindingCounts(int fields, int events) {
    }

    record ValueCounts(int noisePredicates, int noiseSamples, int biomeValues) {
    }
}
