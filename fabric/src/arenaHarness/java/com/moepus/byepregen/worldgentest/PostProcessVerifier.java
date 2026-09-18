package com.moepus.byepregen.worldgentest;

import com.moepus.byepregen.worldgen.postprocess.PostProcessGenerationOptimizer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class PostProcessVerifier {
    private static final BlockPos POS = new BlockPos(-16, 80, 15);
    private static final Block[] BLOCKS = {Blocks.AIR, Blocks.STONE, Blocks.OAK_LOG, Blocks.MAGMA_BLOCK,
            Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.PODZOL, Blocks.SNOW, Blocks.CARPET.white(),
            Blocks.COCOA, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.CACTUS,
            Blocks.OAK_LEAVES, Blocks.OAK_SLAB, Blocks.OAK_TRAPDOOR, Blocks.GRAVEL, Blocks.SAND,
            Blocks.SUSPICIOUS_SAND, Blocks.BEEHIVE, Blocks.OAK_FENCE, Blocks.COBBLESTONE_WALL,
            Blocks.RAIL, Blocks.DANDELION, Blocks.TALL_GRASS, Blocks.WATER, Blocks.LAVA};
    private static final Block[] NEIGHBORS = {Blocks.AIR, Blocks.STONE, Blocks.WATER, Blocks.LAVA,
            Blocks.SNOW, Blocks.JUNGLE_LOG, Blocks.DIRT, Blocks.SAND, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT};

    static int verify(ServerLevel server) {
        int count = 0;
        long nativeReads = 0, optimizedReads = 0;
        for (Block block : BLOCKS) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                for (int pattern = 0; pattern < 12; pattern++) {
                    var a = new Fixture(server, state, pattern);
                    var b = new Fixture(server, state, pattern);
                    BlockState expected = Block.updateFromNeighbourShapes(state, a.level, POS);
                    BlockState actual = PostProcessGenerationOptimizer.updateFromNeighbourShapes(state, b.level, POS);
                    if (expected != actual || !a.ticks.equals(b.ticks) || a.random.nextLong() != b.random.nextLong()) {
                        throw new AssertionError("Postprocess mismatch: " + state + " pattern=" + pattern
                                + " states=" + expected + '/' + actual + " ticks=" + a.ticks + '/' + b.ticks);
                    }
                    nativeReads += a.reads;
                    optimizedReads += b.reads;
                    count++;
                }
            }
        }
        if (optimizedReads >= nativeReads) throw new AssertionError("Postprocess did not reduce neighbor lookups");
        return count;
    }

    private static final class Fixture implements InvocationHandler {
        private final ServerLevel server;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final List<List<Object>> ticks = new ArrayList<>();
        private final RandomSource random = RandomSource.create(12345);
        private final LevelAccessor level;
        private int reads;

        Fixture(ServerLevel server, BlockState center, int pattern) {
            this.server = server;
            this.blocks.put(POS, center);
            for (Direction direction : Direction.values()) {
                int index = pattern < NEIGHBORS.length ? pattern : (direction.ordinal() + pattern) % NEIGHBORS.length;
                this.blocks.put(POS.relative(direction), NEIGHBORS[index].defaultBlockState());
            }
            this.level = (LevelAccessor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{LevelAccessor.class}, this);
        }

        public Object invoke(Object proxy, Method method, Object[] nullableArgs) throws Throwable {
            Object[] args = nullableArgs == null ? new Object[0] : nullableArgs;
            return switch (method.getName()) {
                case "getBlockState" -> this.state((BlockPos) args[0]);
                case "getFluidState" -> this.state((BlockPos) args[0]).getFluidState();
                case "getRandom" -> this.random;
                case "scheduleTick" -> { this.tick(args); yield null; }
                case "getMinY" -> this.server.getMinY();
                case "getHeight" -> this.server.getHeight();
                case "getSeaLevel" -> this.server.getSeaLevel();
                case "dimensionType" -> this.server.dimensionType();
                case "environmentAttributes" -> this.server.environmentAttributes();
                case "registryAccess" -> this.server.registryAccess();
                case "isClientSide" -> false;
                case "toString" -> "PostprocessFixture";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> this.defaultCall(proxy, method, args);
            };
        }

        private Object defaultCall(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            throw new UnsupportedOperationException("Postprocess fixture needs " + method);
        }

        private BlockState state(BlockPos pos) {
            this.reads++;
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        private void tick(Object[] args) {
            Object[] captured = args.clone();
            captured[0] = ((BlockPos) args[0]).immutable();
            this.ticks.add(Arrays.asList(captured));
        }
    }
}
