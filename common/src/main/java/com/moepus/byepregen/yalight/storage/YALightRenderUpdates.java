package com.moepus.byepregen.yalight.storage;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongConsumer;

/** Client-only reverse dependencies of the one-block light/AO sampling halo. */
public final class YALightRenderUpdates {
    private static final int AXIS_STATES = 3;
    private static final int Y_STRIDE = AXIS_STATES * AXIS_STATES;
    private static final int NEIGHBOR_COUNT = Y_STRIDE * AXIS_STATES;
    public static final int CENTER = 1 << (NEIGHBOR_COUNT / 2);
    public static final int WHOLE_SECTION = (1 << NEIGHBOR_COUNT) - 1;
    private static final int[] MASKS = createMasks();

    private final LongOpenHashSet sections = new LongOpenHashSet();
    private final LongConsumer invalidate;
    private final int minSection;
    private final int maxSection;

    public YALightRenderUpdates(int minSection, int maxSection, LongConsumer invalidate) {
        this.minSection = minSection;
        this.maxSection = maxSection;
        this.invalidate = invalidate;
    }

    public static int maskForBlock(int localIndex) {
        int x = boundaryState(localIndex & 15);
        int z = boundaryState((localIndex >>> 4) & 15);
        int y = boundaryState((localIndex >>> 8) & 15);
        return MASKS[x + AXIS_STATES * z + Y_STRIDE * y];
    }

    public void add(ChunkPos pos, int sectionY, int mask) {
        while (mask != 0) {
            int bit = Integer.numberOfTrailingZeros(mask);
            mask &= mask - 1;
            int y = sectionY + bit / Y_STRIDE - 1;
            if (y >= this.minSection && y < this.maxSection) {
                this.sections.add(SectionPos.asLong(pos.x() + bit % AXIS_STATES - 1,
                        y, pos.z() + bit / AXIS_STATES % AXIS_STATES - 1));
            }
        }
    }

    public void flush() {
        var iterator = this.sections.iterator();
        while (iterator.hasNext()) {
            this.invalidate.accept(iterator.nextLong());
        }
        this.sections.clear();
    }

    private static int boundaryState(int local) {
        return local == 0 ? 0 : local == 15 ? 2 : 1;
    }

    private static int[] createMasks() {
        int[] masks = new int[NEIGHBOR_COUNT];
        for (int state = 0; state < masks.length; ++state) {
            masks[state] = createMask(state % AXIS_STATES, state / Y_STRIDE,
                    state / AXIS_STATES % AXIS_STATES);
        }
        return masks;
    }

    private static int createMask(int xState, int yState, int zState) {
        int mask = 0;
        for (int y = Math.min(yState, 1); y <= Math.max(yState, 1); ++y) {
            for (int z = Math.min(zState, 1); z <= Math.max(zState, 1); ++z) {
                for (int x = Math.min(xState, 1); x <= Math.max(xState, 1); ++x) {
                    mask |= 1 << (x + AXIS_STATES * z + Y_STRIDE * y);
                }
            }
        }
        return mask;
    }
}
