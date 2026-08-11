/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 */

package com.moepus.byepregen.dfc.column;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ast.binary.*;
import com.ishland.c2me.opts.dfc.common.ast.misc.*;
import com.ishland.c2me.opts.dfc.common.ast.noise.DFTWeirdScaledSamplerNode;
import com.ishland.c2me.opts.dfc.common.ast.noise.GenericShiftedNoiseNode;
import com.ishland.c2me.opts.dfc.common.ast.spline.SplineAstNode;
import com.ishland.c2me.opts.dfc.common.ast.unary.*;
import com.ishland.c2me.base.mixin.access.IDensityFunctionsCaveScaler;
import com.ishland.c2me.opts.dfc.common.ducks.IFastCacheLike;
import com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen;
import com.ishland.c2me.opts.dfc.common.gen.jvm.InvocationShim;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

final class ColumnPointBytecodeGen {

    static final String DESC = Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.INT_TYPE, Type.INT_TYPE,
            Type.INT_TYPE, Type.getType(ColumnEvaluationContext.class));

    private final BytecodeGen.Context owner;
    private final Map<AstNode, String> methods = new IdentityHashMap<>();
    private final ColumnSplineBytecodeGen splines;
    private final ToIntFunction<IFastCacheLike> interpolationSourceIndex;

    ColumnPointBytecodeGen(BytecodeGen.Context owner, ToIntFunction<IFastCacheLike> interpolationSourceIndex) {
        this.owner = owner;
        this.interpolationSourceIndex = interpolationSourceIndex;
        this.splines = new ColumnSplineBytecodeGen(owner, this::method);
    }

    String method(AstNode node) {
        String existing = this.methods.get(node);
        if (existing != null) return existing;
        String name = this.owner.nextMethodName("ColumnPoint_" + node.getClass().getSimpleName());
        this.methods.put(node, name);
        this.generate(node, name);
        return name;
    }

    private void generate(AstNode node, String name) {
        InstructionAdapter m = new InstructionAdapter(new AnalyzerAdapter(this.owner.className,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, DESC,
                this.owner.classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name, DESC, null, null)));
        LocalAllocator locals = new LocalAllocator(5);
        this.emit(node, m, locals);
        m.visitMaxs(0, 0);
    }

    private void emit(AstNode node, InstructionAdapter m, LocalAllocator locals) {
        Class<?> type = node.getClass();
        if (type == RootNode.class) {
            RootNode root = (RootNode) node;
            this.call(m, root.next);
        } else if (type == ConstantNode.class) {
            ConstantNode constant = (ConstantNode) node;
            m.dconst(constant.getValue());
        } else if (type == ColumnMemoized2DNode.class) {
            this.emitMemoized((ColumnMemoized2DNode) node, m, locals);
        } else if (type == ColumnCacheNode.class) {
            ColumnCacheNode cache = (ColumnCacheNode) node;
            this.emitCache(cache, m);
        } else if (type == CoordinateNode.class) {
            CoordinateNode coordinate = (CoordinateNode) node;
            m.load(coordinate.axis == CoordinateNode.Axis.X ? 1 : coordinate.axis == CoordinateNode.Axis.Y ? 2 : 3,
                    Type.INT_TYPE);
            m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        } else if (ColumnSupport.isSupportedUnary(node)) {
            AbstractUnaryNode unary = (AbstractUnaryNode) node;
            this.call(m, unary.operand);
            emitUnary(unary, m, locals);
        } else if (ColumnSupport.isSupportedBinary(node)) {
            AbstractBinaryNode binary = (AbstractBinaryNode) node;
            this.emitBinary(binary, m, locals);
        } else if (type == RangeChoiceNode.class) {
            RangeChoiceNode range = (RangeChoiceNode) node;
            this.emitRange(range, m, locals);
        } else if (type == YClampedGradientNode.class) {
            YClampedGradientNode gradient = (YClampedGradientNode) node;
            this.emitGradient(gradient, m);
        } else if (type == SplineAstNode.class) {
            this.splines.emitSample((SplineAstNode) node, m);
        } else if (type == GenericShiftedNoiseNode.class) {
            this.emitShiftedNoise((GenericShiftedNoiseNode) node, m);
        } else if (type == DFTWeirdScaledSamplerNode.class) {
            this.emitWeirdScaled((DFTWeirdScaledSamplerNode) node, m, locals);
        } else if (ColumnSupport.isSupportedDelegate(node)) {
            this.emitDelegate((DelegateNode) node, m, locals);
        } else {
            throw new UnsupportedOperationException("Unsupported ColumnPoint node: " + node.getClass().getName());
        }
        m.areturn(Type.DOUBLE_TYPE);
    }

    private void emitBinary(AbstractBinaryNode node, InstructionAdapter m, LocalAllocator locals) {
        this.call(m, node.left);
        if (node instanceof MulNode) {
            Label nonZero = new Label();
            m.dup2();
            m.dconst(0.0);
            m.cmpl(Type.DOUBLE_TYPE);
            m.ifne(nonZero);
            m.pop2();
            m.dconst(0.0);
            m.areturn(Type.DOUBLE_TYPE);
            m.visitLabel(nonZero);
            this.call(m, node.right);
            m.mul(Type.DOUBLE_TYPE);
            return;
        }
        if (node instanceof MinShortNode minShort) {
            Label evaluate = new Label();
            m.dup2();
            m.dconst(minShort.rightMin);
            m.cmpg(Type.DOUBLE_TYPE);
            m.ifge(evaluate);
            m.areturn(Type.DOUBLE_TYPE);
            m.visitLabel(evaluate);
            this.call(m, node.right);
            invokeMath(m, "min");
            return;
        }
        if (node instanceof MaxShortNode maxShort) {
            Label evaluate = new Label();
            m.dup2();
            m.dconst(maxShort.rightMax);
            m.cmpl(Type.DOUBLE_TYPE);
            m.ifle(evaluate);
            m.areturn(Type.DOUBLE_TYPE);
            m.visitLabel(evaluate);
            this.call(m, node.right);
            invokeMath(m, "max");
            return;
        }
        this.call(m, node.right);
        if (node instanceof AddNode) m.add(Type.DOUBLE_TYPE);
        else if (node instanceof DivNode) m.div(Type.DOUBLE_TYPE);
        else if (node instanceof MinNode) invokeMath(m, "min");
        else if (node instanceof MaxNode) invokeMath(m, "max");
        else throw new UnsupportedOperationException("Unsupported ColumnPoint binary: " + node.getClass().getName());
    }

    private void emitRange(RangeChoiceNode node, InstructionAdapter m, LocalAllocator locals) {
        int value = locals.allocate(Type.DOUBLE_TYPE);
        this.call(m, node.input);
        m.store(value, Type.DOUBLE_TYPE);
        Label out = new Label();
        Label end = new Label();
        m.load(value, Type.DOUBLE_TYPE);
        m.dconst(node.minInclusive);
        m.cmpl(Type.DOUBLE_TYPE);
        m.iflt(out);
        m.load(value, Type.DOUBLE_TYPE);
        m.dconst(node.maxExclusive);
        m.cmpg(Type.DOUBLE_TYPE);
        m.ifge(out);
        this.callOrValue(m, node.whenInRange, node.input, value);
        m.goTo(end);
        m.visitLabel(out);
        this.callOrValue(m, node.whenOutOfRange, node.input, value);
        m.visitLabel(end);
    }

    private void callOrValue(InstructionAdapter m, AstNode selected, AstNode input, int value) {
        if (selected == input) m.load(value, Type.DOUBLE_TYPE);
        else this.call(m, selected);
    }

    private void emitCache(ColumnCacheNode node, InstructionAdapter m) {
        String field = this.owner.newField(IFastCacheLike.class, node.cacheLike());
        if (node.mode() == ColumnCacheNode.Mode.INTERPOLATED) {
            m.load(4, InstructionAdapter.OBJECT_TYPE);
            m.iconst(this.interpolationSourceIndex.applyAsInt(node.cacheLike()));
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(this.owner.className, field, Type.getDescriptor(IFastCacheLike.class));
            m.load(2, Type.INT_TYPE);
            m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "interpolatedValue",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.INT_TYPE, Type.getType(IFastCacheLike.class),
                            Type.INT_TYPE), false);
        } else {
            m.load(0, InstructionAdapter.OBJECT_TYPE);
            m.getfield(this.owner.className, field, Type.getDescriptor(IFastCacheLike.class));
            m.load(1, Type.INT_TYPE);
            m.load(2, Type.INT_TYPE);
            m.load(3, Type.INT_TYPE);
            this.loadObjectCache(m);
            m.invokestatic(Type.getInternalName(ColumnFlatCacheSource.class), "sample",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(IFastCacheLike.class),
                            Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE,
                            Type.getType(DfcObjectCache.class)), true);
        }
    }

    private void emitDelegate(DelegateNode node, InstructionAdapter m, LocalAllocator locals) {
        String field = this.owner.newField(DensityFunction.class, node.getDelegate());
        int noisePos = locals.allocate(Type.getType(NoisePosVanillaInterface.class));
        this.loadObjectCache(m);
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.getstatic(Type.getInternalName(EvalType.class), "NORMAL", Type.getDescriptor(EvalType.class));
        this.loadObjectCache(m);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "getNoisePosVanillaInterface",
                DfcObjectCache.GET_NOISE_POS_VANILLA_INTERFACE_DESC);
        m.store(noisePos, InstructionAdapter.OBJECT_TYPE);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(this.owner.className, field, Type.getDescriptor(DensityFunction.class));
        m.load(noisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeDensityFunctionSample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.class),
                        Type.getType(DensityFunction.FunctionContext.class)), false);
        int result = locals.allocate(Type.DOUBLE_TYPE);
        m.store(result, Type.DOUBLE_TYPE);
        this.loadObjectCache(m);
        m.load(noisePos, InstructionAdapter.OBJECT_TYPE);
        m.invokeinterface(Type.getInternalName(DfcObjectCache.class), "recycle",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(NoisePosVanillaInterface.class)));
        m.load(result, Type.DOUBLE_TYPE);
    }

    private void emitGradient(YClampedGradientNode node, InstructionAdapter m) {
        m.load(2, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.dconst(node.fromY);
        m.dconst(node.toY);
        m.dconst(node.fromValue);
        m.dconst(node.toValue);
        m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeMathHelperClampedMap", "(DDDDD)D", false);
    }

    private void emitShiftedNoise(GenericShiftedNoiseNode node, InstructionAdapter m) {
        String field = this.owner.newField(DensityFunction.NoiseHolder.class, node.noise);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(this.owner.className, field, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        this.call(m, node.inputX);
        this.call(m, node.inputY);
        this.call(m, node.inputZ);
        invokeNoise(m);
    }

    private void emitWeirdScaled(DFTWeirdScaledSamplerNode node, InstructionAdapter m, LocalAllocator locals) {
        int scale = locals.allocate(Type.DOUBLE_TYPE);
        this.call(m, node.input);
        String mapper = node.mapper.name();
        if ("TYPE1".equals(mapper)) {
            m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleTunnels",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
        } else if ("TYPE2".equals(mapper)) {
            m.invokestatic(Type.getInternalName(IDensityFunctionsCaveScaler.class), "invokeScaleCaves",
                    Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), true);
        } else {
            throw new UnsupportedOperationException("Unknown Column rarity mapper: " + node.mapper);
        }
        m.store(scale, Type.DOUBLE_TYPE);
        String field = this.owner.newField(DensityFunction.NoiseHolder.class, node.noise);
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.getfield(this.owner.className, field, Type.getDescriptor(DensityFunction.NoiseHolder.class));
        this.loadScaledCoordinate(m, 1, scale);
        this.loadScaledCoordinate(m, 2, scale);
        this.loadScaledCoordinate(m, 3, scale);
        invokeNoise(m);
        m.invokestatic(Type.getInternalName(Math.class), "abs", "(D)D", false);
        m.load(scale, Type.DOUBLE_TYPE);
        m.mul(Type.DOUBLE_TYPE);
    }

    private void loadScaledCoordinate(InstructionAdapter m, int local, int scale) {
        m.load(local, Type.INT_TYPE);
        m.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        m.load(scale, Type.DOUBLE_TYPE);
        m.div(Type.DOUBLE_TYPE);
    }

    private static void invokeNoise(InstructionAdapter m) {
        m.invokestatic(Type.getInternalName(InvocationShim.class), "invokeDensityFunctionNoiseSample",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(DensityFunction.NoiseHolder.class),
                        Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), false);
    }

    private void emitMemoized(ColumnMemoized2DNode node, InstructionAdapter m, LocalAllocator locals) {
        int value = locals.allocate(Type.DOUBLE_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.iconst(node.slot());
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "memoizedValue",
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.INT_TYPE), false);
        m.store(value, Type.DOUBLE_TYPE);
        Label hit = new Label();
        // Compare raw bits so ordinary arithmetic NaNs remain valid cached results.
        m.load(value, Type.DOUBLE_TYPE);
        m.invokestatic(Type.getInternalName(Double.class), "doubleToRawLongBits",
                Type.getMethodDescriptor(Type.LONG_TYPE, Type.DOUBLE_TYPE), false);
        m.lconst(IFastCacheLike.CACHE_MISS_NAN_BITS);
        m.lcmp();
        m.ifne(hit);
        this.call(m, node.delegate());
        m.store(value, Type.DOUBLE_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.iconst(node.slot());
        m.load(value, Type.DOUBLE_TYPE);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "setMemoizedValue",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE, Type.DOUBLE_TYPE), false);
        m.visitLabel(hit);
        m.load(value, Type.DOUBLE_TYPE);
    }

    private void call(InstructionAdapter m, AstNode node) {
        m.load(0, InstructionAdapter.OBJECT_TYPE);
        m.load(1, Type.INT_TYPE);
        m.load(2, Type.INT_TYPE);
        m.load(3, Type.INT_TYPE);
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(this.owner.className, this.method(node), DESC, false);
    }

    private void loadObjectCache(InstructionAdapter m) {
        m.load(4, InstructionAdapter.OBJECT_TYPE);
        m.invokevirtual(Type.getInternalName(ColumnEvaluationContext.class), "objectCache",
                Type.getMethodDescriptor(Type.getType(DfcObjectCache.class)), false);
    }

    static void emitUnary(AbstractUnaryNode node, InstructionAdapter m, LocalAllocator locals) {
        if (node instanceof AbsNode) {
            m.invokestatic(Type.getInternalName(Math.class), "abs", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), false);
        } else if (node instanceof SquareNode) {
            m.dup2(); m.mul(Type.DOUBLE_TYPE);
        } else if (node instanceof CubeNode) {
            m.dup2(); m.dup2(); m.mul(Type.DOUBLE_TYPE); m.mul(Type.DOUBLE_TYPE);
        } else if (node instanceof SqueezeNode) {
            invokeMathWithConstant(m, "max", -1.0);
            invokeMathWithConstant(m, "min", 1.0);
            int value = locals.allocate(Type.DOUBLE_TYPE);
            m.store(value, Type.DOUBLE_TYPE);
            m.load(value, Type.DOUBLE_TYPE); m.dconst(2.0); m.div(Type.DOUBLE_TYPE);
            m.load(value, Type.DOUBLE_TYPE); m.dup2(); m.dup2(); m.mul(Type.DOUBLE_TYPE); m.mul(Type.DOUBLE_TYPE);
            m.dconst(24.0); m.div(Type.DOUBLE_TYPE); m.sub(Type.DOUBLE_TYPE);
        } else if (node instanceof NegMulNode negMul) {
            int value = locals.allocate(Type.DOUBLE_TYPE);
            m.store(value, Type.DOUBLE_TYPE);
            Label positive = new Label(); Label end = new Label();
            m.load(value, Type.DOUBLE_TYPE); m.dconst(0.0); m.cmpl(Type.DOUBLE_TYPE); m.ifgt(positive);
            m.load(value, Type.DOUBLE_TYPE); m.dconst(negMul.negMul); m.mul(Type.DOUBLE_TYPE); m.goTo(end);
            m.visitLabel(positive); m.load(value, Type.DOUBLE_TYPE); m.visitLabel(end);
        } else {
            throw new UnsupportedOperationException("Unsupported Column unary: " + node.getClass().getName());
        }
    }

    static void invokeMath(InstructionAdapter m, String name) {
        m.invokestatic(Type.getInternalName(Math.class), name,
                Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), false);
    }

    private static void invokeMathWithConstant(InstructionAdapter m, String name, double value) {
        m.dconst(value);
        invokeMath(m, name);
    }

    static final class LocalAllocator {
        private int next;
        LocalAllocator(int first) { this.next = first; }
        int allocate(Type type) { int result = this.next; this.next += type.getSize(); return result; }
    }
}
