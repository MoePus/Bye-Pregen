package com.moepus.byepregen.yalight.access;

import java.util.concurrent.CompletableFuture;

public interface YAPendingTaskAccess {
    CompletableFuture<?> byepregen$waitForPendingTasks(int chunkX, int chunkZ);
}
