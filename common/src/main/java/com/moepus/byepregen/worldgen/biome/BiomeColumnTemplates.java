package com.moepus.byepregen.worldgen.biome;

import com.moepus.byepregen.dfc.compile.DensityColumnCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.CompiledColumnEvaluator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** RandomState-owned compiled templates for the six climate density roots. */
public final class BiomeColumnTemplates {
    private final EnumMap<BiomeColumnEvaluator.Root, ColumnTemplate> templates;
    private final boolean depthOnlyClimate;

    private BiomeColumnTemplates(EnumMap<BiomeColumnEvaluator.Root, ColumnTemplate> templates) {
        this.templates = new EnumMap<>(templates);
        this.depthOnlyClimate = yIndependent(templates, BiomeColumnEvaluator.Root.TEMPERATURE)
                && yIndependent(templates, BiomeColumnEvaluator.Root.HUMIDITY)
                && yIndependent(templates, BiomeColumnEvaluator.Root.CONTINENTALNESS)
                && yIndependent(templates, BiomeColumnEvaluator.Root.EROSION)
                && yIndependent(templates, BiomeColumnEvaluator.Root.WEIRDNESS);
    }

    public static BiomeColumnTemplates compile(NoiseRouter router) {
        EnumMap<BiomeColumnEvaluator.Root, ColumnTemplate> templates =
                new EnumMap<>(BiomeColumnEvaluator.Root.class);
        IdentityHashMap<DensityFunction, ColumnTemplate> compiled = new IdentityHashMap<>();
        templates.put(BiomeColumnEvaluator.Root.TEMPERATURE,
                compileRoot(compiled, router.temperature()));
        templates.put(BiomeColumnEvaluator.Root.HUMIDITY,
                compileRoot(compiled, router.vegetation()));
        templates.put(BiomeColumnEvaluator.Root.CONTINENTALNESS,
                compileRoot(compiled, router.continents()));
        templates.put(BiomeColumnEvaluator.Root.EROSION,
                compileRoot(compiled, router.erosion()));
        templates.put(BiomeColumnEvaluator.Root.DEPTH,
                compileRoot(compiled, router.depth()));
        templates.put(BiomeColumnEvaluator.Root.WEIRDNESS,
                compileRoot(compiled, router.ridges()));
        return new BiomeColumnTemplates(templates);
    }

    public boolean available() {
        return this.templates.size() == BiomeColumnEvaluator.Root.values().length
                && this.templates.values().stream().allMatch(ColumnTemplate::available);
    }

    public boolean depthOnlyClimate() {
        return this.depthOnlyClimate;
    }

    public EnumMap<BiomeColumnEvaluator.Root, CompiledColumnEvaluator> bind(
            Function<ColumnTemplate, CompiledColumnEvaluator> binder
    ) {
        EnumMap<BiomeColumnEvaluator.Root, CompiledColumnEvaluator> bound =
                new EnumMap<>(BiomeColumnEvaluator.Root.class);
        this.templates.forEach((root, template) -> bound.put(root, binder.apply(template)));
        return bound;
    }

    private static ColumnTemplate compileRoot(
            IdentityHashMap<DensityFunction, ColumnTemplate> compiled,
            DensityFunction source
    ) {
        ColumnTemplate template = compiled.get(source);
        if (template == null) {
            template = DensityColumnCompiler.compile(source);
            compiled.put(source, template);
        }
        return template;
    }

    private static boolean yIndependent(
            EnumMap<BiomeColumnEvaluator.Root, ColumnTemplate> templates,
            BiomeColumnEvaluator.Root root
    ) {
        ColumnTemplate template = templates.get(root);
        return template != null && template.yIndependent();
    }
}
