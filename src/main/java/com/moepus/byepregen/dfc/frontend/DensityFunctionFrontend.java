/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.frontend;

import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.ast.AstNodes;
import com.moepus.byepregen.dfc.ast.AstNodes.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** Converts a seed-bound vanilla density graph to ByePregen's immutable column AST. */
public final class DensityFunctionFrontend {
    private final Map<DensityFunction, AstNode> memo = new IdentityHashMap<>();

    public AstNode convert(DensityFunction function) {
        AstNode existing = this.memo.get(function);
        if (existing != null) return existing;
        AstNode result = this.convertNew(function);
        this.memo.put(function, result);
        return result;
    }

    private AstNode convertNew(DensityFunction function) {
        if (function instanceof DensityFunctions.HolderHolder holder) {
            return this.convert(holder.function().value());
        }
        if (function instanceof DensityFunctions.Constant constant) {
            return new ConstantNode(constant.value());
        }
        if (function instanceof DensityFunctions.TwoArgumentSimpleFunction binary) {
            return this.binary(binary);
        }
        if (function instanceof DensityFunctions.Mapped mapped) return this.mapped(mapped);
        if (function instanceof DensityFunctions.Clamp clamp) return this.clamp(clamp);
        if (function instanceof DensityFunctions.Marker marker) return this.marker(marker);
        if (function instanceof DensityFunctions.Noise noise) return this.noise(noise);
        if (function instanceof DensityFunctions.ShiftedNoise noise) return this.shiftedNoise(noise);
        if (function instanceof DensityFunctions.ShiftA shift) return this.shiftA(shift);
        if (function instanceof DensityFunctions.ShiftB shift) return this.shiftB(shift);
        if (function instanceof DensityFunctions.Shift shift) return this.shift(shift);
        if (function instanceof DensityFunctions.RangeChoice range) return this.range(range);
        if (function instanceof DensityFunctions.YClampedGradient gradient) return this.gradient(gradient);
        if (function instanceof DensityFunctions.WeirdScaledSampler weird) return this.weird(weird);
        if (function instanceof DensityFunctions.Spline spline) return this.spline(spline);
        if (function instanceof DensityFunctions.BlendDensity blend) return this.convert(blend.input());
        if (function instanceof DensityFunctions.BlendAlpha) return new ConstantNode(1.0D);
        if (function instanceof DensityFunctions.BlendOffset) return new ConstantNode(0.0D);
        return new DelegateNode(function, ColumnDensityFunctionRegistry.isYIndependentDelegate(function));
    }

