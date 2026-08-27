package com.moepus.byepregen.worldgen.feature;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
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
    private FastPlacementContext parent;
    private IdentityHashMap<DiskConfiguration, KnownFalseDiskPredicateCache> nestedDiskCaches;
    private FastFeaturePlacement terminal;
    private boolean placed;

    private FastPlacementContext() {
    }

    public static FastPlacementContext acquire(
        PlacementContext placementContext,
        RandomSource random,
        ConfiguredFeature<?, ?> feature,
        List<PlacementModifier> modifiers
    ) {
        Stack stack = STACK.get();
        FastPlacementContext parent = stack.current();
        FastPlacementContext context = stack.acquire();
        context.init(placementContext, random, feature, modifiers);
        context.parent = parent;
        return context;
    }

    public static void release(FastPlacementContext context) {
        STACK.get().release(context);
    }

    public static FastPlacementContext current() {
        return STACK.get().current();
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
        this.nestedDiskCaches = null;
        this.terminal = null;
        this.placed = false;
    }

    private void clear() {
        this.placementContext = null;
        this.random = null;
        this.feature = null;
        this.modifiers = null;
        this.parent = null;
        this.nestedDiskCaches = null;
        this.terminal = null;
        this.placed = false;
    }

    public boolean apply(int index, int x, int y, int z) {
        if (index == this.modifiers.size()) {
            if (this.terminal != null) {
                this.placed |= this.terminal.placeOrigin(x, y, z);
                return this.placed;
            }
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

    public PlacementContext nestedPlacementContext() {
        if (this.placementContext.topFeature().isEmpty()) {
            return this.placementContext;
        }
        return STACK.get().nestedPlacementContext(this.placementContext);
    }

    public RandomSource random() {
        return this.random;
    }

    public ConfiguredFeature<?, ?> feature() {
        return this.feature;
    }

    public List<PlacementModifier> modifiers() {
        return this.modifiers;
    }

    public FastPlacementContext parent() {
        return this.parent;
    }

    KnownFalseDiskPredicateCache nestedDiskCache(
            DiskConfiguration config,
            Vec3i[] dependencies
    ) {
        if (this.nestedDiskCaches == null) {
            this.nestedDiskCaches = new IdentityHashMap<>();
        }
        return this.nestedDiskCaches.computeIfAbsent(config, ignored -> new KnownFalseDiskPredicateCache(
                dependencies,
                this.placementContext.getLevel().getMinBuildHeight(),
                this.placementContext.getLevel().getMaxBuildHeight()
        ));
    }

    public void terminal(FastFeaturePlacement terminal) {
        this.terminal = terminal;
    }

    private static final class Stack {
        private FastPlacementContext[] contexts = new FastPlacementContext[INITIAL_STACK_SIZE];
        private int depth;
        private PlacementContext nestedPlacementContext;

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

        private FastPlacementContext current() {
            return this.depth == 0 ? null : this.contexts[this.depth - 1];
        }

        private PlacementContext nestedPlacementContext(PlacementContext source) {
            if (this.nestedPlacementContext == null) {
                WorldGenLevel level = source.getLevel();
                ChunkGenerator generator = source.generator();
                this.nestedPlacementContext = new PlacementContext(level, generator, Optional.empty());
            }
            return this.nestedPlacementContext;
        }

        private void release(FastPlacementContext context) {
            this.depth--;
            if (this.contexts[this.depth] != context) {
                throw new IllegalStateException("FastPlacementContext stack released out of order");
            }
            context.clear();
            if (this.depth == 0) {
                this.nestedPlacementContext = null;
            }
        }

        private void grow() {
            FastPlacementContext[] oldContexts = this.contexts;
            FastPlacementContext[] newContexts = new FastPlacementContext[oldContexts.length * 2];
            System.arraycopy(oldContexts, 0, newContexts, 0, oldContexts.length);
            this.contexts = newContexts;
        }
    }
}
