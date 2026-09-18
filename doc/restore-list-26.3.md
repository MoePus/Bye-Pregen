# 26.3 归档配置与访问规则：退役记录 / 待恢复清单

本文件替代原来放在 `common/src/main/resources/` 下的三份"归档输入"，它们已在本轮删除：

| 已删除文件 | 原作用 | 现在谁负责 |
|---|---|---|
| `common/src/main/resources/byepregen.mixins.json` | 26.2 的 mono mixin 配置（87 条注册） | 7 个分片配置（下表） |
| `common/src/main/resources/byepregen.accesswidener` | 26.2 的 Fabric 访问扩宽器 | `common/src/main/resources/byepregen.arena.accesswidener` |
| `common/src/main/resources/META-INF/accesstransformer.cfg` | 26.2 的 NeoForge 访问转换器 | `common/src/main/resources/META-INF/arena-core.cfg` |

删除理由：它们不进产物、不被任何 loader 加载（`fabric.mod.json` 与 `neoforge.mods.toml` 只列分片配置），却登记了 5 个磁盘上已不存在的类，是"看着像已注册、实际没加载"的误导源。**恢复方法**：需要时从 git 历史取回（`git show <26.2 提交>:<路径>`），不要把旧文件重新放回 `src/main/resources`。

### 收口时实测到的一件重要事实（2026-09-18）

**归档的访问转换器其实是"隐式生效"的**：ModDevGradle 会自动采用默认路径 `src/main/resources/META-INF/accesstransformer.cfg`，所以 `common/build.gradle` 里显式配置的 `META-INF/arena-core.cfg` 并不是唯一的 AT —— 删掉归档文件后 `:common:compileJava` 立刻失败：

```
mixin/yalight/ThreadedLevelLightEngineYAMixin.java:109/123/195、yalight/scheduler/YAThreadedLightScheduler.java:31
    错误: TaskType 在 ThreadedLevelLightEngine 中是 private 访问控制
```

因此本次收口把**仍然需要、且目标在 26.3 存在**的条目迁移进了生效的一对文件（其余归档条目属于 26.2 旧设计，目标类型在 26.3 已不存在，未迁移）：

| 迁移到 `META-INF/arena-core.cfg` | 迁移到 `byepregen.arena.accesswidener` |
|---|---|
| `public net.minecraft.server.level.ThreadedLevelLightEngine$TaskType` | `accessible class net/minecraft/server/level/ThreadedLevelLightEngine$TaskType` |
| `public net.minecraft.server.level.ThreadedLevelLightEngine addTask(IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V` | `accessible method net/minecraft/server/level/ThreadedLevelLightEngine addTask (IILjava/util/function/IntSupplier;Lnet/minecraft/server/level/ThreadedLevelLightEngine$TaskType;Ljava/lang/Runnable;)V` |

**结论与约定**：
1. `META-INF/arena-core.cfg` 与 `byepregen.arena.accesswidener` 是**唯一生效的一对**，两者必须同步维护（同一成员在 AT 用 `public`、在 AW 用 `accessible`）。
2. 顺带修正了一个**潜藏问题**：生效的 AW 之前缺少 `ThreadedLevelLightEngine$TaskType`，而 YA 光的 `YAThreadedLightScheduler` 在运行期要读该枚举 —— 也就是说 **Fabric 侧一旦启用 YA 光就会 `IllegalAccessError`**（此前的 Fabric 验证跑法里 YA 光是关闭的，所以没暴露）。现在已补齐。
3. 未迁移的归档条目（仅供参考，目标已在 26.3 消失或不需要）：`NoiseChunk$NoiseInterpolator *`（`NoiseChunk` 在 26.3 已无嵌套类）、`NoiseChunk firstNoiseX/firstNoiseZ`、`DensityFunctions$*` 的 26.2 构造器、`Climate$ParameterList index`、`Climate$RTree root/lastResult/$SubTree <init>`、`CombiningPredicate`（26.3 本来就是 public）。其中 `Climate$RTree$SubTree <init>` 在 **W16**（density-column biome 重写、`ClimateRTreeBuildOptimizer`）里会重新需要，届时再加。

---

## 1 当前生效的 7 个分片配置（唯一事实来源）

| 配置 | 装载者 | 内容概览 |
|---|---|---|
| `byepregen.arena.mixins.json` | 两侧 loader | arena 存储/序列化 6 条 |
| `byepregen.dfc.mixins.json` | 两侧 loader | `dfc.DensityFunctionCompilerMixin` |
| `byepregen.features.mixins.json` | 两侧 loader | placement/predicate/disk/tree 22 条 |
| `byepregen.runtime.mixins.json` | 两侧 loader | palette + fasttick + tick accessor 10 条 |
| `byepregen.storage.mixins.json` | 两侧 loader | chunkio + chunksave + C2ME/Architectury 兼容 13 条 |
| `byepregen.worldgen.mixins.json` | 两侧 loader | climate 搜索 + biome 缓存 + material + postprocess 7 条 |
| `byepregen.yalight.mixins.json` | 两侧 loader | YA 光 17 + 3 client |

