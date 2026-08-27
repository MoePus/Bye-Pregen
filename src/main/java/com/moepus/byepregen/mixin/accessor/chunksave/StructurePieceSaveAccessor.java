package com.moepus.byepregen.mixin.accessor.chunksave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StructurePiece.class)
public interface StructurePieceSaveAccessor {
    @Invoker("addAdditionalSaveData")
    void byepregen$addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag);
}
