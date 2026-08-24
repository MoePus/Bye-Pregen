/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate.BindingKind;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class BindingRegistry {
    private final ClassWriter writer;
    private final Map<Object, FieldRef> fields = new IdentityHashMap<>();
    private final List<FieldRef> ordered = new ArrayList<>();

    BindingRegistry(ClassWriter writer) {
        this.writer = writer;
    }

    FieldRef field(Object value, Class<?> type, boolean resolveDensity) {
        return this.field(value, type, resolveDensity ? BindingKind.DENSITY : BindingKind.DIRECT, -1);
    }

    FieldRef interpolatedField(DensityFunction source, int slot) {
        return this.field(source, DensityFunction.class, BindingKind.INTERPOLATED, slot);
    }

    private FieldRef field(Object value, Class<?> type, BindingKind kind, int slot) {
        FieldRef existing = this.fields.get(value);
        if (existing != null) {
            if (existing.type() != type || existing.binding().kind() != kind
                    || existing.binding().interpolatorSlot() != slot) {
                throw new IllegalArgumentException("Incompatible generated field reuse");
            }
            return existing;
        }
        String name = "binding" + this.ordered.size();
        ColumnTemplate.Binding binding = new ColumnTemplate.Binding(value, kind, slot);
        FieldRef result = new FieldRef(name, type, binding);
        this.fields.put(value, result);
        this.ordered.add(result);
        this.writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name,
                Type.getDescriptor(type), null, null).visitEnd();
        return result;
    }

    List<FieldRef> fields() {
        return List.copyOf(this.ordered);
    }

    List<ColumnTemplate.Binding> bindings() {
        return this.ordered.stream().map(FieldRef::binding).toList();
    }

    record FieldRef(String name, Class<?> type, ColumnTemplate.Binding binding) {
    }
}
