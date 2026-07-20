package com.moepus.byepregen.PaletteContainer.ArenaPelette;

import com.moepus.byepregen.PaletteContainer.BlockStatePackedDataBuilder;
import com.moepus.byepregen.PaletteContainer.BlockStateRawIdAccess;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.NetworkWriter;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.StateImporter;
import com.moepus.byepregen.UnsafeIntArrayAccess;
import com.moepus.byepregen.mixin.accessor.PalettedContainerAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jetbrains.annotations.NotNull;

/**
 * Worldgen-only block-state container backed by raw global block-state ids.
 * Starts as one uniform raw id, upgrades to four 16x16x4 page-local 4-bit palettes,
 * and falls back to dense int[4096] when any page needs more than 16 live states.
 */
public final class ArenaBlockStatePalettedContainer extends PalettedContainer<BlockState> implements BlockStateRawIdAccess {
    static final int SECTION_SIZE = 4096;

    private static final int PAGE_HEIGHT = 4;
    static final int PAGE_COUNT = 4;
    static final int PAGE_SIZE = 16 * 16 * PAGE_HEIGHT;

    private static final int BITS_PER_ENTRY = 4;
    private static final int ENTRIES_PER_WORD = Integer.SIZE / BITS_PER_ENTRY;
    static final int INDEX_WORDS_PER_PAGE = PAGE_SIZE / ENTRIES_PER_WORD;

    static final int PAGE_PALETTE_SIZE = 16;
    private static final int PAGE_DEFAULT_OFFSET = INDEX_WORDS_PER_PAGE;
    private static final int EXTRA_PALETTE_OFFSET = PAGE_DEFAULT_OFFSET + 1;
    private static final int EXTRA_PALETTE_SIZE = PAGE_PALETTE_SIZE - 1;

    private static final int PAGE_STRIDE = INDEX_WORDS_PER_PAGE + PAGE_PALETTE_SIZE;
    private static final int ARENA_INTS = PAGE_COUNT * PAGE_STRIDE;
    private static final int PALETTE_INDEX_MASK = PAGE_PALETTE_SIZE - 1;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final int AIR_RAW_ID = Block.BLOCK_STATE_REGISTRY.getId(AIR);

    private int uniformRawId = AIR_RAW_ID;
    private BlockState uniformState = AIR;

    private int[] arena;
    private int[] denseIds;
    private Int2IntOpenHashMap denseRawIdCounts;

    public ArenaBlockStatePalettedContainer() {
        super(Block.BLOCK_STATE_REGISTRY, AIR, Strategy.SECTION_STATES);
    }

    public boolean isDirty() {
        return this.uniformRawId != AIR_RAW_ID || this.arena != null || this.denseIds != null;
    }

    public void releaseRawIds() {
        this.uniformRawId = AIR_RAW_ID;
        this.uniformState = AIR;
        this.arena = null;
        this.denseIds = null;
        this.denseRawIdCounts = null;
    }

    public void importPackedPalette(BlockState[] paletteStates, long[] packedStorage) {
        StateImporter.importPackedPalette(this, paletteStates, packedStorage);
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
        int rawId = paletteIndex == 0
                ? arena[base + PAGE_DEFAULT_OFFSET]
                : arena[base + EXTRA_PALETTE_OFFSET + paletteIndex - 1] - 1;
        return Block.stateById(rawId);
    }

    @Override
    protected @NotNull BlockState get(int index) {
        return Block.stateById(this.rawIdAt(index));
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
        if (strategy != Strategy.SECTION_STATES) {
            return super.pack(idMap, strategy);
        }
        if (this.isUniform()) {
            return BlockStatePackedDataBuilder.packSingle(this.uniformState);
        }

        return BlockStatePackedDataBuilder.pack(this::rawIdAt);
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
        return this.rawIdForPaletteIndex(base, paletteIndex);
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
        return this.rawIdForPaletteIndex(base, paletteIndex);
    }

    public int arenaPaletteWord(int base, int wordIndex) {
        return this.arena[base + wordIndex];
    }

    public void forEachRawId(ArenaBlockStateQueries.RawIdConsumer consumer) {
        int[] dense = this.denseIds;
        if (dense != null) {
            for (int i = 0; i < SECTION_SIZE; ++i) {
                consumer.accept(i, UnsafeIntArrayAccess.get(dense, i));
            }
            return;
        }

        int[] arena = this.arena;
        if (arena == null) {
            for (int i = 0; i < SECTION_SIZE; ++i) {
                consumer.accept(i, this.uniformRawId);
            }
            return;
        }

        for (int page = 0; page < PAGE_COUNT; ++page) {
            int base = pageBase(page);
            for (int local = 0; local < PAGE_SIZE; ++local) {
                int paletteIndex = localPaletteIndex(arena, base, local);
                consumer.accept(sectionIndex(page, local), this.rawIdForPaletteIndex(base, paletteIndex));
            }
        }
    }

