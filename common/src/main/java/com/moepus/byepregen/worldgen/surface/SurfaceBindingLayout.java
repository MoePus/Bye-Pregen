package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

final class SurfaceBindingLayout {
    private final List<BindEvent> events;
    private final List<Slot> storedSlots;

    SurfaceBindingLayout(List<BindEvent> events, List<Slot> storedSlots) {
        this.events = List.copyOf(events);
        this.storedSlots = List.copyOf(storedSlots);
    }

    List<BindEvent> events() {
        return this.events;
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
        for (BindEvent event : this.events) {
            Object value = resolver.resolve(event, rawContext, context);
            if (event instanceof Slot slot) {
                values[slot.id().value()] = value;
            }
        }
        return values;
    }

    private static Object resolveRuntime(
            BindEvent event,
            Object rawContext,
            SurfaceContextAccess context
    ) {
        return switch (event.kind()) {
            case STATE, Y_ANCHOR -> event.source();
            case NOISE -> context.byepregen$randomState().getOrCreateNoise(
                    castNoiseKey(event.source())
            );
            case RESOLVED_ANCHOR -> ((VerticalAnchor) event.source()).resolveY(
                    context.byepregen$worldGenerationContext()
            );
            case RANDOM_FACTORY -> context.byepregen$randomState().getOrCreateRandomFactory(
                    (Identifier) event.source()
            );
            case CONDITION -> bindCondition(event.source(), rawContext);
            case RULE -> bindRule(event.source(), rawContext);
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

    sealed interface BindEvent permits Slot, Discarded {
        Kind kind();

        Object source();
    }

    record Slot(SurfaceRulePlan.BindingSlotId id, Kind kind, Object source)
            implements BindEvent {
        Slot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
        }

        String fieldName() {
            return "binding$" + this.id.value();
        }
    }

    record Discarded(Kind kind, Object source) implements BindEvent {
        Discarded {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(source, "source");
        }
    }

    static final class Builder {
        private final List<BindEvent> events = new ArrayList<>();
        private final List<Slot> storedSlots = new ArrayList<>();

        SurfaceRulePlan.BindingSlotId add(Kind kind, Object source) {
            int index = this.storedSlots.size();
            SurfaceRulePlan.BindingSlotId id = new SurfaceRulePlan.BindingSlotId(index);
            Slot slot = new Slot(id, kind, source);
            this.events.add(slot);
            this.storedSlots.add(slot);
            return id;
        }

        void addDiscarded(Kind kind, Object source) {
            this.events.add(new Discarded(kind, source));
        }

        SurfaceBindingLayout build() {
            return new SurfaceBindingLayout(this.events, this.storedSlots);
        }
    }

    @FunctionalInterface
    interface Resolver {
        Object resolve(BindEvent event, Object rawContext, SurfaceContextAccess context);
    }
}
