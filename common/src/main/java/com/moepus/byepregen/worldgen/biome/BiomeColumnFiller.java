package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import com.moepus.byepregen.mixin.accessor.arena.LevelChunkSectionAccessor;
import com.moepus.byepregen.mixin.accessor.worldgen.biome.MultiNoiseBiomeSourceAccessor;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Fills biome palettes in quart-column order using reusable compiled climate columns. */
public final class BiomeColumnFiller {
    private static final int QUARTS_PER_SECTION = 4;
    private static final int QUARTS_PER_CHUNK_AXIS = 4;
    private static final int QUART_COLUMNS_PER_FILL =
            QUARTS_PER_CHUNK_AXIS * QUARTS_PER_CHUNK_AXIS;
    private static final int BLOCKS_PER_QUART = 4;
    private static final int EVALUATED_COLUMNS_PER_FILL =
            BiomeColumnEvaluator.Root.values().length
                    * QUART_COLUMNS_PER_FILL;
    private static final boolean VERIFY_BIOMES = Boolean.getBoolean("byepregen.verifyDfcColumn");

    private BiomeColumnFiller() {
    }

    public static boolean fill(Options options) {
        DensityColumnMetrics.recordBiomeFillAttempt();
        FillState state = FillState.create(options);
        if (state == null) return false;
        if (state.fill()) return true;
        DensityColumnMetrics.recordBiomeEvaluationFallback();
        return false;
    }

    public record Options(
            ChunkAccess chunk,
            BiomeResolver resolver,
            BiomeSource biomeSource,
            Climate.Sampler sampler,
            BiomeColumnEvaluator evaluator
    ) {
        public Options {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(resolver, "resolver");
            Objects.requireNonNull(biomeSource, "biomeSource");
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(evaluator, "evaluator");
        }
    }

    private static final class FillState {
        private final Options options;
        private final LevelChunkSection[] sections;
        private final PalettedContainer<Holder<Biome>>[] biomeContainers;
        private final Holder<Biome>[] biomeValues;
        private final ColumnSampler columnSampler;
        private final FastClimateParameterList<Holder<Biome>> biomeLookup;
        private final DepthClimateParameterList<Holder<Biome>> depthBiomeLookup;
        private final int minQuartX;
        private final int minQuartY;
        private final int minQuartZ;

        private FillState(
                Options options,
                LevelChunkSection[] sections,
                FastClimateParameterList<Holder<Biome>> biomeLookup
        ) {
            this.options = options;
            this.sections = sections;
            this.biomeLookup = biomeLookup;
            this.depthBiomeLookup = depthLookup(
                    biomeLookup, options.evaluator().byepregen$hasDepthOnlyClimate());
            this.biomeContainers = recreateBiomeContainers(sections);
            this.biomeValues = createBiomeValues(sections.length);
            this.minQuartX = QuartPos.fromBlock(options.chunk().getPos().getMinBlockX());
            this.minQuartZ = QuartPos.fromBlock(options.chunk().getPos().getMinBlockZ());
            LevelHeightAccessor height = options.chunk().getHeightAccessorForGeneration();
            this.minQuartY = QuartPos.fromBlock(height.getMinY());
            this.columnSampler = new ColumnSampler(options, this.minQuartY, sections.length);
        }

        private static FillState create(Options options) {
            LevelChunkSection[] sections = targetSections(options.chunk());
            if (sections.length == 0 || !hasArenaTargets(sections)) {
                DensityColumnMetrics.recordBiomeArenaFallback();
                return null;
            }
            FastClimateParameterList<Holder<Biome>> biomeLookup = directBiomeLookup(options);
            if (biomeLookup == null) {
                DensityColumnMetrics.recordBiomeSourceFallback();
                return null;
            }
            if (!options.evaluator().byepregen$prepareBiomeColumns()) {
                DensityColumnMetrics.recordBiomeBindFallback();
                return null;
            }
            return new FillState(options, sections, biomeLookup);
        }

