package com.moepus.byepregen;

import com.moepus.byepregen.integration.c2me.GcFreeCompat;
import com.moepus.byepregen.config.Config;
import com.moepus.byepregen.config.ConfigManager;
import com.moepus.byepregen.yalight.engine.YABlockStateLightClass;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.IdMappingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Byepregen.MODID)
public class Byepregen {
    private static final Logger LOGGER = LoggerFactory.getLogger(Byepregen.class);
    public static final String MODID = "byepregen";

    public Byepregen() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(Byepregen::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(Byepregen::onIdMapping);
        GcFreeCompat.register();
    }

    private static void onIdMapping(IdMappingEvent event) {
        Config config = ConfigManager.getConfig();
        if (MixinPlugin.MIXIN_FEATURE_EVALUATOR.isEnabled(MixinFeature.YA_LIGHT, config)) {
            // Forge has finished baking the state ID map, including snapshot restores.
            YABlockStateLightClass.rebuild();
        }
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        Config config = ConfigManager.getConfig();
        if (!config.lighting().ya().enabled()) {
            return;
        }
        if (!MixinPlugin.MIXIN_FEATURE_EVALUATOR.isEnabled(MixinFeature.YA_LIGHT, config)) {
            LOGGER.warn("ScalableLux is installed, so ByePregen YA light has been disabled "
                    + "despite lighting.ya.enabled=true");
            return;
        }
        event.enqueueWork(() -> {
            // Forge can populate block-state IDs after load-complete; leave initialization lazy then.
            if (Block.BLOCK_STATE_REGISTRY.size() != 0) YABlockStateLightClass.initialize();
        });
    }
}
