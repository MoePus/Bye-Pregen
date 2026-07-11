package com.moepus.byepregen.jfr;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

public final class ByepregenJfrEvents {
    private static final EventType SYNC_CHUNK_WAIT = EventType.getEventType(SyncChunkWaitEvent.class);

    private ByepregenJfrEvents() {
    }

    public static SyncChunkWaitEvent beginSyncChunkWait(
            int chunkX,
            int chunkZ,
            String status,
            boolean requireChunk,
            boolean c2me
    ) {
        if (!SYNC_CHUNK_WAIT.isEnabled()) {
            return null;
        }
        SyncChunkWaitEvent event = new SyncChunkWaitEvent();
        event.chunkX = chunkX;
        event.chunkZ = chunkZ;
        event.status = status;
        event.requireChunk = requireChunk;
        event.c2me = c2me;
        event.begin();
        return event;
    }

    public static void commit(SyncChunkWaitEvent event, boolean futureDone) {
        if (event != null) {
            event.futureDone = futureDone;
            event.commit();
        }
    }

    @Name("com.moepus.byepregen.SyncChunkWait")
    @Label("ByePregen Sync Chunk Wait")
    @Category({"ByePregen", "Chunk"})
    @Enabled(true)
    @StackTrace(false)
    @Threshold("0 ns")
    public static final class SyncChunkWaitEvent extends Event {
        @Label("Chunk X")
        public int chunkX;
        @Label("Chunk Z")
        public int chunkZ;
        @Label("Status")
        public String status;
        @Label("Require Chunk")
        public boolean requireChunk;
        @Label("C2ME")
        public boolean c2me;
        @Label("Future Done")
        public boolean futureDone;
    }
}
