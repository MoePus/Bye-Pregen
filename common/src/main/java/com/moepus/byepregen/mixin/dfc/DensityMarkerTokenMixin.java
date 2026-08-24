package com.moepus.byepregen.mixin.dfc;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.arena.InterpolatedMarkerAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.mixinlite.injector.InjectLite;
import org.mixinlite.injector.MethodScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(feature = MixinFeature.DFC)
@Mixin(value = DensityFunctions.Marker.class, priority = 500)
public abstract class DensityMarkerTokenMixin implements InterpolatedMarkerAccess {
    @Unique private static final ThreadLocal<Object> byepregen$activeInterpolationToken = new ThreadLocal<>();
    @Unique private Object byepregen$interpolationToken;

    @InjectLite(method = "<init>", at = @At("RETURN"))
    private void byepregen$inheritInterpolationToken(
            DensityFunctions.Marker.Type type,
            DensityFunction wrapped
    ) {
        if (type == DensityFunctions.Marker.Type.Interpolated) {
            this.byepregen$interpolationToken = byepregen$activeInterpolationToken.get();
        }
    }

    @Override
    public Object byepregen$getInterpolationToken() {
        return this.byepregen$interpolationToken;
    }

    @Override
    public void byepregen$setInterpolationToken(Object token) {
        this.byepregen$interpolationToken = token;
    }

    // MethodScope is priority-neutral; require=0 keeps this optional when C2ME DFC is absent.
    @MethodScope(
            method = "c2me$withDelegate",
            exit = "byepregen$exitWithDelegate",
            require = 0,
            remap = false
    )
    private Object byepregen$enterWithDelegate() {
        Object previous = byepregen$activeInterpolationToken.get();
        Object current = this.byepregen$interpolationToken;
        if (current == null) {
            byepregen$activeInterpolationToken.remove();
        } else {
            byepregen$activeInterpolationToken.set(current);
        }
        return previous;
    }

    @Unique
    private void byepregen$exitWithDelegate(Object previous) {
        if (previous == null) {
            byepregen$activeInterpolationToken.remove();
        } else {
            byepregen$activeInterpolationToken.set(previous);
        }
    }
}
