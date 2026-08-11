package com.moepus.byepregen.gcfree;

import com.moepus.byepregen.ConfigParser;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

public final class ChunkSavingCompression {
    private static final ThreadLocal<Slot> CACHE = ThreadLocal.withInitial(Slot::new);

    private ChunkSavingCompression() {}

    public static OutputStream wrap(RegionFileVersion version, OutputStream output) throws IOException {
        if (!ConfigParser.getConfig().retainChunkSavingBuffer
                || version != RegionFileVersion.VERSION_DEFLATE) {
            return version.wrap(output);
        }

        Lease lease = acquire();
        try {
            return new BufferedOutputStream(new RetainedDeflaterOutputStream(output, lease));
        } catch (RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    private static Lease acquire() {
        Slot slot = CACHE.get();
        if (slot.inUse) {
            return new Lease(new Deflater(), null);
        }
        if (slot.deflater == null) {
            slot.deflater = new Deflater();
        }
        slot.inUse = true;
        return new Lease(slot.deflater, slot);
    }

    private static final class RetainedDeflaterOutputStream extends DeflaterOutputStream {
        private final Lease lease;
        private boolean closed;

        private RetainedDeflaterOutputStream(OutputStream output, Lease lease) {
            super(output, lease.deflater);
            this.lease = lease;
        }

        @Override
        public void close() throws IOException {
            if (this.closed) {
                return;
            }
            this.closed = true;
            try {
                super.close();
            } catch (IOException | RuntimeException | Error failure) {
                releaseAfterFailure(failure);
                throw failure;
            }
            this.lease.close();
        }

        private void releaseAfterFailure(Throwable failure) {
            try {
                this.lease.close();
            } catch (RuntimeException | Error releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
        }
    }

    private static final class Lease implements AutoCloseable {
        private final Deflater deflater;
        private final Slot slot;
        private boolean closed;

        private Lease(Deflater deflater, Slot slot) {
            this.deflater = deflater;
            this.slot = slot;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.slot == null) {
                this.deflater.end();
                return;
            }

            this.slot.inUse = false;
            if (!ConfigParser.getConfig().retainChunkSavingBuffer) {
                this.deflater.end();
                this.slot.deflater = null;
                return;
            }
            try {
                this.deflater.reset();
            } catch (RuntimeException | Error failure) {
                this.deflater.end();
                this.slot.deflater = null;
                throw failure;
            }
        }
    }

    private static final class Slot {
        private Deflater deflater;
        private boolean inUse;
    }
}
