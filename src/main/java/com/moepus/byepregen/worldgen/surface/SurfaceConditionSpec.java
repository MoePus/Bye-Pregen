package com.moepus.byepregen.worldgen.surface;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public sealed interface SurfaceConditionSpec
        permits SurfaceConditionSpec.Singleton,
        SurfaceConditionSpec.Noise,
        SurfaceConditionSpec.StoneDepth,
        SurfaceConditionSpec.VerticalGradient,
        SurfaceConditionSpec.Water,
        SurfaceConditionSpec.YAbove,
        SurfaceConditionSpec.Opaque {
    enum Singleton implements SurfaceConditionSpec {
        ABOVE_PRELIMINARY_SURFACE,
        HOLE,
        STEEP,
        TEMPERATURE
    }

    record Noise(
            ResourceKey<NormalNoise.NoiseParameters> noise,
            double minimum,
            double maximum
    ) implements SurfaceConditionSpec {
        public Noise {
            Objects.requireNonNull(noise, "noise");
        }
    }

    record StoneDepth(
            int offset,
            boolean addSurfaceDepth,
            int secondaryDepthRange,
            CaveSurface surfaceType
    ) implements SurfaceConditionSpec {
        public StoneDepth {
            Objects.requireNonNull(surfaceType, "surfaceType");
        }

        boolean hasFixedLimit() {
            return !this.addSurfaceDepth && this.secondaryDepthRange == 0;
        }

        int baseLimit() {
            return 1 + this.offset;
        }
    }

    record VerticalGradient(
            ResourceLocation randomName,
            VerticalAnchor trueAtAndBelow,
            VerticalAnchor falseAtAndAbove
    ) implements SurfaceConditionSpec {
        public VerticalGradient {
            Objects.requireNonNull(randomName, "randomName");
            Objects.requireNonNull(trueAtAndBelow, "trueAtAndBelow");
            Objects.requireNonNull(falseAtAndAbove, "falseAtAndAbove");
        }
    }

    record Water(
            int offset,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
    ) implements SurfaceConditionSpec {
    }

    record YAbove(
            VerticalAnchor anchor,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
    ) implements SurfaceConditionSpec {
        public YAbove {
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    record Opaque(String sourceClass) implements SurfaceConditionSpec {
        public Opaque {
            Objects.requireNonNull(sourceClass, "sourceClass");
        }
    }
}
