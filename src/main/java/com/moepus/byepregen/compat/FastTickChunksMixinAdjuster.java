package com.moepus.byepregen.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import com.moepus.byepregen.ConfigParser;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class FastTickChunksMixinAdjuster implements MixinAnnotationAdjuster {
    private static final String SERVER_CHUNK_CACHE = "net.minecraft.server.level.ServerChunkCache";
    private static final String CHUNK_HOLDER = "net.minecraft.server.level.ChunkHolder";
    private static final String SERVER_LEVEL = "net.minecraft.server.level.ServerLevel";
    private static final String LEVEL_CHUNK = "net.minecraft.world.level.chunk.LevelChunk";
    private static final List<String> TARGET_CLASSES = List.of(
            SERVER_CHUNK_CACHE,
            CHUNK_HOLDER,
            SERVER_LEVEL,
            LEVEL_CHUNK
    );

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

    private static final String SERVERCORE_BROADCAST_CHUNK_CACHE_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.broadcast.ServerChunkCacheMixin";
    private static final String SERVERCORE_BROADCAST_CHUNK_HOLDER_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.broadcast.ChunkHolderMixin";
    private static final String SERVERCORE_CACHE_CHUNK_CACHE_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.cache.ServerChunkCacheMixin";
    private static final String SERVERCORE_RANDOM_CHUNK_CACHE_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.random.ServerChunkCacheMixin";
    private static final String SERVERCORE_RANDOM_SERVER_LEVEL_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.random.ServerLevelMixin";
    private static final String SERVERCORE_RANDOM_LEVEL_CHUNK_MIXIN =
            "me.wesley1808.servercore.mixin.optimizations.ticking.chunk.random.LevelChunkMixin";

    private static final String WRAP_METHOD =
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String WRAP_OPERATION =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String MODIFY_EXPRESSION_VALUE =
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    private static final String INJECT =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String MODIFY_VARIABLE =
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
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
            new DisabledHandler(LITHIUM_SPAWNING_CHUNK_CACHE_MIXIN, "iterateEntitiesChunkAware", REDIRECT),
            new DisabledHandler(SERVERCORE_BROADCAST_CHUNK_CACHE_MIXIN, "servercore$broadcastChanges", REDIRECT),
            new DisabledHandler(SERVERCORE_BROADCAST_CHUNK_HOLDER_MIXIN, "servercore$onBlockChanged", INJECT),
            new DisabledHandler(SERVERCORE_BROADCAST_CHUNK_HOLDER_MIXIN, "servercore$onLightChanged", INJECT),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$noList", REDIRECT),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$replaceList", MODIFY_VARIABLE),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$updateCachedChunks", MODIFY_EXPRESSION_VALUE),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$cancelShuffle", REDIRECT),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$skipUnloadedChunks", REDIRECT),
            new DisabledHandler(SERVERCORE_CACHE_CHUNK_CACHE_MIXIN, "servercore$skipCheck", REDIRECT),
            new DisabledHandler(SERVERCORE_RANDOM_CHUNK_CACHE_MIXIN, "servercore$resetIceAndSnowTick", INJECT),
            new DisabledHandler(SERVERCORE_RANDOM_SERVER_LEVEL_MIXIN, "servercore$replaceLightningCheck", REDIRECT),
            new DisabledHandler(SERVERCORE_RANDOM_SERVER_LEVEL_MIXIN, "servercore$replaceIceAndSnowCheck", REDIRECT),
            new DisabledHandler(SERVERCORE_RANDOM_LEVEL_CHUNK_MIXIN, "servercore$initLightingTick", INJECT)
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
        if (!targetsAny(targetClassNames)) {
            return annotationNode;
        }

        if (DisabledHandler.matchesAny(DISABLED_HANDLERS,
                DisabledHandler.target(mixinClassName, handlerNode, annotationNode))) {
            return null;
        }

        return annotationNode;
    }

    private static boolean targetsAny(List<String> targetClassNames) {
        for (String targetClassName : targetClassNames) {
            if (TARGET_CLASSES.contains(targetClassName)) {
                return true;
            }
        }
        return false;
    }
}
