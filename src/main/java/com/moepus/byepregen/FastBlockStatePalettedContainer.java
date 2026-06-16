package com.moepus.byepregen;

import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jetbrains.annotations.NotNull;

public final class FastBlockStatePalettedContainer extends FastPalettedContainer<BlockState> {
    public FastBlockStatePalettedContainer(IdMap<BlockState> idList, BlockState defaultValue, Strategy strategy) {
        super(idList, defaultValue, strategy);
    }

    public FastBlockStatePalettedContainer(PalettedContainer<BlockState> source) {
        super(source);
    }

    public static PalettedContainer<BlockState> wrap(PalettedContainer<BlockState> container) {
        return container.getClass() == PalettedContainer.class ? new FastBlockStatePalettedContainer(container) : container;
    }

    @Override
    public @NotNull BlockState get(int x, int y, int z) {
        return this.getFast((y << 8) | (z << 4) | x);
    }

    @Override
    protected @NotNull BlockState get(int index) {
        return this.getFast(index);
    }

    @Override
    public @NotNull BlockState getAndSet(int x, int y, int z, @NotNull BlockState state) {
        return this.getAndSetFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public @NotNull BlockState getAndSetUnchecked(int x, int y, int z, @NotNull BlockState state) {
        return this.getAndSetFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        this.setFast((y << 8) | (z << 4) | x, state);
    }

    @Override
    public PalettedContainerRO.@NotNull PackedData<BlockState> pack(@NotNull IdMap<BlockState> idMap, @NotNull Strategy strategy) {
        if (strategy != Strategy.SECTION_STATES) {
            return super.pack(idMap, strategy);
        }

        BitStorage storage = this.fastData.storage();
        if (storage.getBits() == 0) {
            return BlockStatePackedDataBuilder.packSingle(this.fastData.palette().valueFor(0));
        }

        Palette<BlockState> palette = this.fastData.palette();
        if (palette instanceof GlobalPalette<?>) {
            return BlockStatePackedDataBuilder.pack(storage::get);
        }

        return BlockStatePackedDataBuilder.pack(new LocalPaletteRawIdGetter(storage, palette));
    }

    @Override
    public @NotNull PalettedContainer<BlockState> recreate() {
        return new FastBlockStatePalettedContainer(this.registry, this.fastData.palette().valueFor(0), this.strategy);
    }

    private static final class LocalPaletteRawIdGetter implements BlockStatePackedDataBuilder.RawIdGetter {
        private final BitStorage storage;
        private final Palette<BlockState> palette;
        private int[] localToRaw = new int[16];

        private LocalPaletteRawIdGetter(BitStorage storage, Palette<BlockState> palette) {
            this.storage = storage;
            this.palette = palette;
        }

        @Override
        public int rawId(int sectionIndex) {
            int localId = this.storage.get(sectionIndex);
            this.ensureLocalCapacity(localId);
            int marker = this.localToRaw[localId];
            if (marker == 0) {
                marker = BlockStatePackedDataBuilder.rawId(this.palette.valueFor(localId)) + 1;
                this.localToRaw[localId] = marker;
            }
            return marker - 1;
        }

        private void ensureLocalCapacity(int localId) {
            if (localId < this.localToRaw.length) {
                return;
            }

            int newSize = this.localToRaw.length;
            do {
                newSize *= 2;
            } while (localId >= newSize);
            int[] newLocalToRaw = new int[newSize];
            System.arraycopy(this.localToRaw, 0, newLocalToRaw, 0, this.localToRaw.length);
            this.localToRaw = newLocalToRaw;
        }
    }
}
