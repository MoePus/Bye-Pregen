package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.runtime.DensityColumnVerification;
import org.junit.jupiter.api.Test;

final class DensityColumnVerificationTest {
    @Test
    void rejectsSmallErrorsThatChangeAquiferBranch() {
        assertFalse(DensityColumnVerification.equivalent(1.0E-8D, -1.0E-8D));
        assertFalse(DensityColumnVerification.equivalent(-1.0E-8D, 1.0E-8D));
        assertFalse(DensityColumnVerification.equivalent(0.0D, Double.MIN_VALUE));
        assertFalse(DensityColumnVerification.equivalent(Double.MIN_VALUE, -0.0D));
    }

    @Test
    void preservesToleranceWithinTheSameBranch() {
        assertTrue(DensityColumnVerification.equivalent(1.0D, 1.0D + 1.0E-7D));
        assertTrue(DensityColumnVerification.equivalent(-1.0E-8D, 0.0D));
        assertTrue(DensityColumnVerification.equivalent(-0.0D, 0.0D));
        assertFalse(DensityColumnVerification.equivalent(1.0D, 1.001D));
    }

    @Test
    void keepsExplicitNonFiniteComparisonSemantics() {
        assertTrue(DensityColumnVerification.equivalent(Double.NaN, Double.NaN));
        assertTrue(DensityColumnVerification.equivalent(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(DensityColumnVerification.equivalent(Double.NaN, 0.0D));
        assertFalse(DensityColumnVerification.equivalent(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));
        assertFalse(DensityColumnVerification.equivalent(Double.MAX_VALUE, Double.POSITIVE_INFINITY));
    }
}
