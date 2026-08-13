package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.integration.runtime.ModEnvironment;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

final class MixinGateEvaluator {
    private static final String GATE_DESCRIPTOR = Type.getDescriptor(MixinGate.class);

    private final ClassNodeSource classNodes;
    private final Predicate<String> modExists;
    private final Predicate<String> classExists;

    MixinGateEvaluator(
            ClassNodeSource classNodes,
            Predicate<String> modExists,
            Predicate<String> classExists
    ) {
        this.classNodes = classNodes;
        this.modExists = modExists;
        this.classExists = classExists;
    }

    static MixinGateEvaluator createDefault() {
        return new MixinGateEvaluator(
                className -> MixinService.getService().getBytecodeProvider().getClassNode(className),
                ModEnvironment::isModLoaded,
                ModEnvironment::isClassAvailable
        );
    }

    boolean shouldApply(String targetClassName, String mixinClassName, Config config) {
        return this.evaluate(targetClassName, mixinClassName, config).annotationEnabled();
    }

    GateEvaluation evaluate(String targetClassName, String mixinClassName, Config config) {
        GateSpec gate = readGate(mixinClassName);
        validate(gate, mixinClassName);
        AnnotationGateContext context = new AnnotationGateContext(
                targetClassName, mixinClassName, config);
        return new GateEvaluation(
                gate.feature(),
                this.passesAnnotationGate(context, gate)
        );
    }

    private boolean passesAnnotationGate(AnnotationGateContext context, GateSpec gate) {
        if (!isConfigEnabled(gate.config(), context.config(), context.mixinClassName())) {
            return false;
        }
        if (!allModsExist(gate.requiredMods())) {
            return false;
        }
        if (hasConflictingMod(gate.conflictingMods())) {
            return false;
        }
        return gate.requiredMods().isEmpty() || this.classExists.test(context.targetClassName());
    }

    private GateSpec readGate(String mixinClassName) {
        try {
            ClassNode classNode = this.classNodes.get(mixinClassName);
            AnnotationNode annotation = findGate(classNode.invisibleAnnotations);
            if (annotation == null) {
                annotation = findGate(classNode.visibleAnnotations);
            }
            return annotation == null ? GateSpec.NONE : GateSpec.from(annotation, mixinClassName);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidGate(mixinClassName, "could not read mixin bytecode", exception);
        }
    }

    private boolean allModsExist(List<String> requiredMods) {
        return requiredMods.stream().allMatch(this.modExists);
    }

    private boolean hasConflictingMod(List<String> conflictingMods) {
        return conflictingMods.stream().anyMatch(this.modExists);
    }

    private static AnnotationNode findGate(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return null;
        }
        return annotations.stream()
                .filter(annotation -> GATE_DESCRIPTOR.equals(annotation.desc))
                .findFirst()
                .orElse(null);
    }

    private static void validate(GateSpec gate, String mixinClassName) {
        validateModIds(gate.requiredMods(), mixinClassName);
        validateModIds(gate.conflictingMods(), mixinClassName);

        Set<String> conflicts = new HashSet<>(gate.conflictingMods());
        if (gate.requiredMods().stream().anyMatch(conflicts::contains)) {
            throw invalidGate(mixinClassName, "a mod cannot be both required and conflicting", null);
        }
    }

    private static void validateModIds(List<String> modIds, String mixinClassName) {
        if (modIds.stream().anyMatch(modId -> modId == null || modId.isBlank())) {
            throw invalidGate(mixinClassName, "mod ids must not be blank", null);
        }
    }

    private static boolean isConfigEnabled(String fieldName, Config config, String mixinClassName) {
        if (fieldName.isEmpty()) {
            return true;
        }

        try {
            Field field = Config.class.getField(fieldName);
            if (field.getType() != boolean.class) {
                throw invalidGate(mixinClassName, "Config." + fieldName + " is not boolean", null);
            }
            return field.getBoolean(config);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw invalidGate(mixinClassName, "invalid Config field: " + fieldName, exception);
        }
    }

    private static IllegalStateException invalidGate(String mixinClassName, String message, Throwable cause) {
        return new IllegalStateException("Invalid @MixinGate on " + mixinClassName + ": " + message, cause);
    }

    @FunctionalInterface
    interface ClassNodeSource {
        ClassNode get(String className) throws Exception;
    }

    record GateEvaluation(MixinFeature feature, boolean annotationEnabled) {
    }

    private record AnnotationGateContext(
            String targetClassName,
            String mixinClassName,
            Config config
    ) {
    }

    private record GateSpec(
            MixinFeature feature,
            List<String> requiredMods,
            List<String> conflictingMods,
            String config
    ) {
        private static final GateSpec NONE = new GateSpec(
                MixinFeature.NONE, List.of(), List.of(), ""
        );

        static GateSpec from(AnnotationNode annotation, String mixinClassName) {
            return new GateSpec(
                    featureValue(annotation, mixinClassName),
                    stringListValue(annotation, "requiredMods", mixinClassName),
                    stringListValue(annotation, "conflictingMods", mixinClassName),
                    stringValue(annotation, "config", mixinClassName)
            );
        }

        private static MixinFeature featureValue(AnnotationNode annotation, String mixinClassName) {
            Object value = value(annotation, "feature");
            if (value == null) {
                return MixinFeature.NONE;
            }
            if (!(value instanceof String[] enumValue)
                    || enumValue.length != 2
                    || !Type.getDescriptor(MixinFeature.class).equals(enumValue[0])) {
                throw invalidGate(mixinClassName, "feature must be a MixinFeature", null);
            }
            try {
                return MixinFeature.valueOf(enumValue[1]);
            } catch (IllegalArgumentException exception) {
                throw invalidGate(mixinClassName, "unknown feature: " + enumValue[1], exception);
            }
        }

        private static List<String> stringListValue(
                AnnotationNode annotation,
                String name,
                String mixinClassName
        ) {
            Object value = value(annotation, name);
            if (value == null) {
                return List.of();
            }
            if (!(value instanceof List<?> values) || values.stream().anyMatch(item -> !(item instanceof String))) {
                throw invalidGate(mixinClassName, name + " must be a string array", null);
            }
            return values.stream().map(String.class::cast).toList();
        }

        private static String stringValue(AnnotationNode annotation, String name, String mixinClassName) {
            Object value = value(annotation, name);
            if (value == null) {
                return "";
            }
            if (!(value instanceof String string)) {
                throw invalidGate(mixinClassName, name + " must be a string", null);
            }
            return string;
        }

        private static Object value(AnnotationNode annotation, String name) {
            if (annotation.values == null) {
                return null;
            }
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (name.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
            return null;
        }
    }
}
