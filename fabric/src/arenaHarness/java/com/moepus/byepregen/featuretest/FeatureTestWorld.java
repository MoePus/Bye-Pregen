package com.moepus.byepregen.featuretest;

import com.moepus.byepregen.worldgen.feature.WorldGenRegionSectionCache;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;

/** Isolated, writable chunks: feature invocations have identical inputs without scheduler timing. */
final class FeatureTestWorld implements InvocationHandler {
    private static final int HEIGHT = 96;
    private static final int GROUND_Y = 62;
    private static final int LEAF_DECAY_DISTANCE = 7;
    private static final int LEAF_DISTANCE_TICK_DELAY = 1;
    private final ServerLevel serverLevel;
    private final Map<Long, ProtoChunk> chunks = new TreeMap<>();
    private final Map<Long, BlockEntity> blockEntities = new TreeMap<>();
    private final MessageDigest writes;
    private final MessageDigest treeWrites;
    private final RandomSource worldRandom = RandomSource.create(314159);
    private final boolean layers;
    private final WorldGenLevel level;
    private int writeCount;
    private int treeWriteCount;

    FeatureTestWorld(ServerLevel serverLevel, boolean layers) throws Exception {
        this.serverLevel = serverLevel;
        this.layers = layers;
        this.writes = MessageDigest.getInstance("SHA-256");
        this.treeWrites = MessageDigest.getInstance("SHA-256");
        this.level = (WorldGenLevel) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{WorldGenLevel.class, WorldGenRegionSectionCache.class}, this);
    }

    WorldGenLevel level() { return this.level; }
    int writeCount() { return this.writeCount; }

    @Override
    public Object invoke(Object proxy, Method method, Object[] nullableArgs) throws Throwable {
        Object[] args = nullableArgs == null ? new Object[0] : nullableArgs;
        return switch (method.getName()) {
            case "getMinY" -> 0;
            case "getMaxY" -> HEIGHT - 1;
            case "getHeight", "getHeightmapPos" -> args.length == 0 ? HEIGHT : this.height(args);
            case "getBlockState" -> this.state((BlockPos) args[0]);
            case "getFluidState" -> this.state((BlockPos) args[0]).getFluidState();
            case "isStateAtPosition", "isFluidAtPosition" -> this.testPosition(method.getName(), args);
            case "setBlock" -> this.set((BlockPos) args[0], (BlockState) args[1], (int) args[2]);
            case "getChunk", "byepregen$getCachedChunk" -> this.getChunk(args);
            default -> this.worldServices(proxy, method, args);
        };
    }

    private boolean testPosition(String method, Object[] args) {
        BlockState state = this.state((BlockPos) args[0]);
        return test(args[1], method.equals("isStateAtPosition") ? state : state.getFluidState());
    }

    private Object worldServices(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "getLevel" -> this.serverLevel;
            case "registryAccess" -> this.serverLevel.registryAccess();
            case "getBiome" -> this.serverLevel.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            case "getSeed" -> 314159L;
            case "getRandom" -> this.worldRandom;
            case "scheduleTick" -> { this.event("tick", args); yield null; }
            case "getBlockEntity" -> this.blockEntity(args);
            default -> this.worldEvents(proxy, method, args);
        };
    }

    private Object worldEvents(Object proxy, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "ensureCanWrite", "hasChunk", "hasChunkAt", "hasChunksAt", "hasChunksAtImmediately" -> true;
            case "isClientSide", "addFreshEntity" -> false;
            case "setCurrentlyGenerating", "blockUpdated", "levelEvent", "gameEvent" -> null;
            case "toString" -> "FeatureTestWorld";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> this.defaultCall(proxy, method, args);
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean test(Object predicate, Object value) {
        return ((Predicate<Object>) predicate).test(value);
    }

    private Object defaultCall(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
        throw new UnsupportedOperationException("Fixture needs " + method);
    }

    private Object height(Object[] args) {
        Heightmap.Types type = (Heightmap.Types) args[0];
        BlockPos at = args.length == 2 ? (BlockPos) args[1] : new BlockPos((int) args[1], 0, (int) args[2]);
        int y = HEIGHT - 1;
        while (y >= 0 && !type.isOpaque().test(this.state(new BlockPos(at.getX(), y, at.getZ())))) --y;
        return args.length == 2 ? new BlockPos(at.getX(), y + 1, at.getZ()) : y + 1;
    }

    private ProtoChunk getChunk(Object[] args) {
        if (args[0] instanceof BlockPos pos) return this.chunk(pos.getX() >> 4, pos.getZ() >> 4);
        return this.chunk((int) args[0], (int) args[1]);
    }

    private ProtoChunk chunk(int x, int z) {
        if (Math.abs(x) > 8 || Math.abs(z) > 8) throw new AssertionError("Feature escaped fixture: " + x + ',' + z);
        return this.chunks.computeIfAbsent(ChunkPos.pack(x, z), ignored -> this.createChunk(x, z));
    }

    private ProtoChunk createChunk(int x, int z) {
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY,
                LevelHeightAccessor.create(0, HEIGHT), PalettedContainerFactory.create(this.serverLevel.registryAccess()), null);
        for (int y = 0; y <= GROUND_Y; ++y) {
            BlockState state = this.initialState(y);
            for (int index = 0; index < 256; ++index) {
                chunk.getSection(y >> 4).setBlockState(index & 15, y & 15, index >> 4, state);
            }
        }
        return chunk;
    }

    private BlockState initialState(int y) {
        if (y == 0) return Blocks.BEDROCK.defaultBlockState();
        if (this.layers && y % 16 >= 8) return Blocks.AIR.defaultBlockState();
        if (y == GROUND_Y) return Blocks.GRASS_BLOCK.defaultBlockState();
        return y >= GROUND_Y - 4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private BlockState state(BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= HEIGHT) return Blocks.VOID_AIR.defaultBlockState();
        return this.chunk(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
    }

    private boolean set(BlockPos pos, BlockState state, int flags) {
        if (pos.getY() < 0 || pos.getY() >= HEIGHT) return false;
        this.recordSet(pos, state, flags);
        ++this.writeCount;
        if (state.getBlock() instanceof EntityBlock block) {
            this.blockEntities.put(pos.asLong(), block.newBlockEntity(pos.immutable(), state));
        } else {
            this.blockEntities.remove(pos.asLong());
        }
        return this.chunk(pos.getX() >> 4, pos.getZ() >> 4).setBlockState(pos, state, flags) != null;
    }

    private Object blockEntity(Object[] args) {
        BlockEntity entity = this.blockEntities.get(((BlockPos) args[0]).asLong());
        if (args.length == 1) return entity;
        return entity != null && entity.getType() == args[1] ? Optional.of(entity) : Optional.empty();
    }

    private void event(String kind, Object[] args) {
        event(this.writes, kind, args);
        // Distance differences can schedule different leaf updates; retain them in the exact trace.
        if (kind.equals("tick") && args[1] instanceof LeavesBlock
                && ((Number) args[2]).intValue() == LEAF_DISTANCE_TICK_DELAY) return;
        event(this.treeWrites, kind, args);
    }

    private static void event(MessageDigest digest, String kind, Object[] args) {
        StringBuilder text = new StringBuilder(kind);
        for (Object arg : args) text.append('|').append(arg);
        digest.update(text.append('\n').toString().getBytes(StandardCharsets.UTF_8));
    }

    private void recordSet(BlockPos pos, BlockState state, int flags) {
        event(this.writes, "set", new Object[]{pos.getX(), pos.getY(), pos.getZ(), state, flags});
        BlockState appearance = leafAppearance(state);
        if (state.getBlock() instanceof LeavesBlock && appearance == leafAppearance(this.state(pos))) return;
        event(this.treeWrites, "set", new Object[]{pos.getX(), pos.getY(), pos.getZ(), appearance, flags});
        ++this.treeWriteCount;
    }

    private static BlockState leafAppearance(BlockState state) {
        return state.getBlock() instanceof LeavesBlock ? state.setValue(BlockStateProperties.DISTANCE, 1) : state;
    }

    private static BlockState treeState(BlockState state) {
        // Accept live-distance differences, but still detect a change in decay eligibility.
        if (state.getBlock() instanceof LeavesBlock
                && state.getValue(BlockStateProperties.DISTANCE) == LEAF_DECAY_DISTANCE) return state;
        return leafAppearance(state);
    }

    String signature() throws Exception {
        return this.signature(false);
    }

    String treeSignature() throws Exception {
        return this.signature(true);
    }

    private String signature(boolean acceptLiveLeafDistance) throws Exception {
        MessageDigest blocks = MessageDigest.getInstance("SHA-256");
        MessageDigest events = acceptLiveLeafDistance ? this.treeWrites : this.writes;
        for (var entry : this.chunks.entrySet()) {
            ProtoChunk chunk = entry.getValue();
            for (int y = 0; y < HEIGHT; ++y) {
                for (int index = 0; index < 256; ++index) {
                    BlockState actual = chunk.getSection(y >> 4).getBlockState(index & 15, y & 15, index >> 4);
                    if (actual == (y <= GROUND_Y ? this.initialState(y) : Blocks.AIR.defaultBlockState())) continue;
                    if (acceptLiveLeafDistance) actual = treeState(actual);
                    String value = entry.getKey() + ":" + y + ":" + index + ":" + Block.getId(actual) + '\n';
                    blocks.update(value.getBytes(StandardCharsets.UTF_8));
                }
            }
            for (var marks : chunk.getPostProcessing()) {
                if (marks != null && !marks.isEmpty()) {
                    events.update((entry.getKey() + ":" + marks + '\n').getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        int count = acceptLiveLeafDistance ? this.treeWriteCount : this.writeCount;
        return count + ":" + HexFormat.of().formatHex(blocks.digest()) + ":" + HexFormat.of().formatHex(events.digest());
    }
}
