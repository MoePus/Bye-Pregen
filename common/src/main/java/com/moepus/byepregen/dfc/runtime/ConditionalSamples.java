package com.moepus.byepregen.dfc.runtime;

import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.world.level.levelgen.densityfunction.*;

/** Selected pure Y runs use native bulk calls; only the current 3D column needs scratch. */
final class ConditionalSamples {
    private final SamplerContext context;
    private final DensityVolume volume;
    private final DensitySampler sampler;
    private final boolean yIndependent;
    private final float[] values;
    private final BitSet ready;
    private int columnX = Integer.MIN_VALUE, columnZ = Integer.MIN_VALUE;

    ConditionalSamples(SamplerContext context, DensityVolume volume, NativeDensitySource source) {
        this.context = context;
        this.volume = volume;
        this.sampler = source.sampler();
        this.yIndependent = (source.axes() & DensityFunction.AXIS_Y) == 0;
        this.values = new float[this.yIndependent ? volume.sizeX() * volume.sizeZ() : volume.sizeY()];
        this.ready = new BitSet(this.values.length);
    }

    float value(int x, int y, int z) {
        if (this.yIndependent) return this.value2D(x, z);
        int index = (y - this.volume.minBlockY()) / this.volume.stepBlockY();
        this.prepare(x, z, index, index + 1);
        return this.values[index];
    }

    void copyColumn(int x, int z, float[] target, int from, int to) {
        if (this.yIndependent) Arrays.fill(target, from, to, this.value2D(x, z));
        else {
            this.prepare(x, z, from, to);
            System.arraycopy(this.values, from, target, from, to - from);
        }
    }

    private float value2D(int x, int z) {
        int index = (z - this.volume.minBlockZ()) / this.volume.stepBlockZ() * this.volume.sizeX()
                + (x - this.volume.minBlockX()) / this.volume.stepBlockX();
        if (!this.ready.get(index)) {
            try (ScopedDensityBuffer output = this.context.acquireBuffer(new DensityVolume(1, 1, 1, x, this.volume.minBlockY(), z))) {
                this.sampler.sampleVolume(this.context, output, this.request(x, z, 0, 1));
                this.values[index] = output.get(0);
                this.ready.set(index);
            }
        }
        return this.values[index];
    }

    private void prepare(int x, int z, int from, int to) {
        if (x != this.columnX || z != this.columnZ) {
            this.ready.clear();
            this.columnX = x;
            this.columnZ = z;
        }
        for (int start = this.ready.nextClearBit(from); start < to; start = this.ready.nextClearBit(start)) {
            int occupied = this.ready.nextSetBit(start);
            int end = occupied < 0 ? to : Math.min(occupied, to);
            DensityVolume request = this.request(x, z, start, end - start);
            try (ScopedDensityBuffer output = this.context.acquireBuffer(request)) {
                this.sampler.sampleVolume(this.context, output, request);
                System.arraycopy(output.values, 0, this.values, start, end - start);
                this.ready.set(start, end);
            }
            start = end;
        }
    }

    private DensityVolume request(int x, int z, int from, int size) {
        return new DensityVolume(1, size, 1, x, this.volume.blockY(from), z,
                this.volume.stepBlockX(), this.volume.stepBlockY(), this.volume.stepBlockZ());
    }
}
