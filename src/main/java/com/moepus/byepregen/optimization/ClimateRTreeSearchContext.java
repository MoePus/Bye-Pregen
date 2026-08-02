package com.moepus.byepregen.optimization;

import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

public final class ClimateRTreeSearchContext<T> extends ThreadLocal<Climate.RTree.Leaf<T>> {
    public static final int PARAMETER_COUNT = 7;

    private final ThreadLocal<State<T>> state = ThreadLocal.withInitial(State::new);

    public State<T> context() {
        return this.state.get();
    }

    @Override
    public Climate.RTree.Leaf<T> get() {
        return this.context().bestLeaf;
    }

    @Override
    public void set(@Nullable final Climate.RTree.Leaf<T> value) {
        this.context().bestLeaf = value;
    }

    @Override
    public void remove() {
        this.state.remove();
    }

    public static final class State<T> {
        public final long[] values = new long[PARAMETER_COUNT];
        public Climate.RTree.Leaf<T> bestLeaf;
        public long bestDistance;

        public void setTarget(final Climate.TargetPoint target) {
            this.values[0] = target.temperature();
            this.values[1] = target.humidity();
            this.values[2] = target.continentalness();
            this.values[3] = target.erosion();
            this.values[4] = target.depth();
            this.values[5] = target.weirdness();
            this.values[6] = 0L;
        }
    }
}
