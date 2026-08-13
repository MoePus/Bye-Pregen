package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.serialization.nbt.NbtWriter;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.slf4j.Logger;

final class ChunkAttachmentWriter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte[] ATTACHMENTS = NbtWriter.asciiName(AttachmentHolder.ATTACHMENTS_NBT_KEY);

    private ChunkAttachmentWriter() {
    }

    static void write(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        try {
            Tag tag = chunk.writeAttachmentsToNBT(level.registryAccess());
            if (tag != null) {
                writer.putTag(ATTACHMENTS, tag);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to write chunk attachments", exception);
        }
    }
}
