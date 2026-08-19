package com.moepus.byepregen.dfc.compile;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class ColumnDumpWriter {
    private static final AtomicLong IDS = new AtomicLong();

    private ColumnDumpWriter() {
    }

    static void write(Path directory, AstNode before, AstNode after, byte[] classBytes) throws IOException {
        Files.createDirectories(directory);
        String prefix = "final-density-" + IDS.incrementAndGet();
        Files.writeString(directory.resolve(prefix + "-before.txt"), AstPrinter.print(before), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(prefix + "-after.txt"), AstPrinter.print(after), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(prefix + ".dot"), dot(after), StandardCharsets.UTF_8);
        Files.write(directory.resolve(prefix + ".class"), classBytes);
    }

    private static String dot(AstNode root) {
        StringBuilder output = new StringBuilder("digraph FinalDensity {\n");
        Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        append(root, output, visited);
        return output.append("}\n").toString();
    }

    private static void append(AstNode node, StringBuilder output, Set<AstNode> visited) {
        if (!visited.add(node)) return;
        int id = System.identityHashCode(node);
        output.append("  n").append(id).append(" [label=\"")
                .append(node.getClass().getSimpleName()).append("\"];\n");
        for (AstNode child : node.children()) {
            output.append("  n").append(id).append(" -> n")
                    .append(System.identityHashCode(child)).append(";\n");
            append(child, output, visited);
        }
    }
}
