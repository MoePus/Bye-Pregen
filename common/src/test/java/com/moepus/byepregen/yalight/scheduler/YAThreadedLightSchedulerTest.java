package com.moepus.byepregen.yalight.scheduler;

import com.moepus.byepregen.yalight.storage.YALightSaveState;
import net.minecraft.server.level.ThreadedLevelLightEngine.TaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YAThreadedLightSchedulerTest {
    private final YALightSaveState state = new YALightSaveState();
    private Runnable duringUpdates = () -> {};
    private int resolved;
    private final YAThreadedLightScheduler scheduler = new YAThreadedLightScheduler(
            key -> {}, key -> {}, key -> { ++this.resolved; return this.state; },
            () -> { this.duringUpdates.run(); return 0; });

    @Test
    void mergesTrackingAndKeepsPendingThroughAllPreOperations() {
        this.duringUpdates = () -> assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
        Runnable assertPending = () -> assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE, assertPending, false);
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE, assertPending, false);
        this.scheduler.enqueue(0, TaskType.POST_UPDATE,
                () -> assertNotEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision()), false);
        assertEquals(1, this.resolved);
        this.scheduler.drain(1);
        assertNotEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
    }

    @Test
    void postOnlyBarrierDoesNotInvalidateSave() {
        this.scheduler.enqueue(0, TaskType.POST_UPDATE, () -> {}, false);
        assertEquals(0, this.resolved);
        assertNotEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
        this.scheduler.drain(1);
    }

    @Test
    void aLaterBatchCannotBeCompletedByAnEarlierBatch() {
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE,
                () -> this.scheduler.enqueue(0, TaskType.PRE_UPDATE, () -> {}, false), false);
        this.scheduler.drain(1);
        assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
        this.scheduler.drain(1);
        assertNotEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
    }

    @Test
    void failedWorkNeverMakesSaveTrusted() {
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE, () -> { throw new IllegalStateException("test"); }, false);
        assertThrows(IllegalStateException.class, () -> this.scheduler.drain(1));
        assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE, () -> {}, false);
        this.scheduler.drain(1);
        assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
    }

    @Test
    void propagationFailureKeepsSaveUntrusted() {
        this.duringUpdates = () -> { throw new IllegalStateException("propagation failed"); };
        this.scheduler.enqueue(0, TaskType.PRE_UPDATE, () -> {}, false);
        assertThrows(IllegalStateException.class, () -> this.scheduler.drain(1));
        assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision());
    }

    @Test
    void lightChunkTracksSuppliedOwnerWithoutCoordinateLookup() {
        this.scheduler.enqueueLightChunk(0,
                () -> assertEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision()),
                () -> assertNotEquals(YALightSaveState.UNAVAILABLE, this.state.captureRevision()), this.state);
        this.scheduler.drain(1);
        assertEquals(0, this.resolved);
    }
}
