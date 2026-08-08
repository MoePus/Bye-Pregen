/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.*;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.*;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

final class ColumnSupport {

    private ColumnSupport() {
    }

    static Preparation prepare(AstNode root) {
        Optional<String> specializationFailure = findUnsupportedSpecialization(root);
        if (specializationFailure.isPresent()) {
            return new Rejected("unsupported column specialization: " + specializationFailure.get());
        }
        AstNode specialized = ColumnAstSpecializer.specialize(root);
        Optional<String> codegenFailure = findUnsupported(specialized);
        return codegenFailure.<Preparation>map(Rejected::new).orElseGet(() -> new Supported(specialized));
    }

    private static Optional<String> findUnsupported(AstNode root) {
        Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findUnsupported(root, visited);
    }

    private static Optional<String> findUnsupportedSpecialization(AstNode root) {
        Set<AstNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findUnsupportedSpecialization(root, visited);
    }

    private static Optional<String> findUnsupportedSpecialization(AstNode node, Set<AstNode> visited) {
        if (!visited.add(node)) return Optional.empty();
        if (!canSpecialize(node)) return unsupported(node, "specializer cannot rebuild this node");
        for (AstNode child : node.getChildren()) {
            Optional<String> childFailure = findUnsupportedSpecialization(child, visited);
            if (childFailure.isPresent()) return childFailure;
        }
        return Optional.empty();
    }

    private static boolean canSpecialize(AstNode node) {
        Class<?> type = node.getClass();
        if (node instanceof RootNode) return type == RootNode.class;
        if (node instanceof AbstractUnaryNode) return isSupportedUnary(node);
        if (node instanceof AbstractBinaryNode) return isSupportedBinary(node);
        return canSpecializeMisc(node, type);
    }

    private static boolean canSpecializeMisc(AstNode node, Class<?> type) {
        if (node instanceof RangeChoiceNode) return type == RangeChoiceNode.class;
        if (node instanceof CacheLikeNode) return type == CacheLikeNode.class;
        if (node instanceof FindTopSurfaceNode) return type == FindTopSurfaceNode.class;
        if (node instanceof GenericShiftedNoiseNode) return type == GenericShiftedNoiseNode.class;
        if (node instanceof DFTWeirdScaledSamplerNode) return type == DFTWeirdScaledSamplerNode.class;
        if (node instanceof SplineAstNode) return type == SplineAstNode.class;
        if (node instanceof ColumnMemoized2DNode) return type == ColumnMemoized2DNode.class;
        return node.getChildren().length == 0;
    }

    private static Optional<String> findUnsupported(AstNode node, Set<AstNode> visited) {
        if (!visited.add(node)) return Optional.empty();
        Optional<String> directFailure = checkDirectSupport(node);
        if (directFailure.isPresent()) return directFailure;
        for (AstNode child : node.getChildren()) {
            Optional<String> childFailure = findUnsupported(child, visited);
            if (childFailure.isPresent()) return childFailure;
        }
        return Optional.empty();
    }

    private static Optional<String> checkDirectSupport(AstNode node) {
        Class<?> type = node.getClass();
        if (node instanceof DelegateNode && !isSupportedDelegateType(node.getClass())) {
            return unsupported(node, "only Beardifier delegates are safe in column codegen");
        }
        if (type == DFTWeirdScaledSamplerNode.class) {
            DFTWeirdScaledSamplerNode weird = (DFTWeirdScaledSamplerNode) node;
            if (!isSupportedMapper(weird)) {
                return unsupported(node, "unknown rarity mapper " + weird.mapper);
            }
        }
        if (type == SplineAstNode.class) {
            SplineAstNode spline = (SplineAstNode) node;
            if (!isSupportedSpline(spline, spline.spline)) {
                return unsupported(node, "unsupported or malformed spline");
            }
        }
        if (isDirectlySupported(node)) return Optional.empty();
        return unsupported(node, "no dedicated column emitter");
    }

    private static boolean isDirectlySupported(AstNode node) {
        Class<?> type = node.getClass();
        return type == RootNode.class
                || type == ConstantNode.class
                || type == ColumnMemoized2DNode.class
                || type == ColumnCacheNode.class
                || type == CoordinateNode.class
                || type == YClampedGradientNode.class
                || type == RangeChoiceNode.class
                || isSupportedDelegateType(type)
                || type == GenericShiftedNoiseNode.class
                || type == DFTWeirdScaledSamplerNode.class
                || type == SplineAstNode.class
                || isSupportedUnary(node)
                || isSupportedBinary(node);
    }

    static boolean isSupportedUnary(AstNode node) {
        Class<?> type = node.getClass();
        return type == AbsNode.class
                || type == SquareNode.class
                || type == CubeNode.class
                || type == SqueezeNode.class
                || type == NegMulNode.class;
    }

    static boolean isSupportedBinary(AstNode node) {
        Class<?> type = node.getClass();
        return type == AddNode.class
                || type == MulNode.class
                || type == DivNode.class
                || type == MinNode.class
                || type == MaxNode.class
                || type == MinShortNode.class
                || type == MaxShortNode.class;
    }

    static boolean isSupportedDelegateType(Class<?> type) {
        return type == BeardifierNode.class;
    }

    private static boolean isSupportedMapper(DFTWeirdScaledSamplerNode node) {
        String name = node.mapper.name();
        return "TYPE1".equals(name) || "TYPE2".equals(name);
    }

    private static boolean isSupportedSpline(SplineAstNode root, CubicSpline<?, ?> spline) {
        if (spline instanceof CubicSpline.Constant<?, ?>) return true;
        if (!(spline instanceof CubicSpline.Multipoint<?, ?> impl)) return false;
        int size = impl.values().size();
        if (size == 0 || impl.locations().length != size || impl.derivatives().length != size) return false;
        if (!root.children.containsKey(impl.coordinate())) return false;
        for (CubicSpline<?, ?> child : impl.values()) {
            if (!isSupportedSpline(root, child)) return false;
        }
        return true;
    }

    private static Optional<String> unsupported(AstNode node, String reason) {
        return Optional.of(node.getClass().getName() + ": " + reason);
    }

    sealed interface Preparation permits Supported, Rejected {
    }

    record Supported(AstNode root) implements Preparation {
    }

    record Rejected(String reason) implements Preparation {
    }
}

