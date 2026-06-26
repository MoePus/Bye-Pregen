package com.moepus.byepregen.mixin;

import com.moepus.byepregen.PaletteContainer.BlockStateRawIdAccess;
import com.moepus.byepregen.PaletteContainer.PaletteRawIdAccess;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PalettedContainer.class, remap = false)
public abstract class PalettedContainerMixin<T> implements BlockStateRawIdAccess {
    @Unique
    private static final ThreadingDetector c6c$dummy = new ThreadingDetector("c6c$PalettedContainer");

    @Shadow
    public volatile PalettedContainer.Data<T> data;

    @Shadow
    @Final
    public PalettedContainer.Strategy strategy;

    @Redirect(method = "<init>*",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/util/ThreadingDetector;"))
    ThreadingDetector onInit(String p_199415_) {
        return c6c$dummy;
    }

    @Override
    public int getRawId(int x, int y, int z) {
        PalettedContainer.Data<T> data = this.data;
        int localId = data.storage().get(this.strategy.getIndex(x, y, z));
        Palette<T> palette = data.palette();
        if (palette instanceof PaletteRawIdAccess rawIdAccess) {
            return rawIdAccess.byepregen$rawIdForLocalId(localId);
        }
        throw new UnsupportedOperationException("Missing raw-id access for palette " + palette.getClass().getName());
    }
}
