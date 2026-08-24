package com.moepus.byepregen.integration.neoforge;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.platform.PlatformBridge.ChunkAttachmentContext;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.slf4j.Logger;

final class NeoForgeChunkAttachments {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte[] ATTACHMENTS = NbtWriter.asciiName(AttachmentHolder.ATTACHMENTS_NBT_KEY);

    private NeoForgeChunkAttachments() {
    }

    static void write(ChunkAttachmentContext context) {
        try {
            Tag tag = context.chunk().writeAttachmentsToNBT(context.level().registryAccess());
            if (tag != null) {
                context.writer().putTag(ATTACHMENTS, tag);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to write chunk attachments", exception);
        }
    }
}
