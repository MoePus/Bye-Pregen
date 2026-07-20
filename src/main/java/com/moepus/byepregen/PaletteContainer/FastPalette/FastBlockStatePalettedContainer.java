package com.moepus.byepregen.PaletteContainer.FastPalette;

import com.moepus.byepregen.PaletteContainer.BlockStatePackedDataBuilder;
import com.moepus.byepregen.mixin.accessor.PalettedContainerAccessor;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jetbrains.annotations.NotNull;

public final class FastBlockStatePalettedContainer extends FastPalettedContainer<BlockState> {
    private static final int SECTION_SIZE = 4096;
    private static final int MAX_LOCAL_PALETTE_BITS = 8;

    public FastBlockStatePalettedContainer(IdMap<BlockState> idList, BlockState defaultValue, Strategy strategy) {
        super(idList, defaultValue, strategy);
    }

    boolean importVanillaPackedRawIds(int[] paletteRawIds, long[] packedStorage) {
        if (paletteRawIds == null || paletteRawIds.length == 0) {
            return false;
        }

        int bits = vanillaSerializedBits(paletteRawIds.length);
        PalettedContainer.Data<BlockState> data =
                ((PalettedContainerAccessor<BlockState>) (Object) this).byepregen$invokeCreateOrReuseData(null, bits);
        int[] localIds = fillPalette(data.palette(), paletteRawIds);
        if (bits != 0 && !this.importStorage(data, paletteRawIds, localIds, packedStorage, bits)) {
            return false;
        }

        ((PalettedContainerAccessor<BlockState>) (Object) this).byepregen$setData(data);
        this.byepregen$updateFastData(data);
        return true;
    }

    private boolean importStorage(
            PalettedContainer.Data<BlockState> data,
            int[] paletteRawIds,
            int[] localIds,
            long[] packedStorage,
            int bits) {
        if (packedStorage == null || packedStorage.length != packedLength(bits)) {
            return false;
        }

        if (bits <= MAX_LOCAL_PALETTE_BITS) {
            return writeLocalStorage(data.storage(), paletteRawIds.length, localIds, packedStorage, bits);
        }

        return writeGlobalStorage(data.storage(), paletteRawIds, packedStorage, bits);
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
        PalettedContainerAccessor<BlockState> accessor = (PalettedContainerAccessor<BlockState>) (Object) this;
        return new FastBlockStatePalettedContainer(
                accessor.byepregen$getRegistry(), this.fastData.palette().valueFor(0), accessor.byepregen$getStrategy());
    }

    private static int[] fillPalette(Palette<BlockState> palette, int[] paletteRawIds) {
        int[] localIds = new int[paletteRawIds.length];
        if (palette instanceof GlobalPalette<?>) {
            System.arraycopy(paletteRawIds, 0, localIds, 0, paletteRawIds.length);
            return localIds;
        }
        for (int i = 0; i < paletteRawIds.length; ++i) {
            localIds[i] = palette.idFor(Block.stateById(paletteRawIds[i]));
        }
        return localIds;
    }

    private static boolean writeLocalStorage(
            BitStorage storage, int paletteSize, int[] localIds, long[] packedStorage, int serializedBits) {
        long[] output = storage.getRaw();
        int outputBits = storage.getBits();
        for (int index = 0; index < SECTION_SIZE; ++index) {
            int localId = packedValueAt(packedStorage, serializedBits, index);
            if (localId >= paletteSize) {
                return false;
            }
            setPackedValue(output, outputBits, index, localIds[localId]);
        }
        return true;
    }

    private static boolean writeGlobalStorage(
            BitStorage storage, int[] paletteRawIds, long[] packedStorage, int serializedBits) {
        long[] output = storage.getRaw();
        int outputBits = storage.getBits();
        for (int index = 0; index < SECTION_SIZE; ++index) {
            int localId = packedValueAt(packedStorage, serializedBits, index);
            if (localId >= paletteRawIds.length) {
                return false;
            }
            setPackedValue(output, outputBits, index, paletteRawIds[localId]);
        }
        return true;
    }

    private static int packedValueAt(long[] packedStorage, int bits, int index) {
        int valuesPerLong = Long.SIZE / bits;
        int cell = index / valuesPerLong;
        int shift = (index - cell * valuesPerLong) * bits;
        return (int) ((packedStorage[cell] >>> shift) & ((1L << bits) - 1L));
    }

    private static void setPackedValue(long[] packedStorage, int bits, int index, int value) {
        int valuesPerLong = Long.SIZE / bits;
        int cell = index / valuesPerLong;
        int shift = (index - cell * valuesPerLong) * bits;
        packedStorage[cell] |= ((long) value & ((1L << bits) - 1L)) << shift;
    }

    private static int vanillaSerializedBits(int paletteSize) {
        if (paletteSize == 1) {
            return 0;
        }
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(4, bits);
    }

    private static int packedLength(int bits) {
        int valuesPerLong = Long.SIZE / bits;
        return (SECTION_SIZE + valuesPerLong - 1) / valuesPerLong;
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
