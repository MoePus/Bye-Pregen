package com.moepus.byepregen.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkArenaHeightmapMixin {
    @Shadow private volatile ChunkStatus status;

    @Unique private Heightmap byepregen$worldSurfaceWg;
    @Unique private Heightmap byepregen$oceanFloorWg;

    @Inject(
            method = "setBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;"
                            + "isOrAfter(Lnet/minecraft/world/level/chunk/status/ChunkStatus;)Z",
                    ordinal = 0
            ),
            cancellable = true,
            require = 1,
            allow = 1
    )
    private void byepregen$updateArenaNoiseHeightmaps(
            BlockPos pos,
            BlockState state,
            boolean isMoving,
            CallbackInfoReturnable<BlockState> callback,
            @Local LevelChunkSection section,
            @Local(ordinal = 1) BlockState oldState
    ) {
        ProtoChunk chunk = (ProtoChunk) (Object) this;
        if (this.status != ChunkStatus.NOISE
                || chunk.getBelowZeroRetrogen() != null
                || !(section.getStates() instanceof ArenaBlockStatePalettedContainer)) {
            return;
        }

        if (!this.byepregen$loadWorldgenHeightmaps(chunk)) {
            return;
        }

        int x = SectionPos.sectionRelative(pos.getX());
        int y = pos.getY();
        int z = SectionPos.sectionRelative(pos.getZ());

        int surfaceTop = this.byepregen$worldSurfaceWg.getHighestTaken(x, z);
        boolean surfaceOpaque = !state.isAir();
        if (surfaceOpaque ? y > surfaceTop : y == surfaceTop) {
            this.byepregen$worldSurfaceWg.update(x, y, z, state);
        }

        int oceanFloorTop = this.byepregen$oceanFloorWg.getHighestTaken(x, z);
        boolean oceanFloorOpaque = state.blocksMotion();
        if (oceanFloorOpaque ? y > oceanFloorTop : y == oceanFloorTop) {
            this.byepregen$oceanFloorWg.update(x, y, z, state);
        }

        callback.setReturnValue(oldState);
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
