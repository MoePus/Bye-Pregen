package com.moepus.byepregen.PaletteContainer.ArenaPelette;

import static com.moepus.byepregen.PaletteContainer.ArenaPelette.Layout.*;

import com.moepus.byepregen.PaletteContainer.BlockStateRawIdAccess;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.StateImporter;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.NetworkWriter;
import com.moepus.byepregen.UnsafeIntArrayAccess;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.jetbrains.annotations.NotNull;

/**
 * Worldgen-only block-state container backed by raw global block-state ids.
 * Starts as one uniform raw id, upgrades to four 16x16x4 page-local 4-bit palettes,
 * and falls back to dense int[4096] when any page needs more than 16 live states.
 */
public final class ArenaBlockStatePalettedContainer extends PalettedContainer<BlockState> implements BlockStateRawIdAccess {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final PalettedContainer.Configuration<BlockState> DUMMY_PARENT_CONFIGURATION =
            Strategy.SECTION_STATES.getConfiguration(Block.BLOCK_STATE_REGISTRY, 0);
    private static final PalettedContainer.Data<BlockState> DUMMY_PARENT_DATA = new PalettedContainer.Data<>(
            DUMMY_PARENT_CONFIGURATION,
            new ZeroBitStorage(SECTION_SIZE),
            createDummyParentPalette()
    );
    public static final int AIR_RAW_ID = Block.BLOCK_STATE_REGISTRY.getId(AIR);

    private int uniformRawId = AIR_RAW_ID;
    private BlockState uniformState = AIR;

    private int[] arena;
    private int[] denseIds;
    private Int2IntOpenHashMap denseRawIdCounts;

    public ArenaBlockStatePalettedContainer() {
        super(Block.BLOCK_STATE_REGISTRY, Strategy.SECTION_STATES, DUMMY_PARENT_DATA);
    }

    private static Palette<BlockState> createDummyParentPalette() {
        return new SingleValuePalette<>(
                Block.BLOCK_STATE_REGISTRY,
                null,
                List.of(AIR)
        );
    }

    public void releaseRawIds() {
        this.uniformRawId = AIR_RAW_ID;
        this.uniformState = AIR;
        this.arena = null;
        this.denseIds = null;
        this.denseRawIdCounts = null;
    }

    public boolean importVanillaPackedRawIds(int[] paletteRawIds, long[] packedStorage) {
        return StateImporter.importVanillaPackedRawIds(this, paletteRawIds, packedStorage);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buffer) {
        StateImporter.importNetworkData(this, buffer);
    }

