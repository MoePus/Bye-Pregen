package com.moepus.byepregen.palette.arena;

import static com.moepus.byepregen.palette.arena.Layout.SECTION_SIZE;
import static com.moepus.byepregen.palette.arena.Layout.localIndex;

import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import com.moepus.byepregen.palette.arena.codec.NetworkWriter;
import com.moepus.byepregen.palette.arena.codec.StateImporter;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.jetbrains.annotations.NotNull;

/** Worldgen-only block-state container backed by raw global block-state ids. */
public final class ArenaBlockStatePalettedContainer extends PalettedContainer<BlockState>
        implements BlockStateRawIdAccess {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Strategy<BlockState> BLOCK_STATE_STRATEGY =
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
    public static final int AIR_RAW_ID = Block.BLOCK_STATE_REGISTRY.getId(AIR);

    private final ArenaBlockStateStorage storage = new ArenaBlockStateStorage(AIR_RAW_ID, AIR);

    public ArenaBlockStatePalettedContainer() {
        super(AIR, BLOCK_STATE_STRATEGY);
    }

    public void releaseRawIds() {
        this.storage.releaseRawIds();
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
        return this.storage.stateAt(x, y, z);
    }

    @Override
    protected @NotNull BlockState get(int index) {
        return this.storage.stateAt(index);
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
    public PalettedContainerRO.@NotNull PackedData<BlockState> pack(@NotNull Strategy<BlockState> strategy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull PalettedContainer<BlockState> copy() {
        ArenaBlockStatePalettedContainer copy = new ArenaBlockStatePalettedContainer();
        copy.storage.copyFrom(this.storage);
        return copy;
    }

    public PalettedContainerRO<BlockState> sodium$copy() {
        return this.copy();
    }

    public void sodium$unpack(Object[] values) {
        this.storage.unpack(values);
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
        return this.storage.rawIdAt(index);
    }

    @Override
    public int getRawId(int x, int y, int z) {
        return this.rawIdAt(localIndex(x, y, z));
    }

    public boolean isUniform() {
        return this.storage.isUniform();
    }

    public boolean isFreshAirForWorldgen() {
        return this.storage.isFreshAirForWorldgen(AIR_RAW_ID, AIR);
    }

    public int uniformRawId() {
        return this.storage.uniformRawId();
    }

    public boolean hasPagePalettes() {
        return this.storage.hasPagePalettes();
    }

    public boolean hasDenseIds() {
        return this.storage.hasDenseIds();
    }

    public Int2IntOpenHashMap denseRawIdCounts() {
        return this.storage.denseRawIdCounts();
    }

    public int arenaPageBase(int page) {
        return this.storage.arenaPageBase(page);
    }

    public int arenaLivePaletteMask(int base) {
        return this.storage.arenaLivePaletteMask(base);
    }

    public int arenaPaletteRawId(int base, int paletteIndex) {
        return this.storage.arenaPaletteRawId(base, paletteIndex);
    }

    public int arenaPaletteWord(int base, int wordIndex) {
        return this.storage.arenaPaletteWord(base, wordIndex);
    }

    public void forEachRawId(ArenaBlockStateQueries.RawIdConsumer consumer) {
        ArenaBlockStateQueries.forEachRawId(this, consumer);
    }

    public void batchWriteRawId(int page, int pageLocalIndex, int rawId) {
        this.storage.batchWriteRawId(page, pageLocalIndex, rawId, AIR_RAW_ID);
    }

    public void setRawId(int index, int rawId) {
        this.storage.setRawId(index, rawId < 0 ? AIR_RAW_ID : rawId);
    }

    public void c2me$setUnsafe(int x, int y, int z, Object state) {
        this.setRawId(localIndex(x, y, z), rawId((BlockState) state));
    }

    public int[] ensureArena() {
        return this.storage.ensureArena();
    }

    public void promoteToDense() {
        this.storage.promoteToDense();
    }

    public void tryPromoteFullUniformSection() {
        this.storage.tryPromoteFullUniformSection();
    }

    public void setUniformSection(int rawId) {
        this.storage.setUniformSection(rawId);
    }

    public boolean isUniformRawId(int rawId) {
        return this.storage.isUniformRawId(rawId);
    }

    public static int rawId(BlockState state) {
        int id = state == null ? AIR_RAW_ID : Block.BLOCK_STATE_REGISTRY.getId(state);
        return id < 0 ? AIR_RAW_ID : id;
    }
}
