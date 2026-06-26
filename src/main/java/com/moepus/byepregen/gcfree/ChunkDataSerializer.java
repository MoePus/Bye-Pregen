package com.moepus.byepregen.gcfree;

/* Adapted from C2ME's GC-free chunk serializer. MIT License, copyright (c) 2021-2024 ishland. */

import com.moepus.byepregen.PaletteContainer.ArenaPelette.ArenaBlockStatePalettedContainer;
import com.moepus.byepregen.PaletteContainer.ArenaPelette.Codecs.SectionWriter;
import com.moepus.byepregen.compat.C2MEAsyncSerializationCompat;
import com.moepus.byepregen.yalight.YAChunkLightAccess;
import com.moepus.byepregen.yalight.YAChunkLightData;
import com.moepus.byepregen.yalight.YANibbleArray;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager;
import org.slf4j.Logger;

public final class ChunkDataSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte[] DATA_VERSION = NbtWriter.asciiName("DataVersion");
    private static final byte[] X_POS = NbtWriter.asciiName("xPos");
    private static final byte[] Y_POS = NbtWriter.asciiName("yPos");
    private static final byte[] Z_POS = NbtWriter.asciiName("zPos");
    private static final byte[] LAST_UPDATE = NbtWriter.asciiName("LastUpdate");
    private static final byte[] INHABITED_TIME = NbtWriter.asciiName("InhabitedTime");
    private static final byte[] STATUS = NbtWriter.asciiName("Status");
    private static final byte[] SECTIONS = NbtWriter.asciiName("sections");
    private static final byte[] BLOCK_STATES = NbtWriter.asciiName("block_states");
    private static final byte[] BIOMES = NbtWriter.asciiName("biomes");
    private static final byte[] PALETTE = NbtWriter.asciiName("palette");
    private static final byte[] DATA = NbtWriter.asciiName("data");
    private static final byte[] BLOCK_LIGHT = NbtWriter.asciiName("BlockLight");
    private static final byte[] SKY_LIGHT = NbtWriter.asciiName("SkyLight");
    private static final byte[] Y = NbtWriter.asciiName("Y");
    private static final byte[] IS_LIGHT_ON = NbtWriter.asciiName("isLightOn");
    private static final byte[] BLOCK_ENTITIES = NbtWriter.asciiName("block_entities");
    private static final byte[] ENTITIES = NbtWriter.asciiName("entities");
    private static final byte[] CARVING_MASKS = NbtWriter.asciiName("CarvingMasks");
    private static final byte[] BLOCK_TICKS = NbtWriter.asciiName("block_ticks");
    private static final byte[] FLUID_TICKS = NbtWriter.asciiName("fluid_ticks");
    private static final byte[] POST_PROCESSING = NbtWriter.asciiName("PostProcessing");
    private static final byte[] HEIGHTMAPS = NbtWriter.asciiName("Heightmaps");
    private static final byte[] AUX_LIGHT = NbtWriter.asciiName(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY);
    private static final GenerationStep.Carving[] CARVINGS = GenerationStep.Carving.values();
    private static final byte[][] CARVING_MASK_NAMES = createCarvingMaskNames();
    private static final ConcurrentHashMap<ChunkStatus, byte[]> STATUS_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Heightmap.Types, byte[]> HEIGHTMAP_NAMES = new ConcurrentHashMap<>();

    private ChunkDataSerializer() {}

    public static void write(ServerLevel level, ChunkAccess chunk, NbtWriter writer) {
        ChunkPos pos = chunk.getPos();
        writer.putInt(DATA_VERSION, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        writer.putInt(X_POS, pos.x);
        writer.putInt(Y_POS, chunk.getMinSection());
        writer.putInt(Z_POS, pos.z);
        writer.putLong(LAST_UPDATE, level.getGameTime());
        writer.putLong(INHABITED_TIME, chunk.getInhabitedTime());
        writer.putString(STATUS, statusName(chunk.getPersistedStatus()));
        ChunkInlineDataWriter.write(writer, chunk);
        writeSections(writer, level, chunk, pos);
        if (chunk.isLightCorrect()) {
            writer.putBoolean(IS_LIGHT_ON, true);
        }
        writeBlockEntities(writer, level, chunk);
        writeChunkTypeExtras(writer, level, chunk, pos);
        writeTicks(writer, level, chunk);
        writePostProcessing(writer, chunk.getPostProcessing());
        writeHeightmaps(writer, chunk);
        writeAttachments(writer, level, chunk);
        ChunkStructureDataWriter.write(writer, level, pos, chunk);
    }

    private static void writeSections(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
        LevelChunkSection[] sections = chunk.getSections();
        LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        YAChunkLightAccess yaLight = chunk instanceof YAChunkLightAccess access ? access : null;
        long listStart = writer.startList(SECTIONS, Tag.TAG_COMPOUND);
        int count = 0;
        for (int y = lightEngine.getMinLightSection(); y < lightEngine.getMaxLightSection(); ++y) {
            int index = chunk.getSectionIndexFromSectionY(y);
            boolean hasSection = index >= 0 && index < sections.length;
            YANibbleArray blockYALight = yaLight(yaLight, LightLayer.BLOCK, y);
            YANibbleArray skyYALight = yaLight(yaLight, LightLayer.SKY, y);
            DataLayer blockLight = yaLight == null ? lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(pos, y)) : null;
            DataLayer skyLight = yaLight == null ? lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(pos, y)) : null;
            if (writeSection(writer, hasSection ? sections[index] : null, biomeRegistry, blockLight, skyLight, blockYALight, skyYALight, y)) {
                ++count;
            }
        }
        writer.finishList(listStart, count);
    }

    private static boolean writeSection(
            NbtWriter writer, LevelChunkSection section, Registry<Biome> biomeRegistry,
            DataLayer blockLight, DataLayer skyLight, YANibbleArray blockYALight, YANibbleArray skyYALight, int sectionY) {
        if (section == null && isEmpty(blockLight) && isEmpty(skyLight) && isEmpty(blockYALight) && isEmpty(skyYALight)) {
            return false;
        }

        writer.compoundEntryStart();
        if (section != null) {
            writeBlockStates(writer, section.getStates());
            writeBiomes(writer, section.getBiomes(), biomeRegistry);
        }
        if (!isEmpty(blockYALight)) {
            writeLight(writer, BLOCK_LIGHT, blockYALight);
        } else if (!isEmpty(blockLight)) {
            writer.putByteArray(BLOCK_LIGHT, blockLight.getData());
        }
        if (!isEmpty(skyYALight)) {
            writeLight(writer, SKY_LIGHT, skyYALight);
        } else if (!isEmpty(skyLight)) {
            writer.putByteArray(SKY_LIGHT, skyLight.getData());
        }
        writer.putByte(Y, (byte) sectionY);
        writer.finishCompound();
        return true;
    }

    private static void writeBlockStates(NbtWriter writer, PalettedContainer<BlockState> states) {
        if (states instanceof ArenaBlockStatePalettedContainer arena) {
            SectionWriter.write(writer, arena);
            return;
        }

        PalettedContainerRO.PackedData<BlockState> data = states.pack(
                Block.BLOCK_STATE_REGISTRY, PalettedContainer.Strategy.SECTION_STATES);
        writer.startCompound(BLOCK_STATES);
        writer.startFixedList(PALETTE, data.paletteEntries().size(), Tag.TAG_COMPOUND);
        for (BlockState state : data.paletteEntries()) {
            writer.compoundEntryStart();
            SectionWriter.writeStateEntry(writer, state);
            writer.finishCompound();
        }
        data.storage().ifPresent(stream -> writer.putLongArray(DATA, stream.toArray()));
        writer.finishCompound();
    }

    private static void writeBiomes(
            NbtWriter writer, PalettedContainerRO<Holder<Biome>> biomes, Registry<Biome> biomeRegistry) {
        PalettedContainerRO.PackedData<Holder<Biome>> data =
                biomes.pack(biomeRegistry.asHolderIdMap(), PalettedContainer.Strategy.SECTION_BIOMES);
        writer.startCompound(BIOMES);
        writer.startFixedList(PALETTE, data.paletteEntries().size(), Tag.TAG_STRING);
        for (Holder<Biome> biome : data.paletteEntries()) {
            writer.write(BiomeNbtCache.nameBytes(biome));
        }
        data.storage().ifPresent(stream -> writer.putLongArray(DATA, stream.toArray()));
        writer.finishCompound();
    }

    private static void writeBlockEntities(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        long listStart = writer.startList(BLOCK_ENTITIES, Tag.TAG_COMPOUND);
        int count = 0;
        for (BlockPos pos : blockEntityPositions(chunk)) {
            Tag tag = blockEntityNbtForSaving(level, chunk, pos);
            if (tag != null) {
                writer.putTagEntry(tag);
                ++count;
            }
        }
        writer.finishList(listStart, count);
    }

    private static Iterable<BlockPos> blockEntityPositions(ChunkAccess chunk) {
        if (GcFreeChunkSerializer.hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.blockEntityPositions(chunk);
        }
        return chunk.getBlockEntitiesPos();
    }

    private static Tag blockEntityNbtForSaving(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        if (GcFreeChunkSerializer.hasC2MEAsyncSerializationManager()) {
            return C2MEAsyncSerializationCompat.blockEntityNbtForSaving(level, chunk, pos);
        }
        return chunk.getBlockEntityNbtForSaving(pos, level.registryAccess());
    }

    private static void writeChunkTypeExtras(NbtWriter writer, ServerLevel level, ChunkAccess chunk, ChunkPos pos) {
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            writeProtoChunkExtras(writer, (ProtoChunk) chunk);
        } else if (chunk instanceof LevelChunk levelChunk) {
            Tag lightTag = levelChunk.getAuxLightManager(pos).serializeNBT(level.registryAccess());
            if (lightTag != null) {
                writer.putTag(AUX_LIGHT, lightTag);
            }
        }
    }

    private static void writeProtoChunkExtras(NbtWriter writer, ProtoChunk chunk) {
        writer.startFixedList(ENTITIES, chunk.getEntities().size(), Tag.TAG_COMPOUND);
        for (Tag entity : chunk.getEntities()) {
            writer.putTagEntry(entity);
        }
        writer.startCompound(CARVING_MASKS);
        for (GenerationStep.Carving carving : CARVINGS) {
            CarvingMask mask = chunk.getCarvingMask(carving);
            if (mask != null) {
                writer.putLongArray(CARVING_MASK_NAMES[carving.ordinal()], mask.toArray());
            }
        }
        writer.finishCompound();
    }

    private static void writeTicks(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        long time = level.getLevelData().getGameTime();
        ChunkAccess.TicksToSave ticks = chunk.getTicksForSerialization();
        writer.putTag(BLOCK_TICKS, ticks.blocks().save(time, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        writer.putTag(FLUID_TICKS, ticks.fluids().save(time, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
    }

    private static void writePostProcessing(NbtWriter writer, ShortList[] lists) {
        writer.startFixedList(POST_PROCESSING, lists.length, Tag.TAG_LIST);
        for (ShortList list : lists) {
            int size = list == null ? 0 : list.size();
            writer.startFixedListEntry(size, size == 0 ? Tag.TAG_END : Tag.TAG_SHORT);
            for (int i = 0; i < size; ++i) {
                writer.writeShort(list.getShort(i));
            }
        }
    }

    private static void writeHeightmaps(NbtWriter writer, ChunkAccess chunk) {
        writer.startCompound(HEIGHTMAPS);
        for (java.util.Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getPersistedStatus().getChunkSaveHeightmaps().contains(entry.getKey())) {
                writer.putLongArray(heightmapName(entry.getKey()), entry.getValue().getRawData());
            }
        }
        writer.finishCompound();
    }

    private static void writeAttachments(NbtWriter writer, ServerLevel level, ChunkAccess chunk) {
        try {
            Tag tag = chunk.writeAttachmentsToNBT(level.registryAccess());
            if (tag != null) {
                writer.putTag(NbtWriter.asciiName(AttachmentHolder.ATTACHMENTS_NBT_KEY), tag);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to write chunk attachments", exception);
        }
    }

    private static boolean isEmpty(DataLayer layer) { return layer == null || layer.isEmpty(); }

    private static boolean isEmpty(YANibbleArray nibble) {
        return nibble == null || nibble.visibleSaveKind() == YANibbleArray.SAVE_EMPTY;
    }

    private static YANibbleArray yaLight(YAChunkLightAccess access, LightLayer layer, int sectionY) {
        if (access == null) {
            return null;
        }
        YAChunkLightData data = access.byepregen$yaLightData(layer, false);
        return data == null ? null : data.getVisibleSection(sectionY);
    }

    private static void writeLight(NbtWriter writer, byte[] name, YANibbleArray nibble) {
        int kind = nibble.visibleSaveKind();
        if (kind == YANibbleArray.SAVE_FULL) {
            writer.putByteArrayFilled(name, YANibbleArray.SIZE, (byte)-1);
        } else if (kind == YANibbleArray.SAVE_DATA) {
            writer.putByteArray(name, nibble.visibleDataForSave());
        }
    }

    private static byte[] statusName(ChunkStatus status) {
        return STATUS_NAMES.computeIfAbsent(
                status, key -> NbtWriter.stringBytes(BuiltInRegistries.CHUNK_STATUS.getKey(key).toString()));
    }

    private static byte[] heightmapName(Heightmap.Types type) {
        return HEIGHTMAP_NAMES.computeIfAbsent(type, key -> NbtWriter.asciiName(key.getSerializationKey()));
    }

    private static byte[][] createCarvingMaskNames() {
        byte[][] names = new byte[CARVINGS.length][];
        for (int i = 0; i < CARVINGS.length; ++i) {
            names[i] = NbtWriter.asciiName(CARVINGS[i].toString());
        }
        return names;
    }
}
