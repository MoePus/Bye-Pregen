package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void configFieldControlsApplication() {
        ClassNode gated = node(gate("config", "enablePlacedFeatureMixin"));
        EvaluatorFixture fixture = fixture(gated, Set.of(), Set.of());
        Config config = new Config();

        assertFalse(fixture.evaluator().shouldApply(TARGET, MIXIN, config));
        config.enablePlacedFeatureMixin = true;
        assertTrue(fixture.evaluator().shouldApply(TARGET, MIXIN, config));
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
    void invalidConfigFieldIsReported() {
        MixinGateEvaluator evaluator = fixture(
                node(gate("config", "doesNotExist")), Set.of(), Set.of()
        ).evaluator();

        assertInvalid(evaluator, "invalid Config field: doesNotExist");
    }

    @Test
    void malformedGateValuesAreReported() {
        MixinGateEvaluator wrongConfigType = fixture(
                node(gate("config", List.of("enableArenaPalette"))), Set.of(), Set.of()
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

        assertInvalid(wrongConfigType, "config must be a string");
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

    private record EvaluatorFixture(MixinGateEvaluator evaluator, List<String> classLookups) {
    }
}
