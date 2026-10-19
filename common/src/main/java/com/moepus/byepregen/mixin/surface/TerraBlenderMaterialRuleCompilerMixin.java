package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.MixinFeature;
import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.worldgen.surface.TerraBlenderCompat;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@MixinGate(feature = MixinFeature.SURFACE_RULE_COMPILER, requiredMods = "terrablender")
@Mixin(targets = "terrablender.worldgen.surface.MaterialRuleCompiler", remap = false)
public abstract class TerraBlenderMaterialRuleCompilerMixin {
    // Leave holder/sequence recursion, shared-node memoization and lazy guard inspection native.
    @Redirect(method = "compile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;compile(Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;)Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;"))
    private RuleEvaluator byepregen$compileMaterial(MaterialRule source, MaterialRuleContext context) {
        return TerraBlenderCompat.compileRule(source, context);
    }
}
