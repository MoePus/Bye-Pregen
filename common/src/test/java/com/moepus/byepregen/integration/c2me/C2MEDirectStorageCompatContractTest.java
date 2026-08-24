package com.moepus.byepregen.integration.c2me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class C2MEDirectStorageCompatContractTest {
    private static final String DIRECT_STORAGE_OWNER =
            "com/ishland/c2me/base/common/theinterface/IDirectStorage";
    private static final String COMPLETION_AWARE_DESCRIPTOR =
            "(Lnet/minecraft/world/level/ChunkPos;Ljava/util/concurrent/CompletableFuture;)"
                    + "Ljava/util/concurrent/CompletableFuture;";

    @Test
    void rawWritesUseCompletionAwareC2meOverload() throws Exception {
        String resource = "/" + C2MEDirectStorageCompat.class.getName().replace('.', '/') + ".class";
        ClassNode node = new ClassNode();
        try (InputStream input = C2MEDirectStorageCompatContractTest.class.getResourceAsStream(resource)) {
            new ClassReader(input).accept(node, 0);
        }

        List<MethodInsnNode> calls = node.methods.stream()
                .filter(method -> method.name.equals("setRawChunkData"))
                .flatMap(method -> StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(DIRECT_STORAGE_OWNER))
                .filter(call -> call.name.equals("setRawChunkData"))
                .toList();

        assertEquals(1, calls.size());
        assertEquals(COMPLETION_AWARE_DESCRIPTOR, calls.getFirst().desc);
    }
}
