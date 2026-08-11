/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.binary.*;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.AbstractUnaryNode;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import java.util.Arrays;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class ColumnBytecodeGen {

    private static final String NODE_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class),
            Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final BytecodeGen.Context owner;
    private final ColumnPointBytecodeGen points;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();
    private final Map<IFastCacheLike, Integer> interpolationSources = new IdentityHashMap<>();

    ColumnBytecodeGen(BytecodeGen.Context owner) {
        this.owner = owner;
        this.points = new ColumnPointBytecodeGen(owner, this::interpolationSourceIndex);
    }

    void generate(String entryName, AstNode root, int memoizedCount) {
        String rootMethod = this.method(root);
        this.generateEntry(entryName, rootMethod, memoizedCount);
    }

    private String method(AstNode node) {
        String existing = this.methods.get(node);
        if (existing != null) return existing;
        String name = this.owner.nextMethodName("Column_" + node.getClass().getSimpleName());
        this.methods.put(node, name);
        this.generateMethod(node, name);
        return name;
    }

    private void generateMethod(AstNode node, String name) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(this.owner.className,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, NODE_DESC,
                this.owner.classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, NODE_DESC, null, null)));
        ColumnPointBytecodeGen.LocalAllocator locals = new ColumnPointBytecodeGen.LocalAllocator(7);
        this.emit(node, m, locals);
        m.areturn(Type.VOID_TYPE);
        m.visitMaxs(0, 0);
    }

    private void generateEntry(String name, String rootMethod, int memoizedCount) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(this.owner.className,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, ColumnEvaluationContext.METHOD_DESC,
                this.owner.classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name,
                        ColumnEvaluationContext.METHOD_DESC, null, null)));
        loadContext(m, "output", Type.getType(double[].class)); m.store(2, InstructionAdapter.OBJECT_TYPE);
        loadContext(m, "x", Type.INT_TYPE); m.store(3, Type.INT_TYPE);
        loadContext(m, "z", Type.INT_TYPE); m.store(4, Type.INT_TYPE);
        loadContext(m, "minY", Type.INT_TYPE); m.store(5, Type.INT_TYPE);
        loadContext(m, "cellHeight", Type.INT_TYPE); m.store(6, Type.INT_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE); m.store(7, InstructionAdapter.OBJECT_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.iconst(memoizedCount);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "prepareMemoizedCount",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE), false);
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.iconst(this.interpolationSources.size());
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "prepareInterpolationCount",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE), false);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(2, InstructionAdapter.OBJECT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, Type.INT_TYPE);
        m.load(5, Type.INT_TYPE);
        m.load(6, Type.INT_TYPE);
        m.load(7, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, rootMethod, NODE_DESC, false);
        m.areturn(Type.VOID_TYPE);
        m.visitMaxs(0, 0);
    }

    private void emit(AstNode node, InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals) {
        Class<?> type = node.getClass();
        if (type == RootNode.class) {
            RootNode root = (RootNode) node;
            this.callColumn(m, root.next, 1);
        } else if (type == ConstantNode.class) {
            ConstantNode constant = (ConstantNode) node;
            this.fillConstant(m, constant.getValue());
        } else if (type == ColumnMemoized2DNode.class) {
            this.fillMemoized(m, (ColumnMemoized2DNode) node);
        } else if (type == ColumnCacheNode.class) {
            ColumnCacheNode cache = (ColumnCacheNode) node;
            this.emitCache(cache, m, locals);
        } else if (type == CoordinateNode.class) {
            CoordinateNode coordinate = (CoordinateNode) node;
            this.emitCoordinate(coordinate, m, locals);
        } else if (ColumnSupport.isSupportedUnary(node)) {
            AbstractUnaryNode unary = (AbstractUnaryNode) node;
            this.callColumn(m, unary.operand, 1);
            this.emitUnary(unary, m, locals);
        } else if (ColumnSupport.isSupportedBinary(node)) {
            AbstractBinaryNode binary = (AbstractBinaryNode) node;
            this.emitBinary(binary, m, locals);
        } else if (type == RangeChoiceNode.class) {
            RangeChoiceNode range = (RangeChoiceNode) node;
            this.emitRange(range, m, locals);
        } else if (type == YClampedGradientNode.class) {
            YClampedGradientNode gradient = (YClampedGradientNode) node;
            this.emitGradient(gradient, m, locals);
        } else if (ColumnSupport.isSupportedDelegate(node) || type == SplineAstNode.class
                || type == GenericShiftedNoiseNode.class || type == DFTWeirdScaledSamplerNode.class) {
            this.emitPointLoop(node, m, locals);
        } else {
            throw new UnsupportedOperationException("Unsupported Column node: " + node.getClass().getName());
        }
    }

    private void emitBinary(AbstractBinaryNode node, InstructionAdapter m,
                            ColumnPointBytecodeGen.LocalAllocator locals) {
        this.callColumn(m, node.left, 1);
        if (node instanceof MulNode || node instanceof MinShortNode || node instanceof MaxShortNode) {
            this.emitShortCircuitBinary(node, m, locals);
            return;
        }
        int temp = this.borrowDoubleArray(m, locals);
        this.callColumn(m, node.right, temp);
        this.loop(m, locals, index -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(index, Type.INT_TYPE);
            loadArray(m, 1, index);
            loadArray(m, temp, index);
            if (node instanceof AddNode) m.add(Type.DOUBLE_TYPE);
            else if (node instanceof DivNode) m.div(Type.DOUBLE_TYPE);
            else if (node instanceof MinNode) ColumnPointBytecodeGen.invokeMath(m, "min");
            else if (node instanceof MaxNode) ColumnPointBytecodeGen.invokeMath(m, "max");
            else throw new UnsupportedOperationException("Unsupported Column binary: " + node.getClass().getName());
            m.astore(Type.DOUBLE_TYPE);
        });
        this.recycleDoubleArray(m, temp);
    }

    private void emitShortCircuitBinary(AbstractBinaryNode node, InstructionAdapter m,
                                        ColumnPointBytecodeGen.LocalAllocator locals) {
        int value = locals.allocate(Type.DOUBLE_TYPE);
        this.loop(m, locals, index -> {
            loadArray(m, 1, index);
            m.store(value, Type.DOUBLE_TYPE);
            Label skip = new Label();
            if (node instanceof MulNode) {
                m.load(value, Type.DOUBLE_TYPE); m.dconst(0.0); m.cmpl(Type.DOUBLE_TYPE); m.ifeq(skip);
            } else if (node instanceof MinShortNode minShort) {
                m.load(value, Type.DOUBLE_TYPE); m.dconst(minShort.rightMin); m.cmpg(Type.DOUBLE_TYPE); m.iflt(skip);
            } else if (node instanceof MaxShortNode maxShort) {
                m.load(value, Type.DOUBLE_TYPE); m.dconst(maxShort.rightMax); m.cmpl(Type.DOUBLE_TYPE); m.ifgt(skip);
            }
            m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
            m.load(value, Type.DOUBLE_TYPE);
            this.callPointAtIndex(m, node.right, index);
            if (node instanceof MulNode) m.mul(Type.DOUBLE_TYPE);
            else if (node instanceof MinShortNode) ColumnPointBytecodeGen.invokeMath(m, "min");
            else ColumnPointBytecodeGen.invokeMath(m, "max");
            m.astore(Type.DOUBLE_TYPE);
            m.visitLabel(skip);
        });
    }

    private void emitRange(RangeChoiceNode node, InstructionAdapter m,
                           ColumnPointBytecodeGen.LocalAllocator locals) {
        this.callColumn(m, node.input, 1);
        int value = locals.allocate(Type.DOUBLE_TYPE);
        this.loop(m, locals, index -> {
            loadArray(m, 1, index); m.store(value, Type.DOUBLE_TYPE);
            Label out = new Label(); Label end = new Label();
            m.load(value, Type.DOUBLE_TYPE); m.dconst(node.minInclusive); m.cmpl(Type.DOUBLE_TYPE); m.iflt(out);
            m.load(value, Type.DOUBLE_TYPE); m.dconst(node.maxExclusive); m.cmpg(Type.DOUBLE_TYPE); m.ifge(out);
            this.storeSelected(m, node.whenInRange, node.input, value, index); m.goTo(end);
            m.visitLabel(out); this.storeSelected(m, node.whenOutOfRange, node.input, value, index);
            m.visitLabel(end);
        });
    }

    private void storeSelected(InstructionAdapter m, AstNode selected, AstNode input, int value, int index) {
        m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
        if (selected == input) m.load(value, Type.DOUBLE_TYPE);
        else this.callPointAtIndex(m, selected, index);
        m.astore(Type.DOUBLE_TYPE);
    }

    private void emitUnary(AbstractUnaryNode node, InstructionAdapter m,
                           ColumnPointBytecodeGen.LocalAllocator locals) {
        this.loop(m, locals, index -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
            loadArray(m, 1, index);
            ColumnPointBytecodeGen.emitUnary(node, m, locals);
            m.astore(Type.DOUBLE_TYPE);
        });
    }

    private void emitCoordinate(CoordinateNode node, InstructionAdapter m,
                                ColumnPointBytecodeGen.LocalAllocator locals) {
        if (node.axis != CoordinateNode.Axis.Y) {
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.load(node.axis == CoordinateNode.Axis.X ? 2 : 3, Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.invokestatic(Type.getInternalName(Arrays.class), "fill",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class), Type.DOUBLE_TYPE), false);
            return;
        }
        this.loop(m, locals, index -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
            this.loadYAtIndex(m, index); m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE); m.astore(Type.DOUBLE_TYPE);
        });
    }

    private void emitCache(ColumnCacheNode node, InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals) {
        String field = this.owner.newField(IFastCacheLike.class, node.cacheLike());
        if (node.mode() == ColumnCacheNode.Mode.INTERPOLATED) {
            m.load(6, InstructionAdapter.OBJECT_TYPE);
            m.iconst(this.interpolationSourceIndex(node.cacheLike()));
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(this.owner.className, field, Type.getDescriptor(IFastCacheLike.class));
            m.load(1, InstructionAdapter.OBJECT_TYPE);
            m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "copyInterpolatedColumn",
                    Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE, Type.getType(IFastCacheLike.class),
                            Type.getType(double[].class)), false);
            return;
        }
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(this.owner.className, field, Type.getDescriptor(IFastCacheLike.class));
        int cache = locals.allocate(Type.getType(IFastCacheLike.class));
        m.store(cache, InstructionAdapter.OBJECT_TYPE);
        m.load(cache, InstructionAdapter.OBJECT_TYPE);
        m.load(2, Type.INT_TYPE); m.load(4, Type.INT_TYPE); m.load(3, Type.INT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "objectCache",
                Type.getMethodDescriptor(Type.getType(DfcObjectCache.class)), false);
        m.invokestatic(Type.getInternalName(ColumnFlatCacheSource.class), "sample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(IFastCacheLike.class),
                        Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.getType(DfcObjectCache.class)), true);
        int value = locals.allocate(Type.DOUBLE_TYPE);
        m.store(value, Type.DOUBLE_TYPE);
        m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(value, Type.DOUBLE_TYPE);
        m.invokestatic(Type.getInternalName(Arrays.class), "fill",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class), Type.DOUBLE_TYPE), false);
    }

    private void emitGradient(YClampedGradientNode node, InstructionAdapter m,
                              ColumnPointBytecodeGen.LocalAllocator locals) {
        this.loop(m, locals, index -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
            this.loadYAtIndex(m, index); m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
            m.dconst(node.fromY); m.dconst(node.toY); m.dconst(node.fromValue); m.dconst(node.toValue);
            m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeMathHelperClampedMap", "(DDDDD)D", false);
            m.astore(Type.DOUBLE_TYPE);
        });
    }

    private void emitPointLoop(AstNode node, InstructionAdapter m,
                               ColumnPointBytecodeGen.LocalAllocator locals) {
        this.loop(m, locals, index -> {
            m.load(1, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE);
            this.callPointAtIndex(m, node, index); m.astore(Type.DOUBLE_TYPE);
        });
    }

    private void callColumn(InstructionAdapter m, AstNode node, int outputLocal) {
        m.load(0, InstructionAdapter.OBJECT_TYPE); m.load(outputLocal, InstructionAdapter.OBJECT_TYPE);
        m.load(2, Type.INT_TYPE); m.load(3, Type.INT_TYPE); m.load(4, Type.INT_TYPE); m.load(5, Type.INT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, this.method(node), NODE_DESC, false);
    }

    private void callPointAtIndex(InstructionAdapter m, AstNode node, int index) {
        m.load(0, InstructionAdapter.OBJECT_TYPE); m.load(2, Type.INT_TYPE);
        this.loadYAtIndex(m, index); m.load(3, Type.INT_TYPE); m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, this.points.method(node), ColumnPointBytecodeGen.DESC, false);
    }

    private void loadYAtIndex(InstructionAdapter m, int index) {
        m.load(4, Type.INT_TYPE); m.load(index, Type.INT_TYPE); m.load(5, Type.INT_TYPE);
        m.mul(Type.INT_TYPE); m.add(Type.INT_TYPE);
    }

    private int borrowDoubleArray(InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals) {
        int temp = locals.allocate(Type.getType(double[].class));
        this.loadObjectCache(m);
        m.load(1, InstructionAdapter.OBJECT_TYPE); m.arraylength(); m.iconst(0);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getDoubleArray",
                Type.getMethodDescriptor(Type.getType(double[].class), Type.INT_TYPE, Type.BOOLEAN_TYPE));
        m.store(temp, InstructionAdapter.OBJECT_TYPE);
        return temp;
    }

    private void recycleDoubleArray(InstructionAdapter m, int local) {
        this.loadObjectCache(m);
        m.load(local, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class)));
    }

    private void fillConstant(InstructionAdapter m, double value) {
        m.load(1, InstructionAdapter.OBJECT_TYPE); m.dconst(value);
        m.invokestatic(Type.getInternalName(Arrays.class), "fill",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class), Type.DOUBLE_TYPE), false);
    }

    private void fillMemoized(InstructionAdapter m, ColumnMemoized2DNode node) {
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(2, Type.INT_TYPE); m.load(4, Type.INT_TYPE); m.load(3, Type.INT_TYPE);
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, this.points.method(node), ColumnPointBytecodeGen.DESC, false);
        m.invokestatic(Type.getInternalName(Arrays.class), "fill",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(double[].class), Type.DOUBLE_TYPE), false);
    }

    private void loop(InstructionAdapter m, ColumnPointBytecodeGen.LocalAllocator locals, IntConsumer body) {
        int index = locals.allocate(Type.INT_TYPE); m.iconst(0); m.store(index, Type.INT_TYPE);
        Label start = new Label(); Label end = new Label(); m.visitLabel(start);
        m.load(index, Type.INT_TYPE); m.load(1, InstructionAdapter.OBJECT_TYPE); m.arraylength(); m.ificmpge(end);
        body.accept(index); m.iinc(index, 1); m.goTo(start); m.visitLabel(end);
    }

    private static void loadArray(InstructionAdapter m, int array, int index) {
        m.load(array, InstructionAdapter.OBJECT_TYPE); m.load(index, Type.INT_TYPE); m.aload(Type.DOUBLE_TYPE);
    }

    private static void loadContext(InstructionAdapter m, String method, Type returnType) {
        m.load(1, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), method,
                Type.getMethodDescriptor(returnType), false);
    }

    private int interpolationSourceIndex(IFastCacheLike source) {
        return this.interpolationSources.computeIfAbsent(source, ignored -> this.interpolationSources.size());
    }

    private void loadObjectCache(InstructionAdapter m) {
        m.load(6, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "objectCache",
                Type.getMethodDescriptor(Type.getType(DfcObjectCache.class)), false);
    }
}