    public void countRawIds(Int2IntOpenHashMap counts) {
        int[] dense = this.denseIds;
        if (dense != null) {
            this.denseRawIdCounts().int2IntEntrySet().forEach(
                    entry -> counts.addTo(entry.getIntKey(), entry.getIntValue()));
            return;
        }

        int[] arena = this.arena;
        if (arena == null) {
            counts.addTo(this.uniformRawId, SECTION_SIZE);
            return;
        }

        int[] paletteCounts = new int[PAGE_PALETTE_SIZE];
        for (int page = 0; page < PAGE_COUNT; ++page) {
            Arrays.fill(paletteCounts, 0);
            int base = pageBase(page);
            for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
                int word = arena[base + wordIndex];
                ++paletteCounts[word & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 4) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 8) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 12) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 16) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 20) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 24) & PALETTE_INDEX_MASK];
                ++paletteCounts[(word >>> 28) & PALETTE_INDEX_MASK];
            }

            for (int paletteIndex = 0; paletteIndex < PAGE_PALETTE_SIZE; ++paletteIndex) {
                int count = paletteCounts[paletteIndex];
                if (count != 0) {
                    counts.addTo(this.rawIdForPaletteIndex(base, paletteIndex), count);
                }
            }
        }
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
        int oldRawId = this.rawIdForPaletteIndex(base, oldPaletteIndex);
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

    private void importData(PalettedContainer.Data<BlockState> data) {
        this.releaseRawIds();

        Palette<BlockState> palette = data.palette();
        BitStorage storage = data.storage();
        int bits = storage.getBits();
        if (bits == 0) {
            this.setUniformSection(rawId(palette.valueFor(0)));
            return;
        }

        int[] localToRaw = new int[1 << bits];
        int[] pageRawIds = new int[PAGE_PALETTE_SIZE + 1];
        RawIdSource source = index -> rawIdForLocal(palette, localToRaw, storage.get(index));
        for (int page = 0; page < PAGE_COUNT; ++page) {
            if (!this.importPage(page, source, pageRawIds)) {
                this.importPagesToDense(page, source);
                return;
            }
        }

        this.tryPromoteFullUniformSection();
    }

    private boolean importPage(int page, RawIdSource source, int[] pageRawIds) {
        int uniqueCount = 0;
        for (int local = 0; local < PAGE_SIZE; ++local) {
            int index = sectionIndex(page, local);
            uniqueCount = addUniqueRawId(pageRawIds, uniqueCount, source.rawId(index));
            if (uniqueCount > PAGE_PALETTE_SIZE) {
                this.promoteToDense();
                return false;
            }
        }

        int defaultRawId = pageRawIds[0];
        if (uniqueCount == 1 && this.arena == null && defaultRawId == this.uniformRawId) {
            return true;
        }

        int[] arena = this.ensureArena();
        int base = pageBase(page);
        clearPage(arena, base, defaultRawId);
        for (int i = 1; i < uniqueCount; ++i) {
            arena[base + EXTRA_PALETTE_OFFSET + i - 1] = pageRawIds[i] + 1;
        }

        for (int local = 0; local < PAGE_SIZE; ++local) {
            int index = sectionIndex(page, local);
            int rawId = source.rawId(index);
            if (rawId != defaultRawId) {
                setLocalPaletteIndex(arena, base, local, paletteIndexOf(pageRawIds, uniqueCount, rawId));
            }
        }
        return true;
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
                    dense[sectionIndex(page, local)] = this.rawIdForPaletteIndex(base, paletteIndex);
                }
            }
        }

        this.denseIds = dense;
        this.arena = null;
        this.denseRawIdCounts = null;
    }

    private void importPagesToDense(int firstPage, RawIdSource source) {
        int[] dense = this.denseIds;
        for (int page = firstPage; page < PAGE_COUNT; ++page) {
            for (int local = 0; local < PAGE_SIZE; ++local) {
                int index = sectionIndex(page, local);
                UnsafeIntArrayAccess.set(dense, index, source.rawId(index));
            }
        }
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

    private int rawIdForPaletteIndex(int base, int paletteIndex) {
        if (paletteIndex == 0) {
            return this.arena[base + PAGE_DEFAULT_OFFSET];
        }
        return this.arena[base + EXTRA_PALETTE_OFFSET + paletteIndex - 1] - 1;
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

    private static void clearPage(int[] arena, int base, int defaultRawId) {
        Arrays.fill(arena, base, base + PAGE_STRIDE, 0);
        arena[base + PAGE_DEFAULT_OFFSET] = defaultRawId;
    }

    private static int addUniqueRawId(int[] rawIds, int uniqueCount, int rawId) {
        for (int i = 0; i < uniqueCount; ++i) {
            if (rawIds[i] == rawId) {
                return uniqueCount;
            }
        }

        if (uniqueCount == rawIds.length) {
            return uniqueCount + 1;
        }

        rawIds[uniqueCount] = rawId;
        return uniqueCount + 1;
    }

    private static int pageLivePaletteMask(int[] arena, int base) {
        int seen = 0;
        for (int wordIndex = 0; wordIndex < INDEX_WORDS_PER_PAGE; ++wordIndex) {
            int word = arena[base + wordIndex];
            seen |= 1 << (word & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 4) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 8) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 12) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 16) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 20) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 24) & PALETTE_INDEX_MASK);
            seen |= 1 << ((word >>> 28) & PALETTE_INDEX_MASK);
        }
        return seen;
    }

    private static int localPaletteIndex(int[] arena, int base, int local) {
        int word = arena[base + (local >>> 3)];
        int shift = (local & 7) << 2;
        return (word >>> shift) & PALETTE_INDEX_MASK;
    }

    private static void setLocalPaletteIndex(int[] arena, int base, int local, int paletteIndex) {
        int wordIndex = base + (local >>> 3);
        int shift = (local & 7) << 2;
        int oldWord = arena[wordIndex];
        arena[wordIndex] = (oldWord & ~(PALETTE_INDEX_MASK << shift)) | ((paletteIndex & PALETTE_INDEX_MASK) << shift);
    }

    private static int paletteIndexOf(int[] paletteRawIds, int paletteSize, int rawId) {
        for (int i = 0; i < paletteSize; ++i) {
            if (paletteRawIds[i] == rawId) {
                return i;
            }
        }
        return 0;
    }

    public static int rawId(BlockState state) {
        int id = state == null ? AIR_RAW_ID : Block.BLOCK_STATE_REGISTRY.getId(state);
        return id < 0 ? AIR_RAW_ID : id;
    }

    private static int sanitizeRawId(int rawId) {
        return rawId < 0 ? AIR_RAW_ID : rawId;
    }

    private static int rawIdForLocal(Palette<BlockState> palette, int[] localToRaw, int localId) {
        int marker = localId < localToRaw.length ? localToRaw[localId] : 0;
        if (marker != 0) {
            return marker - 1;
        }

        int rawId = rawId(palette.valueFor(localId));
        if (localId < localToRaw.length) {
            localToRaw[localId] = rawId + 1;
        }
        return rawId;
    }

    private static int packedRawIdAt(int[] rawIds, long[] packedStorage, int sectionIndex) {
        int yz = sectionIndex >> 4;
        int x = sectionIndex & 15;
        int paletteIndex = (int) ((packedStorage[yz] >>> (x << 2)) & 15L);
        return rawIds[paletteIndex];
    }

    private static int vanillaSerializedBits(int paletteSize) {
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(BITS_PER_ENTRY, bits);
    }

    private static int packedLength(int bits) {
        int valuesPerLong = Long.SIZE / bits;
        return (SECTION_SIZE + valuesPerLong - 1) / valuesPerLong;
    }

    private static int pageIndexFromSectionIndex(int sectionIndex) {
        return sectionIndex >>> 10;
    }

    private static int pageLocalIndexFromSectionIndex(int sectionIndex) {
        int y = sectionIndex >>> 8;
        return ((y & 3) << 8) | (sectionIndex & 255);
    }

    private static int sectionIndex(int page, int local) {
        int y = (page << 2) | (local >>> 8);
        return (y << 8) | (local & 255);
    }

    private static int pageBase(int page) {
        return page * PAGE_STRIDE;
    }

    private static int localIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    @FunctionalInterface
    private interface RawIdSource {
        int rawId(int sectionIndex);
    }

    private static final class VanillaPackedRawIdSource implements RawIdSource {
        private final int[] paletteRawIds;
        private final long[] packedStorage;
        private final long mask;
        private final int bits;
        private final int valuesPerLong;
        private boolean valid = true;

        private VanillaPackedRawIdSource(int[] paletteRawIds, long[] packedStorage, int bits) {
            this.paletteRawIds = paletteRawIds;
            this.packedStorage = packedStorage;
            this.bits = bits;
            this.valuesPerLong = Long.SIZE / bits;
            this.mask = (1L << bits) - 1L;
        }

        @Override
        public int rawId(int sectionIndex) {
            int cell = sectionIndex / this.valuesPerLong;
            int shift = (sectionIndex - cell * this.valuesPerLong) * this.bits;
            int localId = (int) ((this.packedStorage[cell] >>> shift) & this.mask);
            if (localId >= this.paletteRawIds.length) {
                this.valid = false;
                return AIR_RAW_ID;
            }
            return sanitizeRawId(this.paletteRawIds[localId]);
        }

        private boolean isValid() {
            return this.valid;
        }
    }
}
