package com.moepus.byepregen.integration.c2me;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class C2MEAsyncSerializationCompat {
    private static final Adapter ADAPTER = Adapter.create();

    private C2MEAsyncSerializationCompat() {
    }

    public static boolean available() {
        return ADAPTER != null;
    }

    public static boolean hasBlockEntities(ChunkAccess chunk) {
        return !blockEntityPositions(chunk).isEmpty();
    }

    public static Set<BlockPos> blockEntityPositions(ChunkAccess chunk) {
        Object scope = scope(chunk);
        return scope == null ? chunk.getBlockEntitiesPos() : ADAPTER.positions(scope);
    }

    public static Tag blockEntityNbtForSaving(ChunkAccess chunk, BlockPos pos) {
        Object scope = scope(chunk);
        if (scope != null) {
            Tag tag = ADAPTER.tag(scope, pos);
            if (tag != null) {
                return tag;
            }
        }
        return chunk.getBlockEntityNbtForSaving(pos);
    }

    private static Object scope(ChunkAccess chunk) {
        return ADAPTER == null ? null : ADAPTER.scope(chunk);
    }

    private record Adapter(
            Method getScope,
            Field positions,
            Field tags,
            Field blockEntities,
            Field pendingTags
    ) {
        private static final String[] MANAGERS = {
                "com.ishland.c2me.threading.chunkio.common.AsyncSerializationManager",
                "com.misanthropy.fastchunkgen.threading.chunkio.common.AsyncSerializationManager"
        };

        static Adapter create() {
            for (String manager : MANAGERS) {
                try {
                    return create(manager);
                } catch (ClassNotFoundException ignored) {
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    throw new IllegalStateException("Cannot inspect async serialization scope " + manager, exception);
                }
            }
            return null;
        }

        private static Adapter create(String managerName) throws ReflectiveOperationException {
            Class<?> manager = Class.forName(managerName, false, Adapter.class.getClassLoader());
            Method getScope = manager.getMethod("getScope", net.minecraft.world.level.ChunkPos.class);
            Class<?> scope = getScope.getReturnType();
            return new Adapter(
                    getScope,
                    optionalField(scope, "blockEntityPositions"),
                    optionalField(scope, "blockEntityNbts"),
                    optionalField(scope, "blockEntities"),
                    optionalField(scope, "pendingBlockEntityNbtsPacked")
            );
        }

        Object scope(ChunkAccess chunk) {
            try {
                return this.getScope.invoke(null, chunk.getPos());
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            } catch (InvocationTargetException exception) {
                throw propagate(exception.getCause());
            }
        }

        Set<BlockPos> positions(Object scope) {
            if (this.positions != null) {
                return Set.copyOf(mapOrSet(this.positions, scope));
            }
            LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
            result.addAll(map(this.blockEntities, scope).keySet());
            result.addAll(map(this.pendingTags, scope).keySet());
            return result;
        }

        Tag tag(Object scope, BlockPos pos) {
            Tag direct = (Tag) map(this.tags, scope).get(pos);
            if (direct != null) {
                return direct;
            }
            Tag pending = (Tag) map(this.pendingTags, scope).get(pos);
            if (pending != null) {
                return pending;
            }
            Object entity = map(this.blockEntities, scope).get(pos);
            return entity instanceof BlockEntity blockEntity ? blockEntity.saveWithFullMetadata() : null;
        }

        private static Field optionalField(Class<?> owner, String name) {
            try {
                Field field = owner.getField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<BlockPos, ?> map(Field field, Object owner) {
            if (field == null) {
                return Map.of();
            }
            try {
                return (Map<BlockPos, ?>) field.get(owner);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @SuppressWarnings("unchecked")
        private static Set<BlockPos> mapOrSet(Field field, Object owner) {
            try {
                return (Set<BlockPos>) field.get(owner);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static RuntimeException propagate(Throwable throwable) {
            return throwable instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(throwable);
        }
    }
}
