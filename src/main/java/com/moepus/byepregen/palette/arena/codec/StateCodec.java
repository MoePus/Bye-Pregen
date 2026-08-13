package com.moepus.byepregen.palette.arena.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class StateCodec implements Codec<PalettedContainer<BlockState>> {
    private final Codec<PalettedContainer<BlockState>> fallback;

    public StateCodec(Codec<PalettedContainer<BlockState>> fallback) {
        this.fallback = fallback;
    }

    @Override
    public <T> DataResult<Pair<PalettedContainer<BlockState>, T>> decode(DynamicOps<T> ops, T input) {
        return this.fallback.decode(ops, input);
    }

    @Override
    public <T> DataResult<T> encode(PalettedContainer<BlockState> input, DynamicOps<T> ops, T prefix) {
        if (ops == NbtOps.INSTANCE && input instanceof ArenaBlockStatePalettedContainer arena) {
            return DataResult.success(cast(encodeArena(arena)));
        }
        return this.fallback.encode(input, ops, prefix);
    }

    private static CompoundTag encodeArena(ArenaBlockStatePalettedContainer container) {
        if (container.isUniform()) {
            return ByteStreamTag.uniform(container.uniformRawId());
        }

        SerializationScratch scratch = SerializationScratch.get();
        try {
            scratch.collect(container);
            return ByteStreamTag.packed(scratch, container);
        } finally {
            scratch.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(CompoundTag tag) {
        return (T) tag;
    }
}
