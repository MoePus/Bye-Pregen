/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.runtime;

import net.minecraft.util.Mth;

public final class ColumnMath {
    private ColumnMath() {
    }

    public static float squeeze(float value) {
        float clamped = Mth.clamp(value, -1.0F, 1.0F);
        return clamped * 0.5F - clamped * clamped * clamped / 24.0F;
    }

    public static int findSplineRange(float[] locations, float point) {
        int start = 0;
        int remaining = locations.length;
        while (remaining > 0) {
            int half = remaining / 2;
            int middle = start + half;
            if (point < locations[middle]) {
                remaining = half;
            } else {
                start = middle + 1;
                remaining -= half + 1;
            }
        }
        return start - 1;
    }

    public static float clampedMap(float value, float from, float to,
                                    float fromValue, float toValue) {
        return Mth.clampedMap(value, from, to, fromValue, toValue);
    }

}
