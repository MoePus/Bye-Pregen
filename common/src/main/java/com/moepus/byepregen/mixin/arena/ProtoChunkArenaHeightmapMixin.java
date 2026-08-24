package com.moepus.byepregen.mixin.arena;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.worldgen.arena.ArenaHeightmapUpdate;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@MixinGate(feature = MixinFeature.ARENA)
@Mixin(ProtoChunk.class)
public abstract class ProtoChunkArenaHeightmapMixin {
    @Unique private static final EnumSet<Heightmap.Types> byepregen$NO_HEIGHTMAPS =
            EnumSet.noneOf(Heightmap.Types.class);

    @Shadow private volatile ChunkStatus status;

    @Unique private Heightmap byepregen$worldSurfaceWg;
    @Unique private Heightmap byepregen$oceanFloorWg;

    @ModifyExpressionValue(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;"
                            + "heightmapsAfter()Ljava/util/EnumSet;",
                    ordinal = 0
            ),
            require = 1,
            allow = 1
    )
    private EnumSet<Heightmap.Types> byepregen$updateArenaNoiseHeightmaps(
            EnumSet<Heightmap.Types> original,
            BlockPos pos,
            BlockState state,
            int flags
    ) {
        ProtoChunk chunk = (ProtoChunk) (Object) this;
        int y = pos.getY();
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        if (this.status != ChunkStatus.NOISE
                || chunk.getBelowZeroRetrogen() != null
                || !(section.getStates() instanceof ArenaBlockStatePalettedContainer)) {
            return original;
        }

        if (!this.byepregen$loadWorldgenHeightmaps(chunk)) {
            return original;
        }

        int x = SectionPos.sectionRelative(pos.getX());
        int z = SectionPos.sectionRelative(pos.getZ());

        int surfaceTop = this.byepregen$worldSurfaceWg.getHighestTaken(x, z);
        if (ArenaHeightmapUpdate.isNeeded(
                Heightmap.Types.WORLD_SURFACE_WG, state, Integer.compare(y, surfaceTop)
        )) {
            this.byepregen$worldSurfaceWg.update(x, y, z, state);
        }

        int oceanFloorTop = this.byepregen$oceanFloorWg.getHighestTaken(x, z);
        if (ArenaHeightmapUpdate.isNeeded(
                Heightmap.Types.OCEAN_FLOOR_WG, state, Integer.compare(y, oceanFloorTop)
        )) {
            this.byepregen$oceanFloorWg.update(x, y, z, state);
        }

        return byepregen$NO_HEIGHTMAPS;
    }

    @Unique
    private boolean byepregen$loadWorldgenHeightmaps(ProtoChunk chunk) {
        if (this.byepregen$worldSurfaceWg != null) {
            return true;
        }

        EnumSet<Heightmap.Types> types = ChunkStatus.NOISE.heightmapsAfter();
        if (types.size() != 2
                || !types.contains(Heightmap.Types.WORLD_SURFACE_WG)
                || !types.contains(Heightmap.Types.OCEAN_FLOOR_WG)
                || !chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG)
                || !chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG)) {
            return false;
        }

        this.byepregen$worldSurfaceWg = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        this.byepregen$oceanFloorWg = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        return true;
    }
}
