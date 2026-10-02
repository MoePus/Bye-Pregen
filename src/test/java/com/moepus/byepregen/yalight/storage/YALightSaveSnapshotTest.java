package com.moepus.byepregen.yalight.storage;

import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YALightSaveSnapshotTest {
    private static final int MIN_SECTION = -5;
    private static final int TEST_SECTION = 4;
    private final Fixture fixture = new Fixture();

    @Test
    void validSnapshotReusesBytesAndSurvivesLaterCowPublication() {
        YANibbleArray nibble = this.fixture.block.getVisibleSection(TEST_SECTION);
        YALightSaveSnapshot snapshot = this.capture();
        byte[] original = snapshot.section(LightLayer.BLOCK, TEST_SECTION).data();
        assertSame(nibble.visibleDataForSave(), original);
        this.fixture.state.begin();
        nibble.setUpdating(0, 2);
        nibble.publish();
        this.fixture.state.finish(true);
        assertEquals(9, snapshot.vanillaLayer(LightLayer.BLOCK, TEST_SECTION).get(0, 0, 0));
        assertEquals(2, this.capture().vanillaLayer(LightLayer.BLOCK, TEST_SECTION).get(0, 0, 0));
        assertNotSame(original, nibble.visibleDataForSave());
    }

    @Test
    void pendingSnapshotNeverUpgradesAfterCompletion() {
        this.fixture.state.begin();
        YALightSaveSnapshot snapshot = this.capture();
        this.fixture.state.finish(true);
        assertFalse(snapshot.valid());
        assertNull(snapshot.section(LightLayer.BLOCK, TEST_SECTION));
        assertNull(snapshot.section(LightLayer.SKY, TEST_SECTION));
        assertTrue(this.capture().valid());
    }

    @Test
    void rejectsACompleteUpdateDuringCaptureEvenWhenPendingReturnsToZero() {
        this.fixture.onSkyRead = () -> {
            this.fixture.state.begin();
            this.fixture.state.finish(true);
        };
        assertFalse(this.capture().valid());
    }

    @Test
    void haloPublicationRemainsPendingBetweenLayers() {
        int index = TEST_SECTION - MIN_SECTION;
        this.fixture.block.markDirty(index);
        this.fixture.block.publishDirty(NO_CALLBACKS, LightLayer.BLOCK);
        assertFalse(this.capture().valid());
        this.fixture.sky.markDirty(index);
        this.fixture.sky.publishDirty(NO_CALLBACKS, LightLayer.SKY);
        this.fixture.block.finishPublication(true);
        assertFalse(this.capture().valid());
        this.fixture.sky.finishPublication(true);
        assertTrue(this.capture().valid());
    }

    @Test
    void invalidChunkAndFailedWorkOmitBothLayers() {
        assertFalse(YALightSaveSnapshot.capture(this.fixture, false, MIN_SECTION).valid());
        this.fixture.state.begin();
        this.fixture.state.finish(false);
        assertSame(YALightSaveSnapshot.UNLIT, this.capture());
    }

    @Test
    void clearingLayerDoesNotCompleteAnOutstandingChunkTask() {
        this.fixture.state.begin();
        this.fixture.block.markDirty(TEST_SECTION - MIN_SECTION);
        this.fixture.block.clear();
        assertFalse(this.capture().valid());
        this.fixture.block.finishPublication(true);
        this.fixture.state.finish(true);
        assertTrue(this.capture().valid());
        assertNull(this.capture().section(LightLayer.BLOCK, TEST_SECTION));
    }

    @Test
    void failedHaloPublicationRemainsUntrustedAfterOtherLayerCompletes() {
        int index = TEST_SECTION - MIN_SECTION;
        this.fixture.block.markDirty(index);
        this.fixture.sky.markDirty(index);
        this.fixture.block.finishPublication(false);
        this.fixture.sky.finishPublication(true);
        assertSame(YALightSaveSnapshot.UNLIT, this.capture());
    }

    @Test
    void preservesFullZeroAndMissingSectionSemantics() {
        this.fixture.sky.loadInitialSection(5, YANibbleArray.fullArray());
        this.fixture.sky.loadInitialSection(6, new YANibbleArray());
        this.fixture.sky.finishInitialLoad();
        YALightSaveSnapshot snapshot = this.capture();
        assertEquals(15, snapshot.vanillaLayer(LightLayer.SKY, 5).get(1, 1, 1));
        assertTrue(snapshot.vanillaLayer(LightLayer.SKY, 6).isEmpty());
        assertNull(snapshot.vanillaLayer(LightLayer.SKY, 7));
        assertEquals(YANibbleArray.SAVE_FULL, snapshot.section(LightLayer.SKY, 5).kind());
        assertNull(snapshot.section(LightLayer.SKY, 5).data());
    }

    private YALightSaveSnapshot capture() {
        return YALightSaveSnapshot.capture(this.fixture, true, MIN_SECTION);
    }

    private static final LightChunkGetter NO_CALLBACKS = new LightChunkGetter() {
        public BlockGetter getLevel() { return null; }
        public LightChunk getChunkForLighting(int x, int z) { return null; }
        public void onLightUpdate(LightLayer layer, SectionPos pos) {}
    };

    private static final class Fixture implements YAChunkLightAccess {
        private final YALightSaveState state = new YALightSaveState();
        private final LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        private final YAChunkLightData block = new YAChunkLightData(new ChunkPos(0, 0), this.height, this.state);
        private final YAChunkLightData sky = new YAChunkLightData(new ChunkPos(0, 0), this.height, this.state);
        private Runnable onSkyRead = () -> {};

        private Fixture() {
            byte[] bytes = new byte[YANibbleArray.SIZE];
            bytes[0] = 9;
            this.block.loadInitialSection(TEST_SECTION, YANibbleArray.fromOwnedBytes(bytes));
            this.block.finishInitialLoad();
        }

        public YAChunkLightData byepregen$yaLightData(LightLayer layer, boolean create) {
            return layer == LightLayer.BLOCK ? this.block : this.sky;
        }
        public YAChunkLightData byepregen$blockLightData() { return this.block; }
        public YAChunkLightData byepregen$skyLightData() { this.onSkyRead.run(); return this.sky; }
        public YALightSaveState byepregen$yaLightSaveState() { return this.state; }
        public void byepregen$setYALightData(LightLayer layer, YAChunkLightData data) { throw new UnsupportedOperationException(); }
        public void byepregen$setYALightSaveState(YALightSaveState state) { throw new UnsupportedOperationException(); }
    }
}
