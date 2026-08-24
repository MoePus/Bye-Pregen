package com.moepus.byepregen.dfc.compile;

import com.moepus.byepregen.dfc.analysis.ColumnSpecializer;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.RootNode;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.frontend.DensityFunctionFrontend;
import com.moepus.byepregen.dfc.opt.ColumnOptimizer;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.DensityColumnMetrics;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DensityColumnCompiler {
    public static final String DUMP_DIRECTORY_PROPERTY = "byepregen.dfc.dumpDir";
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Density Column Compiler");

    private DensityColumnCompiler() {
    }

    public static ColumnTemplate compile(DensityFunction finalDensity) {
        long start = System.nanoTime();
        return compile(finalDensity, start, start);
    }

    public static ColumnTemplate compile(NoiseRouter router) {
        long start = System.nanoTime();
        return compile(router.finalDensity(), start, start);
    }

    private static ColumnTemplate compile(
            DensityFunction finalDensity,
            long totalStart,
            long frontendStart
    ) {
        try {
            DensityFunctionFrontend frontend = new DensityFunctionFrontend();
            AstNode initial = new RootNode(frontend.convert(finalDensity));
            long frontendNanos = System.nanoTime() - frontendStart;

            ColumnOptimizer.Result optimized = ColumnOptimizer.optimize(initial);
            long specializationStart = System.nanoTime();
            ColumnSpecializer.Result specialized = ColumnSpecializer.specialize(optimized.root());
            long specializationNanos = System.nanoTime() - specializationStart;

            long asmStart = System.nanoTime();
            ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(
                    specialized.memoizedSlots()).build(specialized.root());
            long asmNanos = System.nanoTime() - asmStart;
            long defineStart = System.nanoTime();
            byte[] classBytes = generated.classBytes();
            MethodHandle constructor = ColumnClassDefiner.defineConstructor(classBytes);
            long defineNanos = System.nanoTime() - defineStart;
            ColumnTemplate template = new ColumnTemplate(constructor, generated.bindings());
            DensityColumnMetrics.recordCompiled();
            dumpIfRequested(initial, specialized.root(), classBytes);
            logTimings(frontendNanos, optimized, specializationNanos, asmNanos,
                    defineNanos, System.nanoTime() - totalStart);
            return template;
        } catch (Exception | LinkageError throwable) {
            if (throwable instanceof ColumnOptimizer.OptimizationCycleException cycle) {
                LOGGER.error("Final-density optimizer did not converge; final AST:\n{}", cycle.astDump());
            }
            LOGGER.error("Disabling ByePregen final-density column compiler for this RandomState", throwable);
            return ColumnTemplate.disabled(throwable.toString());
        }
    }

    static void dumpIfRequested(AstNode before, AstNode after, byte[] classBytes) {
        String configured = System.getProperty(DUMP_DIRECTORY_PROPERTY);
        if (configured == null || configured.isBlank()) return;
        try {
            ColumnDumpWriter.write(Path.of(configured), before, after, classBytes);
        } catch (Exception throwable) {
            LOGGER.warn("Cannot write requested density column compiler dump", throwable);
        }
    }

    private static void logTimings(
            long frontend,
            ColumnOptimizer.Result optimized,
            long specialization,
            long asm,
            long define,
            long total
    ) {
        LOGGER.info("Compiled final-density column: frontend={} ms, Core-1={} ms, Spline={} ms, "
                        + "Core-2={} ms, specialization/CSE={} ms, ASM={} ms, class define={} ms, total={} ms",
                millis(frontend), millis(optimized.core1Nanos()), millis(optimized.splineNanos()),
                millis(optimized.core2Nanos()), millis(specialization), millis(asm),
                millis(define), millis(total));
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0D;
    }
}
