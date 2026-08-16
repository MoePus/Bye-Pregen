package com.moepus.byepregen.yalight.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.yalight.engine.YALightMath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

public final class YASkyOwnerTransfersTest {
    @Test
    void holdsDeclaredInboxUntilOwnerIsInitialized() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long owner = owner(2, -3);
        long fromPos = 42L;
        transfers.setDeclaredDomain(List.of(new ChunkPos(2, -3)));

        transfers.enqueue(owner, fromPos, 14, 5);

        assertTrue(transfers.isTransferTarget(transfers.state(owner)));
        assertFalse(transfers.isInitialized(transfers.state(owner)));
        assertEquals(0, transfers.drain());
        assertTrue(propagated.isEmpty());

        transfers.markInitialized(owner);

        assertTrue(transfers.isInitialized(transfers.state(owner)));
        assertEquals(1, transfers.drain());
        assertEquals(List.of(freshTransfer(fromPos, 14, 5)), propagated);
    }

    @Test
    void drainsReadyOwnersBeforeTransientFallback() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long waitingOwner = owner(0, 0);
        long readyOwner = owner(1, 0);
        long transientOwner = owner(8, 8);
        transfers.setDeclaredDomain(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)));
        transfers.register(transientOwner);

        transfers.enqueue(waitingOwner, 10L, 10, 1);
        transfers.enqueue(transientOwner, 20L, 11, 2);
        transfers.markInitialized(readyOwner);
        transfers.enqueue(readyOwner, 30L, 12, 3);

        assertEquals(1, transfers.drain());
        assertEquals(List.of(freshTransfer(30L, 12, 3)), propagated);

        assertEquals(1, transfers.drain());
        assertEquals(new Transfer(20L, 11, 2, 0L), propagated.get(1));

        transfers.markInitialized(waitingOwner);
        assertEquals(1, transfers.drain());
        assertEquals(freshTransfer(10L, 10, 1), propagated.get(2));
    }

    @Test
    void finishRunRemovesTransientStateAndPendingInboxes() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long declaredOwner = owner(4, 5);
        long transientOwner = owner(6, 7);
        transfers.setDeclaredDomain(List.of(new ChunkPos(4, 5), new ChunkPos(4, 5)));
        transfers.register(transientOwner);
        transfers.enqueue(transientOwner, 50L, 8, 4);

        transfers.finishRun();

        assertTrue(transfers.isTransferTarget(transfers.state(declaredOwner)));
        assertFalse(transfers.isTransferTarget(transfers.state(transientOwner)));
        transfers.markInitialized(transientOwner);
        assertEquals(0, transfers.drain());
        assertTrue(propagated.isEmpty());
    }

    @Test
    void replacingDeclaredDomainClearsOldOwnerAndInboxState() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long oldOwner = owner(-1, -2);
        long newOwner = owner(3, 4);
        transfers.setDeclaredDomain(List.of(new ChunkPos(-1, -2)));
        transfers.enqueue(oldOwner, 60L, 7, 0);

        transfers.setDeclaredDomain(List.of(new ChunkPos(3, 4)));

        assertFalse(transfers.isTransferTarget(transfers.state(oldOwner)));
        assertTrue(transfers.isTransferTarget(transfers.state(newOwner)));
        transfers.markInitialized(oldOwner);
        assertEquals(0, transfers.drain());
        assertTrue(propagated.isEmpty());
    }

    @Test
    void removingReadyOwnerKeepsRemainingReadyCountAccurate() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long removedOwner = owner(5, 0);
        long remainingOwner = owner(6, 0);
        transfers.setDeclaredDomain(List.of(new ChunkPos(5, 0), new ChunkPos(6, 0)));
        transfers.markInitialized(removedOwner);
        transfers.markInitialized(remainingOwner);
        transfers.enqueue(removedOwner, 65L, 8, 2);
        transfers.enqueue(remainingOwner, 66L, 9, 3);

        transfers.remove(removedOwner);

        assertFalse(transfers.isTransferTarget(transfers.state(removedOwner)));
        assertEquals(1, transfers.drain());
        assertEquals(List.of(freshTransfer(66L, 9, 3)), propagated);
    }

    @Test
    void preservesInboxOrderAndMakesRegistrationIdempotent() {
        List<Transfer> propagated = new ArrayList<>();
        YASkyOwnerTransfers transfers = transfers(propagated);
        long owner = owner(9, 10);

        transfers.register(owner);
        transfers.register(owner);
        transfers.markInitialized(owner);
        transfers.markInitialized(owner);
        transfers.enqueue(owner, 70L, 1, 0);
        transfers.enqueue(owner, 71L, 15, 5);

        assertEquals(2, transfers.drain());
        assertEquals(List.of(
                freshTransfer(70L, 1, 0),
                freshTransfer(71L, 15, 5)
        ), propagated);

        transfers.finishRun();
        assertFalse(transfers.isTransferTarget(transfers.state(owner)));
    }

    private static YASkyOwnerTransfers transfers(List<Transfer> propagated) {
        return new YASkyOwnerTransfers((fromPos, level, directionIndex, flags) ->
                propagated.add(new Transfer(fromPos, level, directionIndex, flags)));
    }

    private static long owner(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private static Transfer freshTransfer(long fromPos, int level, int directionIndex) {
        return new Transfer(fromPos, level, directionIndex, YALightMath.FLAG_FRESH_OWNER_TRANSFER);
    }

    private record Transfer(long fromPos, int level, int directionIndex, long flags) {
    }
}
