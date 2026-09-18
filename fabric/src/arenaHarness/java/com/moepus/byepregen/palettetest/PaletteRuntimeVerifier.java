package com.moepus.byepregen.palettetest;

import com.moepus.byepregen.arenatest.HarnessResult;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.palette.access.BlockStateRawIdAccess;
import com.moepus.byepregen.palette.access.PaletteRawIdAccess;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.IdMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

public final class PaletteRuntimeVerifier {
    private static final int SECTION_WIDTH = 16;
    private static final int SECTION_SIZE = SECTION_WIDTH * SECTION_WIDTH * SECTION_WIDTH;
    private static final int[] PALETTE_SIZES = {1, 4, 17, 257};

    private PaletteRuntimeVerifier() { }

    public static void run(MinecraftServer server) {
        HarnessResult.run(server, "byepregen.paletteHarness.result", PaletteRuntimeVerifier::verify);
    }

    public static int verify() throws Exception {
        int values = 0;
        for (int size : PALETTE_SIZES) values += verifySize(size);
        values += verifyCustomPalette();
        verifyIndependentDetectors();
        return values;
    }

    private static int verifySize(int size) {
        var palette = container();
        for (int index = 0; index < SECTION_SIZE; index++) {
            palette.set(index & 15, index >> 8, index >> 4 & 15,
                    Block.BLOCK_STATE_REGISTRY.byId(index % size));
        }
        PalettedContainer.Data<BlockState> data = data(palette);
        if (!(data.palette() instanceof PaletteRawIdAccess)) {
            throw new AssertionError("Native palette bridge missing: " + data.palette().getClass());
        }
        assertAllIds(palette, false);
        var copy = palette.copy();
        assertAllIds(copy, false);
        return 2 * SECTION_SIZE;
    }

    private static int verifyCustomPalette() {
        var palette = container();
        palette.set(1, 0, 0, Blocks.STONE.defaultBlockState());
        var data = data(palette);
        var custom = new CustomPalette();
        setData(palette, new PalettedContainer.Data<>(data.configuration(), data.storage(), custom));
        assertAllIds(palette, true);
        if (custom.reads != 2 * SECTION_SIZE) throw new AssertionError("Custom palette mapping bypassed");
        return SECTION_SIZE;
    }

    private static void assertAllIds(PalettedContainer<BlockState> palette, boolean custom) {
        var raw = (BlockStateRawIdAccess) palette;
        for (int index = 0; index < SECTION_SIZE; index++) {
            int x = index & 15, y = index >> 8, z = index >> 4 & 15;
            int expected = Block.BLOCK_STATE_REGISTRY.getId(palette.get(x, y, z));
            if (raw.getRawId(x, y, z) != expected) {
                throw new AssertionError("Raw ID mismatch at " + index + ", custom=" + custom);
            }
        }
    }

    private static void verifyIndependentDetectors() throws Exception {
        var first = container();
        var second = container();
        Field detector = PalettedContainer.class.getDeclaredField("threadingDetector");
        detector.setAccessible(true);
        // The port keeps vanilla per-container locking, so the native layout must not share detectors.
        // Lithium's chunk.no_locking module owns this instead and installs one shared detector, so the
        // identity check only applies when it is absent; the independence check below always applies.
        if (!ModEnvironment.isModLoaded("lithium") && detector.get(first) == detector.get(second)) {
            throw new AssertionError("Shared container detector");
        }
        first.acquire();
        try {
            CompletableFuture.runAsync(() -> second.set(0, 0, 0, Blocks.STONE.defaultBlockState()))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            first.release();
        }
        first.set(0, 0, 0, Blocks.DIRT.defaultBlockState());
        if (first.get(0, 0, 0) != Blocks.DIRT.defaultBlockState()
                || second.get(0, 0, 0) != Blocks.STONE.defaultBlockState()) {
            throw new AssertionError("Independent container writes failed");
        }
    }

    @SuppressWarnings("unchecked")
    private static PalettedContainer.Data<BlockState> data(PalettedContainer<BlockState> container) {
        try {
            Field field = PalettedContainer.class.getDeclaredField("data");
            field.setAccessible(true);
            return (PalettedContainer.Data<BlockState>) field.get(container);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect palette data", exception);
        }
    }

    private static void setData(PalettedContainer<BlockState> container,
                                PalettedContainer.Data<BlockState> data) {
        try {
            Field field = PalettedContainer.class.getDeclaredField("data");
            field.setAccessible(true);
            field.set(container, data);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot replace palette data", exception);
        }
    }

    private static PalettedContainer<BlockState> container() {
        return new PalettedContainer<>(Blocks.AIR.defaultBlockState(),
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
    }

    private static final class CustomPalette extends HashMapPalette<BlockState> implements PaletteRawIdAccess {
        private int reads;

        private CustomPalette() {
            super(4, List.of(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState()));
        }

        @Override public BlockState valueFor(int id) {
            reads++;
            return id == 0 ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState();
        }

        @Override public int byepregen$rawIdForLocalId(int id, IdMap<?> map) {
            throw new AssertionError("Custom palette inherited shortcut used");
        }
    }
}
