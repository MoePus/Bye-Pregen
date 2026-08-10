package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

final class SurfaceBindingLayout {
    private final List<Slot> slots;
    private final List<Slot> storedSlots;

    SurfaceBindingLayout(List<Slot> slots, List<Slot> storedSlots) {
        this.slots = List.copyOf(slots);
        this.storedSlots = List.copyOf(storedSlots);
    }

    List<Slot> slots() {
        return this.slots;
    }

    List<Slot> storedSlots() {
        return this.storedSlots;
    }

    Slot storedSlot(SurfaceRulePlan.BindingSlotId id) {
        return this.storedSlots.get(id.value());
    }

    Object[] bind(Object rawContext) {
        SurfaceContextAccess context = (SurfaceContextAccess) rawContext;
        return this.bind(rawContext, context, SurfaceBindingLayout::resolveRuntime);
    }

    Object[] bindForTest(Object rawContext, Resolver resolver) {
        return this.bind(rawContext, (SurfaceContextAccess) rawContext, resolver);
    }

    private Object[] bind(
            Object rawContext,
            SurfaceContextAccess context,
            Resolver resolver
    ) {
        Object[] values = new Object[this.storedSlots.size()];
        for (Slot slot : this.slots) {
            Object value = resolver.resolve(slot, rawContext, context);
            if (slot.valueIndex() >= 0) {
                values[slot.valueIndex()] = value;
            }
        }
        return values;
    }

    private static Object resolveRuntime(
            Slot slot,
            Object rawContext,
            SurfaceContextAccess context
    ) {
        return switch (slot.kind()) {
            case STATE, BIOME_TABLE, Y_ANCHOR -> slot.source();
            case NOISE -> context.byepregen$randomState().getOrCreateNoise(
                    castNoiseKey(slot.source())
            );
            case RESOLVED_ANCHOR -> ((VerticalAnchor) slot.source()).resolveY(
                    context.byepregen$worldGenerationContext()
            );
            case RANDOM_FACTORY -> context.byepregen$randomState().getOrCreateRandomFactory(
                    (ResourceLocation) slot.source()
            );
            case CONDITION -> bindCondition(slot.source(), rawContext);
            case RULE -> bindRule(slot.source(), rawContext);
        };
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<NormalNoise.NoiseParameters> castNoiseKey(Object source) {
        return (ResourceKey<NormalNoise.NoiseParameters>) source;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SurfaceBoundAccess.Condition bindCondition(Object source, Object context) {
        Object bound = ((Function) source).apply(context);
        return (SurfaceBoundAccess.Condition) bound;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SurfaceBoundAccess.Rule bindRule(Object source, Object context) {
        Object bound = ((Function) source).apply(context);
        return (SurfaceBoundAccess.Rule) bound;
    }

    enum Kind {
        STATE(BlockState.class),
        BIOME_TABLE(SurfaceBiomeBehaviorTable.class),
        NOISE(NormalNoise.class),
        RESOLVED_ANCHOR(int.class),
        RANDOM_FACTORY(PositionalRandomFactory.class),
        Y_ANCHOR(VerticalAnchor.class),
        CONDITION(null),
        RULE(null);

        private final Class<?> fieldType;

        Kind(Class<?> fieldType) {
            this.fieldType = fieldType;
        }

        Class<?> fieldType() {
            return this.fieldType;
        }
    }

    record Slot(
            SurfaceRulePlan.BindingSlotId id,
            int valueIndex,
            Kind kind,
            Object source
    ) {
        Slot {
            Objects.requireNonNull(id, "id");
            if (valueIndex < -1) {
                throw new IllegalArgumentException("Invalid binding value index: " + valueIndex);
            }
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
        }

        String fieldName() {
            return "binding$" + this.id.value();
        }
    }

    static final class Builder {
        private final List<Slot> slots = new ArrayList<>();
        private final List<Slot> storedSlots = new ArrayList<>();

        SurfaceRulePlan.BindingSlotId add(Kind kind, Object source) {
            int index = this.storedSlots.size();
            SurfaceRulePlan.BindingSlotId id = new SurfaceRulePlan.BindingSlotId(index);
            Slot slot = new Slot(id, index, kind, source);
            this.slots.add(slot);
            this.storedSlots.add(slot);
            return id;
        }

        void addDiscarded(Kind kind, Object source) {
            this.slots.add(new Slot(
                    new SurfaceRulePlan.BindingSlotId(this.slots.size()),
                    -1,
                    kind,
                    source
            ));
        }

        SurfaceBindingLayout build() {
            return new SurfaceBindingLayout(this.slots, this.storedSlots);
        }
    }

    @FunctionalInterface
    interface Resolver {
        Object resolve(Slot slot, Object rawContext, SurfaceContextAccess context);
    }
}
