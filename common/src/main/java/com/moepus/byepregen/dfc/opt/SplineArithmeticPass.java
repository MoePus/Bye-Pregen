/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.opt;

import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import com.moepus.byepregen.dfc.ast.AstRewriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;

final class SplineArithmeticPass {
    private SplineArithmeticPass() {
    }

    static AstNode apply(AstNode root) {
        return AstRewriter.rewrite(root, SplineArithmeticPass::rewrite);
    }

    private static AstNode rewrite(AstNode node) {
        if (node instanceof SplineNode spline) return optimizeLocationAffine(spline);
        if (node instanceof AddNode add && add.left() instanceof ConstantNode value
                && add.right() instanceof SplineNode spline && exactFloat(value.value())) {
            return withSpline(spline, offset(spline.spline(), (float) value.value()));
        }
        if (node instanceof MulNode mul && mul.left() instanceof ConstantNode value
                && mul.right() instanceof SplineNode spline && exactFloat(value.value())) {
            return withSpline(spline, scale(spline.spline(), (float) value.value()));
        }
        if (node instanceof NegNode neg && neg.operand() instanceof SplineNode spline) {
            return withSpline(spline, scale(spline.spline(), -1.0F));
        }
        return node;
    }

    private static SplineNode optimizeLocationAffine(SplineNode node) {
        Map<DensityFunctions.Spline.Coordinate, Affine> rewrites = new IdentityHashMap<>();
        List<AstNode> coordinates = new ArrayList<>(node.coordinates().size());
        boolean changed = false;
        for (DensityFunctions.Spline.Coordinate coordinate : node.coordinates()) {
            Affine affine = extractAffine(node.coordinateNode(coordinate));
            coordinates.add(affine.base());
            if (affine.changed()) {
                rewrites.put(coordinate, affine);
                changed = true;
            }
        }
        if (!changed) return node;
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> rewritten =
                rewriteLocations(node.spline(), rewrites);
        return new SplineNode(rewritten, node.coordinates(), coordinates);
    }

    private static Affine extractAffine(AstNode node) {
        AstNode current = node;
        float scale = 1.0F;
        float offset = 0.0F;
        while (true) {
            ConstantOperand add = constantOperand(current, AddNode.class);
            if (add != null && exactFloat(add.constant())) {
                offset += scale * (float) add.constant();
                current = add.other();
                continue;
            }
            ConstantOperand mul = constantOperand(current, MulNode.class);
            if (mul != null && exactNonzeroFloat(mul.constant())) {
                scale *= (float) mul.constant();
                current = mul.other();
                continue;
            }
            if (current instanceof NegNode neg) {
                scale = -scale;
                current = neg.operand();
                continue;
            }
            break;
        }
        if (!Float.isFinite(scale) || !Float.isFinite(offset) || scale == 0.0F) {
            return new Affine(node, 1.0F, 0.0F);
        }
        return new Affine(current, scale, offset);
    }

    private static ConstantOperand constantOperand(AstNode node, Class<? extends BinaryNode> type) {
        if (!type.isInstance(node)) return null;
        BinaryNode binary = (BinaryNode) node;
        if (binary.left() instanceof ConstantNode constant) {
            return new ConstantOperand(constant.value(), binary.right());
        }
        if (binary.right() instanceof ConstantNode constant) {
            return new ConstantOperand(constant.value(), binary.left());
        }
        return null;
    }

    private static boolean exactNonzeroFloat(double value) {
        return exactFloat(value) && (float) value != 0.0F;
    }

    private static CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>
    rewriteLocations(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            Map<DensityFunctions.Spline.Coordinate, Affine> rewrites
    ) {
        if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points)) return spline;
        List<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> values =
                new ArrayList<>(points.values().size());
        for (CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> value : points.values()) {
            values.add(rewriteLocations(value, rewrites));
        }
        float[] locations = points.locations().clone();
        float[] derivatives = points.derivatives().clone();
        Affine affine = rewrites.get(points.coordinate());
        if (affine != null) {
            for (int i = 0; i < locations.length; ++i) {
                locations[i] = (locations[i] - affine.offset()) / affine.scale();
                derivatives[i] *= affine.scale();
            }
            if (affine.scale() < 0.0F) {
                reverse(locations);
                reverse(derivatives);
                Collections.reverse(values);
            }
        }
        return new CubicSpline.Multipoint<>(points.coordinate(), locations, values, derivatives,
                points.minValue(), points.maxValue());
    }

    private static void reverse(float[] values) {
        for (int left = 0, right = values.length - 1; left < right; ++left, --right) {
            float value = values[left];
            values[left] = values[right];
            values[right] = value;
        }
    }

    private record ConstantOperand(double constant, AstNode other) {
    }

    private record Affine(AstNode base, float scale, float offset) {
        private boolean changed() {
            return this.scale != 1.0F || this.offset != 0.0F;
        }
    }

    private static boolean exactFloat(double value) {
        return Double.isFinite(value) && (double) (float) value == value;
    }

    private static SplineNode withSpline(
            SplineNode original,
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline
    ) {
        return new SplineNode(spline, original.coordinates(), List.of(original.children()));
    }

    private static CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> scale(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            float factor
    ) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> constant) {
            return CubicSpline.constant(constant.value() * factor);
        }
        CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points = multipoint(spline);
        List<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> values =
                new ArrayList<>(points.values().size());
        for (CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> value : points.values()) {
            values.add(scale(value, factor));
        }
        float[] derivatives = points.derivatives().clone();
        for (int i = 0; i < derivatives.length; ++i) derivatives[i] *= factor;
        float min = points.minValue() * factor;
        float max = points.maxValue() * factor;
        return new CubicSpline.Multipoint<>(points.coordinate(), points.locations().clone(), values,
                derivatives, Math.min(min, max), Math.max(min, max));
    }

    private static CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> offset(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            float amount
    ) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> constant) {
            return CubicSpline.constant(constant.value() + amount);
        }
        CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points = multipoint(spline);
        List<CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>> values =
                new ArrayList<>(points.values().size());
        for (CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> value : points.values()) {
            values.add(offset(value, amount));
        }
        return new CubicSpline.Multipoint<>(points.coordinate(), points.locations().clone(), values,
                points.derivatives().clone(), points.minValue() + amount, points.maxValue() + amount);
    }

    @SuppressWarnings("unchecked")
    private static CubicSpline.Multipoint<DensityFunctions.Spline.Point,
            DensityFunctions.Spline.Coordinate> multipoint(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline
    ) {
        if (spline instanceof CubicSpline.Multipoint<?, ?> points) {
            return (CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                    DensityFunctions.Spline.Coordinate>) points;
        }
        throw new IllegalArgumentException("Unsupported spline implementation " + spline.getClass().getName());
    }
}
