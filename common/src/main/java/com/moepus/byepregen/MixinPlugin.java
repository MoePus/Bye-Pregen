package com.moepus.byepregen;

import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.integration.platform.PlatformServices;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MixinPlugin implements IMixinConfigPlugin {
    private static final String CONFIG_FILE_NAME = "byepregen.toml";
    private static final MixinGateEvaluator MIXIN_GATE_EVALUATOR = MixinGateEvaluator.createDefault();
    private static final MixinFeatureEvaluator MIXIN_FEATURE_EVALUATOR =
            MixinFeatureEvaluator.createDefault();

    public MixinPlugin() {
    }

    @Override
    public void onLoad(String mixinPackage) {
        ConfigManager.initialize(PlatformServices.get().configDirectory().resolve(CONFIG_FILE_NAME));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Config config = ConfigManager.getConfig();
        MixinGateEvaluator.GateEvaluation gate = MIXIN_GATE_EVALUATOR.evaluate(
                targetClassName, mixinClassName, config);
        return gate.annotationEnabled()
                && MIXIN_FEATURE_EVALUATOR.isEnabled(gate.feature(), config);
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
}
