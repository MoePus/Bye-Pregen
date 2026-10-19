package com.moepus.byepregen.lighttest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Static emission is zero; only the NeoForge world/position hook can make this block emit. */
public final class DynamicLightTestBlock extends Block {
    private static DeferredBlock<DynamicLightTestBlock> registered;

    private DynamicLightTestBlock(Properties properties) {
        super(properties.noCollision().noOcclusion().lightLevel(state -> 0));
    }

    public static void register(IEventBus bus, String namespace) {
        DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(namespace);
        registered = blocks.registerBlock("dynamic_light", DynamicLightTestBlock::new);
        blocks.register(bus);
    }

    public static BlockState state() {
        return registered.get().defaultBlockState();
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
        return world instanceof Source source ? source.emissionAt(pos) : 0;
    }

    public interface Source {
        int emissionAt(BlockPos pos);
    }
}
