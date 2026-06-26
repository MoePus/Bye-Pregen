package com.moepus.byepregen.yalight;

final class YABlockRunCache extends YAChunkRunCache {
    boolean enabled(YALightStorage storage, int chunkX, int chunkZ) {
        return super.enabled(storage, storage.chunkGetter(), chunkX, chunkZ);
    }
}
