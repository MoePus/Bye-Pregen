package com.moepus.byepregen.fabric;

import com.mojang.logging.LogUtils;
import com.moepus.byepregen.integration.platform.PlatformBridge.ChunkAttachmentContext;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;

final class FabricChunkAttachments {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ATTACHMENT_KEY = "fabric:attachments";
    private static final byte[] ATTACHMENTS = NbtWriter.asciiName(ATTACHMENT_KEY);

    private FabricChunkAttachments() {
    }

    static void write(ChunkAttachmentContext context) {
        try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(
                    problems, context.level().registryAccess());
            ((AttachmentTargetImpl) context.chunk()).fabric_writeAttachmentsToNbt(output);
            CompoundTag attachments = output.buildResult().getCompound(ATTACHMENT_KEY).orElse(null);
            if (attachments != null) {
                context.writer().putTag(ATTACHMENTS, attachments);
            }
        }
    }
}
