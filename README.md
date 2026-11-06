# ByePregen

ByePregen is a world-generation optimization mod for Minecraft. In our measurements, world generation is 19% faster in ATM9 and 50% faster in vanilla Minecraft, with larger gains in WWOO and Still Life. It also reduces main-thread stalls during chunk generation by 30% to 70%.

## Features

All options are in `config/byepregen.toml`.

| Feature | Config path |
|---|---|
| Arena block storage | `worldgen.arena.enabled` |
| Density column compiler | `worldgen.arena.density-column-compiler` |
| Server-side Arena runtime storage | `worldgen.arena.server-runtime` |
| Client-side Arena storage | `worldgen.arena.client` |
| Surface rule compiler | `worldgen.surface.rule-compiler` |
| Surface biome cache | `worldgen.surface.biome-cache` |
| Placed feature optimizations | `worldgen.placed-features.enabled` |
| Placed feature local optimizations | `worldgen.placed-features.local-optimizations` |
| Disk feature generation optimization | `worldgen.placed-features.memoized-disk-plan` |
| Density flat-cache access | `worldgen.misc.flat-cache-access` |
| Palette lock optimization | `worldgen.misc.palette-lock` |
| GC-free chunk saving | `chunk-saving.gc-free-worldgen` |
| Chunk saving buffer reuse | `chunk-saving.retain-buffer` |
| Experimental fast chunk ticking | `server.fast-chunk-ticking.enabled` |
| Experimental YA lighting engine | `lighting.ya.enabled` |

## YA Lighting

YA is an experimental lighting engine built around the architecture of Minecraft's vanilla lighting engine, with native support for Sable and Flywheel. Its single-threaded lighting speed is on par with, and slightly faster than, ScalableLux. ScalableLux is faster with multiple threads; YA uses a single lighting thread.

## World Generation Results

ByePregen targets algebraic equivalence rather than bitwise identity. C2ME preserves vanilla's implementation, including incorrect behavior, to maintain bitwise-identical results; ByePregen may fix vanilla bugs.

World generation matches vanilla in the vast majority of cases. The internal ordering of placed features, especially leaves, is not guaranteed to match. Vanilla's feature results can also vary with chunk generation order.

## Code Origins

`chunk-saving.gc-free-worldgen` and `worldgen.arena.density-column-compiler` are derived from C2ME. YA references portions of ScalableLux's code. All other features are original work.

## AI Disclosure

As of the writing of this README, approximately 70% of the code was AI-generated.

## For Dev

Other mods can declare ByePregen config paths that conflict with their behavior in `META-INF/neoforge.mods.toml`:

```toml
[byepregen]
conflict = [
    "worldgen.arena.enabled",
    "worldgen.surface.rule-compiler"
]
```

Use the full config paths from the table above. A declaration disables the option when it is set to `Default`; explicit `True` and `False` values in ByePregen's config take precedence.
