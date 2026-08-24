package com.moepus.byepregen.chunksave.serialize;

import com.moepus.byepregen.palette.arena.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.palette.arena.codec.SectionWriter;
import com.moepus.byepregen.serialization.nbt.BiomeNbtCache;
import com.moepus.byepregen.serialization.nbt.NbtWriter;
import com.moepus.byepregen.yalight.access.YAChunkLightAccess;
import com.moepus.byepregen.yalight.storage.YAChunkLightData;
import com.moepus.byepregen.yalight.storage.YANibbleArray;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

final class ChunkSectionDataWriter {
    private static final byte[] SECTIONS = NbtWriter.asciiName("sections");
    private static final byte[] BLOCK_STATES = NbtWriter.asciiName("block_states");
    private static final byte[] BIOMES = NbtWriter.asciiName("biomes");
    private static final byte[] PALETTE = NbtWriter.asciiName("palette");
    private static final byte[] DATA = NbtWriter.asciiName("data");
    private static final byte[] BLOCK_LIGHT = NbtWriter.asciiName("BlockLight");
    private static final byte[] SKY_LIGHT = NbtWriter.asciiName("SkyLight");
    private static final byte[] Y = NbtWriter.asciiName("Y");

    private ChunkSectionDataWriter() {
    }

    static void write(
            NbtWriter writer,
            ServerLevel level,
            ChunkAccess chunk,
            ChunkPos pos,
            ChunkSectionSerializationContext context
    ) {
        LevelChunkSection[] sections = chunk.getSections();
        LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        YAChunkLightAccess yaLight = chunk instanceof YAChunkLightAccess access ? access : null;
        LayerLightEventListener blockListener = yaLight == null ? lightEngine.getLayerListener(LightLayer.BLOCK) : null;
        LayerLightEventListener skyListener = yaLight == null ? lightEngine.getLayerListener(LightLayer.SKY) : null;
        long listStart = writer.startList(SECTIONS, Tag.TAG_COMPOUND);
        int count = 0;
        for (int y = lightEngine.getMinLightSection(); y < lightEngine.getMaxLightSection(); ++y) {
            int index = chunk.getSectionIndexFromSectionY(y);
            LevelChunkSection section = index >= 0 && index < sections.length ? sections[index] : null;
            byte[] blockLight;
            byte[] skyLight;
            boolean blockFull;
            boolean skyFull;
            if (yaLight != null) {
                YANibbleArray block = yaLight(yaLight, LightLayer.BLOCK, y);
                YANibbleArray sky = yaLight(yaLight, LightLayer.SKY, y);
                int blockKind = saveKind(block);
                int skyKind = saveKind(sky);
                blockFull = blockKind == YANibbleArray.SAVE_FULL;
                skyFull = skyKind == YANibbleArray.SAVE_FULL;
                blockLight = blockKind == YANibbleArray.SAVE_DATA ? block.visibleDataForSave() : null;
                skyLight = skyKind == YANibbleArray.SAVE_DATA ? sky.visibleDataForSave() : null;
            } else {
                SectionPos sectionPos = SectionPos.of(pos, y);
                DataLayer block = blockListener.getDataLayerData(sectionPos);
                DataLayer sky = skyListener.getDataLayerData(sectionPos);
                blockFull = isFull(block);
                skyFull = isFull(sky);
                blockLight = lightBytes(block, blockFull);
                skyLight = lightBytes(sky, skyFull);
            }
            if (writeSection(
                    writer, section, biomeRegistry, context,
                    blockLight, skyLight, blockFull, skyFull, y)) {
                ++count;
            }
        }
        writer.finishList(listStart, count);
    }

