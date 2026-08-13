package com.moepus.byepregen.mixin.nbt;

import com.moepus.byepregen.mixin.accessor.nbt.CompoundTagAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;

/**
 * @reason Use faster collection
 * @author Maity
 */
@Mixin(CompoundTag.class)
public abstract class CompoundTagFastRuntimeMixin {
    @Shadow
    @Final
    private Map<String, Tag> tags;

    @ModifyArg(
            method = "<init>()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;<init>(Ljava/util/Map;)V")
    )
    private static Map<String, Tag> useFastEmptyMap(Map<String, Tag> oldMap) {
        return new Object2ObjectOpenHashMap<>();
    }

    /**
     * @author MoePus
     * @reason Keep copied runtime compound maps on the same low-overhead map implementation after disabling Lithium's NBT allocation mixin.
     */
    @Overwrite
    public CompoundTag copy() {
        Object2ObjectOpenHashMap<String, Tag> copy = new Object2ObjectOpenHashMap<>(this.tags.size());
        for (Map.Entry<String, Tag> entry : this.tags.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return CompoundTagAccessor.byepregen$new(copy);
    }
}