        private boolean fill() {
            for (int x = 0; x < QUARTS_PER_CHUNK_AXIS; ++x) {
                int quartX = this.minQuartX + x;
                for (int z = 0; z < QUARTS_PER_CHUNK_AXIS; ++z) {
                    int quartZ = this.minQuartZ + z;
                    if (!this.fillColumn(x, z, quartX, quartZ)) return false;
                }
            }
            this.writeBiomeContainers();
            this.publish();
            this.verifyPublishedBiomes();
            DensityColumnMetrics.recordBiomeFill(
                    EVALUATED_COLUMNS_PER_FILL,
                    this.depthBiomeLookup == null ? 0 : QUART_COLUMNS_PER_FILL);
            return true;
        }

        private boolean fillColumn(int localX, int localZ, int quartX, int quartZ) {
            if (!this.columnSampler.evaluate(quartX, quartZ)) return false;
            if (this.depthBiomeLookup != null) {
                return this.fillDepthColumn(localX, localZ);
            }
            int sampleCount = this.sections.length * QUARTS_PER_SECTION;
            for (int sample = 0; sample < sampleCount; ++sample) {
                Holder<Biome> biome = this.biomeLookup.byepregen$findValue(
                        this.columnSampler.target(sample));
                this.biomeValues[biomeIndex(sample, localX, localZ)] = biome;
            }
            return true;
        }

        private boolean fillDepthColumn(int localX, int localZ) {
            this.depthBiomeLookup.byepregen$beginDepthColumn(this.columnSampler.target(0));
            int sampleCount = this.sections.length * QUARTS_PER_SECTION;
            for (int sample = 0; sample < sampleCount; ++sample) {
                Holder<Biome> biome = this.depthBiomeLookup.byepregen$findValueAtDepth(
                        this.columnSampler.depth(sample));
                this.biomeValues[biomeIndex(sample, localX, localZ)] = biome;
            }
            return true;
        }

        private void writeBiomeContainers() {
            for (int section = 0; section < this.sections.length; ++section) {
                PalettedContainer<Holder<Biome>> container = this.biomeContainers[section];
                for (int x = 0; x < QUARTS_PER_SECTION; ++x) {
                    this.writeBiomeX(container, section, x);
                }
            }
        }

        private void writeBiomeX(
                PalettedContainer<Holder<Biome>> container,
                int section,
                int x
        ) {
            for (int y = 0; y < QUARTS_PER_SECTION; ++y) {
                int sample = section * QUARTS_PER_SECTION + y;
                for (int z = 0; z < QUARTS_PER_SECTION; ++z) {
                    container.getAndSetUnchecked(
                            x, y, z, this.biomeValues[biomeIndex(sample, x, z)]);
                }
            }
        }

        private void verifyPublishedBiomes() {
            if (!VERIFY_BIOMES) return;
            int sampleCount = this.sections.length * QUARTS_PER_SECTION;
            for (int x = 0; x < QUARTS_PER_CHUNK_AXIS; ++x) {
                for (int z = 0; z < QUARTS_PER_CHUNK_AXIS; ++z) {
                    for (int sample = 0; sample < sampleCount; ++sample) {
                        this.verifyPublishedBiome(x, z, sample);
                    }
                }
            }
            DensityColumnMetrics.recordBiomeVerifiedCells(
                    (long) sampleCount * QUARTS_PER_CHUNK_AXIS * QUARTS_PER_CHUNK_AXIS);
        }

        private void verifyPublishedBiome(int localX, int localZ, int sample) {
            int quartX = this.minQuartX + localX;
            int quartY = this.minQuartY + sample;
            int quartZ = this.minQuartZ + localZ;
            Holder<Biome> actual = this.sections[sample / QUARTS_PER_SECTION]
                    .getNoiseBiome(localX, sample % QUARTS_PER_SECTION, localZ);
            Holder<Biome> expected = this.options.resolver().getNoiseBiome(
                    quartX, quartY, quartZ, this.options.sampler());
            if (actual != expected) {
                throw new IllegalStateException("Biome palette mismatch at quart "
                        + quartX + ',' + quartY + ',' + quartZ
                        + ": expected=" + expected + ", actual=" + actual);
            }
        }

        private void publish() {
            for (int i = 0; i < this.sections.length; ++i) {
                ((LevelChunkSectionAccessor) this.sections[i])
                        .byepregen$setBiomes(this.biomeContainers[i]);
            }
        }

