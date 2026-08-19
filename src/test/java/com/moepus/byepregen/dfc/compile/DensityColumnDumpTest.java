package com.moepus.byepregen.dfc.compile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.ConstantNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
final class DensityColumnDumpTest {
    private final String originalProperty = System.getProperty(
            DensityColumnCompiler.DUMP_DIRECTORY_PROPERTY);

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void restoreProperty() {
        if (this.originalProperty == null) {
            System.clearProperty(DensityColumnCompiler.DUMP_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(DensityColumnCompiler.DUMP_DIRECTORY_PROPERTY, this.originalProperty);
        }
    }

    @Test
    void defaultPathDoesNotCreateDumpDirectory() {
        Path dumpDirectory = this.temporaryDirectory.resolve("absent");
        System.clearProperty(DensityColumnCompiler.DUMP_DIRECTORY_PROPERTY);

        DensityColumnCompiler.dumpIfRequested(ast(1.0D), ast(2.0D), new byte[]{1, 2, 3});

        assertFalse(Files.exists(dumpDirectory));
        assertEquals(0L, countEntries(this.temporaryDirectory));
    }

    @Test
    void configuredPathWritesBothAstsGraphAndClass() throws IOException {
        Path dumpDirectory = this.temporaryDirectory.resolve("configured");
        byte[] classBytes = {1, 2, 3};
        System.setProperty(DensityColumnCompiler.DUMP_DIRECTORY_PROPERTY, dumpDirectory.toString());

        DensityColumnCompiler.dumpIfRequested(ast(1.0D), ast(2.0D), classBytes);

        List<Path> files;
        try (var entries = Files.list(dumpDirectory)) {
            files = entries.toList();
        }
        assertEquals(4, files.size());
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().endsWith("-before.txt")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().endsWith("-after.txt")));
        assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().endsWith(".dot")));
        Path classFile = files.stream()
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .findFirst()
                .orElseThrow();
        assertArrayEquals(classBytes, Files.readAllBytes(classFile));
    }

    private static AstNode ast(double value) {
        return new ConstantNode(value);
    }

    private static long countEntries(Path directory) {
        try (var entries = Files.list(directory)) {
            return entries.count();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
