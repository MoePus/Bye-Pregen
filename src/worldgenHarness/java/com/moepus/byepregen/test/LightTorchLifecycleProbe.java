package com.moepus.byepregen.test;

import com.moepus.byepregen.chunksave.serialize.GcFreeChunkSerializer;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

final class LightTorchLifecycleProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CENTER_CHUNK_X = 16;
    private static final int CENTER_CHUNK_Z = 0;
    private static final int LOCAL_SOURCE_X = 8;
    private static final int LOCAL_SOURCE_Z = 8;
    private static final int SCENARIO_COUNT = 4;
    private static final long UNLOAD_TIMEOUT_NANOS = 30_000_000_000L;
    private static final ChunkPos CENTER = new ChunkPos(CENTER_CHUNK_X, CENTER_CHUNK_Z);

    private final ServerLevel level;
    private final LightTorchReloadProbe reloadProbe;
    private BlockPos source;
    private BlockPos secondTorch;
    private int y;
    private int scenario;
    private Phase phase = Phase.SETUP;
    private long unloadDeadline;
    private LightChunkSnapshot baseline;
    private LightChunkSnapshot recovered;
    private ReloadPurpose reloadPurpose = ReloadPurpose.NORMAL;
    private String checkpoint = "initial";
    private LightTorchNbtProbe lastNbt;
    private int reloadPlan;

    LightTorchLifecycleProbe(ServerLevel level) {
        this.level = level;
        this.reloadProbe = new LightTorchReloadProbe(level, CENTER);
    }

    boolean isComplete() {
        return this.phase == Phase.COMPLETE;
    }

    String pendingStage() {
        String stage = this.phase.name().toLowerCase(java.util.Locale.ROOT);
        return this.phase == Phase.RELOAD_STEP
                ? stage + " " + this.reloadProbe.waitingDescription()
                : stage;
    }

    CompletableFuture<Void> waitForLight() {
        return this.phase == Phase.RELOAD_STEP
                ? this.reloadProbe.waitForLight()
                : this.reloadProbe.waitForAllLight();
    }

    boolean advance() {
        return switch (this.phase) {
            case SETUP -> this.setup();
            case RESET_REMOVE -> this.removeSource();
            case RESET_PLACE -> this.placeSource();
            case STABLE_UNLOAD, IMMEDIATE_UNLOAD, SAVE_BEFORE_WAIT, STABLE_SAVE_UNLOAD -> this.startScenario();
            case WAIT_UNLOAD -> this.pollUnload();
            case RELOAD_STEP -> this.reloadProbe.loadNext();
            case CORRUPT -> this.corruptSourceSection();
            case RECOVERY_PLACE -> this.placeSecondTorch();
            case RECOVERY_REMOVE -> this.removeSecondTorch();
            case RECOVERY_SAVE -> this.saveRecovered();
            case COMPLETE -> false;
            default -> throw new IllegalStateException("Unexpected torch probe phase " + this.phase);
        };
    }

    void lightWaitCompleted() {
        switch (this.phase) {
            case SETUP -> {
                this.checkpoint = "after placement";
                this.baseline = this.captureAndAssert("placed");
                this.phase = Phase.RESET_REMOVE;
            }
            case RESET_REMOVE -> this.phase = Phase.RESET_PLACE;
            case STABLE_UNLOAD, STABLE_SAVE_UNLOAD -> {
                // The next tick performs the save/unload action after this light barrier.
            }
            case SAVE_BEFORE_WAIT -> {
                this.startUnload(this.scenario);
            }
            case RELOAD_STEP -> {
                this.checkpoint = "after reload";
                this.finishReloadStep();
            }
            case RECOVERY_PLACE -> this.phase = Phase.RECOVERY_REMOVE;
            case RECOVERY_REMOVE -> {
                this.recovered = this.captureAndAssert("recovery");
                this.phase = Phase.RECOVERY_SAVE;
            }
            default -> throw new IllegalStateException("Unexpected completed torch phase " + this.phase);
        }
    }

    private boolean setup() {
        this.y = Math.min(this.level.getHeight(Heightmap.Types.WORLD_SURFACE,
                CENTER.getMinBlockX() + LOCAL_SOURCE_X, CENTER.getMinBlockZ() + LOCAL_SOURCE_Z) + 8,
                this.level.getMaxBuildHeight() - 3);
        this.source = new BlockPos(CENTER.getMinBlockX() + LOCAL_SOURCE_X, this.y,
                CENTER.getMinBlockZ() + LOCAL_SOURCE_Z);
        this.secondTorch = this.source.east(4);
        this.reloadProbe.loadAll();
        this.clearFixture();
        this.put(this.source.below(), Blocks.STONE.defaultBlockState());
        this.put(this.source, Blocks.TORCH.defaultBlockState());
        this.phase = Phase.SETUP;
        return true;
    }

    private boolean removeSource() {
        this.put(this.source, Blocks.AIR.defaultBlockState());
        this.phase = Phase.RESET_REMOVE;
        return true;
    }

    private boolean placeSource() {
        this.put(this.source.below(), Blocks.STONE.defaultBlockState());
        this.put(this.source, Blocks.TORCH.defaultBlockState());
        this.phase = switch (this.scenario) {
            case 0 -> Phase.STABLE_UNLOAD;
            case 1 -> Phase.IMMEDIATE_UNLOAD;
            case 2 -> Phase.SAVE_BEFORE_WAIT;
            default -> Phase.STABLE_SAVE_UNLOAD;
        };
        if (this.phase == Phase.SAVE_BEFORE_WAIT) {
            this.save("saved immediately after placement", false);
        }
        if (this.phase == Phase.IMMEDIATE_UNLOAD) {
            return this.startScenario();
        }
        return this.scenario != 1;
    }

    private boolean startScenario() {
        if (this.phase == Phase.SAVE_BEFORE_WAIT) {
            return true;
        }
        if (this.phase == Phase.STABLE_SAVE_UNLOAD) {
            this.save("saved after stable light", true);
        } else {
            this.assertGcFreeEligible("before unload");
            this.captureNbt("pre-unload preview", this.phase != Phase.IMMEDIATE_UNLOAD);
        }
        this.startUnload(this.scenario);
        return false;
    }

    private boolean pollUnload() {
        if (!this.reloadProbe.isUnloaded()) {
            if (System.nanoTime() > this.unloadDeadline) {
                throw this.failure("unload timeout");
            }
            return false;
        }
        this.checkpoint = "after unload";
        if (this.reloadProbe.centerLoaded() && this.reloadPurpose == ReloadPurpose.NORMAL) {
            this.verifyAtLeast(this.baseline, "neighbors unloaded, center retained");
        }
        this.reloadProbe.beginReload(this.reloadPlan);
        this.phase = Phase.RELOAD_STEP;
        return this.reloadProbe.loadNext();
    }

    private boolean corruptSourceSection() {
        ChunkAccess chunk = this.level.getChunk(CENTER.x, CENTER.z);
        if (!(chunk instanceof YAChunkLightAccess access)) {
            throw this.failure("YA light data is unavailable");
        }
        YAChunkLightData data = access.byepregen$blockLightData();
        if (data == null || !data.lightEnabled()) {
            throw this.failure("YA block light data is missing or disabled");
        }
        int sectionY = this.source.getY() >> 4;
        YANibbleArray nibble = data.getOrCreateUpdatingSection(sectionY);
        for (int index = 0; index < 4096; ++index) {
            nibble.setUpdating(index, 0);
        }
        int sourceIndex = (this.source.getY() & 15) << 8
                | (this.source.getZ() & 15) << 4
                | this.source.getX() & 15;
        nibble.setUpdating(sourceIndex, 14);
        nibble.publish();
        chunk.setUnsaved(true);
        this.save("saved source-only corruption", false);
        this.scenario = SCENARIO_COUNT;
        this.reloadPurpose = ReloadPurpose.CORRUPTION;
        this.startUnload(3);
        return false;
    }

    private boolean placeSecondTorch() {
        this.put(this.secondTorch.below(), Blocks.STONE.defaultBlockState());
        this.put(this.secondTorch, Blocks.TORCH.defaultBlockState());
        this.phase = Phase.RECOVERY_PLACE;
        return true;
    }

    private boolean removeSecondTorch() {
        this.put(this.secondTorch, Blocks.AIR.defaultBlockState());
        this.phase = Phase.RECOVERY_REMOVE;
        return true;
    }

    private boolean saveRecovered() {
        this.save("saved recovered light", true);
        this.reloadPurpose = ReloadPurpose.RECOVERED;
        this.startUnload(3);
        return false;
    }

    private LightChunkSnapshot captureAndAssert(String stage) {
        int sourceLight = this.light(this.source);
        int adjacent = this.light(this.source.east());
        int distant = this.light(this.source.east(2));
        if (sourceLight != 14 || adjacent != 13 || distant != 12) {
            throw this.failure(stage + " torch source=" + sourceLight + " adjacent=" + adjacent
                    + " distant=" + distant);
        }
        return LightChunkSnapshot.captureBlockSection(this.level, CENTER, this.source.getY() >> 4);
    }

    private void verifyAtLeast(LightChunkSnapshot expected, String stage) {
        LightChunkSnapshot actual = LightChunkSnapshot.capture(this.level, CENTER);
        try {
            expected.verifyNoBlockLightLoss(actual, CENTER);
        } catch (IllegalStateException failure) {
            throw this.failure(stage + " " + failure.getMessage());
        }
        int sourceLight = this.light(this.source);
        int adjacent = this.light(this.source.east());
        int distant = this.light(this.source.east(2));
        if (sourceLight != 14 || adjacent < 13 || distant < 12) {
            throw this.failure(stage + " source=" + sourceLight
                    + " adjacent=" + adjacent + " distant=" + distant);
        }
    }

    private void finishReloadStep() {
        if (this.reloadProbe.centerLoaded()) {
            this.checkpoint = "after partial-halo load " + this.reloadProbe.waitingDescription();
            if (this.reloadPurpose == ReloadPurpose.CORRUPTION) {
                this.assertSourceOnly();
            } else {
                LightChunkSnapshot expected = this.reloadPurpose == ReloadPurpose.RECOVERED
                        ? this.recovered : this.baseline;
                this.verifyAtLeast(expected, this.checkpoint);
            }
        }
        if (!this.reloadProbe.hasMore()) {
            this.finishReload();
        }
    }

    private void finishReload() {
        if (this.reloadPurpose == ReloadPurpose.CORRUPTION) {
            this.assertSourceOnly();
            LOGGER.info("Torch source-only saved light retained after reload: chunk={} source={} adjacent={}",
                    CENTER, this.light(this.source), this.light(this.source.east()));
            this.phase = Phase.RECOVERY_PLACE;
            return;
        }
        if (this.reloadPurpose == ReloadPurpose.RECOVERED) {
            this.verifyAtLeast(this.recovered, "recovery reloaded");
            this.phase = Phase.COMPLETE;
            return;
        }
        this.verifyAtLeast(this.baseline, "reloaded");
        ++this.scenario;
        this.phase = this.scenario < SCENARIO_COUNT ? Phase.RESET_REMOVE : Phase.CORRUPT;
    }

    private void assertSourceOnly() {
        int sourceLight = this.light(this.source);
        int adjacent = this.light(this.source.east());
        if (sourceLight != 14 || adjacent != 0) {
            throw this.failure("source-only data changed during partial-halo load: source="
                    + sourceLight + " adjacent=" + adjacent);
        }
    }

    private void save(String stage, boolean expectNormal) {
        this.assertGcFreeEligible(stage);
        this.captureNbt(stage, expectNormal);
        this.level.getChunkSource().save(true);
        this.checkpoint = "after save: " + stage;
    }

    private void captureNbt(String stage, boolean expectNormal) {
        ChunkAccess chunk = this.level.getChunk(CENTER.x, CENTER.z);
        this.lastNbt = LightTorchNbtProbe.capture(this.level, chunk, this.source);
        LOGGER.info("Torch GC-free NBT preview: stage={} values={}", stage, this.lastNbt);
        if (expectNormal && !this.lastNbt.hasExpectedTorch()) {
            throw this.failure(stage + ": GC-free NBT preview lost propagated BlockLight");
        }
    }

    private void startUnload(int plan) {
        this.reloadPlan = plan;
        this.reloadProbe.beginUnload(plan);
        this.phase = Phase.WAIT_UNLOAD;
        this.unloadDeadline = System.nanoTime() + UNLOAD_TIMEOUT_NANOS;
    }

    private void assertGcFreeEligible(String stage) {
        ChunkAccess chunk = this.level.getChunk(CENTER.x, CENTER.z);
        if (!GcFreeChunkSerializer.shouldUseGcFree(chunk)) {
            throw this.failure(stage + ": center chunk is not eligible for GC-free serialization");
        }
    }

    private void clearFixture() {
        for (int y = this.y - 2; y <= this.y + 2; ++y) {
            for (int z = this.source.getZ() - 4; z <= this.source.getZ() + 4; ++z) {
                for (int x = this.source.getX() - 4; x <= this.source.getX() + 4; ++x) {
                    this.put(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private int light(BlockPos pos) {
        return this.level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(pos);
    }

    private void put(BlockPos pos, BlockState state) {
        this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private IllegalStateException failure(String detail) {
        return new IllegalStateException("torch lifecycle stage=" + this.phase
                + " checkpoint=" + this.checkpoint + " chunk=" + CENTER
                + " source=" + this.source + " " + detail
                + " nbtPreview=" + this.lastNbt + " diagnostics=" + this.diagnostics());
    }

    private String diagnostics() {
        ChunkAccess chunk = this.level.getChunkAt(this.source);
        String base = "{sourceLight=" + this.light(this.source) + ",adjacent=" + this.light(this.source.east())
                + ",sectionNonZero=" + this.sectionNonZero() + ",lightCorrect=" + chunk.isLightCorrect();
        if (!(chunk instanceof YAChunkLightAccess access)) {
            return base + ",blockData=false}";
        }
        YAChunkLightData data = access.byepregen$blockLightData();
        return base + ",blockData=" + (data != null) + ",blockEnabled="
                + (data != null && data.lightEnabled()) + "}";
    }

    private int sectionNonZero() {
        int count = 0;
        int minY = this.source.getY() & ~15;
        for (int y = minY; y < minY + 16; ++y) {
            for (int z = CENTER.getMinBlockZ(); z <= CENTER.getMaxBlockZ(); ++z) {
                for (int x = CENTER.getMinBlockX(); x <= CENTER.getMaxBlockX(); ++x) {
                    count += this.light(new BlockPos(x, y, z)) == 0 ? 0 : 1;
                }
            }
        }
        return count;
    }

    private enum Phase {
        SETUP,
        RESET_REMOVE,
        RESET_PLACE,
        STABLE_UNLOAD,
        IMMEDIATE_UNLOAD,
        SAVE_BEFORE_WAIT,
        STABLE_SAVE_UNLOAD,
        WAIT_UNLOAD,
        RELOAD_STEP,
        CORRUPT,
        RECOVERY_PLACE,
        RECOVERY_REMOVE,
        RECOVERY_SAVE,
        COMPLETE
    }

    private enum ReloadPurpose {
        NORMAL,
        CORRUPTION,
        RECOVERED
    }
}
