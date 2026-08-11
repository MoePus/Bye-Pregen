package com.moepus.byepregen.worldgen.surface;

import java.util.Objects;

public record SurfaceConditionPlan(
        SurfaceRulePlan.ConditionValue value,
        Kind kind,
        BindingRecipe bindingRecipe,
        SurfaceRuleSemantics.ProofKind proofKind,
        SurfaceRuleSemantics.ValueReuse valueReuse
) {
    public SurfaceConditionPlan {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(bindingRecipe, "bindingRecipe");
        Objects.requireNonNull(proofKind, "proofKind");
        Objects.requireNonNull(valueReuse, "valueReuse");
    }

    static SurfaceConditionPlan create(SurfaceRulePlan.ConditionValue value) {
        Kind kind = kind(value.spec());
        return new SurfaceConditionPlan(
                value,
                kind,
                bindingRecipe(kind),
                value.semantics().proofKind(),
                value.semantics().valueReuse()
        );
    }

    private static Kind kind(SurfaceConditionSpec spec) {
        return switch (spec) {
            case SurfaceConditionSpec.Biome ignored -> Kind.BIOME;
            case SurfaceConditionSpec.Noise ignored -> Kind.NOISE;
            case SurfaceConditionSpec.StoneDepth ignored -> Kind.STONE_DEPTH;
            case SurfaceConditionSpec.VerticalGradient ignored -> Kind.VERTICAL_GRADIENT;
            case SurfaceConditionSpec.Water ignored -> Kind.WATER;
            case SurfaceConditionSpec.YAbove ignored -> Kind.Y_ABOVE;
            case SurfaceConditionSpec.Negated ignored -> Kind.NEGATED;
            case SurfaceConditionSpec.Opaque ignored -> Kind.OPAQUE;
            case SurfaceConditionSpec.Singleton singleton -> switch (singleton) {
                case ABOVE_PRELIMINARY_SURFACE -> Kind.ABOVE_PRELIMINARY;
                case HOLE -> Kind.HOLE;
                case STEEP -> Kind.STEEP;
                case TEMPERATURE -> Kind.TEMPERATURE;
            };
        };
    }

    private static BindingRecipe bindingRecipe(Kind kind) {
        return switch (kind) {
            // Preserve the original BiomeConditionSource predicate until
            // holder behavior specialization has a live-world proof.
            case BIOME -> BindingRecipe.CONDITION_DELEGATE;
            case NOISE -> BindingRecipe.NOISE;
            case VERTICAL_GRADIENT -> BindingRecipe.GRADIENT;
            case Y_ABOVE -> BindingRecipe.Y_ANCHOR;
            case STEEP, TEMPERATURE -> BindingRecipe.CONDITION_DELEGATE;
            case OPAQUE -> BindingRecipe.OPAQUE_CONDITION;
            default -> BindingRecipe.NONE;
        };
    }

    public enum Kind {
        BIOME,
        NOISE,
        STONE_DEPTH,
        VERTICAL_GRADIENT,
        WATER,
        Y_ABOVE,
        ABOVE_PRELIMINARY,
        HOLE,
        STEEP,
        TEMPERATURE,
        NEGATED,
        OPAQUE
    }

    public enum BindingRecipe {
        NONE,
        BIOME_BEHAVIOR,
        NOISE,
        GRADIENT,
        Y_ANCHOR,
        CONDITION_DELEGATE,
        OPAQUE_CONDITION
    }
}
