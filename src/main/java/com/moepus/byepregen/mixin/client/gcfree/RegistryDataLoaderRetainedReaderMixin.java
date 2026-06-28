package com.moepus.byepregen.mixin.client.gcfree;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moepus.byepregen.client.gcfree.RetainedResourceReaders;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderRetainedReaderMixin {
    @WrapMethod(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;")
    private static RegistryAccess.Frozen byepregen$clearRetainedReadersAfterLoad(
            ResourceManager resourceManager,
            RegistryAccess registryAccess,
            List<RegistryDataLoader.RegistryData<?>> registryData,
            Operation<RegistryAccess.Frozen> original
    ) {
        try {
            return original.call(resourceManager, registryAccess, registryData);
        } finally {
            RetainedResourceReaders.clearRetained();
        }
    }

    @Redirect(
            method = "loadElementFromResource",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/Resource;openAsReader()Ljava/io/BufferedReader;"
            )
    )
    private static BufferedReader byepregen$openRetainedReader(Resource resource) throws IOException {
        return RetainedResourceReaders.open(resource);
    }
}
