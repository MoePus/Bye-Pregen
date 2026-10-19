package com.moepus.byepregen.harness.terrablender;

import com.moepus.byepregen.harness.HarnessResultFile;
import com.moepus.byepregen.harness.HarnessServerLifecycle;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import com.moepus.byepregen.worldgen.surface.SurfaceBoundedStoneDepthRule;
import com.moepus.byepregen.worldgen.surface.SurfaceScalarMetrics;
import com.moepus.byepregen.worldgen.surface.SurfaceTemplateCache;
import com.moepus.byepregen.worldgen.surface.TerraBlenderCompat;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.AbovePreliminarySurfaceCondition;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition;
import net.minecraft.world.level.levelgen.material.rule.BlockRule;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;
import terrablender.api.MaterialRuleManager;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/** Shared by unit tests and both real-loader probes (which also exercise the optional mixin). */
public final class TerraBlenderSurfaceChecks {
    public static final String RESULT_PROPERTY = "byepregen.terraBlenderHarness.result";
    private static final HolderOwner<Biome> OWNER = new HolderOwner<>() { };
    private static final Unsafe UNSAFE = unsafe();
    private static final Holder<Biome> MODDED = biome("example:forest");
    private static final Holder<Biome> OTHER = biome("minecraft:plains");
    private static final Holder<Biome> SAME_NAMESPACE = biome("example:desert");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    private TerraBlenderSurfaceChecks() { }

    public static void registerRules() {
        for (var category : MaterialRuleManager.RuleCategory.values()) {
            MaterialRuleManager.addRules(category, "minecraft", registries -> new ConditionRule(
                    new StoneDepthCondition(0, false, 0, CaveSurface.FLOOR), block(DIRT)));
        }
    }

    public static void run(MinecraftServer server) {
        var failure = new HarnessServerLifecycle.FailureOptions(RESULT_PROPERTY,
                LoggerFactory.getLogger("ByePregen TerraBlender Test"), "TerraBlender verification failed");
        HarnessServerLifecycle.execute(server, failure, () -> {
            require(ModEnvironment.isModLoaded("terrablender"), "TerraBlender is not installed");
            dispatchAndFallback();
            possibleBiomesAndReuse();
            lazyGuard();
            cacheIntegration();
            int sharedRuleCases = TerraBlenderSharedRules.verify(new Fixture(Set.of(MODDED)).context,
                    new Fixture(Set.of(MODDED)).context, new Fixture(Set.of(MODDED)).context);
            int materialSamples = TerraBlenderLiveRules.verify(server);
            var generator = (NoiseBasedChunkGenerator) server.overworld().getChunkSource().getGenerator();
            MaterialRule root = generator.generatorSettings().value().materialRule().value();
            require(TerraBlenderCompat.wrap(root) != null, "Live settings did not use TerraBlender");
            long before = SurfaceScalarMetrics.snapshot().bindings();
            int dimensions = 0;
            for (var level : server.getAllLevels()) {
                level.getChunk(57, -61);
                dimensions++;
            }
            require(SurfaceScalarMetrics.snapshot().bindings() > before, "Live surface compiler did not bind");
            HarnessResultFile.write(RESULT_PROPERTY, "PASS\nnamespaceFallback=true\nbranchReuse=true\n"
                    + "lazyGuard=true\ncacheIntegration=true\nsharedRuleCases=" + sharedRuleCases
                    + "\nmaterialSamples=" + materialSamples
                    + "\nworldgenDimensions=" + dimensions + "\n");
        });
    }

    public static void dispatchAndFallback() throws Exception {
        MaterialRule branch = new PartialRule();
        MaterialRule source = wrapped(new NamespacedSurfaceRuleSource(block(STONE), Map.of("example", branch)));
        Fixture vanilla = new Fixture(null), optimized = new Fixture(null);
        RuleEvaluator reference = source.compile(vanilla.context);
        long before = SurfaceScalarMetrics.snapshot().compiled();
        RuleEvaluator compiled = bind(template(source), optimized.context);
        require(SurfaceScalarMetrics.snapshot().compiled() == before + 2, "Branches were not independently compiled");
        Holder<Biome> unkeyed = Holder.direct(allocate(Biome.class));
        for (Holder<Biome> biome : List.of(MODDED, OTHER, unkeyed)) {
            for (int y : new int[]{63, 64}) {
                vanilla.sample(biome, 1, y);
                optimized.sample(biome, 1, y);
                BlockState expected = biome == MODDED && y == 64 ? DIRT : STONE;
                require(reference.tryApply(0, y, 0) == expected, "Incorrect native fixture");
                require(compiled.tryApply(0, y, 0) == expected, "Namespace/null-child fallback changed");
            }
        }
        MaterialRule nullable = new NamespacedSurfaceRuleSource(new PartialRule(), Map.of("example", branch));
        optimized.sample(OTHER, 1, 63);
        require(bind(template(nullable), optimized.context).tryApply(0, 63, 0) == null, "Null base changed");
        Map<String, MaterialRule> missing = new HashMap<>();
        missing.put("example", null);
        MaterialRule nullNamespace = new NamespacedSurfaceRuleSource(block(STONE), missing);
        optimized.sample(MODDED, 1, 64);
        require(bind(template(nullNamespace), optimized.context).tryApply(0, 64, 0) == STONE,
                "Null namespace source must use the base");
    }

