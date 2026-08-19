package com.moepus.byepregen.worldgen.arena;

/** Project-owned identity carried by an Interpolated marker through compiled visitors. */
public interface InterpolatedMarkerAccess {
    Object byepregen$getInterpolationToken();

    void byepregen$setInterpolationToken(Object token);
}
