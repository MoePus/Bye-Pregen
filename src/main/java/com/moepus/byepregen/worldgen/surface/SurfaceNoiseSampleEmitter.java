package com.moepus.byepregen.worldgen.surface;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class SurfaceNoiseSampleEmitter {
    private static final int SAMPLE_LOCAL = 3;
    private static final int FIRST_BANK_LOCAL = 5;

    private final SurfaceEmissionContext context;
    private final ClassWriter writer;

    SurfaceNoiseSampleEmitter(SurfaceEmissionContext context, ClassWriter writer) {
        this.context = context;
        this.writer = writer;
    }

    void emit(SurfaceScalarLayout.NoiseSample sample) {
        MethodVisitor method = this.writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                SurfaceEmissionContext.noiseSampleMethod(sample.index()),
                "(II)V",
                null,
                null
        );
        method.visitCode();
        Map<Integer, BankState> banks = createBanks(sample);
        this.emitSample(method, sample);
        initializeBanks(method, banks);
        for (SurfaceScalarLayout.NoiseRange range : sample.ranges()) {
            emitRange(method, range, banks.get(range.predicateIndex() / Long.SIZE));
        }
        this.publishValues(method, banks);
        this.publishSampled(method, sample);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private void emitSample(MethodVisitor method, SurfaceScalarLayout.NoiseSample sample) {
        this.context.loadBinding(method, sample.noiseSlot());
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitInsn(Opcodes.I2D);
        method.visitInsn(Opcodes.DCONST_0);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitInsn(Opcodes.I2D);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getInternalName(NormalNoise.class),
                this.context.abi().noiseGetValue(),
                "(DDD)D",
                false
        );
        method.visitVarInsn(Opcodes.DSTORE, SAMPLE_LOCAL);
    }

    private static Map<Integer, BankState> createBanks(
            SurfaceScalarLayout.NoiseSample sample
    ) {
        Map<Integer, BankState> banks = new LinkedHashMap<>();
        for (SurfaceScalarLayout.NoiseRange range : sample.ranges()) {
            int bank = range.predicateIndex() / Long.SIZE;
            BankState state = banks.get(bank);
            if (state == null) {
                state = new BankState(bank, FIRST_BANK_LOCAL + banks.size() * 2);
                banks.put(bank, state);
            }
            state.ownedMask |= range.mask();
        }
        return banks;
    }

    private static void initializeBanks(MethodVisitor method, Map<Integer, BankState> banks) {
        for (BankState bank : banks.values()) {
            method.visitInsn(Opcodes.LCONST_0);
            method.visitVarInsn(Opcodes.LSTORE, bank.local);
        }
    }

    private static void emitRange(
            MethodVisitor method,
            SurfaceScalarLayout.NoiseRange range,
            BankState bank
    ) {
        Label outside = new Label();
        method.visitVarInsn(Opcodes.DLOAD, SAMPLE_LOCAL);
        method.visitLdcInsn(range.minimum());
        method.visitInsn(Opcodes.DCMPL);
        method.visitJumpInsn(Opcodes.IFLT, outside);
        method.visitVarInsn(Opcodes.DLOAD, SAMPLE_LOCAL);
        method.visitLdcInsn(range.maximum());
        method.visitInsn(Opcodes.DCMPG);
        method.visitJumpInsn(Opcodes.IFGT, outside);
        method.visitVarInsn(Opcodes.LLOAD, bank.local);
        method.visitLdcInsn(range.mask());
        method.visitInsn(Opcodes.LOR);
        method.visitVarInsn(Opcodes.LSTORE, bank.local);
        method.visitLabel(outside);
    }

    private void publishValues(MethodVisitor method, Map<Integer, BankState> banks) {
        for (BankState bank : banks.values()) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitFieldInsn(
                    Opcodes.GETFIELD,
                    this.context.owner(),
                    SurfaceEmissionContext.valuesField(bank.index),
                    "J"
            );
            method.visitLdcInsn(~bank.ownedMask);
            method.visitInsn(Opcodes.LAND);
            method.visitVarInsn(Opcodes.LLOAD, bank.local);
            method.visitInsn(Opcodes.LOR);
            method.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    this.context.owner(),
                    SurfaceEmissionContext.valuesField(bank.index),
                    "J"
            );
        }
    }

    private void publishSampled(
            MethodVisitor method,
            SurfaceScalarLayout.NoiseSample sample
    ) {
        int bank = sample.index() / Long.SIZE;
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(
                Opcodes.GETFIELD,
                this.context.owner(),
                SurfaceEmissionContext.sampledField(bank),
                "J"
        );
        method.visitLdcInsn(sample.mask());
        method.visitInsn(Opcodes.LOR);
        method.visitFieldInsn(
                Opcodes.PUTFIELD,
                this.context.owner(),
                SurfaceEmissionContext.sampledField(bank),
                "J"
        );
    }

    private static final class BankState {
        private final int index;
        private final int local;
        private long ownedMask;

        private BankState(int index, int local) {
            this.index = index;
            this.local = local;
        }
    }
}
