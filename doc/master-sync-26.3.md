# 26.3 master 同步与插值核对（2026-10-13）

基线：`26.3/dev@91e9150`；来源：`master@64a6c84`。
采用原提交 cherry-pick 并保留来源 trailer；API 冲突只按 26.3 的实际接口适配。
不恢复旧版 NoiseChunk 插值器、ConfiguredFeature 或单体 mixin 配置。

## 同步范围

| master 来源 | 26.3 处理 |
|---|---|
| `1f9a9d0` feature-plan 重构 | 移植 CompiledFeaturePlan、DiskFeaturePlan、显式 modifier capability 与多 origin 判定。保留 26.3 FeaturePlacer 的 collect-before-descend 顺序；DiskFeature/Holder provider/OffsetPlacement 按新版 API 适配。 |
| `5a13d6a` Blueprint gate | 原样移入 surface-rule compiler 开关。 |
| `9b9f388` FreeTerraForged | Arena terrain 与 climate cache 的有效入口添加冲突 gate。 |
| `67eed0d` Biolith | climate cache 让路，保留独立 DFC、Arena 和通用气候搜索；测试只覆盖实际编译的入口。 |
| `b9c5935` 客户端光照刷新 | 原算法移植；ChunkPos record accessor、section 上界按 26.3 适配。 |
| `8c5fe20`、`7dd6439`、`892fc5b`、`2d5d8ef` | 版本提交与图标移入，最终版本为 1.1.3.2。 |
| `64a6c84` final-density 与列融合 | 下面列出的等价实现已在 `74405dd` 的 26.3 原生管线中；保留而非用旧 double 实现覆盖，补入针对其语义的新版回归测试。 |

此前的 YA 写入、保存、失败发布和不透明块减光更新已在 `ee5e57e`、`935227f`、`b8f82ca`、`91e9150` 中。
树集合优化的 gate 已在 26.3 实现中。原生 biome resolver 已对六个气候根执行批量采样，
不重新引入旧版 NoiseChunk biome filler。旧版被移除的类不因 cherry-pick 冲突而复活。

## interpolator 与 DFC

依据 `minecraft-patched-26.3.0.4-beta.jar` 的 `InterpolatedFunction$Sampler` 字节码核对：

- `sampleVolume` 先采样粗网格，按 Z、X 做 float lerp，再沿 Y 递增加 `valueStep`。
  `InterpolationColumn` 使用相同网格语义和运算顺序；负坐标只有网格对齐时走快路径。
- 点查询 `sampleValue` 使用八个角点的 `lerp3`；它与批量路径本身就可能有舍入差异。
  `InterpolatedInput` 保留原版点查询和不对齐/跨步批量请求的 fallback。
- `ArenaTerrainFiller` 经 `ColumnSession` 取得逐方块 final density。
  `abs/min/max/squeeze/rangeChoice` 在插值输入之后计算，而不是对 final density 的角点结果再插值。
- `SamplingWorkspace` 持有请求自己的粗网格，Aquifer 查询替换原生缓存不会覆盖正在使用的列输入。
  同一 source 的网格复用，列坐标变化后重算系数，`FusedColumnEmitter` 已融合算术链。
- 允许代数优化与浮点误差，不要求 bitwise 相同。运行比较保留 `3e-5 * max(1, abs(expected))`
  容差，同时把 `density > 0` 判定改变设为失败，而非仅计数。正负零不视为地形判定差异。

新增 `InterpolatedFinalDensityTest` 覆盖非线性 interior 判定、嵌套/混合单元、负坐标、
不对齐与跨步请求、乱序列访问及 Aquifer 式缓存替换。继续保留已有原版差分、并发、
异常资源释放、2D memo、采样复用与字节码融合测试。

## 验证

全部使用 Gradle `--offline`：

- `:common:test :fabric:build :neoforge:build :neoforge:compileWorldgenHarnessJava`
- `:fabric:testDfcRuntime :fabric:testArenaRuntime :fabric:testFeatureRuntime :fabric:testWorldgenRuntime`
- `:neoforge:testYALightC2MERestart`：标准/raw 两套，各含两次重启；不透明块减光与客户端 render sink 探针。

DFC 实测 2,955,120 个样本；137,717 个浮点结果与原版不同，变号 0 个，
最大绝对误差 `4.76837158203125E-6`。三维度地形/生物群系比较和 Arena 保存重载通过。
单元测试 281 项通过（common 269、NeoForge 12），无失败或跳过。
feature 差分额外覆盖 fixed/layer 多 origin disk plan，以及不支持的 modifier 回退。
兼容 gate 使用单元测试模拟 mod 存在；不等同于对每个第三方模组整包的运行验证。
render sink 测试不等同于人工客户端画面检查。

日志存放于 `F:\.codex-tmp\port-26.3-*.log`，没有推送分支。
