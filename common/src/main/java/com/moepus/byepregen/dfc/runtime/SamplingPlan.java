package com.moepus.byepregen.dfc.runtime;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import java.util.*;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

/** Separates unconditional batches from pure terminals reached only through selected branches. */
final class SamplingPlan {
    private final Set<DensitySampler> conditional;
    private final List<DensitySampler> eager;

    SamplingPlan(AstNode root) {
        Builder builder = new Builder();
        builder.visit(root, false);
        builder.conditional.removeAll(builder.unconditional);
        this.conditional = Collections.unmodifiableSet(builder.conditional);
        this.eager = List.copyOf(builder.eager);
    }

    boolean conditional(DensitySampler source) { return this.conditional.contains(source); }
    List<DensitySampler> eagerSources() { return this.eager; }

    private static final class Builder {
        private final Set<DensitySampler> conditional = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<DensitySampler> unconditional = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<DensitySampler> eagerSeen = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<DensitySampler> eager = new ArrayList<>();
        private final Map<AstNode, Boolean> visited = new IdentityHashMap<>();

        private void visit(AstNode node, boolean selected) {
            Boolean previous = this.visited.get(node);
            if (previous != null && (!previous || selected)) return;
            this.visited.put(node, selected);
            DensitySampler source = node instanceof DelegateNode d ? d.delegate()
                    : node instanceof SourceNode s ? s.source() : null;
            if (source != null) this.source(source, selected);
            if (node instanceof RangeChoiceNode range) {
                // Collect from the original graph in vanilla batch order, including opaque dead branches.
                this.visit(range.whenInRange(), true);
                this.visit(range.input(), selected);
                this.visit(range.whenOutOfRange(), true);
            } else if (node instanceof IntervalSelectNode interval) {
                this.visit(interval.input(), selected);
                for (AstNode branch : interval.branches()) this.visit(branch, true);
            } else if (node instanceof MinShortNode || node instanceof MaxShortNode) {
                BinaryNode binary = (BinaryNode) node;
                this.visit(binary.left(), selected);
                this.visit(binary.right(), true);
            } else {
                for (AstNode child : node.children()) this.visit(child, selected);
            }
        }

        private void source(DensitySampler source, boolean selected) {
            if (selected && source instanceof NativeDensitySource nativeSource && !nativeSource.eager()) {
                this.conditional.add(source);
            } else this.unconditional.add(source);
            if (source instanceof NativeDensitySource nativeSource && nativeSource.eager()
                    && this.eagerSeen.add(source)) this.eager.add(source);
        }
    }
}
