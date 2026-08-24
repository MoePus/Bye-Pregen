package com.moepus.byepregen.dfc.ast;

import java.util.IdentityHashMap;
import java.util.Map;

public final class AstPrinter {
    private AstPrinter() {
    }

    public static String print(AstNode root) {
        StringBuilder output = new StringBuilder();
        print(root, output, new IdentityHashMap<>(), 0);
        return output.toString();
    }

    private static void print(AstNode node, StringBuilder output,
                              Map<AstNode, Integer> ids, int depth) {
        Integer existing = ids.get(node);
        if (existing != null) {
            output.append("  ".repeat(depth)).append('@').append(existing).append('\n');
            return;
        }
        int id = ids.size();
        ids.put(node, id);
        output.append("  ".repeat(depth)).append('#').append(id).append(' ')
                .append(node).append('\n');
        for (AstNode child : node.children()) print(child, output, ids, depth + 1);
    }
}
