package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.moepus.byepregen.dfc.ast.AstNodes;
import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

final class SplineCoordinateCollectionTest {
    @Test
    void coordinatesFollowFirstOccurrenceTraversalOrder() {
        DensityFunctions.Spline.Coordinate first = SplineTestFixtures.coordinate();
        DensityFunctions.Spline.Coordinate second = SplineTestFixtures.coordinate();
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> nested =
                multipoint(second, List.of(multipoint(first, List.of(CubicSpline.constant(1.0F)))));
        CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> root =
                multipoint(first, List.of(nested, CubicSpline.constant(2.0F)));

        List<DensityFunctions.Spline.Coordinate> coordinates =
                AstNodes.collectSplineCoordinates(root);

        assertEquals(2, coordinates.size());
        assertSame(first, coordinates.get(0));
        assertSame(second, coordinates.get(1));
    }

    private static CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate>
    multipoint(
            DensityFunctions.Spline.Coordinate coordinate,
            List<CubicSpline<DensityFunctions.Spline.Point,
                    DensityFunctions.Spline.Coordinate>> values
    ) {
        int size = values.size();
        float[] locations = new float[size];
        float[] derivatives = new float[size];
        for (int i = 0; i < size; ++i) locations[i] = i;
        return new CubicSpline.Multipoint<>(coordinate, locations, values, derivatives,
                values.get(0).minValue(), values.get(size - 1).maxValue());
    }
}
