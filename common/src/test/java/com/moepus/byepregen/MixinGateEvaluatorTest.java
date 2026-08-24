package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moepus.byepregen.config.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

final class MixinGateEvaluatorTest {
    private static final String TARGET = "example.Target";
    private static final String MIXIN = "example.TestMixin";
    private static final String GATE_DESCRIPTOR = Type.getDescriptor(MixinGate.class);

    @Test
    void ungatedMixinAppliesWithoutLookingUpTarget() {
        EvaluatorFixture fixture = fixture(node(), Set.of(), Set.of());

        assertTrue(fixture.evaluator().shouldApply(TARGET, MIXIN, new Config()));
        assertFalse(fixture.classLookups().contains(TARGET));
    }

    @Test
    void configFlagControlsApplication() {
        ClassNode gated = node(gate("config", enumValue(ConfigFlag.class, "PLACED_FEATURES")));
        EvaluatorFixture fixture = fixture(gated, Set.of(), Set.of());

        assertFalse(fixture.evaluator().shouldApply(TARGET, MIXIN, new Config()));
        assertTrue(fixture.evaluator().shouldApply(
                TARGET, MIXIN, new ConfigTestBuilder().placedFeatures(true).build()));
    }

    @Test
    void featureMetadataIsReturnedWithAnnotationResult() {
        ClassNode gated = node(gate(
                "feature", enumValue(MixinFeature.class, "YA_LIGHT"),
                "config", enumValue(ConfigFlag.class, "PLACED_FEATURES")
        ));
        MixinGateEvaluator evaluator = fixture(gated, Set.of(), Set.of()).evaluator();

        MixinGateEvaluator.GateEvaluation disabled = evaluator.evaluate(TARGET, MIXIN, new Config());
        assertEquals(MixinFeature.YA_LIGHT, disabled.feature());
        assertFalse(disabled.annotationEnabled());
        Config enabled = new ConfigTestBuilder().placedFeatures(true).build();
        assertTrue(evaluator.evaluate(TARGET, MIXIN, enabled).annotationEnabled());
    }

    @Test
    void requiredModsAlsoRequireTheTargetClass() {
        ClassNode gated = node(gate("requiredMods", List.of("dependency")));

        assertFalse(fixture(gated, Set.of(), Set.of(TARGET)).evaluator()
                .shouldApply(TARGET, MIXIN, new Config()));
        assertFalse(fixture(gated, Set.of("dependency"), Set.of()).evaluator()
                .shouldApply(TARGET, MIXIN, new Config()));
        assertTrue(fixture(gated, Set.of("dependency"), Set.of(TARGET)).evaluator()
                .shouldApply(TARGET, MIXIN, new Config()));
    }

    @Test
    void conflictingModDisablesMixin() {
        ClassNode gated = node(gate("conflictingMods", List.of("conflict")));

        assertTrue(fixture(gated, Set.of(), Set.of()).evaluator()
                .shouldApply(TARGET, MIXIN, new Config()));
        assertFalse(fixture(gated, Set.of("conflict"), Set.of()).evaluator()
                .shouldApply(TARGET, MIXIN, new Config()));
    }

    @Test
    void missingMixinBytecodeIsReported() {
        MixinGateEvaluator evaluator = new MixinGateEvaluator(
                ignored -> { throw new ClassNotFoundException("missing"); },
                ignored -> false,
                ignored -> false
        );

        assertInvalid(evaluator, "could not read mixin bytecode");
    }

    @Test
    void invalidConfigFlagIsReported() {
        MixinGateEvaluator evaluator = fixture(
                node(gate("config", enumValue(ConfigFlag.class, "DOES_NOT_EXIST"))), Set.of(), Set.of()
        ).evaluator();

        assertInvalid(evaluator, "unknown config flag: DOES_NOT_EXIST");
    }

    @Test
    void malformedGateValuesAreReported() {
        MixinGateEvaluator wrongConfigType = fixture(
                node(gate("config", List.of("PLACED_FEATURES"))), Set.of(), Set.of()
        ).evaluator();
        MixinGateEvaluator blankMod = fixture(
                node(gate("requiredMods", List.of(""))), Set.of(), Set.of()
        ).evaluator();
        MixinGateEvaluator contradictoryMod = fixture(
                node(gate(
                        "requiredMods", List.of("same"),
                        "conflictingMods", List.of("same")
                )), Set.of(), Set.of()
        ).evaluator();

        assertInvalid(wrongConfigType, "config must be a ConfigFlag");
        assertInvalid(blankMod, "mod ids must not be blank");
        assertInvalid(contradictoryMod, "a mod cannot be both required and conflicting");
    }

    private static void assertInvalid(MixinGateEvaluator evaluator, String message) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> evaluator.shouldApply(TARGET, MIXIN, new Config())
        );
        assertTrue(failure.getMessage().contains(message), failure::getMessage);
    }

    private static EvaluatorFixture fixture(
            ClassNode mixin,
            Set<String> mods,
            Set<String> classes
    ) {
        Map<String, ClassNode> nodes = new HashMap<>();
        nodes.put(MIXIN, mixin);
        List<String> classLookups = new ArrayList<>();
        MixinGateEvaluator evaluator = new MixinGateEvaluator(
                nodes::get,
                mods::contains,
                name -> {
                    classLookups.add(name);
                    return classes.contains(name);
                }
        );
        return new EvaluatorFixture(evaluator, classLookups);
    }

    private static ClassNode node(AnnotationNode... annotations) {
        ClassNode node = new ClassNode();
        node.invisibleAnnotations = List.of(annotations);
        return node;
    }

    private static AnnotationNode gate(Object... values) {
        AnnotationNode annotation = new AnnotationNode(GATE_DESCRIPTOR);
        annotation.values = List.of(values);
        return annotation;
    }

    private static String[] enumValue(Class<?> type, String value) {
        return new String[] {Type.getDescriptor(type), value};
    }

    private record EvaluatorFixture(MixinGateEvaluator evaluator, List<String> classLookups) {
    }
}
