package com.moepus.byepregen.optimization;

import com.moepus.byepregen.mixin.accessor.ClassInstanceMultiMapAccessor;
import com.moepus.byepregen.mixin.accessor.EntitySectionAccessor;
import com.moepus.byepregen.mixin.accessor.EntitySectionStorageAccessor;
import com.moepus.byepregen.mixin.accessor.NaturalSpawnerAccessor;
import com.moepus.byepregen.mixin.accessor.NaturalSpawnerSpawnStateAccessor;
import com.moepus.byepregen.mixin.accessor.PersistentEntitySectionManagerAccessor;
import com.moepus.byepregen.mixin.accessor.ServerLevelEntityManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class FastNaturalSpawner {
    private FastNaturalSpawner() {
    }

    public static NaturalSpawner.SpawnState createState(
            int spawnableChunkCount,
            ServerLevel level,
            ChunkMap chunkMap,
            NaturalSpawner.ChunkGetter chunkGetter,
            Long2ByteMap c2meTickingChunks) {
        LocalMobCapCalculator localMobCapCalculator = new LocalMobCapCalculator(chunkMap);
        SpawnStateCollector collector = new SpawnStateCollector(chunkGetter, localMobCapCalculator);

        for (Long2ObjectMap.Entry<EntitySection<Entity>> entry : Long2ObjectMaps.fastIterable(byepregen$sections(level))) {
            EntitySection<Entity> section = entry.getValue();
            if (byepregen$shouldCollectSection(entry.getLongKey(), section, c2meTickingChunks)) {
                byepregen$collectSectionMobs(section, collector);
            }
        }

        return NaturalSpawnerSpawnStateAccessor.byepregen$newSpawnState(
                spawnableChunkCount,
                collector.mobCategoryCounts,
                collector.spawnPotential,
                localMobCapCalculator);
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectMap<EntitySection<Entity>> byepregen$sections(ServerLevel level) {
        PersistentEntitySectionManager<Entity> entityManager =
                ((ServerLevelEntityManagerAccessor) level).byepregen$getEntityManager();
        EntitySectionStorage<Entity> sectionStorage =
                ((PersistentEntitySectionManagerAccessor<Entity>) entityManager).byepregen$getSectionStorage();
        return ((EntitySectionStorageAccessor<Entity>) sectionStorage).byepregen$getSections();
    }

    private static void byepregen$collectSectionMobs(
            EntitySection<Entity> section,
            SpawnStateCollector collector) {
        for (Mob mob : byepregen$rawMobList(section)) {
            MobCategory category = byepregen$naturalSpawnCategory(mob);
            if (category != null) {
                collector.collect(mob, category);
            }
        }
    }

    private static boolean byepregen$shouldCollectSection(
            long sectionPos,
            EntitySection<Entity> section,
            Long2ByteMap c2meTickingChunks) {
        if (!section.getStatus().isAccessible() || section.isEmpty()) {
            return false;
        }
        return c2meTickingChunks == null
                || c2meTickingChunks.containsKey(ChunkPos.asLong(SectionPos.x(sectionPos), SectionPos.z(sectionPos)));
    }

    @SuppressWarnings("unchecked")
    private static List<Mob> byepregen$rawMobList(EntitySection<Entity> section) {
        ClassInstanceMultiMap<Entity> storage = ((EntitySectionAccessor<Entity>) section).byepregen$getStorage();
        Map<Class<?>, List<Entity>> byClass = ((ClassInstanceMultiMapAccessor<Entity>) storage).byepregen$getByClass();
        List<Entity> mobs = byClass.get(Mob.class);
        if (mobs == null) {
            storage.find(Mob.class);
            mobs = byClass.get(Mob.class);
        }
        return (List<Mob>) (List<?>) mobs;
    }

    private static MobCategory byepregen$naturalSpawnCategory(Mob mob) {
        if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) {
            return null;
        }

        MobCategory category = mob.getClassification(true);
        return category == MobCategory.MISC ? null : category;
    }

    private static final class SpawnStateCollector implements Consumer<LevelChunk> {
        private final NaturalSpawner.ChunkGetter chunkGetter;
        private final LocalMobCapCalculator localMobCapCalculator;
        private final PotentialCalculator spawnPotential = new PotentialCalculator();
        private final Object2IntOpenHashMap<MobCategory> mobCategoryCounts = new Object2IntOpenHashMap<>();

        private Mob mob;
        private MobCategory category;
        private BlockPos blockPos;

        private SpawnStateCollector(NaturalSpawner.ChunkGetter chunkGetter, LocalMobCapCalculator localMobCapCalculator) {
            this.chunkGetter = chunkGetter;
            this.localMobCapCalculator = localMobCapCalculator;
        }

        private void collect(Mob mob, MobCategory category) {
            this.mob = mob;
            this.category = category;
            this.blockPos = mob.blockPosition();
            this.chunkGetter.query(ChunkPos.asLong(this.blockPos), this);
        }

        @Override
        public void accept(LevelChunk chunk) {
            MobSpawnSettings.MobSpawnCost spawnCost = NaturalSpawnerAccessor.byepregen$getRoughBiome(this.blockPos, chunk)
                    .getMobSettings()
                    .getMobSpawnCost(this.mob.getType());
            if (spawnCost != null) {
                this.spawnPotential.addCharge(this.blockPos, spawnCost.charge());
            }

            this.localMobCapCalculator.addMob(chunk.getPos(), this.category);
            this.mobCategoryCounts.addTo(this.category, 1);
        }
    }
}
