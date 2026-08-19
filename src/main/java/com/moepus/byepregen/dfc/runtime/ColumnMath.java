package com.moepus.byepregen.dfc.runtime;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunctions;

public final class ColumnMath {
    private ColumnMath() {
    }

    public static double squeeze(double value) {
        double clamped = Mth.clamp(value, -1.0D, 1.0D);
        return clamped * 0.5D - clamped * clamped * clamped / 24.0D;
    }

    public static double clampedMap(double value, double from, double to,
                                    double fromValue, double toValue) {
        return Mth.clampedMap(value, from, to, fromValue, toValue);
    }

    public static double rarity(
            DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper,
            double value
    ) {
        if (mapper == DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1) {
            if (value < -0.5D) return 0.75D;
            if (value < 0.0D) return 1.0D;
            return value < 0.5D ? 1.5D : 2.0D;
        }
        if (value < -0.75D) return 0.5D;
        if (value < -0.5D) return 0.75D;
        if (value < 0.5D) return 1.0D;
        return value < 0.75D ? 2.0D : 3.0D;
    }
}
