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

    public static void commitSurfaceBiomeProfile(SurfaceBiomeProfile profile) {
        SurfaceBiomeProfileEvent event = new SurfaceBiomeProfileEvent();
        event.chunkX = profile.chunkX();
        event.chunkZ = profile.chunkZ();
        event.certificates = profile.certificates();
        event.uniformCertificates = profile.uniformCertificates();
        event.queries = profile.queries();
        event.interiorQueries = profile.interiorQueries();
        event.uniformHits = profile.uniformHits();
        event.flatSlowLookups = profile.flatSlowLookups();
        event.delegateLookups = profile.delegateLookups();
        event.commit();
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

    @Name("com.moepus.byepregen.SurfaceBiomeProfile")
    @Label("ByePregen Surface Biome Profile")
    @Category({"ByePregen", "Worldgen"})
    @Enabled(true)
    @StackTrace(false)
    public static final class SurfaceBiomeProfileEvent extends Event {
        @Label("Chunk X")
        public int chunkX;
        @Label("Chunk Z")
        public int chunkZ;
        @Label("Certificates")
        public int certificates;
        @Label("Uniform Certificates")
        public int uniformCertificates;
        @Label("Biome Queries")
        public long queries;
        @Label("Interior Queries")
        public long interiorQueries;
        @Label("Uniform Fast Hits")
        public long uniformHits;
        @Label("Flat Slow Lookups")
        public long flatSlowLookups;
        @Label("Delegate Lookups")
        public long delegateLookups;
    }

    public record SurfaceBiomeProfile(
            int chunkX,
            int chunkZ,
            int certificates,
            int uniformCertificates,
            long queries,
            long interiorQueries,
            long uniformHits,
            long flatSlowLookups,
            long delegateLookups
    ) {
    }
}
