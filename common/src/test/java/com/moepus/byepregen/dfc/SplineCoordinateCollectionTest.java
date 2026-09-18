package com.moepus.byepregen.dfc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.moepus.byepregen.dfc.ast.AstNodes;
import java.util.List;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;
import org.junit.jupiter.api.Test;

final class SplineCoordinateCollectionTest {
    @Test
    void coordinatesFollowFirstOccurrenceTraversalOrder() {
        SplineFunction.Coordinate first = SplineTestFixtures.coordinate();
        SplineFunction.Coordinate second = SplineTestFixtures.coordinate();
        CubicSpline<SplineFunction.Coordinate> nested =
                multipoint(second, List.of(multipoint(first, List.of(CubicSpline.constant(1.0F)))));
        CubicSpline<SplineFunction.Coordinate> root =
                multipoint(first, List.of(nested, CubicSpline.constant(2.0F)));

        List<SplineFunction.Coordinate> coordinates =
                AstNodes.collectSplineCoordinates(root);

        assertEquals(2, coordinates.size());
        assertSame(first, coordinates.get(0));
        assertSame(second, coordinates.get(1));
    }

    private static CubicSpline<SplineFunction.Coordinate>
    multipoint(
            SplineFunction.Coordinate coordinate,
            List<CubicSpline<SplineFunction.Coordinate>> values
    ) {
        int size = values.size();
        float[] locations = new float[size];
        float[] derivatives = new float[size];
        for (int i = 0; i < size; ++i) locations[i] = i;
        // 26.3: CubicSpline.Multipoint dropped its explicit minValue/maxValue constructor arguments.
        return new CubicSpline.Multipoint<>(coordinate, locations, values, derivatives);
    }
}
