package com.moepus.byepregen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public final class FastPlacementContext {
    private static final int INITIAL_STACK_SIZE = 8;
    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);

    private final BlockPos.MutableBlockPos modifierPos = new BlockPos.MutableBlockPos();
    private PlacementContext placementContext;
    private RandomSource random;
    private ConfiguredFeature<?, ?> feature;
    private List<PlacementModifier> modifiers;
    private boolean placed;

    private FastPlacementContext() {
    }

    public static FastPlacementContext acquire(
        PlacementContext placementContext,
        RandomSource random,
        ConfiguredFeature<?, ?> feature,
        List<PlacementModifier> modifiers
    ) {
        FastPlacementContext context = STACK.get().acquire();
        context.init(placementContext, random, feature, modifiers);
        return context;
    }

    public static void release(FastPlacementContext context) {
        STACK.get().release(context);
    }

    private void init(
        PlacementContext placementContext,
        RandomSource random,
        ConfiguredFeature<?, ?> feature,
        List<PlacementModifier> modifiers
    ) {
        this.placementContext = placementContext;
        this.random = random;
        this.feature = feature;
        this.modifiers = modifiers;
        this.placed = false;
    }

    private void clear() {
        this.placementContext = null;
        this.random = null;
        this.feature = null;
        this.modifiers = null;
        this.placed = false;
    }

    public boolean apply(int index, int x, int y, int z) {
        if (index == this.modifiers.size()) {
            BlockPos pos = new BlockPos(x, y, z);
            if (this.feature.place(this.placementContext.getLevel(), this.placementContext.generator(), this.random, pos)) {
                this.placed = true;
            }
            return this.placed;
        }

        PlacementModifier modifier = this.modifiers.get(index);
        ((FastPlacementModifier)(Object)modifier).byepregen$collectPositions(this, x, y, z, index + 1);
        return this.placed;
    }

    public BlockPos.MutableBlockPos modifierPos(int x, int y, int z) {
        return this.modifierPos.set(x, y, z);
    }

    public PlacementContext placementContext() {
        return this.placementContext;
    }

    public RandomSource random() {
        return this.random;
    }

    private static final class Stack {
        private FastPlacementContext[] contexts = new FastPlacementContext[INITIAL_STACK_SIZE];
        private int depth;

        private FastPlacementContext acquire() {
            if (this.depth == this.contexts.length) {
                this.grow();
            }

            FastPlacementContext context = this.contexts[this.depth];
            if (context == null) {
                context = new FastPlacementContext();
                this.contexts[this.depth] = context;
            }

            this.depth++;
            return context;
        }

        private void release(FastPlacementContext context) {
            this.depth--;
            if (this.contexts[this.depth] != context) {
                throw new IllegalStateException("FastPlacementContext stack released out of order");
            }
            context.clear();
        }

        private void grow() {
            FastPlacementContext[] oldContexts = this.contexts;
            FastPlacementContext[] newContexts = new FastPlacementContext[oldContexts.length * 2];
            System.arraycopy(oldContexts, 0, newContexts, 0, oldContexts.length);
            this.contexts = newContexts;
        }
    }
}
