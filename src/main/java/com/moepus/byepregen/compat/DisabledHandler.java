package com.moepus.byepregen.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

record DisabledHandler(String mixinClassName, String handlerName, String annotationDesc) {
    static Target target(
            String mixinClassName,
            MethodNode handlerNode,
            AdjustableAnnotationNode annotationNode) {
        return new Target(mixinClassName, handlerNode.name, annotationNode.desc);
    }

    static boolean matchesAny(List<DisabledHandler> handlers, Target target) {
        for (DisabledHandler handler : handlers) {
            if (handler.matches(target)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Target target) {
        return this.mixinClassName.equals(target.mixinClassName)
                && this.handlerName.equals(target.handlerName)
                && this.annotationDesc.equals(target.annotationDesc);
    }

    record Target(String mixinClassName, String handlerName, String annotationDesc) {
    }
}
