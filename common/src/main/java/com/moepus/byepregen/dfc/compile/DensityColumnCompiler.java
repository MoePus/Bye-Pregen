package com.moepus.byepregen.dfc.compile;

import com.moepus.byepregen.dfc.analysis.ColumnSpecializer;
import com.moepus.byepregen.dfc.ast.AstNode;
import com.moepus.byepregen.dfc.codegen.ColumnClassBuilder;
import com.moepus.byepregen.dfc.codegen.ColumnClassDefiner;
import com.moepus.byepregen.dfc.opt.ColumnOptimizer;
import com.moepus.byepregen.dfc.runtime.ColumnTemplate;
import java.nio.file.Path;

/** The 26.2 optimization/specialization/code-generation pipeline, bound to native samplers. */
public final class DensityColumnCompiler {
    public static final String DUMP_DIRECTORY_PROPERTY = "byepregen.dfc.dumpDir";
    private DensityColumnCompiler() { }

    public static ColumnTemplate compile(AstNode initial) {
        ColumnOptimizer.Result optimized = ColumnOptimizer.optimize(initial);
        ColumnSpecializer.Result specialized = ColumnSpecializer.specialize(optimized.root());
        ColumnClassBuilder.BuildResult generated = new ColumnClassBuilder(specialized.memoizedSlots()).build(specialized.root());
        ColumnTemplate template = new ColumnTemplate(ColumnClassDefiner.defineConstructor(generated.classBytes()),
                generated.bindings(), specialized.yIndependent());
        com.moepus.byepregen.dfc.runtime.DensityColumnMetrics.recordCompiled();
        dumpIfRequested(initial, specialized.root(), generated.classBytes());
        return template;
    }

    static void dumpIfRequested(AstNode before, AstNode after, byte[] bytes) {
        String path = System.getProperty(DUMP_DIRECTORY_PROPERTY);
        if (path == null || path.isBlank()) return;
        try { ColumnDumpWriter.write(Path.of(path), before, after, bytes); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot write requested DFC dump", failure); }
    }
}
