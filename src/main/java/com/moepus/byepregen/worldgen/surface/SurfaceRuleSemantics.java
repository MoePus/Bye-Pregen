package com.moepus.byepregen.worldgen.surface;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SurfaceRuleSemantics {
    public static final Semantics TEMPLATE_PURE = semantics(
            Scope.TEMPLATE,
            EvaluationEffect.PURE_TOTAL,
            ProofKind.CONSTANT,
            ValueReuse.NONE
    );
    public static final Semantics POINT_OBSERVATION = semantics(
            Scope.POINT,
            EvaluationEffect.MUTABLE_OBSERVATION,
            ProofKind.BARRIER,
            ValueReuse.NONE
    );
    public static final Semantics OPAQUE = semantics(
            Scope.POINT,
            EvaluationEffect.OPAQUE,
            ProofKind.BARRIER,
            ValueReuse.NONE
    );

    private SurfaceRuleSemantics() {
    }

    public static Semantics semantics(
            Scope scope,
            EvaluationEffect effect,
            ProofKind proofKind,
            ValueReuse valueReuse,
            Dependency... dependencies
    ) {
        Set<Dependency> dependencySet = dependencies.length == 0
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(List.of(dependencies)));
        return new Semantics(scope, effect, dependencySet, proofKind, valueReuse);
    }

    public record Semantics(
            Scope scope,
            EvaluationEffect effect,
            Set<Dependency> dependencies,
            ProofKind proofKind,
            ValueReuse valueReuse
    ) {
        public Semantics {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(proofKind, "proofKind");
            Objects.requireNonNull(valueReuse, "valueReuse");
            dependencies = dependencies.isEmpty()
                    ? Set.of()
                    : Collections.unmodifiableSet(EnumSet.copyOf(dependencies));
        }
    }

    public enum Scope {
        TEMPLATE,
        BIND,
        COLUMN,
        UPDATE_Y,
        POINT
    }

    public enum EvaluationEffect {
        PURE_TOTAL(false, true, true, false),
        MAY_THROW(true, false, true, false),
        MUTABLE_OBSERVATION(true, false, false, true),
        OPAQUE(true, false, false, true);

        private final boolean mayThrow;
        private final boolean speculatable;
        private final boolean stableAfterSuccessWithinScope;
        private final boolean barrier;

        EvaluationEffect(
                boolean mayThrow,
                boolean speculatable,
                boolean stableAfterSuccessWithinScope,
                boolean barrier
        ) {
            this.mayThrow = mayThrow;
            this.speculatable = speculatable;
            this.stableAfterSuccessWithinScope = stableAfterSuccessWithinScope;
            this.barrier = barrier;
        }

        public boolean mayThrow() {
            return this.mayThrow;
        }

        public boolean speculatable() {
            return this.speculatable;
        }

        public boolean stableAfterSuccessWithinScope() {
            return this.stableAfterSuccessWithinScope;
        }

        public boolean barrier() {
            return this.barrier;
        }
    }

    public enum ProofKind {
        CONSTANT,
        COLUMN_FACT,
        AFFINE_TRAJECTORY,
        VERTICAL_RANDOM,
        BARRIER
    }

    public enum ValueReuse {
        NONE,
        CANONICAL_WITHIN_SCOPE,
        HOLDER_BEHAVIOR
    }

    public enum BindingEffect {
        NONE,
        MAY_THROW,
        OPAQUE
    }

    public enum Dependency {
        XZ,
        Y,
        SURFACE_DEPTH,
        STONE_DEPTH,
        WATER,
        BIOME,
        NOISE,
        SURFACE_SECONDARY,
        MIN_SURFACE,
        HEIGHTMAP,
        RANDOM,
        MUTABLE_CONTEXT
    }
}
