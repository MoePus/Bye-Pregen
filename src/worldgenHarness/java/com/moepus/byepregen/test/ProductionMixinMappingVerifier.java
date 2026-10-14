package com.moepus.byepregen.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/** Checks reobfuscated bytecode, not just the presence of a refmap resource. */
public final class ProductionMixinMappingVerifier {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    private ProductionMixinMappingVerifier() {
    }

    public static void main(String[] args) throws IOException {
        Path classes = Path.of(args[2]);
        Map<String, List<MemberMapping>> mappings = readMappings(Path.of(args[1]));
        List<String> failures = new ArrayList<>();
        int checked = 0;
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry : mappings.entrySet()) {
                String name = entry.getKey();
                ClassNode source = read(Files.readAllBytes(classes.resolve(name + ".class")));
                var member = jar.getEntry(name + ".class");
                if (member == null) {
                    failures.add("Missing mapped class: " + name);
                    continue;
                }
                ClassNode packaged;
                try (var input = jar.getInputStream(member)) {
                    packaged = read(input.readAllBytes());
                }
                verifyMembers(source, packaged, entry.getValue(), failures);
                checked += entry.getValue().size();
            }
        }
        requireOverwriteMappings(classes, mappings, failures);
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Production Mixin mappings failed:\n" + String.join("\n", failures));
        }
        System.out.println("Production Mixin mappings passed: " + checked + " field/method mappings");
    }

    static void verifyMembers(ClassNode source, ClassNode packaged, List<MemberMapping> mappings,
                              List<String> failures) {
        for (MemberMapping mapping : mappings) {
            String descriptor = mapping.descriptor();
            boolean present = descriptor == null
                    ? mappedFieldExists(source, packaged, mapping)
                    : packaged.methods.stream().anyMatch(method -> method.name.equals(mapping.target())
                            && method.desc.equals(descriptor));
            if (!present) {
                failures.add(source.name + ": " + mapping.source() + " " + descriptor
                        + " was not reobfuscated to " + mapping.target());
            }
        }
    }

    private static boolean mappedFieldExists(ClassNode source, ClassNode packaged, MemberMapping mapping) {
        FieldNode original = source.fields.stream().filter(field -> field.name.equals(mapping.source()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing source field: " + mapping));
        return packaged.fields.stream().anyMatch(field -> field.name.equals(mapping.target())
                && field.desc.equals(original.desc));
    }

    private static void requireOverwriteMappings(Path classes, Map<String, List<MemberMapping>> mappings,
                                                List<String> failures) throws IOException {
        try (var paths = Files.walk(classes)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".class")).toList()) {
                ClassNode source = read(Files.readAllBytes(path));
                AnnotationNode mixin = annotation(source.invisibleAnnotations, MIXIN);
                if (!targetsMinecraft(mixin)) continue;
                List<MemberMapping> members = mappings.getOrDefault(source.name, List.of());
                for (MethodNode method : source.methods) {
                    AnnotationNode overwrite = annotation(method.visibleAnnotations, OVERWRITE);
                    if (overwrite == null) overwrite = annotation(method.invisibleAnnotations, OVERWRITE);
                    if (overwrite == null || Boolean.FALSE.equals(value(overwrite, "remap"))) continue;
                    if (members.stream().noneMatch(member -> method.name.equals(member.source())
                            && method.desc.equals(member.descriptor()))) {
                        failures.add("Missing @Overwrite mapping: " + source.name + "." + method.name + method.desc);
                    }
                }
            }
        }
    }

    private static boolean targetsMinecraft(AnnotationNode mixin) {
        if (mixin == null || Boolean.FALSE.equals(value(mixin, "remap"))) return false;
        Object targets = value(mixin, "value");
        return targets instanceof List<?> list && list.stream()
                .anyMatch(target -> target instanceof Type type && type.getInternalName().startsWith("net/minecraft/"));
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static Map<String, List<MemberMapping>> readMappings(Path path) throws IOException {
        Map<String, List<MemberMapping>> mappings = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3 && parts.length != 4) {
                throw new IllegalStateException("Unexpected Mixin mapping: " + line);
            }
            MemberMapping member = new MemberMapping(parts[1], parts.length == 4 ? parts[2] : null,
                    parts[parts.length - 1]);
            mappings.computeIfAbsent(parts[0], ignored -> new ArrayList<>()).add(member);
        }
        if (mappings.isEmpty()) throw new IllegalStateException("Empty Mixin mapping output: " + path);
        return mappings;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    record MemberMapping(String source, String descriptor, String target) {
    }
}
