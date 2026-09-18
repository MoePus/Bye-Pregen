package com.moepus.byepregen.palette.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.junit.jupiter.api.Test;

/**
 * Pins the duck-typed surface Sodium uses to read Arena sections.
 *
 * <p>Sodium adds {@code PalettedContainerROExtension} to every {@link
 * net.minecraft.world.level.chunk.PalettedContainer} through its own mixin and then dispatches to
 * {@code sodium$unpack} and {@code sodium$copy}. Nothing in this repository links against Sodium, so
 * only the erased shapes below can be checked here; they mirror
 * {@code net.caffeinemc.mods.sodium.client.world.PalettedContainerROExtension} of Sodium
 * 0.9.2+mc26.3, the first build for this version.
 */
final class SodiumPaletteContractTest {
    @Test
    void exposesTheExtensionSurfaceSodiumDispatchesTo() throws Exception {
        Class<?> container = ArenaBlockStatePalettedContainer.class;

        Method unpackAll = container.getMethod("sodium$unpack", Object[].class);
        assertEquals(void.class, unpackAll.getReturnType());
        assertTrue(unpackAll.isBridge() || unpackAll.getParameterCount() == 1);

        Method unpackRange = container.getMethod("sodium$unpack", Object[].class,
                int.class, int.class, int.class, int.class, int.class, int.class);
        assertEquals(void.class, unpackRange.getReturnType());

        Method copy = container.getMethod("sodium$copy");
        assertEquals(PalettedContainerRO.class, copy.getReturnType());
    }
}
