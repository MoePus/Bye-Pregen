package com.moepus.byepregen.worldgen.surface;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.levelgen.SurfaceRules;

final class SurfaceScalarAsmCompiler {
    private static final AtomicInteger NEXT_CLASS = new AtomicInteger();
    private static final String GENERATED_PACKAGE = "net/minecraft/world/level/levelgen/";
    private static final String DUMP_PROPERTY = "byepregen.surfaceDumpClasses";

    private SurfaceScalarAsmCompiler() {
    }

    static SurfaceDirectTemplate compile(SurfaceRulePlan plan) throws SurfaceCompileException {
        SurfaceRuntimeAbi abi = SurfaceRuntimeAbi.resolve();
        SurfaceScalarLayout layout = SurfaceScalarLayout.lower(plan);
        String simpleName = "SurfaceRules$ByepregenScalar$" + NEXT_CLASS.getAndIncrement();
        String internalName = GENERATED_PACKAGE + simpleName;
        SurfaceEmissionContext emission = new SurfaceEmissionContext(
                internalName, abi, layout
        );
        byte[] bytecode = new SurfaceScalarClassEmitter(emission).emit();
        dumpClass(simpleName, bytecode);
        MethodHandle constructor = defineConstructor(abi, bytecode);
        SurfaceRegionPlan regions = emission.regions();
        SurfaceDirectTemplate.Statistics statistics = new SurfaceDirectTemplate.Statistics(
                new SurfaceDirectTemplate.ClassShape(
                        bytecode.length, regions.regions().size(), regions.describe()
                ),
                new SurfaceDirectTemplate.BindingCounts(
                        layout.bindings().storedSlots().size(), layout.bindings().events().size()
                ),
                new SurfaceDirectTemplate.ValueCounts(
                        layout.noiseOccurrences(),
                        layout.noiseSamples().size()
                )
        );
        return new SurfaceDirectTemplate(layout.bindings(), constructor, statistics);
    }

    private static MethodHandle defineConstructor(
            SurfaceRuntimeAbi abi,
            byte[] bytecode
    ) throws SurfaceCompileException {
        try {
            MethodHandles.Lookup hiddenLookup = abi.lookup().defineHiddenClass(
                    bytecode,
                    true,
                    MethodHandles.Lookup.ClassOption.NESTMATE
            );
            Class<?> generated = hiddenLookup.lookupClass();
            if (!generated.isHidden() || generated.getNestHost() != SurfaceRules.class) {
                throw new SurfaceCompileException("Generated SurfaceRule is not a SurfaceRules nestmate");
            }
            MethodHandle constructor = hiddenLookup.findConstructor(
                    generated,
                    MethodType.methodType(void.class, abi.contextClass(), Object[].class)
            );
            return constructor.asType(MethodType.methodType(
                    Object.class,
                    Object.class,
                    Object[].class
            ));
        } catch (SurfaceCompileException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new SurfaceCompileException("Cannot define bound SurfaceRule class", throwable);
        }
    }

    private static void dumpClass(String simpleName, byte[] bytecode)
            throws SurfaceCompileException {
        String directory = System.getProperty(DUMP_PROPERTY);
        if (directory == null || directory.isBlank()) {
            return;
        }
        try {
            Path output = Path.of(directory)
                    .resolve("net/minecraft/world/level/levelgen")
                    .resolve(simpleName + ".class");
            Files.createDirectories(output.getParent());
            Files.write(output, bytecode);
        } catch (IOException exception) {
            throw new SurfaceCompileException("Cannot dump generated SurfaceRule class", exception);
        }
    }
}
