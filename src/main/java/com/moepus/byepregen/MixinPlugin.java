package com.moepus.byepregen;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String C2ME_SERVER_BLOCK_TICKING =
            "com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerBlockTicking";
    private static final String C2ME_COMPAT_MIXIN =
            "com.moepus.byepregen.mixin.compat.C2MEServerBlockTickingMixin";
    private static final String VANILLA_CHUNK_STATUS_PRENORM_MIXIN =
            "com.moepus.byepregen.mixin.ChunkStatusPostProcessingPreNormMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean hasC2MEChunkSystem = hasClass(C2ME_SERVER_BLOCK_TICKING);
        return switch (mixinClassName) {
            case C2ME_COMPAT_MIXIN -> hasC2MEChunkSystem;
            case VANILLA_CHUNK_STATUS_PRENORM_MIXIN -> !hasC2MEChunkSystem;
            default -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean hasClass(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
