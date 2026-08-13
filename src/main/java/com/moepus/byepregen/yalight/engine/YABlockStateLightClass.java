package com.moepus.byepregen.yalight.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class YABlockStateLightClass {
    public static final int CLEAR = 0;
    public static final int FULL = 1;
    public static final int SHAPED = 2;
    public static final int SLOW = 3;

    private static final int VALUES_PER_WORD = 16;
    private static final int BITS_PER_VALUE = 2;
    private static final int VALUE_MASK = 3;
    private static final int[] PACKED = buildPackedTable();

    private YABlockStateLightClass() {
    }

    public static void initialize() {
        // The static call is intentional: class initialization builds PACKED at a known lifecycle point.
    }

    public static int fromRawId(int rawId) {
        if (rawId < 0 || rawId >= Block.BLOCK_STATE_REGISTRY.size()) {
            return SLOW;
        }
        int wordIndex = rawId / VALUES_PER_WORD;
        if (wordIndex >= PACKED.length) {
            return SLOW;
        }
        int word = PACKED[wordIndex];
        int shift = (rawId % VALUES_PER_WORD) * BITS_PER_VALUE;
        return (word >>> shift) & VALUE_MASK;
    }

    private static int[] buildPackedTable() {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        int[] packed = new int[(size + VALUES_PER_WORD - 1) / VALUES_PER_WORD];
        for (int rawId = 0; rawId < size; ++rawId) {
            int lightClass = classifySafely(Block.stateById(rawId));
            packed[rawId / VALUES_PER_WORD] |= lightClass << ((rawId % VALUES_PER_WORD) * BITS_PER_VALUE);
        }
        return packed;
    }

    private static int classifySafely(BlockState state) {
        if (state == null) {
            return SLOW;
        }
        try {
            return classify(state);
        } catch (Throwable throwable) {
            return SLOW;
        }
    }

    private static int classify(BlockState state) {
        if (needsStateLightColor(state)) {
            return SLOW;
        }
        if (state.getBlock().hasDynamicShape()) {
            return SLOW;
        }

        // Only classify facts that are stable without a world/pos lookup. Everything ambiguous stays slow.
        int lightBlock = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        boolean emptyShape = YALightMath.isEmptyShape(state);
        if (lightBlock == 0 && emptyShape) {
            return CLEAR;
        }
        if (lightBlock == 0) {
            return SHAPED;
        }
        // FULL is packed as the constant 1, so it must be safe to use a constant full face shape.
        if (lightBlock >= 15 && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return FULL;
        }
        return SLOW;
    }

    private static boolean needsStateLightColor(BlockState state) {
        return state.hasDynamicLightEmission() || state.getLightEmission() != 0 || state.emissiveRendering(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
}
