package com.moepus.byepregen.worldgen.biome;

import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

public final class ClimateRTreeSearchContext<T> extends ThreadLocal<Climate.RTree.Leaf<T>> {
    public static final int PARAMETER_COUNT = 7;
    public static final int DEPTH_PARAMETER_INDEX = 4;

    private static final java.util.concurrent.atomic.LongAdder COLUMN_SEARCHES =
            new java.util.concurrent.atomic.LongAdder();

    /** How often the depth path ran; the worldgen harness asserts it is not zero. */
    public static void recordColumnSearch() {
        COLUMN_SEARCHES.increment();
    }

    public static long columnSearches() {
        return COLUMN_SEARCHES.sum();
    }

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
        private long[] fixedDistances = new long[0];
        private int[] fixedDistanceEpochs = new int[0];
        private int fixedDistanceEpoch;
        private final long[] columnValues = new long[PARAMETER_COUNT];
        private boolean columnValid;

        public void setTarget(final Climate.TargetPoint target) {
            this.values[0] = target.temperature();
            this.values[1] = target.humidity();
            this.values[2] = target.continentalness();
            this.values[3] = target.erosion();
            this.values[4] = target.depth();
            this.values[5] = target.weirdness();
            this.values[6] = 0L;
        }

        public void setTarget(final long[] target) {
            System.arraycopy(target, 0, this.values, 0, PARAMETER_COUNT);
        }

        public void beginDepthColumn(long[] target) {
            this.setTarget(target);
            System.arraycopy(target, 0, this.columnValues, 0, PARAMETER_COUNT);
            this.columnValid = true;
            if (++this.fixedDistanceEpoch == 0) {
                Arrays.fill(this.fixedDistanceEpochs, 0);
                this.fixedDistanceEpoch = 1;
            }
        }

        /**
         * Whether the current target differs from the open column only in depth, so the six fixed
         * distances memoized for that column still apply. 26.2 only entered the depth path when the
         * biome column filler asked for it; the search now recognizes the column itself, which also
         * covers the vanilla per-quart walk and structure lookups.
         */
        public boolean columnMatches() {
            if (!this.columnValid) {
                return false;
            }
            for (int index = 0; index < PARAMETER_COUNT; index++) {
                if (index == DEPTH_PARAMETER_INDEX) {
                    continue;
                }
                if (this.columnValues[index] != this.values[index]) {
                    return false;
                }
            }
            return true;
        }

        public void setDepth(long depth) {
            this.values[DEPTH_PARAMETER_INDEX] = depth;
        }

        public long depthOnlyDistance(Climate.RTree.Node<?> node) {
            return this.fixedDistance(node)
                    + Mth.square(node.parameterSpace[DEPTH_PARAMETER_INDEX]
                    .distance(this.values[DEPTH_PARAMETER_INDEX]));
        }

        private long fixedDistance(Climate.RTree.Node<?> node) {
            int index = ((ClimateRTreeCacheNode)(Object)node).byepregen$cacheIndex();
            if (index >= this.fixedDistances.length) {
                this.fixedDistances = Arrays.copyOf(this.fixedDistances, index + 1);
                this.fixedDistanceEpochs = Arrays.copyOf(this.fixedDistanceEpochs, index + 1);
            }
            if (this.fixedDistanceEpochs[index] != this.fixedDistanceEpoch) {
                this.fixedDistances[index] = this.computeFixedDistance(node);
                this.fixedDistanceEpochs[index] = this.fixedDistanceEpoch;
            }
            return this.fixedDistances[index];
        }

        private long computeFixedDistance(Climate.RTree.Node<?> node) {
            Climate.Parameter[] parameters = node.parameterSpace;
            return Mth.square(parameters[0].distance(this.values[0]))
                    + Mth.square(parameters[1].distance(this.values[1]))
                    + Mth.square(parameters[2].distance(this.values[2]))
                    + Mth.square(parameters[3].distance(this.values[3]))
                    + Mth.square(parameters[5].distance(this.values[5]))
                    + Mth.square(parameters[6].distance(this.values[6]));
        }
    }
}
