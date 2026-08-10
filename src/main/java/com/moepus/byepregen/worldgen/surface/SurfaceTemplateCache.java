package com.moepus.byepregen.worldgen.surface;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfaceTemplateCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Surface Scalar");

    private final SurfaceScalarTarget target;
    private volatile Entry current;

    public SurfaceTemplateCache(boolean buildSurface) {
        this.target = buildSurface
                ? SurfaceScalarTarget.BUILD_POINT
                : SurfaceScalarTarget.TOP_POINT;
    }

    public Object bind(SurfaceRules.RuleSource source, Object context) {
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
            Object bound = entry.template().bind(context);
            SurfaceScalarMetrics.binding(this.target, entry.template().statistics());
            if (!SurfaceDifferential.enabled()) {
                return bound;
            }
            Object vanilla = vanillaBind(source, context);
            return SurfaceDifferential.wrap(this.target, bound, vanilla);
        } catch (RuntimeException | Error exception) {
            SurfaceScalarMetrics.bindFailure();
            throw exception;
        } catch (Throwable throwable) {
            SurfaceScalarMetrics.bindFailure();
            throw new IllegalStateException("Cannot bind generated SurfaceRule", throwable);
        }
    }

    private Entry resolve(SurfaceRules.RuleSource source) {
        synchronized (this) {
            Entry entry = this.current;
            if (entry != null && entry.source() == source) {
                return entry;
            }
            this.current = this.compile(source);
            return this.current;
        }
    }

    private Entry compile(SurfaceRules.RuleSource source) {
        try {
            SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(source);
            SurfaceDirectTemplate template = SurfaceScalarAsmCompiler.compile(plan, this.target);
            SurfaceScalarMetrics.compiled(this.target, template.statistics());
            LOGGER.info(
                    "Compiled {} SurfaceRule: bytes={} fields/events={}/{} regions={} "
                            + "noiseSamples/predicates={}/{} biomes={}",
                    this.target,
                    template.statistics().classBytes(),
                    template.statistics().bindingSlots(),
                    template.statistics().bindingEvents(),
                    template.statistics().regions(),
                    template.statistics().noiseSamples(),
                    template.statistics().noiseOccurrences(),
                    template.statistics().biomeValues()
            );
            LOGGER.info("{} SurfaceRule regions: {}", this.target, template.statistics().regionShape());
            return new Entry(source, template);
        } catch (SurfaceCompileException | RuntimeException exception) {
            SurfaceScalarMetrics.rejected();
            LOGGER.warn("Falling back from {} SurfaceRule compilation", this.target, exception);
            return new Entry(source, null);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object vanillaBind(SurfaceRules.RuleSource source, Object context) {
        return ((Function) source).apply(context);
    }

    private record Entry(SurfaceRules.RuleSource source, SurfaceDirectTemplate template) {
    }
}
