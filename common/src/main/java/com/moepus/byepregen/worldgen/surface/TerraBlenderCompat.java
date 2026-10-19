package com.moepus.byepregen.worldgen.surface;

import com.mojang.serialization.MapCodec;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/** Keeps TerraBlender's native dispatch and identity memoization while compiling its material subtrees. */
public final class TerraBlenderCompat {
    private static final ThreadLocal<Map<MaterialRule, SurfaceTemplateCache>> ACTIVE_TEMPLATES = new ThreadLocal<>();

    private TerraBlenderCompat() {
    }

    static SurfaceCompiledTemplate compile(MaterialRule source) {
        MaterialRule wrapped = wrap(source);
        return wrapped == null ? null : new NamespacedTemplate(wrapped);
    }

    /** Returns a runtime-only binding wrapper, or null for unrelated rules. */
    public static MaterialRule wrap(MaterialRule source) {
        Set<MaterialRule> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (source instanceof MaterialRule.HolderHolder reference) {
            if (!visited.add(source)) {
                throw new IllegalArgumentException("Cyclic material rule reference");
            }
            source = reference.holder().value();
        }
        if (!(source instanceof NamespacedSurfaceRuleSource namespaced)) {
            return null;
        }
        return new CompiledSource(namespaced);
    }

    /** Called only at the native compiler's MaterialRule.compile invocation, after its identity lookup. */
    public static RuleEvaluator compileRule(MaterialRule source, MaterialRuleContext context) {
        Map<MaterialRule, SurfaceTemplateCache> templates = ACTIVE_TEMPLATES.get();
        if (templates == null) {
            return source.compile(context);
        }
        SurfaceTemplateCache cache = templates.computeIfAbsent(source, ignored -> new SurfaceTemplateCache(false));
        // Opaque delegates can construct unrelated compilers/rules while binding. Do not retain their
        // per-context sources in this root's persistent map; nested namespaced templates own a scope.
        ACTIVE_TEMPLATES.remove();
        try {
            return (RuleEvaluator) cache.bind(source, context);
        } finally {
            ACTIVE_TEMPLATES.set(templates);
        }
    }

    static final class CompiledSource implements MaterialRule {
        private final MaterialRule source;
        private final Map<MaterialRule, SurfaceTemplateCache> templates =
                Collections.synchronizedMap(new IdentityHashMap<>());

        CompiledSource(MaterialRule source) {
            this.source = source;
        }

        @Override
        public RuleEvaluator compile(MaterialRuleContext context) {
            Map<MaterialRule, SurfaceTemplateCache> previous = ACTIVE_TEMPLATES.get();
            ACTIVE_TEMPLATES.set(this.templates);
            try {
                return this.source.compile(context);
            } finally {
                if (previous == null) {
                    ACTIVE_TEMPLATES.remove();
                } else {
                    ACTIVE_TEMPLATES.set(previous);
                }
            }
        }

        @Override
        public MapCodec<? extends MaterialRule> codec() {
            throw new UnsupportedOperationException("Compiled TerraBlender rules are runtime-only");
        }
    }

    private static final class NamespacedTemplate extends SurfaceCompiledTemplate {
        private final MaterialRule source;

        private NamespacedTemplate(MaterialRule source) {
            this.source = source;
        }

        @Override
        Object bind(Object context) {
            return this.source.compile((MaterialRuleContext) context);
        }
    }
}
