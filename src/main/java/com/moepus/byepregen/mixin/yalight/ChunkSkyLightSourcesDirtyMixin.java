package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.yalight.YASkySourceDirtyAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkSkyLightSources.class, remap = false)
public abstract class ChunkSkyLightSourcesDirtyMixin implements YASkySourceDirtyAccess {
    @Unique
    private static final int byepregen$wordShift = 6;
    @Unique
    private static final int byepregen$bitMask = Long.SIZE - 1;

    @Unique
    private volatile long byepregen$dirty0;
    @Unique
    private volatile long byepregen$dirty1;
    @Unique
    private volatile long byepregen$dirty2;
    @Unique
    private volatile long byepregen$dirty3;

    @Override
    public synchronized void byepregen$markSourceDirty(int columnIndex) {
        int wordIndex = columnIndex >>> byepregen$wordShift;
        long bit = 1L << (columnIndex & byepregen$bitMask);
        this.byepregen$writeDirtyWord(wordIndex, this.byepregen$readDirtyWord(wordIndex) | bit);
    }

    @Override
    public boolean byepregen$consumeSourceDirty(int columnIndex) {
        int wordIndex = columnIndex >>> byepregen$wordShift;
        long bit = 1L << (columnIndex & byepregen$bitMask);
        if ((this.byepregen$readDirtyWord(wordIndex) & bit) == 0L) {
            return false;
        }
        synchronized (this) {
            long dirty = this.byepregen$readDirtyWord(wordIndex);
            if ((dirty & bit) == 0L) {
                return false;
            }
            this.byepregen$writeDirtyWord(wordIndex, dirty & ~bit);
            return true;
        }
    }

    @Unique
    private long byepregen$readDirtyWord(int wordIndex) {
        return switch (wordIndex) {
            case 0 -> this.byepregen$dirty0;
            case 1 -> this.byepregen$dirty1;
            case 2 -> this.byepregen$dirty2;
            case 3 -> this.byepregen$dirty3;
            default -> throw new IllegalArgumentException("Invalid sky source dirty word: " + wordIndex);
        };
    }

    @Unique
    private void byepregen$writeDirtyWord(int wordIndex, long value) {
        switch (wordIndex) {
            case 0 -> this.byepregen$dirty0 = value;
            case 1 -> this.byepregen$dirty1 = value;
            case 2 -> this.byepregen$dirty2 = value;
            case 3 -> this.byepregen$dirty3 = value;
            default -> throw new IllegalArgumentException("Invalid sky source dirty word: " + wordIndex);
        }
    }
}
