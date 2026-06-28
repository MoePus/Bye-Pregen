package com.moepus.byepregen.yalight;

import com.moepus.byepregen.UnsafeIntArrayAccess;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;

public final class YANibbleArray {
    public static final int SIZE = 2048;
    public static final int SAVE_EMPTY = 0;
    public static final int SAVE_FULL = 1;
    public static final int SAVE_DATA = 2;

    @Unique
    public static final byte[] FULL_LIGHT_DATA = createFullLightData();

    static final int STATE_NULL = 0;
    static final int STATE_ZERO = 1;
    static final int STATE_FULL = 2;
    static final int STATE_INIT = 3;
    static final int STATE_HIDDEN = 4;

    // Updating is mutated by the light pass; visible is what readers see until dirty sections publish.
    private int updatingState;
    private volatile int visibleState;
    private byte[] updating;
    private volatile byte[] visible;
    private boolean dirty;

    public YANibbleArray() {
        this(STATE_ZERO, null);
    }

    private YANibbleArray(int state, byte[] data) {
        this.updatingState = state;
        this.visibleState = state;
        this.updating = data;
        this.visible = data;
    }

    public static YANibbleArray nullArray() {
        return new YANibbleArray(STATE_NULL, null);
    }

    public static YANibbleArray fullArray() {
        return new YANibbleArray(STATE_FULL, null);
    }

    public static YANibbleArray fromVanilla(DataLayer layer) {
        if (layer == null) {
            return nullArray();
        }
        if (layer.isEmpty()) {
            return new YANibbleArray();
        }
        if (layer.isDefinitelyFilledWith(15)) {
            return fullArray();
        }
        byte[] data = layer.getData();
        return new YANibbleArray(STATE_INIT, data.clone());
    }

    public static YANibbleArray fromOwnedBytes(byte[] data) {
        if (data == null) {
            return nullArray();
        }
        if (data.length != SIZE) {
            throw new IllegalArgumentException("Expected " + SIZE + " light bytes, got " + data.length);
        }
        boolean allZero = true;
        boolean allFull = true;
        for (int i = 0; i < SIZE && (allZero || allFull); ++i) {
            int value = data[i] & 255;
            allZero &= value == 0;
            allFull &= value == 255;
        }
        if (allZero) {
            return new YANibbleArray();
        }
        if (allFull) {
            return fullArray();
        }
        return new YANibbleArray(STATE_INIT, data);
    }

    public boolean isNullVisible() {
        return this.visibleState == STATE_NULL;
    }

    public boolean hasVisibleLayer() {
        int state = this.visibleState;
        return state != STATE_NULL && state != STATE_HIDDEN;
    }

    public boolean isNullUpdating() {
        return this.updatingState == STATE_NULL;
    }

    public boolean isFullUpdating() {
        return this.updatingState == STATE_FULL;
    }

    public boolean isDirty() {
        return this.dirty || this.updatingState != this.visibleState;
    }

    public int getVisible(int x, int y, int z) {
        int state = this.visibleState;
        if (state == STATE_NULL || state == STATE_ZERO) {
            return 0;
        }
        if (state == STATE_FULL) {
            return 15;
        }
        byte[] data = this.visible;
        if (data == null) {
            return 0;
        }
        int index = index(x, y, z);
        int packed = UnsafeIntArrayAccess.get(data, index >>> 1) & 255;
        return packed >>> ((index & 1) << 2) & 15;
    }

    public int getUpdating(int x, int y, int z) {
        return this.getUpdating(index(x, y, z));
    }

    public int getUpdating(int index) {
        int state = this.updatingState;
        if (state == STATE_NULL || state == STATE_ZERO) {
            return 0;
        }
        if (state == STATE_FULL) {
            return 15;
        }
        byte[] data = this.updating;
        if (data == null) {
            return 0;
        }
        int packed = UnsafeIntArrayAccess.get(data, index >>> 1) & 255;
        return packed >>> ((index & 1) << 2) & 15;
    }

    public void setUpdating(int x, int y, int z, int value) {
        this.setUpdating(index(x, y, z), value);
    }

    public void setUpdating(int index, int value) {
        byte[] data = this.ensureUpdating();
        int byteIndex = index >>> 1;
        int shift = (index & 1) << 2;
        int packed = UnsafeIntArrayAccess.get(data, byteIndex) & 255;
        UnsafeIntArrayAccess.set(data, byteIndex, (byte)((packed & ~(15 << shift)) | ((value & 15) << shift)));
    }

    public void publish() {
        if (!this.isDirty()) {
            return;
        }
        int state = this.updatingState;
        if (state == STATE_NULL || state == STATE_ZERO || state == STATE_FULL) {
            int oldState = this.visibleState;
            this.visibleState = state;
            if (oldState != STATE_INIT && oldState != STATE_HIDDEN) {
                this.visible = null;
            }
        } else {
            byte[] updatingData = this.updating;
            this.visible = updatingData;
            this.updating = updatingData;
            this.visibleState = state;
        }
        this.dirty = false;
    }

    public DataLayer toVanilla() {
        int state = this.visibleState;
        if (state == STATE_NULL || state == STATE_HIDDEN) {
            return null;
        }
        if (state == STATE_ZERO) {
            return new DataLayer();
        }
        if (state == STATE_FULL) {
            return new DataLayer(15);
        }
        byte[] data = this.visible;
        return data == null ? new DataLayer() : new DataLayer(data.clone());
    }

    public int visibleSaveKind() {
        int state = this.visibleState;
        if (state == STATE_FULL) {
            return SAVE_FULL;
        }
        return state == STATE_INIT && this.visible != null ? SAVE_DATA : SAVE_EMPTY;
    }

    public byte[] visibleDataForSave() {
        return this.visible;
    }

    public void retirePublished() {
        // This object may still be reachable through an older visible section snapshot.
        // Keep visibleState/visible immutable for racing readers; only drop writer-private state.
        this.updating = this.visible;
        this.updatingState = this.visibleState;
        this.dirty = false;
    }

    public void discardUnpublished() {
        this.updating = null;
        this.visible = null;
        this.updatingState = STATE_NULL;
        this.visibleState = STATE_NULL;
        this.dirty = false;
    }

    private byte[] ensureUpdating() {
        if (!this.dirty) {
            // Copy-on-write keeps visible stable while the same tick continues mutating updating light.
            byte[] next = new byte[SIZE];
            if ((this.updatingState == STATE_INIT || this.updatingState == STATE_HIDDEN) && this.updating != null) {
                System.arraycopy(this.updating, 0, next, 0, SIZE);
            } else {
                Arrays.fill(next, this.updatingState == STATE_FULL ? (byte)-1 : 0);
            }
            this.updating = next;
            this.updatingState = STATE_INIT;
            this.dirty = true;
        } else if (this.updating == null) {
            this.updating = new byte[SIZE];
            this.updatingState = STATE_INIT;
        }
        return this.updating;
    }

    static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    @Unique
    private static byte[] createFullLightData() {
        byte[] data = new byte[YANibbleArray.SIZE];
        Arrays.fill(data, (byte)-1);
        return data;
    }
}
