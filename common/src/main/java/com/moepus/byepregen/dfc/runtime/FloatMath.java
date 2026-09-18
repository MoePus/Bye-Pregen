package com.moepus.byepregen.dfc.runtime;

import net.minecraft.util.Mth;

/** RC2 operations whose volume semantics differ from Math.min/max or the point path. */
public final class FloatMath {
    private FloatMath() { }
    public static float volumeMin(float left, float right) { return right < left ? right : left; }
    public static float volumeMax(float left, float right) { return right > left ? right : left; }
    public static float leaky(float input, float factor) { return input > 0.0F ? input : input * factor; }
    public static float lerp(float alpha, float first, float second) {
        if (alpha == 0.0F) return first;
        return alpha == 1.0F ? second : Mth.lerp(alpha, first, second);
    }
    public static float squeeze(float input) {
        float clamped = Mth.clamp(input, -1.0F, 1.0F);
        return clamped / 2.0F - Mth.cube(clamped) / 24.0F;
    }
}
