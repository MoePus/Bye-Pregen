package com.moepus.byepregen.worldgen.surface;

import java.util.Objects;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfaceTemplateCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Surface Scalar");

    private final boolean outputDifferential;
    private volatile Entry current;

    public SurfaceTemplateCache() {
        this(true);
    }

    SurfaceTemplateCache(boolean outputDifferential) {
        this.outputDifferential = outputDifferential;
    }

    public Object bind(MaterialRule source, MaterialRuleContext context) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(context, "context");
        Entry entry = this.current;
        if (entry == null || entry.source() != source) {
            entry = this.resolve(source);
        }
        if (entry.template() == null) {
            return vanillaBind(source, context);
        }
        try {
            SurfaceCompiledTemplate template = entry.template();
            Object bound = template.bind(context);
            if (template instanceof SurfaceDirectTemplate direct) {
                SurfaceScalarMetrics.binding(direct.statistics());
            }
            if (!this.outputDifferential || !SurfaceOutputDifferential.enabled()) {
                return bound;
            }
            Object vanilla = vanillaBind(source, context);
            return SurfaceOutputDifferential.wrap(bound, vanilla);
        } catch (RuntimeException | Error exception) {
            SurfaceScalarMetrics.bindFailure();
            throw exception;
        } catch (Throwable throwable) {
            SurfaceScalarMetrics.bindFailure();
            throw new IllegalStateException("Cannot bind generated SurfaceRule", throwable);
        }
    }

    private Entry resolve(MaterialRule source) {
        synchronized (this) {
            Entry entry = this.current;
            if (entry != null && entry.source() == source) {
                return entry;
            }
            this.current = this.compile(source);
            return this.current;
        }
    }

    private Entry compile(MaterialRule source) {
        try {
            SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(source);
            SurfaceDirectTemplate template = SurfaceScalarAsmCompiler.compile(plan);
            SurfaceDirectTemplate.Statistics statistics = template.statistics();
            SurfaceScalarMetrics.compiled(statistics);
            LOGGER.info(
                    "Compiled SurfaceRule: bytes={} fields/events={}/{} regions={} "
                            + "noiseConditions/samples={}/{}",
                    statistics.classBytes(),
                    statistics.bindingSlots(),
                    statistics.bindingEvents(),
                    statistics.regions(),
                    statistics.noiseOccurrences(),
                    statistics.noiseSamples()
            );
            LOGGER.info("SurfaceRule regions: {}", statistics.regionShape());
            return new Entry(source, template);
        } catch (SurfaceCompileException | RuntimeException exception) {
            SurfaceScalarMetrics.rejected();
            LOGGER.warn("Falling back from SurfaceRule compilation", exception);
            return new Entry(source, null);
        }
    }

    static Object vanillaBind(MaterialRule source, MaterialRuleContext context) {
        return source.compile(context);
    }

    private record Entry(MaterialRule source, SurfaceCompiledTemplate template) {
    }
}
