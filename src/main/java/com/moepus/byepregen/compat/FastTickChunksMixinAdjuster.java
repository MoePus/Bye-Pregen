package com.moepus.byepregen.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import com.moepus.byepregen.ConfigParser;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class FastTickChunksMixinAdjuster implements MixinAnnotationAdjuster {
    private static final String SERVER_CHUNK_CACHE = "net.minecraft.server.level.ServerChunkCache";

    private static final String C2ME_BASE_INSTRUMENTATION =
            "com.ishland.c2me.base.mixin.instrumentation.MixinServerChunkManager";
    private static final String C2ME_REWRITES_CHUNK_SYSTEM =
            "com.ishland.c2me.rewrites.chunksystem.mixin.MixinServerChunkManager";
    private static final String C2ME_NOTICKVD =
            "com.ishland.c2me.notickvd.mixin.MixinServerChunkManager";
    private static final String C2ME_MID_TICK_CHUNK_TASKS =
            "com.ishland.c2me.opts.scheduling.mixin.mid_tick_chunk_tasks.MixinServerChunkManager";

    private static final String LITHIUM_ALLOC_CHUNK_TICKING_CHUNK_CACHE_MIXIN =
            "net.caffeinemc.mods.lithium.mixin.alloc.chunk_ticking.ServerChunkCacheMixin";
    private static final String LITHIUM_SPAWNING_CHUNK_CACHE_MIXIN =
            "net.caffeinemc.mods.lithium.mixin.minimal_nonvanilla.spawning.ServerChunkCacheMixin";

    private static final String WRAP_METHOD =
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String WRAP_OPERATION =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String INJECT =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT =
            "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    private static final List<DisabledHandler> DISABLED_HANDLERS = List.of(
            new DisabledHandler(C2ME_BASE_INSTRUMENTATION, "instrumentGetChunk", WRAP_METHOD),
            new DisabledHandler(C2ME_BASE_INSTRUMENTATION, "instrumentAwaitChunk", WRAP_OPERATION),
            new DisabledHandler(C2ME_REWRITES_CHUNK_SYSTEM, "shortcutGetChunk", INJECT),
            new DisabledHandler(C2ME_NOTICKVD, "redirectIterateEntities", WRAP_OPERATION),
            new DisabledHandler(C2ME_NOTICKVD, "redirectIterateEntities", REDIRECT),
            new DisabledHandler(C2ME_NOTICKVD, "broadcastBorderChunks", WRAP_OPERATION),
            new DisabledHandler(C2ME_NOTICKVD, "broadcastBorderChunks", REDIRECT),
            new DisabledHandler(C2ME_MID_TICK_CHUNK_TASKS, "onPostTickChunk", INJECT),
            new DisabledHandler(LITHIUM_ALLOC_CHUNK_TICKING_CHUNK_CACHE_MIXIN, "redirectChunksListClone", REDIRECT),
            new DisabledHandler(LITHIUM_ALLOC_CHUNK_TICKING_CHUNK_CACHE_MIXIN, "preTick", INJECT),
            new DisabledHandler(LITHIUM_SPAWNING_CHUNK_CACHE_MIXIN, "iterateEntitiesChunkAware", REDIRECT)
    );

    @Override
    public AdjustableAnnotationNode adjust(
            List<String> targetClassNames,
            String mixinClassName,
            MethodNode handlerNode,
            AdjustableAnnotationNode annotationNode
    ) {
        if (annotationNode == null || !ConfigParser.getConfig().enableFastTickChunks) {
            return annotationNode;
        }
        if (!targetClassNames.contains(SERVER_CHUNK_CACHE)) {
            return annotationNode;
        }

        if (DisabledHandler.matchesAny(DISABLED_HANDLERS,
                DisabledHandler.target(mixinClassName, handlerNode, annotationNode))) {
            return null;
        }

        return annotationNode;
    }
}
