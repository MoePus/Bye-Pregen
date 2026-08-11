package com.moepus.byepregen.worldgen.surface;

import com.moepus.byepregen.MixinPlugin;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfaceTemplateCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen Surface Scalar");
    private static final boolean TERRABLENDER_LOADED = MixinPlugin.isModExist("terrablender");

    private final boolean outputDifferential;
    private volatile Entry current;

    public SurfaceTemplateCache() {
        this(true);
    }

    SurfaceTemplateCache(boolean outputDifferential) {
        this.outputDifferential = outputDifferential;
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
        SurfaceCompiledTemplate compat = this.compileCompat(source);
        if (compat != null) {
            LOGGER.info("Preserving TerraBlender SurfaceRule namespace dispatcher");
            return new Entry(source, compat);
        }
        try {
            SurfaceRulePlan plan = SurfaceRuleAnalyzer.analyze(source);
            SurfaceDirectTemplate template = SurfaceScalarAsmCompiler.compile(plan);
            SurfaceScalarMetrics.compiled(template.statistics());
            LOGGER.info(
                    "Compiled SurfaceRule: bytes={} fields/events={}/{} regions={} "
                            + "noiseSamples/predicates={}/{}",
                    template.statistics().classBytes(),
                    template.statistics().bindingSlots(),
                    template.statistics().bindingEvents(),
                    template.statistics().regions(),
                    template.statistics().noiseSamples(),
                    template.statistics().noiseOccurrences()
            );
            LOGGER.info("SurfaceRule regions: {}", template.statistics().regionShape());
            return new Entry(source, template);
        } catch (SurfaceCompileException | RuntimeException exception) {
            SurfaceScalarMetrics.rejected();
            LOGGER.warn("Falling back from SurfaceRule compilation", exception);
            return new Entry(source, null);
        }
    }

    private SurfaceCompiledTemplate compileCompat(SurfaceRules.RuleSource source) {
        if (!TERRABLENDER_LOADED) {
            return null;
        }
        try {
            return TerraBlenderCompat.compile(source);
        } catch (LinkageError | RuntimeException exception) {
            LOGGER.warn("Falling back from TerraBlender SurfaceRule compatibility", exception);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object vanillaBind(SurfaceRules.RuleSource source, Object context) {
        return ((Function) source).apply(context);
    }

    private record Entry(SurfaceRules.RuleSource source, SurfaceCompiledTemplate template) {
    }
}
