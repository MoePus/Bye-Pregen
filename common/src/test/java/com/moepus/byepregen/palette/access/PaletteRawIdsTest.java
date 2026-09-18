package com.moepus.byepregen.palette.access;

import java.util.List;
import net.minecraft.core.IdMap;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.chunk.HashMapPalette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaletteRawIdsTest {
    @Test void uninstrumentedVanillaPaletteStillResolvesIds() {
        var registry = registry();
        var palette = new HashMapPalette<>(2, List.of("second", "first"));
        assertEquals(1, PaletteRawIds.get(palette, 0, registry));
        assertEquals(0, PaletteRawIds.get(palette, 1, registry));
    }

    @Test void customSubclassUsesItsValueMappingRatherThanInheritedRawAccess() {
        var registry = registry();
        var palette = new CustomPalette();
        assertEquals(1, PaletteRawIds.get(palette, 0, registry));
        assertEquals(0, PaletteRawIds.get(palette, 1, registry));
        assertEquals(2, palette.reads);
    }

    @Test void customPaletteErrorsAreNotHidden() {
        var palette = new CustomPalette();
        var exception = assertThrows(IllegalArgumentException.class,
                () -> PaletteRawIds.get(palette, 2, registry()));
        assertEquals("invalid id", exception.getMessage());
    }

    private static IdMapper<String> registry() {
        var registry = new IdMapper<String>();
        registry.add("first");
        registry.add("second");
        return registry;
    }

    private static final class CustomPalette extends HashMapPalette<String> implements PaletteRawIdAccess {
        private int reads;

        private CustomPalette() { super(2, List.of("first", "second")); }

        @Override public String valueFor(int id) {
            reads++;
            if (id == 0) return "second";
            if (id == 1) return "first";
            throw new IllegalArgumentException("invalid id");
        }

        @Override public int byepregen$rawIdForLocalId(int id, IdMap<?> map) {
            throw new AssertionError("Custom palette must use its public mapping");
        }
    }
}
