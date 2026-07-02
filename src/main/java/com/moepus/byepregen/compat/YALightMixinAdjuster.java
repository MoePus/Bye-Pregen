package com.moepus.byepregen.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import com.moepus.byepregen.ConfigParser;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class YALightMixinAdjuster implements MixinAnnotationAdjuster {
    private static final String LEVEL_RENDERER = "net.minecraft.client.renderer.LevelRenderer";

    private static final String SUPPLEMENTARIES_LEVEL_RENDERER_MIXIN =
            "net.mehvahdjukaar.supplementaries.mixins.neoforge.LevelRendererMixin";

    private static final String MODIFY_EXPRESSION_VALUE =
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";

    private static final List<DisabledHandler> DISABLED_HANDLERS = List.of(
            new DisabledHandler(SUPPLEMENTARIES_LEVEL_RENDERER_MIXIN, "supp$modifyLumiseneLight",
                    MODIFY_EXPRESSION_VALUE)
    );

    @Override
    public AdjustableAnnotationNode adjust(
            List<String> targetClassNames,
            String mixinClassName,
            MethodNode handlerNode,
            AdjustableAnnotationNode annotationNode
    ) {
        if (annotationNode == null || !ConfigParser.getConfig().enableYALightEngine) {
            return annotationNode;
        }
        if (!targetClassNames.contains(LEVEL_RENDERER)) {
            return annotationNode;
        }

        if (DisabledHandler.matchesAny(DISABLED_HANDLERS,
                DisabledHandler.target(mixinClassName, handlerNode, annotationNode))) {
            return null;
        }

        return annotationNode;
    }
}
