package com.moepus.byepregen.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionMixinMappingVerifierTest {
    @Test
    void acceptsReobfuscatedMethodsAndFields() {
        ClassNode source = type("search", "index", "()I", "I");
        ClassNode packaged = type("m_1_", "f_2_", "()I", "I");
        assertTrue(audit(source, packaged).isEmpty());
    }

    @Test
    void rejectsDevelopmentNamesInProductionBytecode() {
        ClassNode source = type("search", "index", "()I", "I");
        List<String> failures = audit(source, source);
        assertEquals(2, failures.size());
        assertTrue(failures.get(0).contains("m_1_"));
        assertTrue(failures.get(1).contains("f_2_"));
    }

    @Test
    void checksDescriptorsAsWellAsMemberNames() {
        ClassNode source = type("search", "index", "()I", "I");
        ClassNode packaged = type("m_1_", "f_2_", "(I)I", "J");
        assertEquals(2, audit(source, packaged).size());
    }

    private static List<String> audit(ClassNode source, ClassNode packaged) {
        List<String> failures = new ArrayList<>();
        ProductionMixinMappingVerifier.verifyMembers(source, packaged, List.of(
                new ProductionMixinMappingVerifier.MemberMapping("search", "()I", "m_1_"),
                new ProductionMixinMappingVerifier.MemberMapping("index", null, "f_2_")), failures);
        return failures;
    }

    private static ClassNode type(String method, String field, String methodDescriptor, String fieldDescriptor) {
        ClassNode node = new ClassNode();
        node.name = "example/Mixin";
        node.methods.add(new MethodNode(0, method, methodDescriptor, null, null));
        node.fields.add(new FieldNode(0, field, fieldDescriptor, null, null));
        return node;
    }
}
