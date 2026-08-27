package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.access.YAPendingTaskAccess;

import com.moepus.byepregen.mixin.accessor.server.chunk.ChunkMapUnloadAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

final class LightTorchReloadProbe {
    private static final TicketType<ChunkPos> TEST_TICKET =
            TicketType.create("torch_lifecycle_probe", Comparator.comparingLong(ChunkPos::toLong));
    private static final int[][] CENTER_FIRST = {
            {0, 0}, {0, -1}, {-1, 0}, {1, 0}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };
    private static final int[][] NEIGHBORS_FIRST = {
            {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}, {0, 0}
    };
    private static final int[][] RETAIN_CENTER = {
            {0, -1}, {-1, 0}, {1, 0}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };
    private static final int[][] MIXED = {
            {-1, -1}, {0, 0}, {1, 1}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}, {1, -1}, {-1, 1}
    };

    private final ServerLevel level;
    private final ChunkPos center;
    private final Set<ChunkPos> forced = new HashSet<>();
    private final Set<ChunkPos> unloading = new HashSet<>();
    private List<ChunkPos> order = List.of();
    private ChunkPos waitingChunk;
    private int loadIndex;

    LightTorchReloadProbe(ServerLevel level, ChunkPos center) {
        this.level = level;
        this.center = center;
    }

    void loadAll() {
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                this.load(new ChunkPos(this.center.x + dx, this.center.z + dz));
            }
        }
    }

    void beginUnload(int plan) {
        this.unloading.clear();
        boolean retainCenter = plan == 2;
        for (ChunkPos pos : List.copyOf(this.forced)) {
            if (retainCenter && pos.equals(this.center)) {
                continue;
            }
            this.level.getChunkSource().removeRegionTicket(TEST_TICKET, pos, 0, pos);
            this.forced.remove(pos);
            this.unloading.add(pos);
        }
    }

    boolean isUnloaded() {
        var pending = ((ChunkMapUnloadAccessor)this.level.getChunkSource().chunkMap).byepregen$getPendingUnloads();
        for (ChunkPos pos : this.unloading) {
            if (this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null
                    || pending.containsKey(pos.toLong())) {
                return false;
            }
        }
        return true;
    }

    void beginReload(int plan) {
        int[][] offsets = switch (plan) {
            case 0 -> CENTER_FIRST;
            case 1 -> NEIGHBORS_FIRST;
            case 2 -> RETAIN_CENTER;
            default -> MIXED;
        };
        List<ChunkPos> chunks = new ArrayList<>(offsets.length);
        for (int[] offset : offsets) {
            chunks.add(new ChunkPos(this.center.x + offset[0], this.center.z + offset[1]));
        }
        this.order = chunks;
        this.loadIndex = 0;
    }

    boolean loadNext() {
        if (this.loadIndex >= this.order.size()) {
            return false;
        }
        this.waitingChunk = this.order.get(this.loadIndex++);
        this.load(this.waitingChunk);
        return true;
    }

    boolean hasMore() {
        return this.loadIndex < this.order.size();
    }

    boolean centerLoaded() {
        return this.level.getChunkSource().getChunkNow(this.center.x, this.center.z) != null;
    }

    CompletableFuture<Void> waitForLight() {
        CompletableFuture<?> current = this.wait(this.waitingChunk);
        if (this.waitingChunk.equals(this.center) || !this.centerLoaded()) {
            return CompletableFuture.allOf(current);
        }
        return CompletableFuture.allOf(current, this.wait(this.center));
    }

    CompletableFuture<Void> waitForAllLight() {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[9];
        int index = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                futures[index++] = this.wait(new ChunkPos(this.center.x + dx, this.center.z + dz));
            }
        }
        return CompletableFuture.allOf(futures);
    }

    String waitingDescription() {
        return this.waitingChunk == null ? "none" : this.waitingChunk.toString();
    }

    private CompletableFuture<?> wait(ChunkPos pos) {
        return ((YAPendingTaskAccess) this.level.getChunkSource().getLightEngine())
                .byepregen$waitForPendingTasks(pos.x, pos.z);
    }

    private void load(ChunkPos pos) {
        this.forced.add(pos);
        this.level.getChunkSource().addRegionTicket(TEST_TICKET, pos, 0, pos);
        this.level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
    }
}
