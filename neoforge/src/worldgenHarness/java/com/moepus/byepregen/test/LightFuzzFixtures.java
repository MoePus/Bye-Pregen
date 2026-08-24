package com.moepus.byepregen.test;

import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class LightFuzzFixtures {
    private LightFuzzFixtures() {
    }

    static LightFuzzFixture create(ServerLevel level, String variant, long seed) {
        return switch (variant) {
            case "default" -> new DefaultLightFuzzFixture(level, seed);
            case "blackout" -> new BlackoutLightFuzzFixture(level, seed);
            case "edges" -> new EdgeLightFuzzFixture(level);
            case "stress" -> new StressLightFuzzFixture(level, seed);
            case "dirty_columns" -> new DirtyColumnLightFuzzFixture(level);
            case "all" -> new CompositeLightFuzzFixture(List.of(
                    new DefaultLightFuzzFixture(level, seed),
                    new EdgeLightFuzzFixture(level),
                    new StressLightFuzzFixture(level, seed)
            ));
            default -> null;
        };
    }

    private record CompositeLightFuzzFixture(List<LightFuzzFixture> fixtures) implements LightFuzzFixture {
        @Override
        public void clearVolume() {
            this.fixtures.forEach(LightFuzzFixture::clearVolume);
        }

        @Override
        public void buildFixture() {
            this.fixtures.forEach(LightFuzzFixture::buildFixture);
        }

        @Override
        public void applyUpdate(int round) {
            this.fixtures.forEach(fixture -> fixture.applyUpdate(round));
        }
    }
}
