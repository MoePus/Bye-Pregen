package com.moepus.byepregen.mixin.accessor.chunksave;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@MixinGate(feature = MixinFeature.GC_FREE_CHUNK_SAVE)
@Mixin(value = StructurePiece.class, remap = false)
public interface StructurePieceSaveAccessor {
    @Invoker("addAdditionalSaveData")
    void byepregen$addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag);
}
