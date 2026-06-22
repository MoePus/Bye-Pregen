package com.moepus.byepregen.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class C2MEGetChunkAnnotationAdjuster implements MixinAnnotationAdjuster {
    private static final String SERVER_CHUNK_CACHE = "net.minecraft.server.level.ServerChunkCache";
    private static final String C2ME_BASE_INSTRUMENTATION =
            "com.ishland.c2me.base.mixin.instrumentation.MixinServerChunkManager";
    private static final String C2ME_REWRITES_CHUNK_SYSTEM =
            "com.ishland.c2me.rewrites.chunksystem.mixin.MixinServerChunkManager";
    private static final String WRAP_METHOD =
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String WRAP_OPERATION =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String INJECT =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";

    @Override
    public AdjustableAnnotationNode adjust(
            List<String> targetClassNames,
            String mixinClassName,
            MethodNode handlerNode,
            AdjustableAnnotationNode annotationNode
    ) {
        if (annotationNode == null || !targetClassNames.contains(SERVER_CHUNK_CACHE)) {
            return annotationNode;
        }

        if (C2ME_BASE_INSTRUMENTATION.equals(mixinClassName)
                && "instrumentGetChunk".equals(handlerNode.name)
                && WRAP_METHOD.equals(annotationNode.desc)) {
            return null;
        }

        if (C2ME_BASE_INSTRUMENTATION.equals(mixinClassName)
                && "instrumentAwaitChunk".equals(handlerNode.name)
                && WRAP_OPERATION.equals(annotationNode.desc)) {
            return null;
        }

        if (C2ME_REWRITES_CHUNK_SYSTEM.equals(mixinClassName)
                && "shortcutGetChunk".equals(handlerNode.name)
                && INJECT.equals(annotationNode.desc)) {
            return null;
        }

        return annotationNode;
    }
}
