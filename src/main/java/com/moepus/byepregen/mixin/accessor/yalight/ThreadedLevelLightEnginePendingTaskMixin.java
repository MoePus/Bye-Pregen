package com.moepus.byepregen.mixin.accessor.yalight;

import com.moepus.byepregen.MixinGate;
import com.moepus.byepregen.yalight.access.YAPendingTaskAccess;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@MixinGate
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEnginePendingTaskMixin implements YAPendingTaskAccess {
    @Shadow
    @Final
    private ChunkMap chunkMap;

    @Shadow
    private void addTask(
            int chunkX,
            int chunkZ,
            ThreadedLevelLightEngine.TaskType type,
            Runnable task
    ) {
        throw new AssertionError();
    }

    @Override
    public CompletableFuture<?> byepregen$waitForPendingTasks(int chunkX, int chunkZ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        var mainThreadExecutor = ((ChunkMapLightAccessor)this.chunkMap).byepregen$getMainThreadExecutor();
        this.addTask(
                chunkX,
                chunkZ,
                ThreadedLevelLightEngine.TaskType.POST_UPDATE,
                () -> mainThreadExecutor.execute(() -> future.complete(null))
        );
        return future;
    }
}
