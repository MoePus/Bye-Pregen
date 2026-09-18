package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

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

    Object[] bind(MaterialRuleContext context) {
        return this.bind(context, SurfaceBindingLayout::resolveRuntime);
    }

    Object[] bindForTest(Object context, Resolver resolver) {
        return this.bind(context, resolver);
    }

    private Object[] bind(Object context, Resolver resolver) {
        Object[] values = new Object[this.storedSlots.size()];
        for (BindEvent event : this.events) {
            Object value = resolver.resolve(event, context);
            if (event instanceof Slot slot) {
                values[slot.id().value()] = value;
            }
        }
        return values;
    }

    private static Object resolveRuntime(BindEvent event, Object context) {
        MaterialRuleContext material = (MaterialRuleContext) context;
        return switch (event.kind()) {
            case STATE -> event.source();
            // 26.3: vanilla caches one sampler per (noise, is3d) on the context, and it already
            // returns the per-column or per-position value lazily.
            case NOISE -> {
                SurfaceConditionSpec.Noise noise = (SurfaceConditionSpec.Noise) event.source();
                yield material.getNoiseSampler(noise.noise(), noise.is3d());
            }
            // 26.3: anchors are resolved through the context; MaterialRuleContext has no public
            // WorldGenerationContext to call VerticalAnchor.resolveY on.
            case RESOLVED_ANCHOR -> material.resolveAnchorY((VerticalAnchor) event.source());
            case RANDOM_FACTORY -> material.getOrCreateRandomFactory(
                    (Identifier) event.source()
            );
            case CONDITION -> ((MaterialCondition) event.source()).compile(material);
            case RULE -> ((MaterialRule) event.source()).compile(material);
        };
    }

    enum Kind {
        STATE(BlockState.class),
        NOISE(DoubleSupplier.class),
        RESOLVED_ANCHOR(int.class),
        RANDOM_FACTORY(PositionalRandomFactory.class),
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
        Object resolve(BindEvent event, Object context);
    }
}
