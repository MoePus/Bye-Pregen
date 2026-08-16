package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.config.ConfigParser;
import com.moepus.byepregen.serialization.nbt.NbtWriter;

final class ChunkSavingNbtWriterCache {
    static final long MAX_RETAINED_CAPACITY = 512L * 1024L;
    private static final ThreadLocal<Slot> CACHE = ThreadLocal.withInitial(Slot::new);

    private ChunkSavingNbtWriterCache() {}

    static Lease acquire() {
        return acquire(ConfigParser.getConfig().retainChunkSavingBuffer);
    }

    static Lease acquire(boolean retainBuffer) {
        if (!retainBuffer) {
            return new Lease(new NbtWriter(), null);
        }

        Slot slot = CACHE.get();
        if (slot.inUse) {
            return new Lease(new NbtWriter(), null);
        }
        if (slot.writer == null) {
            slot.writer = new NbtWriter();
            slot.writer.enableAutomaticRelease();
        } else {
            slot.writer.reset();
        }
        slot.inUse = true;
        return new Lease(slot.writer, slot);
    }

    static final class Lease implements AutoCloseable {
        private final NbtWriter writer;
        private final Slot slot;
        private boolean closed;

        private Lease(NbtWriter writer, Slot slot) {
            this.writer = writer;
            this.slot = slot;
        }

        NbtWriter writer() {
            return this.writer;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.slot == null) {
                this.writer.release();
                return;
            }

            this.slot.inUse = false;
            if (this.writer.capacity() > MAX_RETAINED_CAPACITY) {
                this.writer.release();
                this.slot.writer = null;
                return;
            }
            this.writer.reset();
        }
    }

    private static final class Slot {
        private NbtWriter writer;
        private boolean inUse;
    }
}