装载清单出处：`fabric/src/main/resources/fabric.mod.json`、`neoforge/src/main/resources/META-INF/neoforge.mods.toml`。

---

## 2 已退役：不再需要恢复的条目（附理由）

| 原注册条目 | 退役理由 |
|---|---|
| `accessor.chunksave.BlendingDataAccessor` | 26.3 的 `ChunkInlineDataWriter` 改用原版 `BlendingData.Packed.CODEC`，不再需要私有访问 |
| `accessor.chunksave.StructurePieceSaveAccessor` | 同上，`ChunkStructureDataWriter` 改用 `StructureStart.createTag(context, pos)` |
| `arena.compat.voxy.VoxyWorldConversionFactoryMixin` | Voxy 无 26.3 工件；混入文件已删除。等 Voxy 更新后按新 API 重写（不是恢复旧文件） |
| `worldgen.noise.NoiseChunkCellCacheBypassMixin` | 目标类 `NoiseChunk$CacheAllInCell` 在 26.3 **不存在**（`NoiseChunk` 已无嵌套类），Arena 路径也不经过 cell-cache 生命周期 → 直接删除（工作项 W2） |
| `arena.compat.fastnoise.FastNoiseOpenCLArenaMixin` | 由 `arena.compat.c2meocl.C2MEOclArenaMixin` 取代（OpenCL 缓冲从模块侧接管） |
| `surface.compat.fastnoise.FastNoiseSurfaceOwnershipMixin` | 由 `surface.SurfaceBuildOwnershipMixin` 取代 |
| 26.2 的 `dfc.Density*` / `surface.SurfaceRules*` / `arena.Noise*` 等 30+ 条 | 随旧 DFC / ASM surface / 旧 arena 设计一并退役；其中**需要以新设计回归**的部分见 §3 |
| `worldgen.feature.LeavesBlockWorldgenTickMixin` 的旧位置 | **不是退役**：该 mixin 要恢复，但 26.3 里它属于 `worldgen` 分片（见 §3） |

---

## 3 待恢复 / 待启用条目（含工作项）

> 工作项编号对应 `port-26.3-checklist.md`。

| 待恢复条目 | 现状 | 工作项 / 做法 |
|---|---|---|
| `palette.PalettedContainerNoLithiumMixin` | **磁盘上已不存在**（26.3 分支删除） | **W7.1**：从 26.2 取回原文件（`@Overwrite acquire/release`，门 `conflictingMods="lithium"`），注册进 `byepregen.runtime.mixins.json` |
| `palette.PalettedContainerThreadingDetectorMixin` | **磁盘上已不存在** | **W7.2**：取回原文件（`@Redirect <init>` 的 `new ThreadingDetector(String)`，门 `ConfigFlag.PALETTE_LOCK`），注册进 `byepregen.runtime.mixins.json`；同时把 `config/ConfigLoader.java` 的 `palette-lock` 文案复位（W6） |
| `postprocess.compat.c2me.C2MEServerBlockTickingMixin` + `postprocess.ChunkStatusPostProcessingPreNormMixin` | 前者文件已删、后者已删 | **W7.3 / W9**：取回两个 mixin，并先恢复 `PostProcessGenerationOptimizer` 的 preNorm 入口与 `PostProcessingSorter`/`PostProcessingContext`；注册进 `byepregen.worldgen.mixins.json` |
| `worldgen.feature.LeavesBlockWorldgenTickMixin` | 文件已删 | **W7.4**：取回原文件（26.3 的 `LeavesBlock.updateShape` 签名一致），**新加门控**（26.2 是无条件生效），注册进 `byepregen.worldgen.mixins.json` |
| `feature.placement.PlacementModifierMixin` / `RandomOffsetPlacementMixin` | 已删除 | **已由新设计取代**：`FeaturePlacerMixin` + `OffsetPlacementMixin`（26.3 原版把 `RandomOffsetPlacement` 改成 `OffsetPlacement` 的三轴 record）。无需恢复 |
| `surface.SurfaceBuildOwnershipMixin` | 在磁盘、**未编译、未注册** | **W12**：若保留 surface 所有权接管（防止第三方包装替换 `buildSurface`），加入白名单与 `byepregen.worldgen.mixins.json`；否则删除 |
| `arena.compat.c2meocl.C2MEOclArenaMixin` | 在磁盘、**未编译、未注册** | **待启用**：需要一个兼容 26.3 的 `c2me-opts-accel-opencl` 工件；同时把 `worldgen/arena/**` 与 `mixin/arena/compat/**` 加入白名单并注册 |
| `mixin/accessor/worldgen/biome/MultiNoiseBiomeSourceAccessor` | 在磁盘、未编译 | **W16**（density-column biome 路径的一部分：`BiomeColumnFiller` 需要 `parameters()`） |
| `climate.ClimateParameterListColumnMixin`、`ClimateRTreeColumnMixin`、`ClimateRTreeNodeCacheMixin`、`ClimateRTreeBuildMixin` | 在磁盘、未编译；前者三个引用已被删除的 `ClimateRTreeSearchContext` depth 方法，`ClimateRTreeBuildMixin` 连 `@MixinGate` 都没有且 `@Overwrite` 签名已过时（26.3 的 `build` 变 3 参） | **W16** |
| `worldgen.biome.NoiseBasedChunkGeneratorBiomeColumnMixin` | 在磁盘、未编译；依赖的 `doCreateBiomes` 与 4 参 `createNoiseChunk` 在 26.3 已变 | **W16** |
| `worldgen/biome/{BiomeColumnTemplates,BiomeColumnFiller,BiomeColumnEvaluator,...}` 等 8 个类 | 在磁盘、未编译，其中 2 个实测编不过（引用已删的旧 DFC 类） | **W16** |

