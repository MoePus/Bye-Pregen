package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moepus.byepregen.config.Config;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

final class MixinRegistryTest {
    private static final String MIXIN_PREFIX = "com.moepus.byepregen.mixin.";
    private static final String SERVER_TICK_PREFIX = MIXIN_PREFIX + "server.tick.";
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String GATE_DESCRIPTOR = Type.getDescriptor(MixinGate.class);
    private static final String UNIQUE_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final Set<String> INJECTION_DESCRIPTORS = Set.of(
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lorg/mixinlite/injector/InjectLite;",
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;"
    );
    private static final EnumMap<MixinFeature, Integer> FEATURE_COUNTS = featureCounts();

    @Test
    void registryIsCompleteAndReferencesCompiledMixins() throws Exception {
        Registry registry = readRegistry();
        Set<String> registered = new LinkedHashSet<>(registry.classes());

        assertEquals(registry.classes().size(), registered.size(), "duplicate mixin registry entries");
        for (String className : registered) {
            assertNotNull(resourceUrl(className), "registered mixin has no compiled class: " + className);
        }
        assertEquals(discoverCompiledMixins(), registered, "mixin registry is missing or has stale classes");
    }

    @Test
    void gateConfigNamesArePublicBooleanFields() throws Exception {
        for (String className : readRegistry().classes()) {
            ClassNode node = readClass(className);
            AnnotationNode gate = annotation(node, GATE_DESCRIPTOR);
            String configName = annotationString(gate, "config");
            if (configName.isEmpty()) {
                continue;
            }
            Field field = Config.class.getField(configName);
            assertEquals(boolean.class, field.getType(), "gate field is not boolean: " + configName);
            assertTrue(Modifier.isPublic(field.getModifiers()), "gate field is not public: " + configName);
        }
    }

    @Test
    void featureMetadataIsValidAndComplete() throws Exception {
        EnumMap<MixinFeature, Integer> actual = new EnumMap<>(MixinFeature.class);
        for (String className : readRegistry().classes()) {
            AnnotationNode gate = annotation(readClass(className), GATE_DESCRIPTOR);
            MixinFeature feature = annotationFeature(gate);
            if (feature != MixinFeature.NONE) {
                actual.merge(feature, 1, Integer::sum);
            }
        }

        assertEquals(FEATURE_COUNTS, actual);
    }

