package com.moepus.byepregen.worldgen.surface;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

final class TerraBlenderCompat {
    private TerraBlenderCompat() {
    }

    static SurfaceCompiledTemplate compile(SurfaceRules.RuleSource source) {
        if (!(source instanceof NamespacedSurfaceRuleSource namespaced)) {
            return null;
        }
        SurfaceRules.RuleSource base = compiledSource(namespaced.base());
        Map<String, SurfaceRules.RuleSource> sources = new LinkedHashMap<>();
        namespaced.sources().forEach((namespace, rule) ->
                sources.put(namespace, compiledSource(rule))
        );
        NamespacedSurfaceRuleSource compiled = new NamespacedSurfaceRuleSource(base, sources);
        return new NamespacedTemplate(compiled);
    }

    private static SurfaceRules.RuleSource compiledSource(SurfaceRules.RuleSource source) {
        return (SurfaceRules.RuleSource) Proxy.newProxyInstance(
                SurfaceRules.RuleSource.class.getClassLoader(),
                new Class<?>[]{SurfaceRules.RuleSource.class},
                new CompiledSourceHandler(source, new SurfaceTemplateCache(false))
        );
    }

    private static final class NamespacedTemplate extends SurfaceCompiledTemplate {
        private final NamespacedSurfaceRuleSource source;

        private NamespacedTemplate(NamespacedSurfaceRuleSource source) {
            this.source = source;
        }

        @Override
        Object bind(Object context) {
            return SurfaceTemplateCache.vanillaBind(this.source, context);
        }
    }

    private record CompiledSourceHandler(
            SurfaceRules.RuleSource source,
            SurfaceTemplateCache cache
    ) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "apply" -> this.cache.bind(this.source, arguments[0]);
                case "codec" -> this.source.codec();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "CompiledTerraBlenderRuleSource[" + this.source + "]";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        }
    }
}
