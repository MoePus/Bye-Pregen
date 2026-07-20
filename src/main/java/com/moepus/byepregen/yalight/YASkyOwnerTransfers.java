package com.moepus.byepregen.yalight;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;

final class YASkyOwnerTransfers {
    private static final byte TRANSIENT = 1;
    private static final byte DECLARED = 1 << 1;
    private static final byte INITIALIZED = 1 << 2;
    private static final int INITIAL_MAP_CAPACITY = 16;
    private static final int MAX_RETAINED_MAP_ENTRIES = 256;
    private static final int MAX_POOLED_INBOXES = 64;
    private static final int LEVEL_MASK = 15;
    private static final int DIRECTION_SHIFT = 4;
    private static final int DIRECTION_MASK = 7;
    private static final int UNSIGNED_BYTE_MASK = 0xFF;

    private final Long2ByteOpenHashMap states = new Long2ByteOpenHashMap(INITIAL_MAP_CAPACITY);
    private final Long2ObjectLinkedOpenHashMap<OwnerInbox> inboxes =
            new Long2ObjectLinkedOpenHashMap<>(INITIAL_MAP_CAPACITY);
    private final EdgePropagator propagator;
    private OwnerInbox pooledInbox;
    private int declaredCount;
    private int readyCount;
    private int pooledInboxCount;

    YASkyOwnerTransfers(EdgePropagator propagator) {
        this.propagator = propagator;
    }

    void setDeclaredDomain(Iterable<ChunkPos> owners) {
        this.clearPersistentState();
        for (ChunkPos pos : owners) {
            long ownerKey = pos.toLong();
            byte state = this.states.get(ownerKey);
            if ((state & DECLARED) == 0) {
                this.states.put(ownerKey, (byte)(state | DECLARED));
                ++this.declaredCount;
            }
        }
    }

    void register(long ownerKey) {
        byte state = this.states.get(ownerKey);
        if (state != 0) {
            return;
        }
        this.states.put(ownerKey, TRANSIENT);
    }

    void markInitialized(long ownerKey) {
        byte state = this.states.get(ownerKey);
        if ((state & INITIALIZED) != 0) {
            return;
        }
        this.states.put(ownerKey, (byte)(state | INITIALIZED));
        if (this.inboxes.getAndMoveToFirst(ownerKey) != null) {
            ++this.readyCount;
        }
    }

    void remove(long ownerKey) {
        byte state = this.states.remove(ownerKey);
        if ((state & DECLARED) != 0) {
            --this.declaredCount;
        }
        OwnerInbox inbox = this.inboxes.remove(ownerKey);
        if (inbox != null) {
            if ((state & INITIALIZED) != 0) {
                --this.readyCount;
            }
            this.recycleInbox(inbox);
        }
    }

    byte state(long ownerKey) {
        return this.states.get(ownerKey);
    }

    boolean isTransferTarget(byte state) {
        return state != 0;
    }

    boolean isInitialized(byte state) {
        return (state & INITIALIZED) != 0;
    }

    void enqueue(long ownerKey, long fromPos, int level, int directionIndex) {
        OwnerInbox inbox = this.inboxes.get(ownerKey);
        if (inbox == null) {
            inbox = this.acquireInbox();
            if (this.isInitialized(this.states.get(ownerKey))) {
                this.inboxes.putAndMoveToFirst(ownerKey, inbox);
                ++this.readyCount;
            } else {
                this.inboxes.put(ownerKey, inbox);
            }
        }
        inbox.add(fromPos, level, directionIndex);
    }

    int drain() {
        int work = this.drainReady(true);
        return work > 0 || this.inboxes.isEmpty() ? work : this.drainFallback();
    }

    void finishRun() {
        if (this.declaredCount == 0) {
            this.clearPersistentState();
            return;
        }
        if (this.states.size() != this.declaredCount) {
            var iterator = this.states.long2ByteEntrySet().fastIterator();
            while (iterator.hasNext()) {
                Long2ByteMap.Entry entry = iterator.next();
                byte state = entry.getByteValue();
                if ((state & TRANSIENT) != 0) {
                    OwnerInbox inbox = this.inboxes.remove(entry.getLongKey());
                    if (inbox != null) {
                        if ((state & INITIALIZED) != 0) {
                            --this.readyCount;
                        }
                        this.recycleInbox(inbox);
                    }
                    iterator.remove();
                }
            }
        }
        this.trimMaps();
    }

