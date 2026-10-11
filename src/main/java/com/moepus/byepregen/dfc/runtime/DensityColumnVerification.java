package com.moepus.byepregen.dfc.runtime;

/** Debug comparison for final densities, including Aquifer's solid/non-solid threshold. */
public final class DensityColumnVerification {
    private static final double TOLERANCE = 1.0E-6D;

    private DensityColumnVerification() {
    }

    public static boolean equivalent(double expected, double actual) {
        if (Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual)) return true;
        if (Double.isNaN(expected) && Double.isNaN(actual)) return true;
        if (!Double.isFinite(expected) || !Double.isFinite(actual)) return false;
        if ((expected > 0.0D) != (actual > 0.0D)) return false;
        return Math.abs(expected - actual) <= TOLERANCE * (1.0D + Math.abs(expected));
    }
}
