package com.moepus.byepregen.startup;

import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import org.slf4j.Logger;

/** Exercises real registry snapshot callbacks before any world or palette-backed chunks exist. */
@EventBusSubscriber(modid = "byepregen_startup_harness")
public final class YALightRegistryRemapProbe {
    private static final String ENABLED_PROPERTY = "byepregen.testYALightRegistryRemap";
    private static final Logger LOGGER = LogUtils.getLogger();

    private YALightRegistryRemapProbe() {
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        if (Boolean.getBoolean(ENABLED_PROPERTY)) {
            event.enqueueWork(YALightRegistryRemapProbe::verify);
        }
    }

    private static void verify() {
        requireClass(Blocks.AIR.defaultBlockState(), YABlockStateLightClass.CLEAR);
        requireClass(Blocks.STONE.defaultBlockState(), YABlockStateLightClass.FULL);
        requireClass(Blocks.LANTERN.defaultBlockState(), YABlockStateLightClass.SLOW);
        Map<BlockState, Integer> expected = captureClasses();
        RegistrySnapshot original = new RegistrySnapshot(BuiltInRegistries.BLOCK, true);
        int originalLanternId = Block.getId(Blocks.LANTERN.defaultBlockState());
        try {
            apply(swappedSnapshot(original));
            require(Block.getId(Blocks.LANTERN.defaultBlockState()) != originalLanternId,
                    "Snapshot did not remap the lantern raw ID");
            verifyClasses(expected);
        } finally {
            apply(original);
        }
        // Full restores rebuild derived state IDs too; classifications must follow the states.
        verifyClasses(expected);
        LOGGER.info("YA_REGISTRY_REMAP_PASS states={} sync/restore", expected.size());
    }

    private static Map<BlockState, Integer> captureClasses() {
        Map<BlockState, Integer> expected = new IdentityHashMap<>();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            expected.put(state, YABlockStateLightClass.fromRawId(Block.getId(state)));
        }
        return expected;
    }

    private static RegistrySnapshot swappedSnapshot(RegistrySnapshot original) {
        var ids = new Int2ObjectRBTreeMap<>(original.getIds());
        int lanternId = BuiltInRegistries.BLOCK.getId(Blocks.LANTERN);
        int carpetId = BuiltInRegistries.BLOCK.getId(Blocks.WHITE_CARPET);
        var lanternName = ids.put(lanternId, ids.get(carpetId));
        ids.put(carpetId, lanternName);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeMap(ids, FriendlyByteBuf::writeVarInt, FriendlyByteBuf::writeResourceLocation);
            buffer.writeMap(original.getAliases(),
                    FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeResourceLocation);
            return RegistrySnapshot.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void apply(RegistrySnapshot snapshot) {
        require(RegistryManager.applySnapshot(Map.of(Registries.BLOCK.location(), snapshot), false).isEmpty(),
                "Snapshot lost registered blocks");
    }

    private static void verifyClasses(Map<BlockState, Integer> expected) {
        expected.forEach(YALightRegistryRemapProbe::requireClass);
        require(YABlockStateLightClass.fromRawId(-1) == YABlockStateLightClass.SLOW, "Negative raw ID");
        require(YABlockStateLightClass.fromRawId(Block.BLOCK_STATE_REGISTRY.size())
                == YABlockStateLightClass.SLOW, "Unclassified raw ID");
    }

    private static void requireClass(BlockState state, int expected) {
        int actual = YABlockStateLightClass.fromRawId(Block.getId(state));
        require(actual == expected, state + " expected class=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("YA registry remap probe: " + message);
        }
    }
}