        private static LevelChunkSection[] targetSections(ChunkAccess chunk) {
            LevelHeightAccessor height = chunk.getHeightAccessorForGeneration();
            int minSection = height.getMinSectionY();
            int count = height.getSectionsCount();
            LevelChunkSection[] sections = new LevelChunkSection[count];
            for (int i = 0; i < count; ++i) {
                sections[i] = chunk.getSection(chunk.getSectionIndexFromSectionY(minSection + i));
            }
            return sections;
        }

        private static boolean hasArenaTargets(LevelChunkSection[] sections) {
            for (LevelChunkSection section : sections) {
                if (!(section.getStates() instanceof ArenaBlockStatePalettedContainer)) return false;
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        private static FastClimateParameterList<Holder<Biome>> directBiomeLookup(Options options) {
            if (options.resolver() != options.biomeSource()
                    || !(options.biomeSource() instanceof MultiNoiseBiomeSource source)) {
                return null;
            }
            Climate.ParameterList<Holder<Biome>> parameters =
                    ((MultiNoiseBiomeSourceAccessor) source).byepregen$parameters();
            if (!((Object) parameters instanceof FastClimateParameterList<?> lookup)) return null;
            return (FastClimateParameterList<Holder<Biome>>) lookup;
        }

        private static int biomeIndex(int sample, int localX, int localZ) {
            return sample * QUARTS_PER_CHUNK_AXIS * QUARTS_PER_CHUNK_AXIS
                    + localZ * QUARTS_PER_CHUNK_AXIS + localX;
        }

        @SuppressWarnings("unchecked")
        private static DepthClimateParameterList<Holder<Biome>> depthLookup(
                FastClimateParameterList<Holder<Biome>> lookup,
                boolean enabled
        ) {
            if (!enabled || !(lookup instanceof DepthClimateParameterList<?> depthLookup)) {
                return null;
            }
            return (DepthClimateParameterList<Holder<Biome>>) depthLookup;
        }

        @SuppressWarnings("unchecked")
        private static Holder<Biome>[] createBiomeValues(int sectionCount) {
            int samples = sectionCount * QUARTS_PER_SECTION;
            return (Holder<Biome>[]) new Holder<?>[
                    samples * QUARTS_PER_CHUNK_AXIS * QUARTS_PER_CHUNK_AXIS
            ];
        }

        @SuppressWarnings("unchecked")
        private static PalettedContainer<Holder<Biome>>[] recreateBiomeContainers(
                LevelChunkSection[] sections
        ) {
            PalettedContainer<Holder<Biome>>[] containers =
                    (PalettedContainer<Holder<Biome>>[]) new PalettedContainer<?>[sections.length];
            for (int i = 0; i < sections.length; ++i) {
                containers[i] = sections[i].getBiomes().recreate();
            }
            return containers;
        }
    }

    private static final class ColumnSampler {
        private static final BiomeColumnEvaluator.Root[] ROOTS = BiomeColumnEvaluator.Root.values();

        private final BiomeColumnEvaluator evaluator;
        private final int minBlockY;
        private final double[][] values;
        private final long[] target = new long[ROOTS.length + 1];

        private ColumnSampler(
                Options options,
                int minQuartY,
                int sectionCount
        ) {
            this.evaluator = options.evaluator();
            this.minBlockY = QuartPos.toBlock(minQuartY);
            this.values = new double[ROOTS.length][sectionCount * QUARTS_PER_SECTION];
        }

        private boolean evaluate(int quartX, int quartZ) {
            int blockX = QuartPos.toBlock(quartX);
            int blockZ = QuartPos.toBlock(quartZ);
            for (BiomeColumnEvaluator.Root root : ROOTS) {
                BiomeColumnEvaluator.Request request = new BiomeColumnEvaluator.Request(
                        this.values[root.ordinal()], blockX, blockZ,
                        this.minBlockY, BLOCKS_PER_QUART);
                if (!this.evaluator.byepregen$evalBiomeColumn(root, request)) return false;
            }
            return true;
        }

        private long[] target(int sample) {
            for (BiomeColumnEvaluator.Root root : ROOTS) {
                this.target[root.ordinal()] = Climate.quantizeCoord(
                        (float) this.values[root.ordinal()][sample]);
            }
            this.target[ROOTS.length] = 0L;
            return this.target;
        }

        private long depth(int sample) {
            return Climate.quantizeCoord((float) this.values[
                    BiomeColumnEvaluator.Root.DEPTH.ordinal()][sample]);
        }
    }
}
