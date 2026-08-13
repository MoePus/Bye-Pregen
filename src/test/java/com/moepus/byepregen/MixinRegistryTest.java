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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

final class MixinRegistryTest {
    private static final String MIXIN_PREFIX = "com.moepus.byepregen.mixin.";
    private static final String MIXIN_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String GATE_DESCRIPTOR = Type.getDescriptor(MixinGate.class);

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

    private record Registry(List<String> classes) {
    }
}
