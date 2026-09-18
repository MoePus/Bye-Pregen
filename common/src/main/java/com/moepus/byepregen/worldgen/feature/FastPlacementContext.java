package com.moepus.byepregen.worldgen.feature;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/** Per-call primitive work stack with RC2's collect-before-descend ordering. */
public final class FastPlacementContext {
    private static final int COORDINATES = 3;
    private static final int MAX_POOLED_CONTEXTS = 8;
    private static final ThreadLocal<ArrayDeque<FastPlacementContext>> POOL =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final IntArrayList pending = new IntArrayList();
    private final IntArrayList emitted = new IntArrayList();
    private final List<BlockPos> nativePositions = new ArrayList<>();
    private final BlockPos.MutableBlockPos modifierPos = new BlockPos.MutableBlockPos();
    private PlacementContext placementContext;
    private RandomSource random;
    private Feature feature;
    private List<PlacementModifier> modifiers;
    private PredicateMemoizedDiskPlacement diskPlacement;
    private boolean placed;

    private FastPlacementContext() { }

    public static FastPlacementContext acquire(PlacementContext context, RandomSource random,
                                               Feature feature, List<PlacementModifier> modifiers) {
        FastPlacementContext result = POOL.get().pollFirst();
        if (result == null) result = new FastPlacementContext();
        result.placementContext = context;
        result.random = random;
        result.feature = feature;
        result.modifiers = modifiers;
        return result;
    }

    public static void release(FastPlacementContext context) {
        if (context.placementContext == null) throw new IllegalStateException("Placement context is already released");
        context.pending.clear();
        context.emitted.clear();
        context.nativePositions.clear();
        context.placementContext = null;
        context.random = null;
        context.feature = null;
        context.modifiers = null;
        context.diskPlacement = null;
        context.placed = false;
        ArrayDeque<FastPlacementContext> pool = POOL.get();
        if (pool.size() < MAX_POOLED_CONTEXTS) pool.addFirst(context);
    }

    public boolean place(BlockPos origin, FeaturePlan plan) {
        if (this.modifiers.isEmpty()) {
            this.trackPlacement();
            return this.feature.place(this.placementContext.getLevel(), this.placementContext.generator(), this.random, origin);
        }
        this.diskPlacement = plan.open(this);
        this.push(origin.getX(), origin.getY(), origin.getZ(), 0);
        while (!this.pending.isEmpty()) this.advance();
        return this.diskPlacement == null ? this.placed : this.diskPlacement.placed();
    }

    private void advance() {
        int index = this.pop();
        int z = this.pop();
        int y = this.pop();
        int x = this.pop();
        this.emitted.clear();
        this.collect(this.modifiers.get(index), x, y, z);
        if (index + 1 == this.modifiers.size()) {
            this.placeEmitted();
            return;
        }
        for (int offset = this.emitted.size() - COORDINATES; offset >= 0; offset -= COORDINATES) {
            this.push(this.emitted.getInt(offset), this.emitted.getInt(offset + 1),
                    this.emitted.getInt(offset + 2), index + 1);
        }
    }

    private void collect(PlacementModifier modifier, int x, int y, int z) {
        if (modifier instanceof FastPlacementModifier fast) {
            fast.byepregen$collectPositions(this, x, y, z);
            return;
        }
        // Keep references until modify returns, matching vanilla even for a reused mutable position.
        try {
            modifier.modify(this.placementContext, this.random, new BlockPos(x, y, z), this.nativePositions::add);
            for (BlockPos pos : this.nativePositions) this.emit(pos.getX(), pos.getY(), pos.getZ());
        } finally {
            this.nativePositions.clear();
        }
    }

    private void placeEmitted() {
        for (int offset = 0; offset < this.emitted.size(); offset += COORDINATES) {
            int x = this.emitted.getInt(offset);
            int y = this.emitted.getInt(offset + 1);
            int z = this.emitted.getInt(offset + 2);
            if (this.diskPlacement == null) {
                this.placed |= this.feature.place(this.placementContext.getLevel(),
                        this.placementContext.generator(), this.random, new BlockPos(x, y, z));
            } else {
                this.diskPlacement.placeOrigin(x, y, z);
            }
            this.trackPlacement();
        }
    }

    private void trackPlacement() {
        if (SharedConstants.DEBUG_FEATURE_COUNT) {
            FeatureCountTracker.featurePlaced(this.placementContext.getLevel().getLevel(),
                    this.feature, this.placementContext.topFeature());
        }
    }

    public void emit(int x, int y, int z) {
        this.emitted.add(x);
        this.emitted.add(y);
        this.emitted.add(z);
    }

    private void push(int x, int y, int z, int modifierIndex) {
        this.pending.add(x);
        this.pending.add(y);
        this.pending.add(z);
        this.pending.add(modifierIndex);
    }

    private int pop() { return this.pending.removeInt(this.pending.size() - 1); }
    public BlockPos.MutableBlockPos modifierPos(int x, int y, int z) { return this.modifierPos.set(x, y, z); }
    public PlacementContext placementContext() { return this.placementContext; }
    public RandomSource random() { return this.random; }
}
