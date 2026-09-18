package com.moepus.byepregen.dfc.runtime;

import java.util.ArrayDeque;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Nested calls borrow distinct frames; released frames retain only reusable primitive scratch. */
final class PointContexts {
    private static final ThreadLocal<ArrayDeque<ColumnEvaluationContext>> FREE = ThreadLocal.withInitial(ArrayDeque::new);
    private PointContexts() { }
    static ColumnEvaluationContext acquire(SamplerContext context, int x, int y, int z) {
        ColumnEvaluationContext frame = FREE.get().pollFirst();
        if (frame == null) frame = new ColumnEvaluationContext(null);
        frame.preparePoint(context, x, y, z);
        return frame;
    }
    static void release(ColumnEvaluationContext frame) {
        frame.clearPoint();
        FREE.get().addFirst(frame);
    }
}
