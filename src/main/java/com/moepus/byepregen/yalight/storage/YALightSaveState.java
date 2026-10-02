package com.moepus.byepregen.yalight.storage;

/** Chunk-local lifecycle shared by both layers. No persisted state and no per-block bookkeeping. */
public final class YALightSaveState {
    public static final long UNAVAILABLE = -1L;

    private long revision;
    private int pending;
    private boolean failed;

    public synchronized void begin() {
        ++this.revision;
        ++this.pending;
    }

    public synchronized void finish(boolean success) {
        if (this.pending == 0) {
            throw new IllegalStateException("Unbalanced YA light save lifecycle");
        }
        --this.pending;
        ++this.revision;
        this.failed |= !success;
    }

    public synchronized long captureRevision() {
        return this.pending == 0 && !this.failed ? this.revision : UNAVAILABLE;
    }

    public synchronized boolean isCurrent(long captured) {
        return captured != UNAVAILABLE && this.pending == 0 && !this.failed && this.revision == captured;
    }
}
