package com.moepus.byepregen.mixin.server.fasttick;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.compat.C2MECompat;
import com.moepus.byepregen.mixin.accessor.server.tick.ChunkMapTickAccessor;
import com.moepus.byepregen.optimization.FastNaturalSpawner;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.function.Consumer;

@MixinGate(config = "enableFastTickChunks", conflictingMods = "servercore")
@Mixin(value = ServerChunkCache.class, remap = false, priority = 900)
public abstract class ServerChunkCacheTickChunksMixin {
    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    private long lastInhabitedUpdate;

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    private boolean spawnFriendlies;

    @Shadow
    private NaturalSpawner.SpawnState lastSpawnState;

    @Unique
    private LevelChunk[] byepregen$tickingChunks = new LevelChunk[0];

    @Unique
    private ChunkHolder[] byepregen$broadcastHolders = new ChunkHolder[0];

    @Unique
    private int[] byepregen$activeTickingChunkIndices = new int[0];

    @Unique
    private int byepregen$activeTickingChunkCount;

    @Unique
    private int byepregen$broadcastChunkCount;

    @Shadow
    private void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter) {
        throw new AssertionError();
    }

    @Shadow
    private void storeInCache(long p_8367_, ChunkAccess p_8368_, ChunkStatus p_8369_) {
        throw new AssertionError();
    }

    /**
     * @author MoePus
     * @reason Avoid per-tick ChunkAndHolder/List allocation and seed the current chunk in ServerChunkCache's small lookup cache.
     */
    @Overwrite
    private void tickChunks() {
        long gameTime = this.level.getGameTime();
        long inhabitedDelta = gameTime - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = gameTime;
        if (this.level.isDebug()) {
            return;
        }

        ProfilerFiller profiler = this.level.getProfiler();
        profiler.push("pollingChunks");
        profiler.push("filteringLoadedChunks");
        this.byepregen$collectTickingChunks();
        int activeTickingChunkCount = this.byepregen$activeTickingChunkCount;
        int broadcastChunkCount = this.byepregen$broadcastChunkCount;

        boolean runsNormally = this.level.tickRateManager().runsNormally();
        if (runsNormally) {
            profiler.popPush("naturalSpawnCount");
            int spawnableChunkCount = this.distanceManager.getNaturalSpawnChunkCount();
            Long2ByteMap c2meTickingChunks = C2MECompat.isC2MEInstalled()
                    ? C2MECompat.tickingChunksForNaturalSpawning(this.chunkMap)
                    : null;
            NaturalSpawner.SpawnState spawnState = FastNaturalSpawner.createState(
                    spawnableChunkCount,
                    this.level,
                    this.chunkMap,
                    this::getFullChunk,
                    c2meTickingChunks);
            this.lastSpawnState = spawnState;
            profiler.popPush("spawnAndTick");

            boolean doMobSpawning = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
            int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            boolean tickPersistentMobs = gameTime % 400L == 0L;
            if (activeTickingChunkCount > 0) {
                RandomSource random = this.level.getRandom();
                int domain = Mth.smallestEncompassingPowerOfTwo(activeTickingChunkCount);
                int lcgMask = domain - 1;
                int lcgMultiplier = (random.nextInt() & ~3) | 1;
                int lcgIncrement = random.nextInt() | 1;
                int state = random.nextInt() & lcgMask;

                int remaining = activeTickingChunkCount;
                while (remaining > 0) {
                    state = this.byepregen$nextPermutationState(state, lcgMultiplier, lcgIncrement, lcgMask);
                    int activeIndex = state & lcgMask;
                    if (activeIndex >= activeTickingChunkCount) {
                        continue;
                    }

                    LevelChunk levelChunk = this.byepregen$tickingChunks[this.byepregen$activeTickingChunkIndices[activeIndex]];
                    ChunkPos chunkPos = levelChunk.getPos();
                    long chunkPosLong = chunkPos.toLong();
                    levelChunk.incrementInhabitedTime(inhabitedDelta);
                    if (doMobSpawning
                            && (this.spawnEnemies || this.spawnFriendlies)
                            && this.level.getWorldBorder().isWithinBounds(chunkPos)) {
                        this.byepregen$seedChunkCache(levelChunk, ChunkStatus.BIOMES);
                        this.byepregen$seedChunkCache(levelChunk, ChunkStatus.FULL);
                        NaturalSpawner.spawnForChunk(
                                this.level,
                                levelChunk,
                                spawnState,
                                this.spawnFriendlies,
                                this.spawnEnemies,
                                tickPersistentMobs);
                    }

                    if (this.level.shouldTickBlocksAt(chunkPosLong)) {
                        this.byepregen$seedChunkCache(levelChunk, ChunkStatus.FULL);
                        if (C2MECompat.isC2MEInstalled()) {
                            C2MECompat.executeTasksMidTick(this.level);
                        }
                        this.level.tickChunk(levelChunk, randomTickSpeed);
                    }
                    remaining--;
                }
            }

            profiler.popPush("customSpawners");
            if (doMobSpawning) {
                this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
            }
        }

        profiler.popPush("broadcast");
        for (int index = 0; index < broadcastChunkCount; index++) {
            this.byepregen$broadcastHolders[index].broadcastChanges(this.byepregen$tickingChunks[index]);
        }
        profiler.pop();
        profiler.pop();
        this.byepregen$clearTickingChunkReferences(broadcastChunkCount);
    }

    @Unique
    private void byepregen$collectTickingChunks() {
        int visibleChunkCount = this.chunkMap.size();
        if (this.byepregen$tickingChunks.length < visibleChunkCount) {
            int newCapacity = Mth.smallestEncompassingPowerOfTwo(Math.max(1, visibleChunkCount));
            this.byepregen$tickingChunks = Arrays.copyOf(this.byepregen$tickingChunks, newCapacity);
            this.byepregen$broadcastHolders = Arrays.copyOf(this.byepregen$broadcastHolders, newCapacity);
            this.byepregen$activeTickingChunkIndices = Arrays.copyOf(this.byepregen$activeTickingChunkIndices, newCapacity);
        }

        int broadcastChunkCount = 0;
        int activeTickingChunkCount = 0;
        boolean c2meInstalled = C2MECompat.isC2MEInstalled();
        ObjectCollection<ChunkHolder> visibleChunks =
                ((ChunkMapTickAccessor) this.chunkMap).byepregen$getVisibleChunkMap().values();
        for (ChunkHolder chunkHolder : visibleChunks) {
            LevelChunk levelChunk = chunkHolder.getTickingChunk();
            LevelChunk broadcastChunk = levelChunk;
            if (broadcastChunk == null && c2meInstalled) {
                broadcastChunk = C2MECompat.chunkForBroadcast(chunkHolder);
            }
            if (broadcastChunk != null) {
                int index = broadcastChunkCount++;
                this.byepregen$tickingChunks[index] = broadcastChunk;
                this.byepregen$broadcastHolders[index] = chunkHolder;
                if (levelChunk != null && this.byepregen$shouldSpawnAndTick(levelChunk)) {
                    this.byepregen$activeTickingChunkIndices[activeTickingChunkCount++] = index;
                }
            }
        }

        this.byepregen$activeTickingChunkCount = activeTickingChunkCount;
        this.byepregen$broadcastChunkCount = broadcastChunkCount;
    }

    @Unique
    private void byepregen$clearTickingChunkReferences(int broadcastChunkCount) {
        if (broadcastChunkCount > 0) {
            Arrays.fill(this.byepregen$tickingChunks, 0, broadcastChunkCount, null);
            Arrays.fill(this.byepregen$broadcastHolders, 0, broadcastChunkCount, null);
        }
    }

    @Unique
    private int byepregen$nextPermutationState(int state, int multiplier, int increment, int mask) {
        return state * multiplier + increment & mask;
    }

    @Unique
    private boolean byepregen$shouldSpawnAndTick(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        return this.level.isNaturalSpawningAllowed(chunkPos)
                && ((ChunkMapTickAccessor) this.chunkMap).byepregen$anyPlayerCloseEnoughForSpawning(chunkPos)
                || this.distanceManager.shouldForceTicks(chunkPos.toLong());
    }

    @Unique
    private void byepregen$seedChunkCache(LevelChunk chunk, ChunkStatus status) {
        this.storeInCache(chunk.getPos().toLong(), chunk, status);
    }
}
