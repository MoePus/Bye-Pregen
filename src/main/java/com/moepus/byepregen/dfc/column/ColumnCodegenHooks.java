package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.RootNode;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.moepus.byepregen.dfc.ColumnCompiledEntry;
import java.util.Arrays;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ColumnCodegenHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("ByePregen DFC Column");

    private ColumnCodegenHooks() {
    }

    public static String[] addEntryInterface(String[] interfaces) {
        String columnEntry = Type.getInternalName(ColumnCompiledEntry.class);
        if (Arrays.asList(interfaces).contains(columnEntry)) {
            return interfaces;
        }
        String[] extended = Arrays.copyOf(interfaces, interfaces.length + 1);
        extended[interfaces.length] = columnEntry;
        return extended;
    }

    public static void registerColumnRoot(
            BytecodeGen.Context context,
            String suffix,
            AstNode node,
            int rootIndex,
            Map<Integer, String> columnMethods
    ) {
        if (!"final_final_density".equals(suffix)) {
            return;
        }
        ColumnSupport.Preparation preparation = ColumnSupport.prepare(new RootNode(node));
        if (preparation instanceof ColumnSupport.Rejected rejected) {
            LOGGER.warn("Disabling final density column codegen: {}", rejected.reason());
            return;
        }

        AstNode columnRoot = ((ColumnSupport.Supported) preparation).root();
        String methodName = "evalColumn_" + rootIndex + "_" + suffix;
        int memoizedCount = ColumnAstSpecializer.countMemoized(columnRoot);
        new ColumnBytecodeGen(context).generate(methodName, columnRoot, memoizedCount);
        columnMethods.put(rootIndex, methodName);
    }

    public static void finish(
            BytecodeGen.Context context,
            Map<Integer, String> columnMethods
    ) {
        generateHasColumn(context, columnMethods);
        generateEvalColumn(context, columnMethods);
    }

    private static void generateHasColumn(
            BytecodeGen.Context context,
            Map<Integer, String> columnMethods
    ) {
        String descriptor = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.INT_TYPE);
        InstructionAdapter m = newPublicMethod(context, "byepregen$hasColumn", descriptor);
        for (int rootIndex : columnMethods.keySet()) {
            Label next = new Label();
            m.load(1, Type.INT_TYPE);
            m.iconst(rootIndex);
            m.ificmpne(next);
            m.iconst(1);
            m.areturn(Type.BOOLEAN_TYPE);
            m.visitLabel(next);
        }
        m.iconst(0);
        m.areturn(Type.BOOLEAN_TYPE);
        m.visitMaxs(0, 0);
    }

    private static void generateEvalColumn(
            BytecodeGen.Context context,
            Map<Integer, String> columnMethods
    ) {
        String descriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE, Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));
        InstructionAdapter m = newPublicMethod(context, "byepregen$evalColumn", descriptor);
        for (Map.Entry<Integer, String> entry : columnMethods.entrySet()) {
            Label next = new Label();
            m.load(1, Type.INT_TYPE);
            m.iconst(entry.getKey());
            m.ificmpne(next);
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.load(2, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(context.className, entry.getValue(), ColumnEvaluationContext.METHOD_DESC, false);
            m.areturn(Type.VOID_TYPE);
            m.visitLabel(next);
        }
        emitMissingRoot(m);
        m.visitMaxs(0, 0);
    }

    private static void emitMissingRoot(InstructionAdapter m) {
        m.anew(Type.getType(IllegalArgumentException.class));
        m.dup();
        m.aconst("No generated DFC column method for this root");
        m.invokespecial(
                Type.getInternalName(IllegalArgumentException.class),
                "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)),
                false
        );
        m.athrow();
    }

    private static InstructionAdapter newPublicMethod(
            BytecodeGen.Context context,
            String name,
            String descriptor
    ) {
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
        return new InstructionAdapter(new AnalyzerAdapter(
                context.className,
                access,
                name,
                descriptor,
                context.classWriter.visitMethod(access, name, descriptor, null, null)
        ));
    }
}
