/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.runtime;

import com.moepus.byepregen.dfc.ast.AstNodes.SplineNode;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** Immutable spline tree using pre-evaluated AST coordinate slots. */
public final class SplineProgram {
    private final Value root;
    private final int coordinateCount;

    private SplineProgram(Value root, int coordinateCount) {
        this.root = root;
        this.coordinateCount = coordinateCount;
    }

    public static SplineProgram compile(SplineNode node) {
        Map<DensityFunctions.Spline.Coordinate, Integer> slots = new IdentityHashMap<>();
        for (int i = 0; i < node.coordinates().size(); ++i) slots.put(node.coordinates().get(i), i);
        return new SplineProgram(compileValue(node.spline(), slots), slots.size());
    }

    public int coordinateCount() {
        return this.coordinateCount;
    }

    public float sample(float[] coordinates) {
        return this.root.sample(coordinates);
    }

    private static Value compileValue(
            CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline,
            Map<DensityFunctions.Spline.Coordinate, Integer> slots
    ) {
        if (spline instanceof CubicSpline.Constant<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> constant) {
            return new ConstantValue(constant.value());
        }
        if (!(spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point,
                DensityFunctions.Spline.Coordinate> points)) {
            throw new IllegalArgumentException("Unsupported spline " + spline.getClass().getName());
        }
        Integer slot = slots.get(points.coordinate());
        if (slot == null) throw new IllegalArgumentException("Missing spline coordinate slot");
        Value[] values = new Value[points.values().size()];
        for (int i = 0; i < values.length; ++i) {
            values[i] = compileValue(points.values().get(i), slots);
        }
        return new MultipointValue(slot, points.locations().clone(), values,
                points.derivatives().clone());
    }

    private static int findRange(float[] locations, float point) {
        int start = 0;
        int remaining = locations.length;
        while (remaining > 0) {
            int half = remaining / 2;
            int middle = start + half;
            if (point < locations[middle]) {
                remaining = half;
            } else {
                start = middle + 1;
                remaining -= half + 1;
            }
        }
        return start - 1;
    }

    private interface Value {
        float sample(float[] coordinates);
    }

    private record ConstantValue(float value) implements Value {
        @Override public float sample(float[] coordinates) { return this.value; }
    }

    private record MultipointValue(
            int coordinateSlot,
            float[] locations,
            Value[] values,
            float[] derivatives
    ) implements Value {
        @Override
        public float sample(float[] coordinates) {
            float point = coordinates[this.coordinateSlot];
            int range = findRange(this.locations, point);
            int last = this.locations.length - 1;
            if (range < 0) return this.outside(point, 0, coordinates);
            if (range == last) return this.outside(point, last, coordinates);
            return this.inside(point, range, coordinates);
        }

        private float outside(float point, int index, float[] coordinates) {
            float value = this.values[index].sample(coordinates);
            float derivative = this.derivatives[index];
            return derivative == 0.0F ? value : value + derivative * (point - this.locations[index]);
        }

        private float inside(float point, int range, float[] coordinates) {
            float location0 = this.locations[range];
            float distance = this.locations[range + 1] - location0;
            float alpha = (point - location0) / distance;
            float value0 = this.values[range].sample(coordinates);
            float value1 = this.values[range + 1].sample(coordinates);
            float delta = value1 - value0;
            float lower = this.derivatives[range] * distance - delta;
            float upper = -this.derivatives[range + 1] * distance + delta;
            return Mth.lerp(alpha, value0, value1)
                    + alpha * (1.0F - alpha) * Mth.lerp(alpha, lower, upper);
        }
    }
}
