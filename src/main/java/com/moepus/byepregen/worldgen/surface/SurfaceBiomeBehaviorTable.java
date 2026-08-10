package com.moepus.byepregen.worldgen.surface;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class SurfaceBiomeBehaviorTable {
    public static final long SUPPORTED = Long.MIN_VALUE;

    private final Reference2LongOpenHashMap<ResourceKey<Biome>> behaviors;
    private final List<Object> fallbackSources;

    SurfaceBiomeBehaviorTable(
            List<SurfaceScalarLayout.BiomeValue> values,
            List<Object> fallbackSources
    ) {
        this.fallbackSources = List.copyOf(fallbackSources);
        this.behaviors = new Reference2LongOpenHashMap<>();
        this.behaviors.defaultReturnValue(0L);
        for (SurfaceScalarLayout.BiomeValue value : values) {
            this.add(value.biomes(), value.mask());
        }
    }

    public long behavior(Holder<Biome> holder) {
        Objects.requireNonNull(holder, "holder");
        if (holder.getClass() == Holder.Reference.class) {
            return SUPPORTED | this.behaviors.getLong(((Holder.Reference<?>) holder).key());
        }
        if (holder.getClass() == Holder.Direct.class) {
            return SUPPORTED;
        }
        return 0L;
    }

    public Object bindFallback(int index, Object context) {
        return apply(this.fallbackSources.get(index), context);
    }

    private void add(Set<ResourceKey<Biome>> biomes, long mask) {
        for (ResourceKey<Biome> biome : biomes) {
            this.behaviors.put(biome, this.behaviors.getLong(biome) | mask);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object apply(Object source, Object context) {
        return ((Function) source).apply(context);
    }
}
