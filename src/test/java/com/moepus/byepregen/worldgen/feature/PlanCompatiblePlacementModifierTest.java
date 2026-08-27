package com.moepus.byepregen.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanCompatiblePlacementModifierTest {
    @Test
    void acceptsOnlyDirectCapabilityDeclarations() {
        assertTrue(PlanCompatiblePlacementModifier.isDirectlyImplementedBy(new DirectCapability()));
        assertTrue(PlanCompatiblePlacementModifier.isDirectlyImplementedBy(new ExplicitSubclassCapability()));
        assertTrue(PlanCompatiblePlacementModifier.isDirectlyImplementedBy(new ExtendedCapability()));
        assertFalse(PlanCompatiblePlacementModifier.isDirectlyImplementedBy(new InheritedCapability()));
    }

    private static class DirectCapability implements PlanCompatiblePlacementModifier {
    }

    private static final class InheritedCapability extends DirectCapability {
    }

    private static final class ExplicitSubclassCapability extends DirectCapability
            implements PlanCompatiblePlacementModifier {
    }

    private interface CustomCapability extends PlanCompatiblePlacementModifier {
    }

    private static final class ExtendedCapability implements CustomCapability {
    }
}