    @Test
    void privateInjectedAndUniqueMembersUseProjectPrefix() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String className : readRegistry().classes()) {
            ClassNode node = readClass(className);
            node.fields.stream()
                    .filter(field -> isPrivate(field.access))
                    .filter(field -> hasAnnotation(field.visibleAnnotations, UNIQUE_DESCRIPTOR)
                            || hasAnnotation(field.invisibleAnnotations, UNIQUE_DESCRIPTOR))
                    .filter(field -> !field.name.startsWith("byepregen$"))
                    .forEach(field -> violations.add(className + "#" + field.name));
            node.methods.stream()
                    .filter(method -> isPrivate(method.access))
                    .filter(MixinRegistryTest::isInjectedOrUnique)
                    .filter(method -> !method.name.startsWith("byepregen$"))
                    .forEach(method -> violations.add(className + "#" + method.name + method.desc));
        }

        assertTrue(violations.isEmpty(), "private mixin members lack byepregen$ prefix: " + violations);
    }

    @Test
    void pluginExplicitMixinReferencesAreRegistered() throws Exception {
        Set<String> registered = new HashSet<>(readRegistry().classes());
        ClassNode plugin = readClass(MixinPlugin.class.getName());
        List<String> missing = new ArrayList<>();

        for (FieldNode field : plugin.fields) {
            if (field.value instanceof String value
                    && value.startsWith(MIXIN_PREFIX)
                    && (value.endsWith("Mixin") || value.endsWith("Accessor"))
                    && !registered.contains(value)) {
                missing.add(field.name + "=" + value);
            }
        }
        assertTrue(missing.isEmpty(), "plugin mixin references are not registered: " + missing);
    }

    @Test
    void serverTickMixinsKeepDistinctConflictPolicies() throws Exception {
        AnnotationNode chunkTick = gate("ServerChunkCacheTickChunksMixin");
        AnnotationNode weatherTick = gate("ServerLevelWeatherTickMixin");

        assertEquals("enableFastTickChunks", annotationString(chunkTick, "config"));
        assertEquals(List.of("servercore"), annotationStrings(chunkTick, "conflictingMods"));
        assertEquals("enableFastTickChunks", annotationString(weatherTick, "config"));
        assertEquals(List.of(), annotationStrings(weatherTick, "conflictingMods"));
    }

    private static AnnotationNode gate(String simpleName) throws IOException {
        return annotation(readClass(SERVER_TICK_PREFIX + simpleName), GATE_DESCRIPTOR);
    }

    private static Registry readRegistry() throws IOException {
        try (InputStream input = MixinRegistryTest.class.getResourceAsStream("/byepregen.mixins.json")) {
            assertNotNull(input, "missing byepregen.mixins.json");
            JsonObject json = JsonParser.parseReader(new java.io.InputStreamReader(
                    input, java.nio.charset.StandardCharsets.UTF_8
            )).getAsJsonObject();
            String packageName = json.get("package").getAsString();
            List<String> classes = new ArrayList<>();
            addClasses(classes, packageName, json.getAsJsonArray("mixins"));
            addClasses(classes, packageName, json.getAsJsonArray("client"));
            return new Registry(List.copyOf(classes));
        }
    }

    private static void addClasses(List<String> target, String packageName, JsonArray entries) {
        if (entries == null) {
            return;
        }
        entries.forEach(entry -> target.add(packageName + "." + entry.getAsString()));
    }

    private static Set<String> discoverCompiledMixins() throws IOException, URISyntaxException {
        Path packageRoot = Path.of(MixinRegistryTest.class.getResource("/com/moepus/byepregen/mixin").toURI());
        Set<String> mixins = new LinkedHashSet<>();
        try (var paths = Files.walk(packageRoot)) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .map(packageRoot::relativize)
                    .map(Path::toString)
                    .map(name -> MIXIN_PREFIX + name.substring(0, name.length() - 6).replace('\\', '.'))
                    .filter(MixinRegistryTest::hasMixinAnnotation)
                    .forEach(mixins::add);
        }
        return mixins;
    }

    private static boolean hasMixinAnnotation(String className) {
        try {
            return annotation(readClass(className), MIXIN_DESCRIPTOR) != null;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + className, exception);
        }
    }

    private static ClassNode readClass(String className) throws IOException {
        try (InputStream input = resource(className)) {
            if (input == null) {
                throw new IOException("Missing class resource " + className);
            }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            return node;
        }
    }

    private static InputStream resource(String className) {
        return MixinRegistryTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class");
    }

    private static URL resourceUrl(String className) {
        return MixinRegistryTest.class.getResource("/" + className.replace('.', '/') + ".class");
    }

    private static AnnotationNode annotation(ClassNode node, String descriptor) {
        AnnotationNode annotation = findAnnotation(node.invisibleAnnotations, descriptor);
        return annotation != null ? annotation : findAnnotation(node.visibleAnnotations, descriptor);
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return null;
        }
        return annotations.stream().filter(item -> descriptor.equals(item.desc)).findFirst().orElse(null);
    }

    private static boolean isPrivate(int access) {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    private static boolean isInjectedOrUnique(MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, UNIQUE_DESCRIPTOR)
                || hasAnnotation(method.invisibleAnnotations, UNIQUE_DESCRIPTOR)
                || hasAnyAnnotation(method.visibleAnnotations, INJECTION_DESCRIPTORS)
                || hasAnyAnnotation(method.invisibleAnnotations, INJECTION_DESCRIPTORS);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        return findAnnotation(annotations, descriptor) != null;
    }

    private static boolean hasAnyAnnotation(List<AnnotationNode> annotations, Set<String> descriptors) {
        return annotations != null && annotations.stream().anyMatch(item -> descriptors.contains(item.desc));
    }

    private static String annotationString(AnnotationNode annotation, String name) {
        if (annotation == null || annotation.values == null) {
            return "";
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                return (String) annotation.values.get(index + 1);
            }
        }
        return "";
    }

    private static List<String> annotationStrings(AnnotationNode annotation, String name) {
        if (annotation == null || annotation.values == null) {
            return List.of();
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (name.equals(annotation.values.get(index))) {
                List<?> values = (List<?>) annotation.values.get(index + 1);
                return values.stream().map(String.class::cast).toList();
            }
        }
        return List.of();
    }

    private static MixinFeature annotationFeature(AnnotationNode annotation) {
        if (annotation == null || annotation.values == null) {
            return MixinFeature.NONE;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if ("feature".equals(annotation.values.get(index))) {
                String[] value = (String[]) annotation.values.get(index + 1);
                return MixinFeature.valueOf(value[1]);
            }
        }
        return MixinFeature.NONE;
    }

    private static EnumMap<MixinFeature, Integer> featureCounts() {
        EnumMap<MixinFeature, Integer> counts = new EnumMap<>(MixinFeature.class);
        counts.put(MixinFeature.ARENA, 13);
        counts.put(MixinFeature.DFC, 5);
        counts.put(MixinFeature.GC_FREE_CHUNK_SAVE, 10);
        counts.put(MixinFeature.SURFACE_BIOME_CACHE, 2);
        counts.put(MixinFeature.SURFACE_RULE_COMPILER, 16);
        counts.put(MixinFeature.YA_LIGHT, 19);
        return counts;
    }

    private record Registry(List<String> classes) {
    }
}
