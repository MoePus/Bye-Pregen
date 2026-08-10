package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$BiomeConditionSource")
public interface SurfaceRulesBiomeSourceMixin extends SurfaceRuleSourceAccess.BiomeCondition {
    @Override
    @Accessor("biomes")
    List<ResourceKey<Biome>> byepregen$biomes();

    @Override
    @Accessor("biomeNameTest")
    Predicate<ResourceKey<Biome>> byepregen$biomeNameTest();
}
