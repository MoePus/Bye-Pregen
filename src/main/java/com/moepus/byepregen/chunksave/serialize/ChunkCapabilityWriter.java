package com.moepus.byepregen.chunksave.serialize;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

final class ChunkCapabilityWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte[] FORGE_CAPS = NbtWriter.asciiName("ForgeCaps");

    private ChunkCapabilityWriter() {
    }

    static void write(NbtWriter writer, ChunkAccess chunk) {
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return;
        }
        try {
            CompoundTag tag = levelChunk.writeCapsToNBT();
            if (tag != null) {
                writer.putTag(FORGE_CAPS, tag);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to write chunk capabilities", exception);
        }
    }
}
