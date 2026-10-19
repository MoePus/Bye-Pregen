package com.moepus.byepregen.fabric;

import com.moepus.byepregen.integration.platform.PlatformBridge;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.loader.api.FabricLoader;

/** Platform services for the Arena-only port. */
public final class ArenaPlatformBridge implements PlatformBridge {
    @Override public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    // The native raw IO path is ported; it stays opt-in through chunk-saving.gc-free-worldgen.
    @Override public boolean supportsGcFreeRawChunkIo() { return true; }
    @Override public boolean canUseGcFreeRawChunkSave() { return FabricChunkSaveHookGate.canUseRawSave(); }

    @Override public boolean hasDynamicLightEmission(BlockState state) { return false; }
    @Override public int lightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return state.getLightEmission();
    }

    // The Fabric attachment list is real again; the level-extras exit stays inert because YA light
    // data is the only writer and that module is still deferred.
    @Override public void writeChunkAttachments(ChunkAttachmentContext context) {
        FabricChunkAttachments.write(context);
    }

    @Override public void writeLevelChunkExtras(LevelChunkExtrasContext context) {
    }
}
