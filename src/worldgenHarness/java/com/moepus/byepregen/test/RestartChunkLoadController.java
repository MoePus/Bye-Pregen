package com.moepus.byepregen.test;

import java.util.Comparator;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

interface RestartChunkLoadController extends AutoCloseable {
    void tick();

    @Override
    void close();

    static RestartChunkLoadController create(ServerLevel level, ChunkPos center, int radius) {
        return new RegionTicket(level.getChunkSource(), center, radius);
    }

    final class RegionTicket implements RestartChunkLoadController {
        private static final TicketType<ChunkPos> TYPE =
                TicketType.create("byepregen_light_restart", Comparator.comparingLong(ChunkPos::toLong));
        private final ServerChunkCache chunks;
        private final ChunkPos center;
        private final int radius;

        private RegionTicket(ServerChunkCache chunks, ChunkPos center, int radius) {
            this.chunks = chunks;
            this.center = center;
            this.radius = radius;
            chunks.addRegionTicket(TYPE, center, radius, center);
        }

        @Override
        public void tick() {
        }

        @Override
        public void close() {
            this.chunks.removeRegionTicket(TYPE, this.center, this.radius, this.center);
        }
    }

}
