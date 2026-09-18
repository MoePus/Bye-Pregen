package com.moepus.byepregen.dfc;

import com.mojang.serialization.MapCodec;
import com.moepus.byepregen.api.dfc.ColumnDensityFunctionRegistry;
import com.moepus.byepregen.dfc.compile.DensitySamplerCompiler;
import com.moepus.byepregen.dfc.runtime.ColumnDensitySampler;
import com.moepus.byepregen.dfc.runtime.ColumnSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RegisteredDelegateTest {
    private static final DensityVolume VOLUME = new DensityVolume(3, 32, 2, -3, -8, 5);

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void explicitTwoDimensionalPromiseCollapsesYAndRetainsAtoBtoAResults() {
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredFunction.class);
        CountingSampler source = new CountingSampler();
        sampleColumns(new RegisteredFunction(source));
        assertEquals(VOLUME.sizeX() * VOLUME.sizeZ(), source.positions);
        assertEquals(1, source.requests.size());
        DensityVolume request = source.requests.getFirst();
        assertEquals(1, request.sizeY());
        assertEquals(VOLUME.sizeX(), request.sizeX());
        assertEquals(VOLUME.sizeZ(), request.sizeZ());
    }

    @Test void unregisteredDelegateKeepsTheOriginalVolumeContract() {
        CountingSampler source = new CountingSampler();
        sampleColumns(new UnregisteredFunction(source));
        assertEquals(VOLUME.size(), source.positions);
        assertEquals(List.of(VOLUME), source.requests);
    }

    @Test void registeredRootRetainsItsPromiseWithoutAnArithmeticParent() {
        ColumnDensityFunctionRegistry.registerYIndependentDelegate(RegisteredFunction.class);
        CountingSampler source = new CountingSampler();
        var root = assertInstanceOf(ColumnDensitySampler.class,
                DensitySamplerCompiler.compile(new RegisteredFunction(source), DfcFixtures.CONTEXT));
        float[] a = new float[VOLUME.sizeY()], b = a.clone(), again = a.clone();
        try (ColumnSession session = root.openColumns(SamplerContext.EMPTY_UNCACHED, VOLUME)) {
            session.evalColumn(0, 0, a);
            session.evalColumn(1, 0, b);
            session.evalColumn(0, 0, again);
        }
        assertArrayEquals(a, again);
        assertEquals(6, source.positions);
        for (int y = 0; y < a.length; ++y) assertEquals(a[y] + 1, b[y]);

        source.positions = 0;
        source.requests.clear();
        DensityBuffer output = ColumnTestSupport.sample(root, SamplerContext.EMPTY_UNCACHED, VOLUME);
        assertEquals(6, source.positions);
        assertEquals(1, source.requests.getFirst().sizeY());
        for (int z = 0; z < VOLUME.sizeZ(); ++z) {
            for (int x = 0; x < VOLUME.sizeX(); ++x) {
                for (int y = 0; y < VOLUME.sizeY(); ++y) {
                    assertEquals(VOLUME.blockX(x) + VOLUME.blockZ(z) * 2,
                            output.get(VOLUME.indexUnchecked(x, y, z)));
                }
            }
        }
    }

    private static void sampleColumns(DensityFunction function) {
        assertEquals(DensityFunction.ALL_AXES, function.domainAxes());
        DensityFunction graph = function.add(DensityFunctions.yClampedGradient(-8, 23, 0, 1));
        var compiled = assertInstanceOf(ColumnDensitySampler.class,
                DensitySamplerCompiler.compile(graph, DfcFixtures.CONTEXT));
        float[] a = new float[VOLUME.sizeY()], b = a.clone(), again = a.clone();
        try (ColumnSession session = compiled.openColumns(SamplerContext.EMPTY_UNCACHED, VOLUME)) {
            session.evalColumn(0, 0, a);
            session.evalColumn(1, 0, b);
            session.evalColumn(0, 0, again);
        }
        assertArrayEquals(a, again);
        for (int y = 0; y < a.length; ++y) {
            assertEquals(VOLUME.minBlockX() + VOLUME.minBlockZ() * 2 + y / 31.0F, a[y], 1.0e-6F);
            assertEquals(a[y] + 1, b[y], 1.0e-6F);
        }
    }

    private interface ConservativeFunction extends DensityFunction {
        CountingSampler sampler();
        @Override default DensitySampler compileSampler(CompileContext context) { return this.sampler(); }
        @Override default DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }
        @Override default Interval range() { return Interval.INFINITE; }
        @Override default int domainAxes() { return ALL_AXES; }
        @Override default MapCodec<? extends DensityFunction> codec() { return MapCodec.unit(this); }
    }

    private record RegisteredFunction(CountingSampler sampler) implements ConservativeFunction { }
    private record UnregisteredFunction(CountingSampler sampler) implements ConservativeFunction { }

    private static final class CountingSampler implements DensitySampler {
        private int positions;
        private final List<DensityVolume> requests = new ArrayList<>();
        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            ++this.positions;
            return x + z * 2;
        }
        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            this.requests.add(volume);
            DensitySampler.sampleVolumeNaive(context, output, volume, this);
        }
    }
}
