package com.moepus.byepregen.worldgen.surface;

import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SurfaceRuleSourceAccess {
    private SurfaceRuleSourceAccess() {
    }

    public interface Block {
        BlockState byepregen$resultState();
    }

    public interface Sequence {
        List<SurfaceRules.RuleSource> byepregen$sequence();
    }

    public interface Test {
        SurfaceRules.ConditionSource byepregen$condition();

        SurfaceRules.RuleSource byepregen$followup();
    }

    public interface NotCondition {
        SurfaceRules.ConditionSource byepregen$target();
    }

    public interface BiomeCondition {
    }

    public interface NoiseCondition {
        ResourceKey<NormalNoise.NoiseParameters> byepregen$noise();

        double byepregen$minimum();

        double byepregen$maximum();
    }

    public interface StoneDepthCondition {
        int byepregen$offset();

        boolean byepregen$addSurfaceDepth();

        int byepregen$secondaryDepthRange();

        CaveSurface byepregen$surfaceType();
    }

    public interface VerticalGradientCondition {
        ResourceLocation byepregen$randomName();

        VerticalAnchor byepregen$trueAtAndBelow();

        VerticalAnchor byepregen$falseAtAndAbove();
    }

    public interface WaterCondition {
        int byepregen$offset();

        int byepregen$surfaceDepthMultiplier();

        boolean byepregen$addStoneDepth();
    }

    public interface YCondition {
        VerticalAnchor byepregen$anchor();

        int byepregen$surfaceDepthMultiplier();

        boolean byepregen$addStoneDepth();
    }
}
