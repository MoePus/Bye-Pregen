package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.YALightEngineHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.BitSet;
import java.util.Iterator;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerYALightMixin {
    @Shadow
    private ClientLevel level;

    /**
     * @author MoePus
     * @reason Feed packet light bytes directly into YA light storage without constructing vanilla DataLayer objects.
     */
    @Overwrite
    private void readSectionList(
            int chunkX,
            int chunkZ,
            LevelLightEngine lightEngine,
            LightLayer layer,
            BitSet yMask,
            BitSet emptyYMask,
            Iterator<byte[]> updates
    ) {
        YALightEngineHolder holder = (YALightEngineHolder)lightEngine;
        for (int i = 0; i < lightEngine.getLightSectionCount(); ++i) {
            int sectionY = lightEngine.getMinLightSection() + i;
            boolean hasUpdate = yMask.get(i);
            boolean isEmpty = emptyYMask.get(i);
            if (!hasUpdate && !isEmpty) {
                continue;
            }

            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
            if (hasUpdate) {
                holder.byepregen$getYALightEngine().queueOwnedSectionBytes(layer, sectionPos, updates.next());
            } else {
                holder.byepregen$getYALightEngine().queueZeroSectionData(layer, sectionPos);
            }
            this.level.setSectionDirtyWithNeighbors(chunkX, sectionY, chunkZ);
        }
    }

    @Redirect(
            method = "handleForgetLevelChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;queueLightRemoval(Lnet/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket;)V"
            )
    )
    private void byepregen$clearYALightData(ClientPacketListener instance, ClientboundForgetLevelChunkPacket packet) {
        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();
        ((YALightEngineHolder)lightEngine).byepregen$getYALightEngine().clearChunk(packet.pos());
    }
}
