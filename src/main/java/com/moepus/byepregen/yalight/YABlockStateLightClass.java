package com.moepus.byepregen.yalight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class YABlockStateLightClass {
    static final int CLEAR = 0;
    static final int FULL = 1;
    static final int SHAPED = 2;
    static final int SLOW = 3;

    private static final int VALUES_PER_WORD = 16;
    private static final int BITS_PER_VALUE = 2;
    private static final int VALUE_MASK = 3;
    private static final int[] PACKED = buildPackedTable();

    private YABlockStateLightClass() {
    }

    static int fromRawId(int rawId) {
        if (rawId < 0 || rawId >= Block.BLOCK_STATE_REGISTRY.size()) {
            return SLOW;
        }
        int word = PACKED[rawId / VALUES_PER_WORD];
        int shift = (rawId % VALUES_PER_WORD) * BITS_PER_VALUE;
        return (word >>> shift) & VALUE_MASK;
    }

    private static int[] buildPackedTable() {
        int size = Block.BLOCK_STATE_REGISTRY.size();
        int[] packed = new int[(size + VALUES_PER_WORD - 1) / VALUES_PER_WORD];
        for (int rawId = 0; rawId < size; ++rawId) {
            int lightClass = classify(Block.stateById(rawId));
            packed[rawId / VALUES_PER_WORD] |= lightClass << ((rawId % VALUES_PER_WORD) * BITS_PER_VALUE);
        }
        return packed;
    }

    private static int classify(BlockState state) {
        if (state.getBlock().hasDynamicShape() || state.getLightEmission() != 0) {
            return SLOW;
        }

        // Only classify facts that are stable without a world/pos lookup. Everything ambiguous stays slow.
        int lightBlock = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (lightBlock == 0) {
            return YALightMath.isEmptyShape(state) ? CLEAR : SHAPED;
        }
        // FULL is packed as the constant 1, so it must be safe to use a constant full face shape.
        if (lightBlock >= 15 && state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return FULL;
        }
        return SLOW;
    }
}
