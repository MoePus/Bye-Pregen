package com.moepus.byepregen.mixin.surface;

import com.moepus.byepregen.worldgen.surface.SurfaceRuleSourceAccess;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$BlockRuleSource")
public interface SurfaceRulesBlockSourceMixin extends SurfaceRuleSourceAccess.Block {
    @Override
    @Accessor("resultState")
    BlockState byepregen$resultState();
}