    private AstNode binary(DensityFunctions.TwoArgumentSimpleFunction function) {
        AstNode left = this.convert(function.argument1());
        AstNode right = this.convert(function.argument2());
        DensityFunctions.TwoArgumentSimpleFunction.Type type = function.type();
        if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.ADD) return new AddNode(left, right);
        if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MUL) return new MulNode(left, right);
        if (type == DensityFunctions.TwoArgumentSimpleFunction.Type.MIN) {
            double rightMin = function.argument2().minValue();
            return function.argument1().minValue() < rightMin
                    ? new MinShortNode(left, right, rightMin)
                    : new MinNode(left, right);
        }
        double rightMax = function.argument2().maxValue();
        return function.argument1().maxValue() > rightMax
                ? new MaxShortNode(left, right, rightMax)
                : new MaxNode(left, right);
    }

    private AstNode mapped(DensityFunctions.Mapped function) {
        AstNode input = this.convert(function.input());
        DensityFunctions.Mapped.Type type = function.type();
        if (type == DensityFunctions.Mapped.Type.ABS) return new AbsNode(input);
        if (type == DensityFunctions.Mapped.Type.SQUARE) return new SquareNode(input);
        if (type == DensityFunctions.Mapped.Type.CUBE) return new CubeNode(input);
        if (type == DensityFunctions.Mapped.Type.HALF_NEGATIVE) return new NegMulNode(input, 0.5D);
        if (type == DensityFunctions.Mapped.Type.QUARTER_NEGATIVE) return new NegMulNode(input, 0.25D);
        return new SqueezeNode(input);
    }

    private AstNode clamp(DensityFunctions.Clamp function) {
        AstNode input = this.convert(function.input());
        AstNode upper = new MinNode(new ConstantNode(function.maxValue()), input);
        return new MaxNode(new ConstantNode(function.minValue()), upper);
    }

    private AstNode marker(DensityFunctions.Marker marker) {
        CacheKind kind = switch (marker.type()) {
            case Cache2D -> CacheKind.CACHE_2D;
            case CacheOnce -> CacheKind.CACHE_ONCE;
            case CacheAllInCell -> CacheKind.CACHE_ALL_IN_CELL;
            case FlatCache -> CacheKind.FLAT_CACHE;
            case Interpolated -> CacheKind.INTERPOLATED;
        };
        return new CacheNode(marker, kind, this.convert(marker.wrapped()));
    }

    private AstNode noise(DensityFunctions.Noise function) {
        return new NoiseNode(
                scaledCoordinate(Axis.X, function.xzScale()),
                scaledCoordinate(Axis.Y, function.yScale()),
                scaledCoordinate(Axis.Z, function.xzScale()),
                function.noise());
    }

    private AstNode shiftedNoise(DensityFunctions.ShiftedNoise function) {
        return new NoiseNode(
                add(scaledCoordinate(Axis.X, function.xzScale()), this.convert(function.shiftX())),
                add(scaledCoordinate(Axis.Y, function.yScale()), this.convert(function.shiftY())),
                add(scaledCoordinate(Axis.Z, function.xzScale()), this.convert(function.shiftZ())),
                function.noise());
    }

    private AstNode shiftA(DensityFunctions.ShiftA function) {
        return scaledNoise(function.offsetNoise(), Axis.X, null, Axis.Z);
    }

    private AstNode shiftB(DensityFunctions.ShiftB function) {
        return scaledNoise(function.offsetNoise(), Axis.Z, Axis.X, null);
    }

    private AstNode shift(DensityFunctions.Shift function) {
        return scaledNoise(function.offsetNoise(), Axis.X, Axis.Y, Axis.Z);
    }

    private static AstNode scaledNoise(
            DensityFunction.NoiseHolder noise, Axis x, Axis y, Axis z
    ) {
        AstNode zero = new ConstantNode(0.0D);
        AstNode sampled = new NoiseNode(quarter(x, zero), quarter(y, zero), quarter(z, zero), noise);
        return new MulNode(new ConstantNode(4.0D), sampled);
    }

    private AstNode range(DensityFunctions.RangeChoice function) {
        return new RangeChoiceNode(this.convert(function.input()), function.minInclusive(),
                function.maxExclusive(), this.convert(function.whenInRange()),
                this.convert(function.whenOutOfRange()));
    }

    private static AstNode gradient(DensityFunctions.YClampedGradient function) {
        return new YClampedGradientNode(function.fromY(), function.toY(),
                function.fromValue(), function.toValue());
    }

    private AstNode weird(DensityFunctions.WeirdScaledSampler function) {
        return new WeirdScaledNode(this.convert(function.input()), function.noise(),
                function.rarityValueMapper());
    }

    private AstNode spline(DensityFunctions.Spline function) {
        List<DensityFunctions.Spline.Coordinate> keys =
                AstNodes.collectSplineCoordinates(function.spline());
        List<AstNode> children = new ArrayList<>(keys.size());
        for (DensityFunctions.Spline.Coordinate coordinate : keys) {
            children.add(this.convert(coordinate.function().value()));
        }
        return new SplineNode(function.spline(), keys, children);
    }

    private static AstNode scaledCoordinate(Axis axis, double scale) {
        AstNode coordinate = new CoordinateNode(axis);
        return scale == 1.0D ? coordinate : new MulNode(new ConstantNode(scale), coordinate);
    }

    private static AstNode quarter(Axis axis, AstNode zero) {
        return axis == null ? zero : new MulNode(new ConstantNode(0.25D), new CoordinateNode(axis));
    }

    private static AstNode add(AstNode left, AstNode right) {
        return right instanceof ConstantNode constant && constant.value() == 0.0D
                ? left : new AddNode(left, right);
    }
}