    void clearPersistentState() {
        this.states.clear();
        this.recycleInboxes();
        this.inboxes.clear();
        this.trimMaps();
        this.declaredCount = 0;
        this.readyCount = 0;
    }

    private int drainFallback() {
        int work = 0;
        var iterator = this.inboxes.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            byte state = this.states.get(entry.getLongKey());
            if ((state & DECLARED) == 0) {
                OwnerInbox inbox = entry.getValue();
                iterator.remove();
                work += this.drainInbox(inbox, false);
            }
        }
        return work;
    }

    private int drainReady(boolean transferToOwner) {
        int work = 0;
        while (this.readyCount > 0) {
            OwnerInbox inbox = this.inboxes.removeFirst();
            --this.readyCount;
            work += this.drainInbox(inbox, transferToOwner);
        }
        return work;
    }

    private int drainInbox(OwnerInbox inbox, boolean transferToOwner) {
        int work = 0;
        long childFlags = transferToOwner ? YALightMath.FLAG_FRESH_OWNER_TRANSFER : 0L;
        try {
            while (!inbox.isEmpty()) {
                long fromPos = inbox.position();
                int edgeMeta = inbox.edgeMeta();
                inbox.remove();
                this.propagator.propagate(
                        fromPos,
                        edgeMeta & LEVEL_MASK,
                        (edgeMeta >>> DIRECTION_SHIFT) & DIRECTION_MASK,
                        childFlags
                );
                ++work;
            }
            return work;
        } finally {
            this.recycleInbox(inbox);
        }
    }

    private OwnerInbox acquireInbox() {
        OwnerInbox inbox = this.pooledInbox;
        if (inbox == null) {
            return new OwnerInbox();
        }
        this.pooledInbox = inbox.nextPooled;
        inbox.nextPooled = null;
        --this.pooledInboxCount;
        return inbox;
    }

    private void recycleInbox(OwnerInbox inbox) {
        if (this.pooledInboxCount >= MAX_POOLED_INBOXES) {
            return;
        }
        inbox.clearForReuse();
        inbox.nextPooled = this.pooledInbox;
        this.pooledInbox = inbox;
        ++this.pooledInboxCount;
    }

    private void recycleInboxes() {
        for (OwnerInbox inbox : this.inboxes.values()) {
            this.recycleInbox(inbox);
        }
    }

    private void trimMaps() {
        this.states.trim(Math.max(this.states.size(), MAX_RETAINED_MAP_ENTRIES));
        this.inboxes.trim(Math.max(this.inboxes.size(), MAX_RETAINED_MAP_ENTRIES));
    }

    private static final class OwnerInbox {
        private static final int INITIAL_CAPACITY = 32;
        private static final int MAX_RETAINED_CAPACITY = 256;

        private long[] positions = new long[INITIAL_CAPACITY];
        private byte[] edgeMetas = new byte[INITIAL_CAPACITY];
        private int readIndex;
        private int writeIndex;
        private OwnerInbox nextPooled;

        private void add(long position, int level, int directionIndex) {
            if (this.writeIndex >= this.positions.length) {
                int newCapacity = this.positions.length << 1;
                this.positions = Arrays.copyOf(this.positions, newCapacity);
                this.edgeMetas = Arrays.copyOf(this.edgeMetas, newCapacity);
            }
            this.positions[this.writeIndex] = position;
            this.edgeMetas[this.writeIndex] = (byte)(level | (directionIndex << DIRECTION_SHIFT));
            ++this.writeIndex;
        }

        private boolean isEmpty() {
            return this.readIndex >= this.writeIndex;
        }

        private long position() {
            return this.positions[this.readIndex];
        }

        private int edgeMeta() {
            return this.edgeMetas[this.readIndex] & UNSIGNED_BYTE_MASK;
        }

        private void remove() {
            ++this.readIndex;
        }

        private void clearForReuse() {
            if (this.positions.length > MAX_RETAINED_CAPACITY) {
                this.positions = new long[MAX_RETAINED_CAPACITY];
                this.edgeMetas = new byte[MAX_RETAINED_CAPACITY];
            }
            this.readIndex = 0;
            this.writeIndex = 0;
        }
    }

    @FunctionalInterface
    interface EdgePropagator {
        void propagate(long fromPos, int level, int directionIndex, long flags);
    }
}