    @Override
    public @NotNull BlockState get(int x, int y, int z) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return Block.stateById(UnsafeIntArrayAccess.get(dense, localIndex(x, y, z)));
        }

        int[] arena = this.arena;
        if (arena == null) {
            return this.uniformState;
        }

        int page = y >>> 2;
        int local = ((y & 3) << 8) | (z << 4) | x;
        int base = pageBase(page);
        int paletteIndex = localPaletteIndex(arena, base, local);
        int rawId = rawIdForPaletteIndex(arena, base, paletteIndex);
        return Block.stateById(rawId);
    }

    @Override
    protected @NotNull BlockState get(int index) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return Block.stateById(UnsafeIntArrayAccess.get(dense, index));
        }

        int[] arena = this.arena;
        if (arena == null) {
            return this.uniformState;
        }

        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        int paletteIndex = localPaletteIndex(arena, base, local);
        return Block.stateById(rawIdForPaletteIndex(arena, base, paletteIndex));
    }

    @Override
    public @NotNull BlockState getAndSet(int x, int y, int z, @NotNull BlockState state) {
        return this.getAndSetUnchecked(x, y, z, state);
    }

    @Override
    public @NotNull BlockState getAndSetUnchecked(int x, int y, int z, @NotNull BlockState state) {
        int index = localIndex(x, y, z);
        BlockState oldState = this.get(index);
        this.setRawId(index, rawId(state));
        return oldState;
    }

    @Override
    public void set(int x, int y, int z, @NotNull BlockState state) {
        this.setRawId(localIndex(x, y, z), rawId(state));
    }

    @Override
    public void getAll(@NotNull Consumer<BlockState> consumer) {
        ArenaBlockStateQueries.getAll(this, consumer);
    }

    @Override
    public boolean maybeHas(@NotNull Predicate<BlockState> predicate) {
        return ArenaBlockStateQueries.maybeHas(this, predicate);
    }

    @Override
    public void count(@NotNull CountConsumer<BlockState> consumer) {
        ArenaBlockStateQueries.count(this, consumer);
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buffer) {
        NetworkWriter.write(buffer, this);
    }

    @Override
    public int getSerializedSize() {
        return NetworkWriter.serializedSize(this);
    }

    @Override
    public PalettedContainerRO.@NotNull PackedData<BlockState> pack(@NotNull IdMap<BlockState> idMap, @NotNull Strategy strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull PalettedContainer<BlockState> copy() {
        ArenaBlockStatePalettedContainer copy = new ArenaBlockStatePalettedContainer();
        copy.uniformRawId = this.uniformRawId;
        copy.uniformState = this.uniformState;
        copy.arena = this.arena == null ? null : this.arena.clone();
        copy.denseIds = this.denseIds == null ? null : this.denseIds.clone();
        copy.denseRawIdCounts = this.denseRawIdCounts == null ? null : new Int2IntOpenHashMap(this.denseRawIdCounts);
        return copy;
    }

    public PalettedContainerRO<BlockState> sodium$copy() {
        return this.copy();
    }

    public void sodium$unpack(Object[] values) {
        int[] dense = this.denseIds;
        if (dense != null) {
            unpackDense(values, dense);
            return;
        }

        int[] arena = this.arena;
        if (arena == null) {
            Arrays.fill(values, this.uniformState);
            return;
        }

        unpackArena(values, arena);
    }

    public void sodium$unpack(
            Object[] values, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    int index = localIndex(x, y, z);
                    values[index] = Block.stateById(this.rawIdAt(index));
                }
            }
        }
    }

    @Override
    public @NotNull PalettedContainer<BlockState> recreate() {
        return new ArenaBlockStatePalettedContainer();
    }

    public int rawIdAt(int index) {
        int[] dense = this.denseIds;
        if (dense != null) {
            return UnsafeIntArrayAccess.get(dense, index);
        }

        int[] arena = this.arena;
        if (arena == null) {
            return this.uniformRawId;
        }

        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        int paletteIndex = localPaletteIndex(arena, base, local);
        return rawIdForPaletteIndex(arena, base, paletteIndex);
    }

    @Override
    public int getRawId(int x, int y, int z) {
        return this.rawIdAt(localIndex(x, y, z));
    }

    public boolean isUniform() {
        return this.arena == null && this.denseIds == null;
    }

    public int uniformRawId() {
        return this.uniformRawId;
    }

    public boolean hasPagePalettes() {
        return this.arena != null && this.denseIds == null;
    }

    public boolean hasDenseIds() {
        return this.denseIds != null;
    }

    public Int2IntOpenHashMap denseRawIdCounts() {
        Int2IntOpenHashMap counts = this.denseRawIdCounts;
        if (counts != null) {
            return counts;
        }

        int[] dense = this.denseIds;
        if (dense == null) {
            return null;
        }

        counts = new Int2IntOpenHashMap();
        for (int i = 0; i < SECTION_SIZE; ++i) {
            counts.addTo(UnsafeIntArrayAccess.get(dense, i), 1);
        }
        this.denseRawIdCounts = counts;
        return counts;
    }

    public int arenaPageBase(int page) {
        return pageBase(page);
    }

    public int arenaLivePaletteMask(int base) {
        return pageLivePaletteMask(this.arena, base);
    }

    public int arenaPaletteRawId(int base, int paletteIndex) {
        return rawIdForPaletteIndex(this.arena, base, paletteIndex);
    }

    public int arenaPaletteWord(int base, int wordIndex) {
        return this.arena[base + wordIndex];
    }

    public void forEachRawId(ArenaBlockStateQueries.RawIdConsumer consumer) {
        ArenaBlockStateQueries.forEachRawId(this, consumer);
    }

    public void setRawId(int index, int rawId) {
        if (rawId < 0) {
            rawId = AIR_RAW_ID;
        }

        int[] dense = this.denseIds;
        if (dense != null) {
            int oldRawId = UnsafeIntArrayAccess.get(dense, index);
            if (oldRawId != rawId) {
                UnsafeIntArrayAccess.set(dense, index, rawId);
                this.updateDenseRawIdCounts(oldRawId, rawId);
            }
            return;
        }

        int[] arena = this.arena;
        if (arena == null) {
            if (rawId == this.uniformRawId) {
                return;
            }
            arena = this.ensureArena();
        }

        int page = pageIndexFromSectionIndex(index);
        int local = pageLocalIndexFromSectionIndex(index);
        int base = pageBase(page);
        int oldPaletteIndex = localPaletteIndex(arena, base, local);
        int oldRawId = rawIdForPaletteIndex(arena, base, oldPaletteIndex);
        if (oldRawId == rawId) {
            return;
        }

        int paletteIndex = this.findPaletteIndex(base, rawId);
        if (paletteIndex < 0) {
            paletteIndex = this.appendPaletteIndex(base, rawId);
        }
        if (paletteIndex < 0) {
            if (this.tryReuseDeadPaletteSlotForWrite(base, local, oldPaletteIndex, rawId)) {
                return;
            }

            this.promoteToDense();
            UnsafeIntArrayAccess.set(this.denseIds, index, rawId);
            return;
        }

        setLocalPaletteIndex(arena, base, local, paletteIndex);
    }

    public void c2me$setUnsafe(int x, int y, int z, Object state) {
        this.setRawId(localIndex(x, y, z), rawId((BlockState) state));
    }

    public int[] ensureArena() {
        int[] arena = this.arena;
        if (arena != null) {
            return arena;
        }

        arena = new int[ARENA_INTS];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            arena[pageBase(page) + PAGE_DEFAULT_OFFSET] = this.uniformRawId;
        }
        this.arena = arena;
        return arena;
    }

    public void promoteToDense() {
        if (this.denseIds != null) {
            return;
        }

        int[] dense = new int[SECTION_SIZE];
        int[] arena = this.arena;
        if (arena == null) {
            Arrays.fill(dense, this.uniformRawId);
        } else {
            for (int page = 0; page < PAGE_COUNT; ++page) {
                int base = pageBase(page);
                for (int local = 0; local < PAGE_SIZE; ++local) {
                    int paletteIndex = localPaletteIndex(arena, base, local);
                    dense[sectionIndex(page, local)] = rawIdForPaletteIndex(arena, base, paletteIndex);
                }
            }
        }

        this.denseIds = dense;
        this.arena = null;
        this.denseRawIdCounts = null;
    }

    private boolean tryReuseDeadPaletteSlotForWrite(int base, int local, int oldPaletteIndex, int newRawId) {
        int[] arena = this.arena;
        int seen = 0;
        int twice = 0;

        for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
            int word = arena[base + wordIndex];
            int bit;

            bit = 1 << (word & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 4) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 8) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 12) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 16) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 20) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 24) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
            bit = 1 << ((word >>> 28) & PALETTE_INDEX_MASK);
            twice |= seen & bit;
            seen |= bit;
        }

        int oldBit = 1 << oldPaletteIndex;
        boolean oldDiesAfterWrite = (seen & oldBit) != 0 && (twice & oldBit) == 0;
        int newPaletteIndex;
        if (oldDiesAfterWrite) {
            newPaletteIndex = oldPaletteIndex;
        } else {
            int deadMask = (~seen) & 0xFFFF;
            int extraDead = deadMask & 0xFFFE;
            if (extraDead != 0) {
                newPaletteIndex = Integer.numberOfTrailingZeros(extraDead);
            } else if ((deadMask & 1) != 0) {
                newPaletteIndex = 0;
            } else {
                return false;
            }
        }

        if (newPaletteIndex == 0) {
            arena[base + PAGE_DEFAULT_OFFSET] = newRawId;
        } else {
            arena[base + EXTRA_PALETTE_OFFSET + newPaletteIndex - 1] = newRawId + 1;
        }
        setLocalPaletteIndex(arena, base, local, newPaletteIndex);
        return true;
    }

    private static int rawIdForPaletteIndex(int[] arena, int base, int paletteIndex) {
        return arena[base + PAGE_DEFAULT_OFFSET + paletteIndex]
                - ((paletteIndex + PAGE_PALETTE_SIZE - 1) >>> BITS_PER_ENTRY);
    }

    private static void unpackDense(Object[] values, int[] dense) {
        for (int i = 0; i < SECTION_SIZE; ++i) {
            values[i] = Block.stateById(UnsafeIntArrayAccess.get(dense, i));
        }
    }

    private static void unpackArena(Object[] values, int[] arena) {
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            for (int local = 0; local < PAGE_SIZE; ++local) {
                int paletteIndex = localPaletteIndex(arena, base, local);
                int rawId = rawIdForPaletteIndex(arena, base, paletteIndex);
                values[sectionIndex(page, local)] = Block.stateById(rawId);
            }
        }
    }

    private int findPaletteIndex(int base, int rawId) {
        if (rawId == this.arena[base + PAGE_DEFAULT_OFFSET]) {
            return 0;
        }

        int stored = rawId + 1;
        for (int i = 0; i < EXTRA_PALETTE_SIZE; ++i) {
            if (this.arena[base + EXTRA_PALETTE_OFFSET + i] == stored) {
                return i + 1;
            }
        }
        return -1;
    }

    private int appendPaletteIndex(int base, int rawId) {
        int stored = rawId + 1;
        for (int i = 0; i < EXTRA_PALETTE_SIZE; ++i) {
            if (this.arena[base + EXTRA_PALETTE_OFFSET + i] == 0) {
                this.arena[base + EXTRA_PALETTE_OFFSET + i] = stored;
                return i + 1;
            }
        }
        return -1;
    }

    public void tryPromoteFullUniformSection() {
        int[] arena = this.arena;
        if (arena == null || this.denseIds != null) {
            return;
        }

        int rawId = arena[PAGE_DEFAULT_OFFSET];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            if (arena[base + PAGE_DEFAULT_OFFSET] != rawId) {
                return;
            }
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                if (arena[base + wordIndex] != 0) {
                    return;
                }
            }
        }

        this.setUniformSection(rawId);
    }

    public void setUniformSection(int rawId) {
        this.uniformRawId = rawId;
        this.uniformState = Block.stateById(rawId);
        this.arena = null;
        this.denseIds = null;
        this.denseRawIdCounts = null;
    }

    private void updateDenseRawIdCounts(int oldRawId, int newRawId) {
        Int2IntOpenHashMap counts = this.denseRawIdCounts;
        if (counts == null) {
            return;
        }

        int oldCount = counts.get(oldRawId);
        if (oldCount <= 1) {
            counts.remove(oldRawId);
        } else {
            counts.put(oldRawId, oldCount - 1);
        }
        counts.addTo(newRawId, 1);
    }

    public boolean isUniformRawId(int rawId) {
        return this.arena == null && rawId == this.uniformRawId;
    }

    public static int rawId(BlockState state) {
        int id = state == null ? AIR_RAW_ID : Block.BLOCK_STATE_REGISTRY.getId(state);
        return id < 0 ? AIR_RAW_ID : id;
    }
}