---

## 4 删除这些文件后仍需注意的引用（历史包袱）

| 引用点 | 说明 | 处理 |
|---|---|---|
| `common/src/test/java/com/moepus/byepregen/AccessRulesParityTest.java` | 原本读取已删除的归档 AT/AW | **已改写**为比对生效的 `META-INF/arena-core.cfg` 与 `byepregen.arena.accesswidener`，并加入 test 白名单（守住"两者必须同步"的不变量） |
| 本文档 §2/§3 的"26.2 注册过什么"完整对照 | 见 `port-diff-26.3-vs-26.2.md` §2.4（26.2 生效 122 条 vs 26.3 生效 79 条的逐条分类） | 参考 |

---

## 4.1 已按本节清单删除的兼容代码

- `worldgen/surface/TerraBlenderCompat.java`、`integration/tectonic/TectonicCompat.java` 及其测试：依赖的 TerraBlender/Tectonic 在 26.3 没有可用工件，且前者还引用 26.3 已删除的旧 surface 编译类型。等工件可用时按新 API 重写，而不是恢复旧文件。
- `common/src/test/java/.../api/dfc/ColumnDensityFunctionRegistryTest.java`：被测的 `ColumnDensityFunctionRegistry` 已随旧 density-column 设计删除。

## 4.2 仍在磁盘、但当前**无法编译**的文件（W16 的对象）

这些文件原样来自 26.2（`3edcc1a` 删除 → `6cce17c` 放回），引用已删除的旧 DFC 类或已变化的 26.3 API，因此**不在任何编译集内**，也不要直接启用：

`worldgen/biome/{BiomeColumnTemplates,BiomeColumnFiller,BiomeColumnEvaluator,ClimateRTreeBuildOptimizer,ClimateRTreeCacheNode,DepthClimateParameterList,DepthClimateRTree,RandomStateBiomeColumnProvider}.java`、`mixin/worldgen/biome/NoiseBasedChunkGeneratorBiomeColumnMixin.java`、`mixin/climate/{ClimateRTreeColumnMixin,ClimateRTreeBuildMixin,ClimateRTreeColumnMixin,ClimateRTreeNodeCacheMixin}.java`、`mixin/accessor/worldgen/biome/MultiNoiseBiomeSourceAccessor.java`、`worldgen/arena/ArenaOpenCLBufferImporter.java`。

实测（全量编译）其中 4 个直接报错：`BiomeColumnTemplates`（22）、`BiomeColumnFiller`（9）、`NoiseBasedChunkGeneratorBiomeColumnMixin`（4）、`ClimateRTreeColumnMixin`（4）。恢复它们 = 按 26.3 的新 DFC 与 `BiomeResolver`/`fillBiomesFromNoise` API 重写（见 `port-26.3-checklist.md` 的 W16）。

---

## 5 为什么不再保留 mono 配置

- 26.3 的模块边界已经由 **sourceSet 白名单 + 7 个分片配置**共同表达；再加一份"全体注册表"会产生第二个事实来源，而它不参与构建、因此不会被任何校验发现腐化——本轮就是这种情况（5 条悬挂注册）。
- 需要"曾经注册过什么"时：git 历史（`git show 26.2/dev:common/src/main/resources/byepregen.mixins.json`）+ 本源文件 §3 的待恢复清单 + `port-diff-26.3-vs-26.2.md` §2.4 三者已经足够。