    private static boolean writeSection(
            NbtWriter writer,
            LevelChunkSection section,
            Registry<Biome> biomeRegistry,
            ChunkSectionSerializationContext context,
            byte[] blockLight,
            byte[] skyLight,
            boolean blockFull,
            boolean skyFull,
            int sectionY
    ) {
        if (section == null && blockLight == null && skyLight == null && !blockFull && !skyFull) {
            return false;
        }
        writer.compoundEntryStart();
        if (section != null) {
            writeBlockStates(writer, section.getStates(), context);
            writeBiomes(writer, section.getBiomes(), biomeRegistry, context);
        }
        writeLight(writer, BLOCK_LIGHT, blockLight, blockFull);
        writeLight(writer, SKY_LIGHT, skyLight, skyFull);
        writer.putByte(Y, (byte) sectionY);
        writer.finishCompound();
        return true;
    }

    private static void writeLight(NbtWriter writer, byte[] name, byte[] data, boolean full) {
        if (full) {
            writer.putByteArrayFilled(name, YANibbleArray.SIZE, (byte)-1);
        } else if (data != null) {
            writer.putByteArray(name, data);
        }
    }

    private static void writeBlockStates(
            NbtWriter writer,
            PalettedContainer<BlockState> states,
            ChunkSectionSerializationContext context
    ) {
        if (states instanceof ArenaBlockStatePalettedContainer arena) {
            SectionWriter.write(writer, arena);
            return;
        }
        context.pack(states, 4);
        try {
            writer.startCompound(BLOCK_STATES);
            writer.startFixedList(PALETTE, context.paletteSize(), Tag.TAG_COMPOUND);
            for (int index = 0; index < context.paletteSize(); ++index) {
                writer.compoundEntryStart();
                SectionWriter.writeStateEntry(writer, context.paletteEntry(index));
                writer.finishCompound();
            }
            if (context.packedLength() != 0) {
                writer.putLongArray(DATA, context.packed(), context.packedLength());
            }
            writer.finishCompound();
        } finally {
            context.clear();
        }
    }

    private static void writeBiomes(
            NbtWriter writer,
            PalettedContainerRO<Holder<Biome>> biomes,
            Registry<Biome> biomeRegistry,
            ChunkSectionSerializationContext context
    ) {
        if (context.pack(biomes, 0)) {
            try {
                writer.startCompound(BIOMES);
                writer.startFixedList(PALETTE, context.paletteSize(), Tag.TAG_STRING);
                for (int index = 0; index < context.paletteSize(); ++index) {
                    writer.write(BiomeNbtCache.nameBytes(context.paletteEntry(index)));
                }
                if (context.packedLength() != 0) {
                    writer.putLongArray(DATA, context.packed(), context.packedLength());
                }
                writer.finishCompound();
            } finally {
                context.clear();
            }
            return;
        }
        PalettedContainerRO.PackedData<Holder<Biome>> data =
                biomes.pack(Strategy.createForBiomes(biomeRegistry.asHolderIdMap()));
        writer.startCompound(BIOMES);
        writer.startFixedList(PALETTE, data.paletteEntries().size(), Tag.TAG_STRING);
        for (Holder<Biome> biome : data.paletteEntries()) {
            writer.write(BiomeNbtCache.nameBytes(biome));
        }
        data.storage().ifPresent(stream -> writer.putLongArray(DATA, stream.toArray()));
        writer.finishCompound();
    }

    private static boolean isFull(DataLayer layer) {
        return layer != null && layer.isDefinitelyFilledWith(15);
    }

    private static byte[] lightBytes(DataLayer layer, boolean full) {
        return layer == null || full || layer.isEmpty() ? null : layer.getData();
    }

    private static int saveKind(YANibbleArray nibble) {
        return nibble == null ? YANibbleArray.SAVE_EMPTY : nibble.visibleSaveKind();
    }

    private static YANibbleArray yaLight(YAChunkLightAccess access, LightLayer layer, int sectionY) {
        YAChunkLightData data = access.byepregen$yaLightData(layer, false);
        return data == null ? null : data.getVisibleSection(sectionY);
    }

}
