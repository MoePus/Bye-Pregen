package com.moepus.byepregen.worldgen.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

final class SurfaceBiomeCertificateBuilder {
    private final Holder<Biome>[] flatBiomes;
    private final int quartHeight;

    private SurfaceBiomeCertificateBuilder(Holder<Biome>[] flatBiomes, int quartHeight) {
        this.flatBiomes = flatBiomes;
        this.quartHeight = quartHeight;
    }

    static SurfaceBiomeCertificates build(Holder<Biome>[] flatBiomes, int quartHeight) {
        return new SurfaceBiomeCertificateBuilder(flatBiomes, quartHeight).build();
    }

    private SurfaceBiomeCertificates build() {
        int certificateHeight = this.quartHeight + 1;
        short[] values = new short[
                SurfaceBiomeLookup.INTERIOR_QUART_CUBES
                        * SurfaceBiomeLookup.INTERIOR_QUART_CUBES
                        * certificateHeight
        ];
        int uniformCount = 0;
        for (int x = 0; x < SurfaceBiomeLookup.INTERIOR_QUART_CUBES; x++) {
            for (int z = 0; z < SurfaceBiomeLookup.INTERIOR_QUART_CUBES; z++) {
                uniformCount += this.buildColumn(values, x, z);
            }
        }
        return new SurfaceBiomeCertificates(values, certificateHeight, uniformCount);
    }

    private int buildColumn(short[] values, int x, int z) {
        int uniformCount = 0;
        for (int cubeY = 0; cubeY <= this.quartHeight; cubeY++) {
            int lowerY = Math.clamp(cubeY - 1, 0, this.quartHeight - 1);
            int firstIndex = this.flatIndex(x, lowerY, z);
            Holder<Biome> first = this.flatBiomes[firstIndex];
            int index = this.certificateIndex(x, cubeY, z);
            if (this.allCornersMatch(first, x, z, cubeY)) {
                values[index] = (short) firstIndex;
                uniformCount++;
            } else {
                values[index] = SurfaceBiomeCertificates.NON_UNIFORM;
            }
        }
        return uniformCount;
    }

    private boolean allCornersMatch(Holder<Biome> first, int x, int z, int cubeY) {
        int lowerY = Math.clamp(cubeY - 1, 0, this.quartHeight - 1);
        int upperY = Math.clamp(cubeY, 0, this.quartHeight - 1);
        for (int cornerX = x; cornerX <= x + 1; cornerX++) {
            for (int cornerZ = z; cornerZ <= z + 1; cornerZ++) {
                if (first != this.biomeAt(cornerX, lowerY, cornerZ)
                        || first != this.biomeAt(cornerX, upperY, cornerZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Holder<Biome> biomeAt(int x, int y, int z) {
        return this.flatBiomes[this.flatIndex(x, y, z)];
    }

    private int flatIndex(int x, int y, int z) {
        return ((x * SurfaceBiomeLookup.QUARTS_PER_CHUNK + z) * this.quartHeight) + y;
    }

    private int certificateIndex(int x, int y, int z) {
        return ((x * SurfaceBiomeLookup.INTERIOR_QUART_CUBES + z) * (this.quartHeight + 1)) + y;
    }
}

record SurfaceBiomeCertificates(short[] values, int certificateHeight, int uniformCount) {
    static final short NON_UNIFORM = -1;

    int at(int x, int y, int z) {
        int index = ((x * SurfaceBiomeLookup.INTERIOR_QUART_CUBES + z) * this.certificateHeight) + y;
        return this.values[index];
    }

    int count() {
        return this.values.length;
    }
}
