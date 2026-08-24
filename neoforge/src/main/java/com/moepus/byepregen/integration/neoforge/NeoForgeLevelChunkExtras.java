package com.moepus.byepregen.integration.neoforge;

import com.moepus.byepregen.integration.platform.PlatformBridge.LevelChunkExtrasContext;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager;

final class NeoForgeLevelChunkExtras {
    private static final byte[] AUX_LIGHT = NbtWriter.asciiName(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY);

    private NeoForgeLevelChunkExtras() {
    }

    static void write(LevelChunkExtrasContext context) {
        if (!(context.chunk() instanceof LevelChunk levelChunk)) {
            return;
        }
        Tag lightTag = levelChunk.getAuxLightManager(context.pos()).serializeNBT();
        if (lightTag != null) {
            context.writer().putTag(AUX_LIGHT, lightTag);
        }
    }
}
