package com.moepus.byepregen.test;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

final class StressLightFuzzFixture implements LightFuzzFixture {
    private static final int[] XZ = {
            -47, -33, -32, -31, -17, -16, -15, -1, 0, 1, 14, 15, 16, 17, 30, 31, 32, 33, 47
    };
    private static final int[] Y_OFFSETS = {-1, 0, 1, 14, 15, 16};
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
    };
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int CLEAR_COLUMNS = 56;
    private static final int INITIAL_UPDATES = 520;
    private static final int MUTATION_UPDATES = 420;
    private static final long CLEAR_SEED_MASK = 0x6A09E667F3BCC909L;
    private static final long INITIAL_SEED_MASK = 0xBB67AE8584CAA73BL;
    private static final long MUTATION_SEED_MASK = 0x3C6EF372FE94F82AL;

    private final ServerLevel level;
    private final long seed;

    StressLightFuzzFixture(ServerLevel level, long seed) {
        this.level = level;
        this.seed = seed;
    }

    @Override
    public void clearVolume() {
        Random random = new Random(this.seed ^ CLEAR_SEED_MASK);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < CLEAR_COLUMNS; ++i) {
            this.clearColumn(this.randomXZ(random), this.randomXZ(random), air);
        }
    }

    private void clearColumn(int x, int z, BlockState state) {
        for (int y = this.level.getMinBuildHeight(); y < this.level.getMaxBuildHeight(); ++y) {
            this.put(x, y, z, state);
        }
    }

    @Override
    public void buildFixture() {
        Random random = new Random(this.seed ^ INITIAL_SEED_MASK);
        this.buildSkyColumns(random);
        this.applyUpdates(random, INITIAL_UPDATES, false);
    }

    private void buildSkyColumns(Random random) {
        int bottom = this.level.getMinBuildHeight();
        int top = this.level.getMaxBuildHeight() - 1;
        for (int i = 0; i < 40; ++i) {
            int x = this.randomXZ(random);
            int z = this.randomXZ(random);
            this.put(x, top, z, opaque(random));
            this.put(x, bottom, z, opaque(random));
            this.put(x, this.randomY(random), z, blockState(random));
        }
    }

    @Override
    public void applyUpdate(int round) {
        Random random = new Random(this.seed ^ MUTATION_SEED_MASK);
        this.applyUpdates(random, MUTATION_UPDATES, true);
    }

    private void applyUpdates(Random random, int updates, boolean mutation) {
        for (int i = 0; i < updates; ++i) {
            int x = this.randomXZ(random);
            int y = this.randomY(random);
            int z = this.randomXZ(random);
            this.put(x, y, z, mutation && i % 5 == 0 ? Blocks.AIR.defaultBlockState() : blockState(random));
            if (i % 11 == 0) {
                this.moveSource(random, new BlockPos(x, y, z));
            }
        }
    }

    private void moveSource(Random random, BlockPos from) {
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        int toX = from.getX() + direction.getStepX();
        int toY = from.getY() + direction.getStepY();
        int toZ = from.getZ() + direction.getStepZ();
        if (!LightFuzzBlocks.inBuildHeight(this.level, toY)
                || !LightFuzzBlocks.inFuzzBlockArea(toX, toZ)) {
            return;
        }
        this.put(from.getX(), from.getY(), from.getZ(), Blocks.AIR.defaultBlockState());
        this.put(toX, toY, toZ, source(random));
    }

    private int randomXZ(Random random) {
        return XZ[random.nextInt(XZ.length)] + random.nextInt(3) - 1;
    }

    private int randomY(Random random) {
        if (random.nextInt(8) == 0) {
            return random.nextBoolean() ? this.level.getMinBuildHeight() : this.level.getMaxBuildHeight() - 1;
        }
        int section = this.level.getMinSection() + random.nextInt(this.level.getSectionsCount());
        int y = (section << 4) + Y_OFFSETS[random.nextInt(Y_OFFSETS.length)];
        return Math.max(this.level.getMinBuildHeight(), Math.min(this.level.getMaxBuildHeight() - 1, y));
    }

    private static BlockState blockState(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> Blocks.AIR.defaultBlockState();
            case 1 -> opaque(random);
            case 2 -> Blocks.GLASS.defaultBlockState();
            case 3 -> Blocks.TINTED_GLASS.defaultBlockState();
            case 4 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
            case 5 -> Blocks.OAK_FENCE.defaultBlockState();
            case 6 -> slab(random);
            case 7 -> Blocks.OAK_STAIRS.defaultBlockState().setValue(
                    StairBlock.FACING,
                    HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)]);
            default -> source(random);
        };
    }

    private static BlockState opaque(Random random) {
        return random.nextBoolean()
                ? Blocks.STONE.defaultBlockState()
                : Blocks.DEEPSLATE.defaultBlockState();
    }

    private static BlockState slab(Random random) {
        SlabType type = random.nextBoolean() ? SlabType.TOP : SlabType.BOTTOM;
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState source(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Blocks.SEA_LANTERN.defaultBlockState();
            case 1 -> Blocks.GLOWSTONE.defaultBlockState();
            case 2 -> Blocks.SHROOMLIGHT.defaultBlockState();
            default -> Blocks.REDSTONE_LAMP.defaultBlockState()
                    .setValue(RedstoneLampBlock.LIT, Boolean.TRUE);
        };
    }

    private void put(int x, int y, int z, BlockState state) {
        LightFuzzBlocks.put(this.level, new BlockPos(x, y, z), state);
    }
}
