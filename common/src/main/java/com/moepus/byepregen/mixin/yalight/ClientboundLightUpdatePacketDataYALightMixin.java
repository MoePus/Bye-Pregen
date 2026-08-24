package com.moepus.byepregen.mixin.yalight;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.access.YALightEngineHolder;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

@MixinGate(feature = MixinFeature.YA_LIGHT)
@Mixin(ClientboundLightUpdatePacketData.class)
public abstract class ClientboundLightUpdatePacketDataYALightMixin {
    /**
     * @author MoePus
     * @reason Write packet light bytes directly from YA light storage without materializing vanilla DataLayer objects.
     */
    @Overwrite
    private void prepareSectionData(
            ChunkPos chunkPos,
            LevelLightEngine lightEngine,
            LightLayer layer,
            int sectionIndex,
            BitSet yMask,
            BitSet emptyYMask,
            List<byte[]> updates
    ) {
        SectionPos sectionPos = SectionPos.of(chunkPos, lightEngine.getMinLightSection() + sectionIndex);
        YANibbleArray nibble = ((YALightEngineHolder)lightEngine)
                .byepregen$getYALightEngine()
                .getVisibleSection(layer, sectionPos);
        if (nibble == null || !nibble.hasVisibleLayer()) {
            return;
        }

        int kind = nibble.visibleSaveKind();
        if (kind == YANibbleArray.SAVE_EMPTY) {
            emptyYMask.set(sectionIndex);
            return;
        }

        yMask.set(sectionIndex);
        updates.add(kind == YANibbleArray.SAVE_FULL ? YANibbleArray.FULL_LIGHT_DATA : nibble.visibleDataForSave().clone());
    }
}
