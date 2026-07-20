package com.moepus.byepregen.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public final class C2MEAsyncSerializationCompat {
    private static final String ASYNC_SERIALIZATION_MANAGER =
            "com.ishland.c2me.threading.chunkio.common.AsyncSerializationManager";
    private static final String ASYNC_SERIALIZATION_MANAGER_OLD =
            "com.ishland.c2me.rewrites.chunksystem.common.async_chunkio.AsyncSerializationManager";

    private static final Class<?> MANAGER_CLASS = findManagerClass();
    private static final Method GET_SCOPE = findGetScope(MANAGER_CLASS);

    private C2MEAsyncSerializationCompat() {
    }

    public static boolean isAvailable() {
        return GET_SCOPE != null;
    }

    public static boolean hasBlockEntities(ChunkAccess chunk) {
        Object scope = scope(chunk);
        if (scope != null) {
            return hasBlockEntities(scope);
        }
        return !chunk.getBlockEntitiesPos().isEmpty();
    }

    public static Iterable<BlockPos> blockEntityPositions(ChunkAccess chunk) {
        Object scope = scope(chunk);
        if (scope != null) {
            return blockEntityPositions(scope);
        }
        return chunk.getBlockEntitiesPos();
    }

    public static Tag blockEntityNbtForSaving(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        Object scope = scope(chunk);
        if (scope == null) {
            return chunk.getBlockEntityNbtForSaving(pos);
        }

        Tag live = liveBlockEntityNbt(scope, chunk, pos);
        if (live != null) {
            return live;
        }
        return pendingPackedNbt(scope, chunk, pos);
    }

    private static boolean hasBlockEntities(Object scope) {
        Object positions = fieldValue(scope, "blockEntityPositions");
        if (positions instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (positions instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }

        Object blockEntities = fieldValue(scope, "blockEntities");
        return blockEntities instanceof Map<?, ?> map && !map.isEmpty();
    }

    private static Object scope(ChunkAccess chunk) {
        if (GET_SCOPE == null) {
            return null;
        }
        try {
            return GET_SCOPE.invoke(null, chunk.getPos());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterable<BlockPos> blockEntityPositions(Object scope) {
        Object positions = fieldValue(scope, "blockEntityPositions");
        if (positions instanceof Iterable<?> iterable) {
            return (Iterable<BlockPos>) iterable;
        }

        Object blockEntities = fieldValue(scope, "blockEntities");
        if (blockEntities instanceof Map<?, ?> map) {
            return (Iterable<BlockPos>) map.keySet();
        }
        return Collections.emptySet();
    }

    private static Tag liveBlockEntityNbt(Object scope, ChunkAccess chunk, BlockPos pos) {
        Object blockEntities = fieldValue(scope, "blockEntities");
        if (!(blockEntities instanceof Map<?, ?> map)) {
            return null;
        }
        Object blockEntity = map.get(pos);
        if (blockEntity == null) {
            return null;
        }
        try {
            Method saveWithFullMetadata = blockEntity.getClass().getMethod("saveWithFullMetadata");
            Object tag = saveWithFullMetadata.invoke(blockEntity);
            if (tag instanceof CompoundTag compoundTag && chunk instanceof LevelChunk) {
                compoundTag.putBoolean("keepPacked", false);
            }
            return tag instanceof Tag nbt ? nbt : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Tag pendingPackedNbt(Object scope, ChunkAccess chunk, BlockPos pos) {
        Object pending = fieldValue(scope, "pendingBlockEntityNbtsPacked");
        if (!(pending instanceof Map<?, ?> map)) {
            return null;
        }
        Object tag = map.get(pos);
        if (tag instanceof CompoundTag compoundTag && chunk instanceof LevelChunk) {
            compoundTag.putBoolean("keepPacked", true);
        }
        return tag instanceof Tag nbt ? nbt : null;
    }

    private static Object fieldValue(Object owner, String name) {
        try {
            Field field = owner.getClass().getField(name);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Class<?> findManagerClass() {
        return findClass(ASYNC_SERIALIZATION_MANAGER, ASYNC_SERIALIZATION_MANAGER_OLD);
    }

    private static Method findGetScope(Class<?> managerClass) {
        if (managerClass == null) {
            return null;
        }
        try {
            return managerClass.getMethod("getScope", net.minecraft.world.level.ChunkPos.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Class<?> findClass(String... names) {
        ClassLoader loader = C2MEAsyncSerializationCompat.class.getClassLoader();
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }
}
