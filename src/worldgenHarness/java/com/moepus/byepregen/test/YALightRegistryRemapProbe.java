package com.moepus.byepregen.test;

import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.IdMappingEvent;

/** Runs before levels/workers exist; restores the real registry even when an assertion fails. */
final class YALightRegistryRemapProbe {
    private YALightRegistryRemapProbe() {
    }

    static void verify() {
        List<BlockState> original = new ArrayList<>();
        Block.BLOCK_STATE_REGISTRY.forEach(original::add);
        int airId = Block.getId(Blocks.AIR.defaultBlockState());
        int stoneId = Block.getId(Blocks.STONE.defaultBlockState());
        requireClass(airId, YABlockStateLightClass.CLEAR);
        requireClass(stoneId, YABlockStateLightClass.FULL);
        try {
            replace(List.of());
            requireClass(0, YABlockStateLightClass.SLOW);
            replace(List.of(Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()));
            requireClass(0, YABlockStateLightClass.FULL);
            requireClass(1, YABlockStateLightClass.CLEAR);
            requireClass(2, YABlockStateLightClass.SLOW); // Last packed word has unused entries.
            List<BlockState> remapped = new ArrayList<>(original);
            Collections.swap(remapped, airId, stoneId);
            replace(remapped);
            requireClass(airId, YABlockStateLightClass.FULL);
            requireClass(stoneId, YABlockStateLightClass.CLEAR);
        } finally {
            replace(original);
        }
        requireClass(airId, YABlockStateLightClass.CLEAR);
        requireClass(stoneId, YABlockStateLightClass.FULL);
        requireClass(-1, YABlockStateLightClass.SLOW);
        requireClass(original.size(), YABlockStateLightClass.SLOW);
        System.out.println("YA same-JVM registry remap probe passed: empty, growth, reorder, restore");
    }

    private static void replace(List<BlockState> states) {
        try {
            var clear = Class.forName("net.minecraftforge.registries.GameData$ClearableObjectIntIdentityMap")
                    .getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(Block.BLOCK_STATE_REGISTRY);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot clear Forge block-state IDs for remap probe", exception);
        }
        states.forEach(Block.BLOCK_STATE_REGISTRY::add);
        // Snapshot restores can fire an empty remapping map; do not filter on that map.
        MinecraftForge.EVENT_BUS.post(new IdMappingEvent(Map.of(), false));
    }

    private static void requireClass(int rawId, int expected) {
        int actual = YABlockStateLightClass.fromRawId(rawId);
        if (actual != expected) {
            throw new IllegalStateException("Light class for raw ID " + rawId
                    + ": expected " + expected + ", got " + actual);
        }
    }
}
