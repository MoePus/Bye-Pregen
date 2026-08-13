# Vendored library manifest

This directory contains compile-time inputs, locally resolved artifacts, and retained compatibility research artifacts.
Absence from the active Gradle graph is not deletion approval.

Status definitions:

- `build-input`: referenced directly by `gradle/byepregen-dependencies.gradle`.
- `coordinate-input`: resolved from this directory through the vendored Ivy repository by Maven coordinates.
- `manual-input`: referenced by a documented manual experiment, but not by the default Gradle graph.
- `retained-unreferenced`: no current build, test, script, or documented manual consumer was found.

| File | Status | Purpose |
| --- | --- | --- |
| `asyncutil-0.1.0.jar` | retained-unreferenced | Local copy of AsyncUtil; the active build resolves `com.ibm.async:asyncutil:0.1.0` from repositories. |
| `c2me-neoforge-base-mc1.21.1-0.4.0-alpha.0.116-all.jar` | build-input | Compiles C2ME integration code against common C2ME APIs. |
| `c2me-neoforge-client-uncapvd-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME client compatibility artifact. |
| `c2me-neoforge-fixes-chunkio-threading-issues-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME chunk-I/O threading compatibility artifact. |
| `c2me-neoforge-fixes-general-threading-issues-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME general-threading compatibility artifact. |
| `c2me-neoforge-fixes-worldgen-threading-issues-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME worldgen-threading compatibility artifact. |
| `c2me-neoforge-fixes-worldgen-vanilla-bugs-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME vanilla-worldgen compatibility artifact. |
| `c2me-neoforge-notickvd-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME no-tick view-distance compatibility artifact. |
| `c2me-neoforge-opts-allocs-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME allocation-optimization artifact. |
| `c2me-neoforge-opts-chunkio-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME chunk-I/O optimization artifact. |
| `c2me-neoforge-opts-dfc-mc1.21.1-0.4.0-alpha.0.116.jar` | build-input | Compiles DFC compatibility mixins against C2ME targets. |
| `c2me-neoforge-opts-math-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME math-optimization artifact. |
| `c2me-neoforge-opts-natives-math-mc1.21.1-0.4.0-alpha.0.116.jar` | manual-input | Prebuilt native-math input referenced by the experiment documented in `plan.md`. |
| `c2me-neoforge-opts-scheduling-mc1.21.1-0.4.0-alpha.0.116.jar` | build-input | Compiles C2ME scheduling compatibility code. |
| `c2me-neoforge-opts-worldgen-general-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME general worldgen optimization artifact. |
| `c2me-neoforge-opts-worldgen-vanilla-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME vanilla worldgen optimization artifact. |
| `c2me-neoforge-rewrites-chunk-system-mc1.21.1-0.4.0-alpha.0.116.jar` | build-input | Compiles C2ME chunk-system compatibility mixins. |
| `c2me-neoforge-rewrites-chunkio-mc1.21.1-0.4.0-alpha.0.116.jar` | build-input | Compiles C2ME asynchronous chunk serialization integration. |
| `c2me-neoforge-server-utils-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME server-utility compatibility artifact. |
| `c2me-neoforge-threading-lighting-mc1.21.1-0.4.0-alpha.0.116.jar` | retained-unreferenced | Retained C2ME threaded-lighting compatibility artifact. |
| `caffeine-3.2.1.jar` | retained-unreferenced | Retained support library from the compatibility artifact set. |
| `exp4j-0.4.8.jar` | retained-unreferenced | Retained support library from the compatibility artifact set. |
| `jctools-core-4.0.5.jar` | retained-unreferenced | Retained support library from the compatibility artifact set. |
| `mixinsquared-common-0.3.7-beta.1.jar` | coordinate-input | Local compile-time and annotation-processor artifact resolved as `com.github.bawnorton.mixinsquared:mixinsquared-common`. |
| `mixinsquared-neoforge-0.3.7-beta.1.jar` | coordinate-input | Local runtime and JarJar artifact resolved as `com.github.bawnorton.mixinsquared:mixinsquared-neoforge`. |
| `reactive-streams-1.0.4.jar` | retained-unreferenced | Retained support library from the compatibility artifact set. |
| `rxjava-3.1.12.jar` | retained-unreferenced | Retained support library for C2ME signatures; not a direct Gradle file dependency. |
| `voxy-0.2.15-beta-3.jar` | build-input | Compiles the Voxy world-conversion compatibility mixin. |
