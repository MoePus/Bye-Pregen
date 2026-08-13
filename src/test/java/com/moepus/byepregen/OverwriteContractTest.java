package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

final class OverwriteContractTest {
    private static final String OVERWRITE_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final Set<String> EXPECTED = Set.of(
            "com.moepus.byepregen.mixin.feature.predicate.AllOfPredicateMixin#test(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Z",
            "com.moepus.byepregen.mixin.feature.predicate.AnyOfPredicateMixin#test(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Z",
            "com.moepus.byepregen.mixin.climate.ClimateRTreeBuildMixin#build(ILjava/util/List;)Lnet/minecraft/world/level/biome/Climate$RTree$Node;",
            "com.moepus.byepregen.mixin.climate.ClimateRTreeBuildMixin#buildParameterSpace(Ljava/util/List;)Ljava/util/List;",
            "com.moepus.byepregen.mixin.feature.disk.DiskFeatureMixin#place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            "com.moepus.byepregen.mixin.feature.predicate.HasSturdyFacePredicateMixin#test(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Z",
            "com.moepus.byepregen.mixin.feature.predicate.MatchingBlocksPredicateMixin#test(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            "com.moepus.byepregen.mixin.palette.PalettedContainerNoLithiumMixin#acquire()V",
            "com.moepus.byepregen.mixin.palette.PalettedContainerNoLithiumMixin#release()V",
            "com.moepus.byepregen.mixin.feature.placement.PlacedFeatureMixin#placeWithContext(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            "com.moepus.byepregen.mixin.server.tick.ServerChunkCacheTickChunksMixin#tickChunks()V",
            "com.moepus.byepregen.mixin.feature.predicate.StateTestingPredicateMixin#test(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Z",
            "com.moepus.byepregen.mixin.climate.ClimateParameterListSearchMixin#findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Ljava/lang/Object;",
            "com.moepus.byepregen.mixin.compat.C2MEHookCompatibilityMixin#isChunkSaveEventFree()Z",
            "com.moepus.byepregen.mixin.compat.FastNoiseOpenCLArenaMixin#copyData(Lnet/minecraft/util/StaticCache2D;[Lnet/minecraft/world/level/block/state/BlockState;ILnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;ILjava/nio/ByteBuffer;Lnet/minecraft/world/level/ChunkPos;I)V",
            "com.moepus.byepregen.mixin.nbt.CompoundTagFastRuntimeMixin#copy()Lnet/minecraft/nbt/CompoundTag;",
            "com.moepus.byepregen.mixin.nbt.CompoundTagLoadSizingMixin#loadCompound(Ljava/io/DataInput;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;",
            "com.moepus.byepregen.mixin.yalight.ClientPacketListenerYALightMixin#readSectionList(IILnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/world/level/LightLayer;Ljava/util/BitSet;Ljava/util/BitSet;Ljava/util/Iterator;)V",
            "com.moepus.byepregen.mixin.yalight.ClientboundLightUpdatePacketDataYALightMixin#prepareSectionData(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/world/level/LightLayer;ILjava/util/BitSet;Ljava/util/BitSet;Ljava/util/List;)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#checkBlock(Lnet/minecraft/core/BlockPos;)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#getDebugData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)Ljava/lang/String;",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#getDebugSectionType(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/lighting/LayerLightSectionStorage$SectionType;",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#getLayerListener(Lnet/minecraft/world/level/LightLayer;)Lnet/minecraft/world/level/lighting/LayerLightEventListener;",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#getRawBrightness(Lnet/minecraft/core/BlockPos;I)I",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#hasLightWork()Z",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#lightOnInSection(Lnet/minecraft/core/SectionPos;)Z",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#propagateLightSources(Lnet/minecraft/world/level/ChunkPos;)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#retainData(Lnet/minecraft/world/level/ChunkPos;Z)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#runLightUpdates()I",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#setLightEnabled(Lnet/minecraft/world/level/ChunkPos;Z)V",
            "com.moepus.byepregen.mixin.yalight.LevelLightEngineYAMixin#updateSectionStatus(Lnet/minecraft/core/SectionPos;Z)V",
            "com.moepus.byepregen.mixin.yalight.LevelRendererYALightMixin#getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            "com.moepus.byepregen.mixin.yalight.LevelRendererYALightMixin#getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#addTask(IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#checkBlock(Lnet/minecraft/core/BlockPos;)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#initializeLight(Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#lightChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#retainData(Lnet/minecraft/world/level/ChunkPos;Z)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#tryScheduleUpdate()V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#updateSectionStatus(Lnet/minecraft/core/SectionPos;Z)V",
            "com.moepus.byepregen.mixin.yalight.ThreadedLevelLightEngineYAMixin#waitForPendingTasks(II)Ljava/util/concurrent/CompletableFuture;"
    );

    @Test
    void overwriteMethodsMatchExplicitContract() throws Exception {
        assertEquals(44, EXPECTED.size(), "overwrite contract count changed");
        assertEquals(EXPECTED, discoverOverwrites());
    }

    @Test
    void mixinConfigurationRequiresOverwriteAnnotations() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/byepregen.mixins.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(json.getAsJsonObject("overwrites").get("requireAnnotations").getAsBoolean());
        }
    }

    private static Set<String> discoverOverwrites() throws Exception {
        Path root = Path.of(OverwriteContractTest.class.getResource("/com/moepus/byepregen/mixin").toURI());
        Set<String> contracts = new LinkedHashSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                try (InputStream input = Files.newInputStream(path)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                }
                for (MethodNode method : node.methods) {
                    if ((method.access & Opcodes.ACC_BRIDGE) == 0 && hasOverwrite(method)) {
                        contracts.add(node.name.replace('/', '.') + "#" + method.name + method.desc);
                    }
                }
            }
        }
        return contracts;
    }

    private static boolean hasOverwrite(MethodNode method) {
        return hasAnnotation(method.invisibleAnnotations) || hasAnnotation(method.visibleAnnotations);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations) {
        return annotations != null && annotations.stream()
                .anyMatch(annotation -> OVERWRITE_DESCRIPTOR.equals(annotation.desc));
    }
}
