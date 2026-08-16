package com.moepus.byepregen.integration.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.objectweb.asm.tree.ClassNode;
import org.junit.jupiter.api.Test;

final class ModEnvironmentTest {
    @Test
    void reportsUnavailableClassesWithoutThrowing() {
        assertFalse(ModEnvironment.isClassAvailable("missing", ignored -> {
            throw new ClassNotFoundException("missing");
        }));
    }

    @Test
    void rejectsNullClassNames() {
        assertThrows(NullPointerException.class, () -> ModEnvironment.isClassAvailable(null));
    }

    @Test
    void distinguishesMissingClassesFromLookupFailures() {
        assertTrue(ModEnvironment.isClassAvailable("present", ignored -> new ClassNode()));
        assertFalse(ModEnvironment.isClassAvailable("missing", ignored -> {
            throw new ClassNotFoundException("missing");
        }));
        assertThrows(IllegalStateException.class, () -> ModEnvironment.isClassAvailable("broken", ignored -> {
            throw new IOException("lookup failed");
        }));
        assertThrows(LinkageError.class, () -> ModEnvironment.isClassAvailable("broken", ignored -> {
            throw new LinkageError("lookup failed");
        }));
    }
}
