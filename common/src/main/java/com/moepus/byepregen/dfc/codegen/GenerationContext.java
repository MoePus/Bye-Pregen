/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.codegen;

import org.objectweb.asm.ClassWriter;

record GenerationContext(
        String owner,
        ClassWriter writer,
        BindingRegistry bindings
) {
}
