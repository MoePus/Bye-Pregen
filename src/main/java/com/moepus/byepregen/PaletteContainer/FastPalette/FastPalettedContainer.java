package com.moepus.byepregen.PaletteContainer.FastPalette;

import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.NotNull;
import com.moepus.byepregen.mixin.accessor.PalettedContainerAccessor;

abstract class FastPalettedContainer<T> extends PalettedContainer<T> implements FastPalettedContainerAccess<T> {
    protected PalettedContainer.Data<T> fastData;
    protected T zeroValue;

    protected FastPalettedContainer(IdMap<T> idList, T defaultValue, Strategy strategy) {
        super(idList, defaultValue, strategy);
        this.updateFastData(((PalettedContainerAccessor<T>) (Object) this).byepregen$getData());
    }

    protected FastPalettedContainer(PalettedContainer<T> source) {
        super(
                ((PalettedContainerAccessor<T>) (Object) source).byepregen$getRegistry(),
                ((PalettedContainerAccessor<T>) (Object) source).byepregen$getData().palette().valueFor(0),
                ((PalettedContainerAccessor<T>) (Object) source).byepregen$getStrategy());
        this.copyFrom(((PalettedContainerAccessor<T>) (Object) source).byepregen$getData());
    }

    @Override
    public int onResize(int bits, @NotNull T value) {
        PalettedContainer.Data<T> oldData =
                this.fastData != null ? this.fastData : ((PalettedContainerAccessor<T>) (Object) this).byepregen$getData();
        PalettedContainer.Data<T> newData =
                ((PalettedContainerAccessor<T>) (Object) this).byepregen$invokeCreateOrReuseData(oldData, bits);

        newData.copyFrom(oldData.palette(), oldData.storage());

        ((PalettedContainerAccessor<T>) (Object) this).byepregen$setData(newData);
        this.updateFastData(newData);

        return newData.palette().idFor(value);
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buffer) {
        super.read(buffer);
        this.updateFastData(((PalettedContainerAccessor<T>) (Object) this).byepregen$getData());
    }

    protected final T getFast(int index) {
        T zeroValue = this.zeroValue;
        if (zeroValue != null) {
            return zeroValue;
        }

        PalettedContainer.Data<T> data = this.fastData;
        return data.palette().valueFor(data.storage().get(index));
    }

    protected final T getAndSetFast(int index, T value) {
        PalettedContainer.Data<T> data = this.fastData;
        int newId = data.palette().idFor(value);

        data = this.fastData;
        int oldId = data.storage().getAndSet(index, newId);
        return data.palette().valueFor(oldId);
    }

    protected final void setFast(int index, T value) {
        PalettedContainer.Data<T> data = this.fastData;
        int newId = data.palette().idFor(value);

        data = this.fastData;
        data.storage().set(index, newId);
    }

    private void copyFrom(PalettedContainer.Data<T> sourceData) {
        PalettedContainer.Data<T> newData =
                ((PalettedContainerAccessor<T>) (Object) this).byepregen$invokeCreateOrReuseData(null, sourceData.storage().getBits());
        newData.copyFrom(sourceData.palette(), sourceData.storage());

        ((PalettedContainerAccessor<T>) (Object) this).byepregen$setData(newData);
        this.updateFastData(newData);
    }

    @Override
    public final void byepregen$updateFastData(PalettedContainer.Data<T> data) {
        this.updateFastData(data);
    }

    private void updateFastData(PalettedContainer.Data<T> data) {
        this.fastData = data;
        this.zeroValue = data.storage() instanceof ZeroBitStorage ? data.palette().valueFor(0) : null;
    }

    public final void c2me$setUnsafe(int x, int y, int z, T value) {
        PalettedContainerAccessor<T> accessor = (PalettedContainerAccessor<T>) (Object) this;
        this.setFast(accessor.byepregen$getStrategy().getIndex(x, y, z), value);
    }
}
