package com.moepus.byepregen.worldgen.surface;

abstract class SurfaceCompiledTemplate {
    abstract Object bind(Object context) throws Throwable;
}
