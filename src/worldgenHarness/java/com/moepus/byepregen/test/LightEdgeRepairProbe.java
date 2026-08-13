package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.YALightEngine;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

final class LightEdgeRepairProbe {
    private final ServerLevel level;
    private BlockPos position;
    private int expectedLight;
    private boolean injected;

    LightEdgeRepairProbe(ServerLevel level) {
        this.level = level;
    }

    void injectOnce(int roofY) {
        if (this.injected) {
            return;
        }
        this.injected = true;
        if (!(this.level.getChunkSource().getLightEngine() instanceof YALightEngineHolder holder)) {
            return;
        }
        this.position = this.findPosition(roofY);
        this.expectedLight = this.light(this.position);
        YALightEngine engine = holder.byepregen$getYALightEngine();
        SectionPos section = SectionPos.of(this.position);
        DataLayer original = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(section);
        if (original == null) {
            throw new IllegalStateException("Missing block-light layer for edge repair probe " + this.position);
        }
        DataLayer corrupted = original.copy();
        corrupted.set(this.position.getX() & 15, this.position.getY() & 15, this.position.getZ() & 15, 0);
        engine.queueOwnedSectionBytes(LightLayer.BLOCK, section, corrupted.getData());
        engine.runLightUpdates();
        if (this.light(this.position) != 0) {
            throw new IllegalStateException("Failed to inject edge repair probe at " + this.position);
        }
    }

    void verifyRepaired() {
        if (this.position == null) {
            return;
        }
        int actual = this.light(this.position);
        if (actual != this.expectedLight) {
            throw new IllegalStateException("Edge repair probe mismatch at " + this.position
                    + " expected=" + this.expectedLight + " actual=" + actual);
        }
    }

    private BlockPos findPosition(int roofY) {
        ChunkPos chunk = new ChunkPos(0, 0);
        for (int y = roofY - 18; y < roofY; ++y) {
            for (int along = 0; along < 16; ++along) {
                BlockPos position = this.litPosition(chunk.getMinBlockX(), y, chunk.getMinBlockZ() + along);
                if (position != null) {
                    return position;
                }
                position = this.litPosition(chunk.getMaxBlockX(), y, chunk.getMinBlockZ() + along);
                if (position != null) {
                    return position;
                }
                position = this.litPosition(chunk.getMinBlockX() + along, y, chunk.getMinBlockZ());
                if (position != null) {
                    return position;
                }
                position = this.litPosition(chunk.getMinBlockX() + along, y, chunk.getMaxBlockZ());
                if (position != null) {
                    return position;
                }
            }
        }
        throw new IllegalStateException("No lit boundary position available for edge repair probe");
    }

    private BlockPos litPosition(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        int light = this.light(position);
        return light > 0 && light < 15 ? position : null;
    }

    private int light(BlockPos position) {
        return this.level.getBrightness(LightLayer.BLOCK, position);
    }
}
