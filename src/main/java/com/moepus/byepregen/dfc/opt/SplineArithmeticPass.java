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
import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;

final class SplineArithmeticPass {
    private SplineArithmeticPass() {
    }

    static AstNode apply(AstNode root) {
        return AstRewriter.rewrite(root, SplineArithmeticPass::rewrite);
    }

    private static AstNode rewrite(AstNode node) {
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