    public static void possibleBiomesAndReuse() throws Exception {
        for (Set<Holder<Biome>> possible : List.of(Set.<Holder<Biome>>of(), Set.of(MODDED),
                Set.of(MODDED, SAME_NAMESPACE), Set.of(MODDED, OTHER))) {
            AtomicInteger unused = new AtomicInteger();
            MaterialRule source = new NamespacedSurfaceRuleSource(block(STONE),
                    Map.of("example", block(DIRT), "unused", counted(unused)));
            MaterialRule template = template(source);
            Fixture vanilla = new Fixture(possible), optimized = new Fixture(possible);
            RuleEvaluator reference = source.compile(vanilla.context);
            RuleEvaluator compiled = bind(template, optimized.context);
            long compiledCount = SurfaceScalarMetrics.snapshot().compiled();
            bind(template, new Fixture(possible).context);
            require(SurfaceScalarMetrics.snapshot().compiled() == compiledCount, "Branch template was recompiled");
            require(unused.get() == 0, "Impossible namespace was compiled");
            for (Holder<Biome> biome : List.of(MODDED, OTHER, SAME_NAMESPACE)) {
                vanilla.sample(biome, 1, 64);
                optimized.sample(biome, 1, 64);
                require(reference.tryApply(0, 64, 0) == compiled.tryApply(0, 64, 0), "Possible-biome dispatch changed");
                require(vanilla.lookups.get() == optimized.lookups.get(), "Uniform dispatcher performed a biome lookup");
            }
        }
        MaterialRule shared = block(DIRT);
        MaterialRule source = new NamespacedSurfaceRuleSource(shared, Map.of("example", shared));
        long before = SurfaceScalarMetrics.snapshot().compiled();
        bind(template(source), new Fixture(null).context);
        require(SurfaceScalarMetrics.snapshot().compiled() == before + 1, "Shared branch identity was lost");
    }

    public static void lazyGuard() throws Exception {
        for (MaterialCondition condition : List.of(new StoneDepthCondition(0, false, 0, CaveSurface.FLOOR),
                AbovePreliminarySurfaceCondition.INSTANCE)) {
            MaterialRule guarded = wrapped(new SequenceRule(List.of(new ConditionRule(condition, block(DIRT)))));
            MaterialRule source = new NamespacedSurfaceRuleSource(block(STONE), Map.of("example", guarded));
            compareGuard(source, null);
            compareGuard(source, Set.of(MODDED, OTHER));
        }
    }

    private static void compareGuard(MaterialRule source, Set<Holder<Biome>> possible) throws Exception {
        Fixture vanilla = new Fixture(possible), optimized = new Fixture(possible);
        RuleEvaluator reference = source.compile(vanilla.context);
        RuleEvaluator compiled = bind(template(source), optimized.context);
        for (int depth : new int[]{9, 1, 9, 1}) {
            for (Holder<Biome> biome : List.of(MODDED, OTHER)) {
                vanilla.sample(biome, depth, 64);
                optimized.sample(biome, depth, 64);
                BlockState expected = depth == 1 && biome == MODDED ? DIRT : STONE;
                require(reference.tryApply(0, 64, 0) == expected, "Incorrect guard fixture");
                require(compiled.tryApply(0, 64, 0) == expected, "Guard output changed across epochs");
                require(vanilla.lookups.get() == optimized.lookups.get(), "False outer guard did not skip biome lookup");
                require(optimized.lookups.get() == (depth == 1 ? 1 : 0), "Unexpected guard laziness");
            }
        }
    }

