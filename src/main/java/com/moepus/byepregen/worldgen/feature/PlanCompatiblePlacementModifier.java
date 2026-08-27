package com.moepus.byepregen.worldgen.feature;

public interface PlanCompatiblePlacementModifier extends FastPlacementModifier {
    static boolean isDirectlyImplementedBy(Object value) {
        for (Class<?> directInterface : value.getClass().getInterfaces()) {
            if (PlanCompatiblePlacementModifier.class.isAssignableFrom(directInterface)) {
                return true;
            }
        }
        return false;
    }

    default boolean byepregen$mayProduceMultipleOrigins() {
        return false;
    }
}
