package com.moepus.byepregen.integration.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ModEnvironmentTest {
    @Test
    void reportsUnavailableClassesWithoutThrowing() {
        assertFalse(ModEnvironment.isClassAvailable("com.moepus.byepregen.missing.NotPresent"));
    }

    @Test
    void rejectsNullClassNames() {
        assertThrows(NullPointerException.class, () -> ModEnvironment.isClassAvailable(null));
    }
}