    public static void cacheIntegration() throws Exception {
        MaterialRule source = wrapped(new NamespacedSurfaceRuleSource(block(STONE), Map.of("example", block(DIRT))));
        Fixture fixture = new Fixture(null);
        fixture.sample(MODDED, 1, 64);
        SurfaceTemplateCache cache = new SurfaceTemplateCache();
        long before = SurfaceScalarMetrics.snapshot().compiled();
        RuleEvaluator compiled = (RuleEvaluator) cache.bind(source, fixture.context);
        require(SurfaceScalarMetrics.snapshot().compiled() == before + 2, "Cache delegated the entire namespace tree");
        require(compiled.tryApply(0, 64, 0) == DIRT, "Cache did not dispatch namespace");
        require(!(compiled instanceof SurfaceBoundedStoneDepthRule), "Dispatcher incorrectly bounded the ceiling scan");
        cache.bind(source, new Fixture(null).context);
        require(SurfaceScalarMetrics.snapshot().compiled() == before + 2, "Cache discarded namespace templates");
        MaterialRule nested = wrapped(new NamespacedSurfaceRuleSource(block(STONE), Map.of("example", source)));
        before = SurfaceScalarMetrics.snapshot().compiled();
        compiled = (RuleEvaluator) new SurfaceTemplateCache().bind(nested, fixture.context);
        require(SurfaceScalarMetrics.snapshot().compiled() == before + 3, "Nested namespace branches were opaque");
        require(compiled.tryApply(0, 64, 0) == DIRT, "Nested namespace dispatch changed");
    }

    private static MaterialRule template(MaterialRule source) {
        MaterialRule result = TerraBlenderCompat.wrap(source);
        require(result != null, "Holder-wrapped namespaced source was not recognized");
        return result;
    }

    private static RuleEvaluator bind(MaterialRule template, MaterialRuleContext context) {
        return template.compile(context);
    }

    private static MaterialRule wrapped(MaterialRule source) {
        return new MaterialRule.HolderHolder(Holder.direct(new MaterialRule.HolderHolder(Holder.direct(source))));
    }

    private static MaterialRule block(BlockState state) {
        return new BlockRule(state);
    }

    private static MaterialRule counted(AtomicInteger count) {
        return new MaterialRule() {
            @Override public RuleEvaluator compile(MaterialRuleContext context) {
                count.incrementAndGet();
                return (x, y, z) -> DIRT;
            }

            @Override public MapCodec<? extends MaterialRule> codec() {
                throw new UnsupportedOperationException("test-only rule");
            }
        };
    }

    private static final class PartialRule implements MaterialRule {
        @Override public RuleEvaluator compile(MaterialRuleContext context) {
            return (x, y, z) -> y == 64 ? DIRT : null;
        }

        @Override public MapCodec<? extends MaterialRule> codec() {
            throw new UnsupportedOperationException("test-only rule");
        }
    }

    private static Holder<Biome> biome(String key) {
        return Holder.Reference.createStandAlone(OWNER, ResourceKey.create(Registries.BIOME, Identifier.parse(key)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static <T> T allocate(Class<T> type) throws InstantiationException {
        return type.cast(UNSAFE.allocateInstance(type));
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void set(MaterialRuleContext context, String name, Object value) throws Exception {
        Field field = MaterialRuleContext.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(context, value);
    }

    // These checks only read biome identity and scalar stone-depth state, not a live density system.
    private static final class Fixture {
        private final MaterialRuleContext context = allocate(MaterialRuleContext.class);
        private final AtomicInteger lookups = new AtomicInteger();
        private Holder<Biome> biome;
        private long epoch;

        private Fixture(Set<Holder<Biome>> possible) throws Exception {
            Function<BlockPos, Holder<Biome>> getter = pos -> {
                this.lookups.incrementAndGet();
                return this.biome;
            };
            set(this.context, "possibleBiomes", possible);
            set(this.context, "biomeGetter", getter);
            set(this.context, "pos", new BlockPos.MutableBlockPos());
        }

        private void sample(Holder<Biome> biome, int depth, int y) throws Exception {
            this.biome = biome;
            this.lookups.set(0);
            set(this.context, "biome", null);
            set(this.context, "blockY", y);
            set(this.context, "stoneDepthAbove", depth);
            set(this.context, "lastUpdateY", ++this.epoch);
            set(this.context, "lastUpdateXZ", this.epoch);
            set(this.context, "lastMinSurfaceLevelUpdate", this.epoch);
            set(this.context, "minSurfaceLevel", depth == 1 ? y - 1 : y + 1);
        }
    }
}
