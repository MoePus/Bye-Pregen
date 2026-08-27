package com.moepus.byepregen.dfc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NoiseHolderRuntimeAbiTest {
    @Test
    void resolvesSrgLikeMethodByDescriptor() {
        assertEquals("m_224006_", NoiseHolderRuntimeAbi.resolveValueMethodName(SrgLikeNoiseHolder.class));
    }

    @Test
    void rejectsMissingOrAmbiguousRuntimeAbi() {
        assertThrows(IllegalStateException.class,
                () -> NoiseHolderRuntimeAbi.resolveValueMethodName(MissingNoiseHolder.class));
        assertThrows(IllegalStateException.class,
                () -> NoiseHolderRuntimeAbi.resolveValueMethodName(AmbiguousNoiseHolder.class));
    }

    private static final class SrgLikeNoiseHolder {
        public double m_224006_(double x, double y, double z) {
            return x + y + z;
        }
    }

    private static final class MissingNoiseHolder {
        public double value(double x, double y) {
            return x + y;
        }
    }

    private static final class AmbiguousNoiseHolder {
        public double first(double x, double y, double z) {
            return x + y + z;
        }

        public double second(double x, double y, double z) {
            return x * y * z;
        }
    }
}
