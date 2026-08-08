# DFC `final_final_density` 专用 Column Codegen 计划

## 1. 目标和范围

本实验只优化 C2ME DFC 优化后的 `final_final_density` root，即 vanilla 的
`finalDensity + Beardifier`。第一阶段不扩展到 Aquifer 内部 noise、OreVeinifier、
其它 NoiseRouter root 或第三方 root。

Arena 固定 block `x/z`，只计算一列垂直 cell 边界：

```text
yMin, yMin + cellHeight, ..., yMax
```

随后继续使用现有 Arena page 插值生成 cell 内 block 密度。不能把所有 block Y
重新逐点计算，否则会破坏 vanilla 的 cell 插值模型并扩大计算量。

最终设计必须同时满足以下条件：

1. 独立的 column AST/codegen 路径。
2. 独立的方法缓存，不能命中普通 `singleMethods`/`multiMethods`。
3. 所有 column 可达方法带 `Column` 标记。
4. 固定 scalar `x/z`，通过 `yMin + index * cellHeight` 生成 Y。
5. 不重新构造通用 `int[] x/y/z`。
6. 节点按 column 语义生成数组循环，使算术循环适合 SuperWord。
7. Cache2D、CacheOnce、CellCache 不进入 column 生成图。
8. FlatCache、Interpolated 使用专用 source。
9. Y-independent 公共子表达式统一使用 sentinel lazy slot，按需计算且每列最多一次。

任何只满足其中一部分的实现都只能作为中间实验，不能称为最终 column codegen。

## 2. 目录和运行环境

```text
ByePregen:       E:\src\ByePregen
NeoForge source: F:\.codex-tmp\neoforge-21.1.233-sources
C2ME experiment: F:\.codex-tmp\C2ME-neoforge-updated\C2ME-fabric-patched
```

C2ME 实验修改只留在私有 worktree，不向上游 C2ME 提交。测试时保留原版 C2ME
主 jar，同时通过 `byepregen.c2meDfcExperimentJar` 加载独立 DFC jar。NeoForge
日志必须确认 JarJar 实际选择了该实验 jar。

```text
Java 25: C:\Program Files\Zulu\zulu-25
Java 21: C:\Program Files\Java\jdk-21
```

Java 25 run 必须显式传入：

```text
-PbyepregenRunJavaVersion=25
```

不能仅设置 `JAVA_HOME`；NeoForge run 使用 Gradle Java launcher。日志必须显示实际
运行时为 Java 25.x；vendor 可以是 Azul 或 Eclipse Adoptium，以启动日志为准。

预编译 native math：

```text
-Pc2me.prebuiltNativesJar=E:\src\ByePregen\libs\c2me-neoforge-opts-natives-math-mc1.21.1-0.4.0-alpha.0.116.jar
```

Java 22+ 会启用 native math，因此 native math 与 column codegen 的收益要分别说明。

## 3. Column 调用契约

```text
prepare(output, x, z, yMin, yMax, cellHeight, objectCache)
```

- `x`、`z` 是整列不变的 scalar。
- `yMin`、`yMax` 是包含两端的 block Y。
- `cellHeight > 0`。
- `yMax - yMin` 必须能被 `cellHeight` 整除。
- `output.length == (yMax - yMin) / cellHeight + 1`。
- 第 `index` 个样本的 Y 是 `yMin + index * cellHeight`。

`ColumnEvaluationContext` 只保存 output、scalar `x/z/yMin/yMax/cellHeight`、
column source 状态和 `DfcObjectCache`。禁止保存或填充等长的通用 `int[] x/y/z`
scratch 数组。

## 4. 独立 Column AST

Column specialization 在普通 DFC opt pass 完成后运行，仅为
`final_final_density` 建立独立图。它不能修改普通 scalar/multi root 的行为。

column 分析必须能明确区分以下状态；可以由 identity side table、专用节点类型和
specialization 结果表达，不要求修改共享的普通 `AstNode`：

```text
YDependency
ColumnRole
ColumnSource
```

最低限度的 `ColumnRole`：

- `INLINE`：不需要 column 级 memoization，按节点自身语义直接生成。
- `MEMOIZED_2D`：整列不变，第一次实际使用时计算，随后读取同一个 slot。
- `SOURCE`：从 FlatCache/Interpolated 等 column source 读取。
- `FALLBACK`：未知语义，保守处理或拒绝生成专用 column root。

Y dependency 规则：

- Constant、X/Z coordinate、EndIslands：Y-independent。
- Y coordinate、YClampedGradient、WeirdScaledSampler、FindTopSurface、
  Beardifier 和未知 Delegate：Y-dependent。
- unary/binary/range/noise/spline：由全部 children 传播。
- Spline 的 location function 和所有 value 分支都参与分析。
- Cache2D 在 normalization 阶段把其 child 标记为 2D；该事实来自 cache kind，
  不由 delegate 决定，随后 wrapper 必须删除。
- FlatCache 的 2D source 语义来自 cache kind，不由 delegate 决定。
- Interpolated 是 Y-dependent source terminal，但不能调用 delegate。
- CacheOnce wrapper 删除后重新分析 delegate。

Cache normalization 后必须建立 canonical CSE DAG。identity memoization 用于保留已有共享，
但还必须把语义等价、对象 identity 不同的纯 AST 子树 canonicalize 到同一节点。CSE key
必须覆盖节点类型和全部语义参数；FlatCache、Interpolated、Delegate 等带对象/source
identity 的节点不得仅凭结构宽松合并。

canonical DAG 建立后统计每个节点的 parent 和 `refCount`。Y-independent 节点满足以下任一
条件时标记为 `MEMOIZED_2D`：

1. 至少一个 parent 是 Y-dependent，或该节点直接作为 column root；这表示它会跨 Y lane
   被动态重复执行。
2. canonical DAG 中 `refCount > 1`；这表示它是实际公共子表达式。

只有一个 Y-independent parent 且 `refCount == 1` 的 Y-independent 节点保持 `INLINE`，
由更高的 2D 父树统一 materialize。这样两个 Cache2D 位于同一个 2D 父树内部时，只给父树
分配一个 slot，内部不保留 cache。若某个内部 2D 节点确实被多次引用，则它作为真正 CSE
拥有自己的 lazy slot。

## 5. Cache 和 Source 语义

Column 图必须执行以下 rewrite：

1. `cache_2d`：记录其 child 的 2D 语义后删除 wrapper；它不直接决定 slot，也绝不进入
   最终 codegen。slot 只由 normalization 后的 CSE/Y-dependency/refCount 分析决定。
2. `cache_once`：wrapper 从图中删除；重新分析 child。
3. `cache_all_in_cell`：wrapper 从图中删除；Arena column context 不启用 CellCache。
4. `flat_cache`：不调用 delegate，直接读取 eager-prefilled cache 值，作为专用
   column source；它可以被上层 `MEMOIZED_2D` 子图按需读取一次。
5. `interpolated`：不调用 delegate 或通用 cache API，直接读取绑定的 cell-boundary
   数组。
6. 未知 CacheLike：保守拒绝专用生成或进入明确 fallback，不能静默复用普通 cache。

specialization 和 codegen 前都必须做完整的 exact-class 白名单检查。未知节点、已知但没有
专用 emitter 的节点、普通 Delegate 或 AST 子类返回显式 `Rejected`；保留已经生成的普通
single/multi root，并令 column method 为 `null`。拒绝必须发生在 ASM 写入前，不能让未知
复合节点先在 specialization 中抛异常。

验收必须检查 `evalColumn` 的完整可达图，而不是只看 class 中是否存在普通 cache 方法。
Column 可达图中不得出现 Cache2D、CacheOnce、CellCache 或 interpolated delegate。

## 6. 独立 Column Codegen

Column codegen 必须拥有独立于普通 codegen 的状态：

```text
columnSingleMethods
columnMethods
columnFields / columnSources
column method-name scope
```

它不得调用普通的：

```text
newSingleMethod(...)
newSingleMethodUnoptimized(...)
newMultiMethod(...)
newMultiMethodUnoptimized(...)
singleMethods
multiMethods
```

Column root 和所有可达 helper 都必须有可辨识名称，例如：

```text
evalColumn_15_final_final_density
method_401_Column_AddNode
method_402_Column_MinShortNode
method_403_Column_InterpolatedSource
method_404_Column_BeardifierNode
```

禁止仅生成一个 `evalColumnMulti_*` trampoline，然后立即进入普通或通用命名的
`method_*` 图。这不算独立 column codegen。

建议的 column helper 逻辑参数是：

```text
double[] output
int x
int z
int yMin
int cellHeight
EvalType.COLUMN
DfcObjectCache
column source/lazy CSE state
```

算术节点使用与 multi emitter 类似的数组结果模型，但由 column emitter 独立生成。
标准循环形状为：

```java
for (int index = 0; index < output.length; ++index) {
    int y = yMin + index * cellHeight;
    output[index] = ...;
}
```

X/Z coordinate 直接使用 scalar；Y coordinate 在循环中由 index 计算。不得先填充
坐标数组再调用普通 `genMulti`。

为了让 C2 SuperWord 有机会向量化，纯算术循环应满足：

- canonical counted loop；
- 连续 `double[]` load/store；
- 无每元素虚调用、interface call 或 scalar helper call；
- 循环边界只依赖 `output.length`；
- loop-invariant source、对象和常量在循环外加载；`MEMOIZED_2D` 的 miss 判断在对应
  array helper 进入 counted loop 前执行，或仅存在于原本就需要分支的 ColumnPoint 路径；
- 不为四路 SIMD 手工固定宽度，由 C2 决定 vector width 和 remainder loop。

### JDK 21 与 JDK 22+ 的 SuperWord 差异

本方案不能只根据 Java 源码看起来像 counted loop，就假设 Java 21 和 Java 25 会生成
相同机器码。至少有两个进入 JDK 22 开发线、但 JDK 21 不具备的关键修复：

#### JDK-8308606：不需要时删除 alignment checks

Patch：<https://github.com/openjdk/jdk/commit/886ac1c261a1b7e91e3981e32810c405a0d90329>

标题是 `C2 SuperWord: remove alignment checks when not required`。旧 SuperWord 即使在
硬件允许 unaligned vector load/store 时，也会要求同一 memory slice 的候选访问满足
额外的 modulo-vector-width 对齐关系。这会拒绝多数组、不同常量 offset 或不同 invariant
base 的本来可以安全向量化的循环。

修复后的行为是：

- 当硬件不要求严格 vector alignment 时，不再用这些 alignment 条件提前拒绝 pack；
- 先建立 adjacent pair，再在 `combine_packs` 后检查所有 lane 是否存在真实的数据依赖；
- 有依赖的 pack 单独删除，允许其它 pack 继续部分向量化；
- read-forward、misaligned load 和多个不同数组的连续访问更容易通过 SuperWord。

对 column codegen 的含义：Java 25 更可能接受同时读取多个临时 `double[]` 并写入
`output[index]` 的算术循环；Java 21 可能因为不同数组 base/alignment 关系直接放弃。
因此应尽量让所有数组使用同一 canonical index 和相同 element stride，不应手工展开、
制造 `index + 1`/`index - 1`，也不应依赖 Java 25 的宽松 alignment 判定来证明
Java 21 没有回退。

#### JDK-8310886：放宽 loop-invariant control 的 isomorphic 判定

Patch：<https://github.com/openjdk/jdk/commit/dd9eab15c832c20e65681c21c5f91df11f4cddf9>

标题是 `C2 SuperWord: Two nodes should be isomorphic if they are loop invariant but pinned at
different nodes outside the loop`。Java 21 的 `SuperWord::isomorphic` 对 control input
不同的候选节点非常保守，只接受少数 range-check/MulAdd 特例。即使两个 control 都在
循环外且对循环 invariant，也可能无法把对应的 scalar nodes 组成 vector pack。

修复后，只要两个不同的 control 都是 loop invariant，就可以继续认为相同 opcode、
arity 和 element type 的节点 isomorphic。这个变化会影响被不同 null check、range
check、cast 或其它循环外 guard 固定的数组 load/算术节点。

对 column codegen 的含义：

- 数组、source、cache 对象和 memoized 2D 值必须尽量在 loop 外解析；
- loop 内避免每 lane 经过不同 control、虚调用、interface call 或重新执行 range check；
- Java 25 可能把由不同循环外 guard 固定的节点打包，Java 21 则可能仍然拒绝；
- 为兼顾 Java 21，应尽量让同一算术 loop 的 load/store 共享相同的显式控制形状，而不是
  仅依赖 JDK-8310886 的放宽。

这两个 patch 解释了为什么相同 `evalMulti` 风格的 bytecode 可能在 Java 22+ 自动
向量化、在 Java 21 却保持 scalar。实现策略是先在 Java 25 验证上限，再检查 Java 21
回退；不能通过重新构造通用坐标数组或直接调用普通 `genMulti` 来冒充专用 column
codegen，也不能仅凭 JFR 中 AVX 样本为零或非零判断某个具体 loop 是否被向量化。

RangeChoice、Mul 短路及其它分支节点不能为了向量化改变语义。无法安全向量化的节点
允许保留专用 column scalar/branch loop，但不能退回普通 single/multi 方法缓存。

## 7. Y-independent CSE 与 Lazy Materialization

最终方案只有两种 materialization：

```text
INLINE
MEMOIZED_2D
```

不再区分 entry eager、conditional invariant 或最近支配点等多套策略。所有
`MEMOIZED_2D` 节点都使用同一种 column-local lazy slot：

```java
double value = invariantValues[slot];
if (Double.doubleToRawLongBits(value) == CACHE_MISS_NAN_BITS) {
    value = computeSubtree(x, minY, z);
    invariantValues[slot] = value;
}
return value;
```

`CACHE_MISS_NAN_BITS` 沿用 C2ME 的 `0x7ffddb972d486a4fL`。每次准备 column 时只需：

```java
Arrays.fill(invariantValues, 0, slotCount,
        Double.longBitsToDouble(CACHE_MISS_NAN_BITS));
```

miss 检查必须使用 `Double.doubleToRawLongBits`；禁止使用 `Double.isNaN` 或
`Double.doubleToLongBits`。其它 NaN payload 都是合法的已计算结果，只有 raw bits 完全
等于 sentinel 才表示未初始化。该约定与 C2ME 现有 cache miss 约定相同；所有 cache/source
miss 都必须在进入普通算术前被消费，不能让 sentinel 参与表达式传播。

column entry 不再遍历所有 slot 调用 delegate，也不再生成连续的 `setInvariant` eager
初始化。无条件 CSE 在其 array helper 进入 counted loop 前第一次解析；只位于 Mul、
MinShort、MaxShort、RangeChoice 等短路路径中的 CSE，在第一个实际命中的 ColumnPoint
路径中初始化。若整个 column 从未进入该分支，昂贵子树完全不计算；若进入多次，也只计算
一次。

lazy miss 分支不得放进原本可向量化的公共算术 counted loop。array emitter 应先解析一次
slot，再让循环只消费 scalar/连续数组；短路节点本来就使用 scalar branch loop，其 lazy
判断只影响该节点，不应阻断其它独立算术循环的 SuperWord。

## 8. Arena 和 Aquifer 边界

`NoiseChunkArenaMixin` 每个 block X/Z 列只物化 `cellCountY + 1` 个边界值。
Arena page 保留 vanilla 4-step 插值顺序；最终 block density 由 page 插值得到。

Arena material fast path 将预计算的最终密度直接传给：

```java
aquifer.computeSubstance(noiseChunk, precomputedFinalDensity)
```

因此 Aquifer 不应再次调用 `final_final_density`。Aquifer 内部 barrier、floodedness、
spread、lava noise 暂不复用，作为后续独立优化范围。

Arena 在进入 Aquifer 前使用严格的 preliminary-surface envelope high-air shortcut。对固定
block X/Z，Aquifer 水平候选只来自相邻 2x2 个 16-block 网格；候选中心的随机偏移为
`nextInt(10)`，经 `preliminarySurfaceLevel` 的 quart 对齐后，每轴每格只可能是
`+0/+4/+8`。因此每个 `(gx,gz)` envelope 覆盖 6x6 个 quart-aligned surface 样本：

```text
fluidUpperBound = max(actual global FluidPicker levels, 36 preliminarySurfaceLevel samples)
```

只有实际 picker 仍是 `NoiseBasedChunkGenerator` 自身生成的 lambda 时才启用 shortcut；
通过 picker 在极低/极高 Y 返回的两个实际 `FluidStatus` 取得 global 上界，因此模组仅修改
lava level 常量时不需要在 ByePregen 复制该常量。picker 被替换时，上界设为
`Integer.MAX_VALUE` 并禁用 shortcut。

任意候选 FluidStatus 的 fluid level 都不超过该上界。当 final density `<= 0` 且
`blockY - 5 >= fluidUpperBound` 时，所有候选在该 Y 都是 AIR，pair pressure 严格非正，
最终结果必然是 AIR。`NoiseBasedAquiferSurfaceMixin` 在 `computeSubstance` 入口返回 AIR，
不生成候选、不执行 `refreshDistPosIdx`，也不采样 Aquifer 内部 noise。若任一 surface 样本为
`Integer.MAX_VALUE`，该 envelope 禁用 shortcut。

一个 chunk 最多缓存 4 个 envelope，四者合计最多涉及 81 个唯一 quart key。Arena 在每列
开始时把上界写入 `NoiseChunk`，并在 `finally` 中清除；mixin 只在该状态有效时命中。因此
shortcut 不依赖 DFC column 是否可用，普通 material fallback 同样受益，也不会通过静态变量
或 `ThreadLocal` 泄露到其它 chunk。实现完全位于 ByePregen，不依赖 C2ME API。

## 9. 验证要求

功能验证：

1. radius-16 smoke，并在实验 DFC 工程中启用 boundary raw-bit verifier。
2. 每个 `yMin + index * cellHeight` 与原 scalar root 做 raw-bit 比较。
3. 覆盖负/非零 minY、所有 page、inCellX/inCellZ、Beardifier、blending fallback、
   Aquifer water/lava 和 ore。
4. 对 Cache2D、CacheOnce、CellCache、Interpolated delegate 增加零调用计数断言。
5. 条件分支整列不命中时，对应 `MEMOIZED_2D` delegate 调用次数必须为 0；命中一个或
   多个 Y 时调用次数必须恰好为 1。
6. 同一 canonical 2D CSE 的多个引用必须共享一个 slot；两个 Cache2D 位于同一个单引用
   2D 父树下时，只允许父树拥有 slot。
7. 覆盖普通 finite 值、canonical NaN 和多个非 sentinel NaN payload；只有
   `0x7ffddb972d486a4fL` 表示 miss。

Class dump 验证：

1. 删除或确认覆盖 `run/test-worldgen/cache/c2me-dfc/classes` 中的旧 dump。
2. `evalColumn` 完整可达图的所有生成 helper 都带 `Column` 标记。
3. 不存在普通 `evalMulti`、普通 `method_*` 或普通 method cache 的可达边。
4. 不存在 `int[] x/y/z` context getter、填充循环或通用 multi descriptor。
5. 算术节点含 canonical 连续数组循环。
6. column entry 不得逐 slot 调用 scalar delegate 或生成 `setInvariant` eager 序列；只允许
   初始化 sentinel slot storage 后调用 Column root。
7. lazy slot helper 必须先做 raw-bit sentinel 判断，miss 时调用 delegate 并写回，hit 时
   直接返回；不得调用任何 Cache2D wrapper/API。

性能/JIT 验证：

1. 先用 Java 25 确认潜力，再用 Java 21 检查回退。
2. 没有 hsdis、PrintCompilation/PrintAssembly 或等价证据时，不声称已经生成 AVX。
3. JDK-8308606/JDK-8310886 改变了 SuperWord 判定，Java 21/25 必须分别测量。

## 10. 性能测量口径

主指标是 Chunky 最终输出：

```text
[Chunky] Task finished ... Total time
```

同时报告 chunks/s。Gradle wall time、JFR duration 只作诊断，不能代替 Chunky 时间。

on/off 必须固定 seed、radius、维度、mods、worker、YA Light、GC 配置和 native math，
并按 off/on 与 on/off 两种顺序做 counter-run。

本节已有 benchmark/JFR 来自修订前的 entry-eager invariant 实现，只能作为独立 Column
codegen 的历史基线。切换到 canonical CSE + `MEMOIZED_2D` sentinel lazy slot 后，必须重新
执行 smoke、class audit、counter-run 和 JFR；旧数字不能直接代表最终方案收益。

以下结果全部不得作为最终方案结论：

- 旧 block-Y context 或完整 block-Y column 的测试；
- scalar point helper column loop 的测试；
- 构造 `int[] x/y/z` 后复用普通 `genMulti` 的测试；
- 仅入口带 `evalColumnMulti` 标记、子图仍是普通 `method_*` 的测试；
- Java 版本未由运行日志确认的测试。

因此最近 Java 25 radius-1000 的 `31 s on / 34 s off` 只记录为“通用 genMulti
复用捷径”的实验结果，不能代表本计划的独立 column codegen 收益，也不进入最终收益
汇总。

### 独立 Column Codegen 的 Java 25 初步结果

固定条件：Zulu Java 25.0.1、radius 1000、overworld、16129 chunks、相同 seed/mods、
YA Light 关闭、相同 DFC 实验 jar，仅切换 `byepregen.enableDfcColumn`。按 off/on 与
on/off 两种顺序各跑一次：

| 顺序 | column off | column on |
| --- | ---: | ---: |
| off -> on | 49 s | 42 s |
| on -> off | 50 s | 44 s |
| 均值 | 49.5 s | 43.0 s |

以 Chunky `Task finished ... Total time` 为准，平均时间减少 13.1%；等价平均吞吐从
约 325.8 chunks/s 提升到约 375.1 chunks/s，提升 15.1%。绝对时间受本机当时负载
影响，明显慢于此前实验，但两种运行顺序的相对收益一致。

对应 JFR 文件名以 `dedicated-column-java25-{on,off}-r1000` 及其 `repeat` 版本为准。
该结果证明当前独立 column 路径有端到端正收益，但仍不等同于已通过汇编证明 AVX。

### Tectonic 3.0.25 的 Java 25 结果

数据包为 `tectonic-datapack-3.0.25.zip`。Tectonic 的 `final_final_density` 图额外覆盖了
`SplineAstNode`、`GenericShiftedNoiseNode` 和 `DFTWeirdScaledSamplerNode`；三者均使用
专用 ColumnPoint/ColumnSpline emitter，不调用原节点的 `apply`，也不回退到普通
single/multi codegen。

radius-16 smoke 启用 `byepregen.verifyDfcColumn=true`，完成 9 chunks，所有 cell-boundary
样本均通过 raw-bit 对照。随后固定 Eclipse Adoptium Java 25.0.3、radius 1000、overworld、
16129 chunks、相同 seed/mods/数据包、YA Light 关闭和相同 DFC jar，只切换
`byepregen.enableDfcColumn`，按两种顺序各跑一次：

| 顺序 | column off | column on |
| --- | ---: | ---: |
| off -> on | 57 s | 52 s |
| on -> off | 59 s | 51 s |
| 均值 | 58.0 s | 51.5 s |

以 Chunky `Task finished ... Total time` 为准，平均时间减少 11.2%；等价平均吞吐从
约 278.1 chunks/s 提升到约 313.2 chunks/s，提升 12.6%。对应 JFR 文件名以
`dedicated-column-tectonic-java25-{on,off}-r1000` 及其 `repeat` 版本为准。

JFR 的 `c2me-worker-*` CPU 样本显示：

- off 的 `evalSingle_15_final_final_density` 栈占 16.4%/18.0%，on 的专用 Column 栈
  占 4.0%/3.9%；
- 全部 DFC 栈从 33.5%/33.4% 降至 21.0%/20.3%；
- Aquifer 从 22.5%/23.0% 上升至 27.4%/28.5%，这是优化后暴露出的主要剩余热点；
- Beardifier 从 6.6%/7.7% 降至 1.0%/1.0%，因为最终密度不再按 block 重复求值；
- 专用 Column 栈只占采样分配约 0.01%，四次记录的总采样分配量接近，未发现新增
  分配压力。GC 次数和 GC CPU 同时下降，但 JFR 记录包含完整启动及 IO 阶段，不能把
  该变化全部归因于 Column codegen。

### 2026-08-08 Pre-Lazy-CSE Tectonic 基线

在实现 canonical CSE + `MEMOIZED_2D` lazy slot 前，对当前 entry-eager 实现重新执行
Java 25 radius-1000 counter-run。固定条件为 Eclipse Adoptium Java 25.0.3、overworld、
16129 chunks、Tectonic 3.0.25、相同 seed/mods、YA Light 关闭、raw-bit verify 关闭，以及
同一个 295040-byte DFC 实验 jar。启动前没有残留 Java worldgen/Gradle 进程。运行顺序为
`off-a -> on-a -> on-b -> off-b`：

| 运行 | column off | column on |
| --- | ---: | ---: |
| A | 36 s | 32 s |
| B（反向顺序） | 37 s | 32 s |
| 均值 | 36.5 s | 32.0 s |

以 Chunky `Task finished ... Total time` 为准，column on 平均时间减少 12.3%；等价平均
吞吐从约 441.9 chunks/s 提升到约 504.0 chunks/s，提升 14.1%。相比上一组 58.0/51.5 s，
本次绝对时间明显缩短，而相对收益 12.3% 与上一组 11.2% 接近。该组数据作为修订方案实施
前的直接性能基线，不代表 lazy CSE 已实现。

四份 JFR：

```text
byepregen-worldgen-radius1000-pre-lazy-cse-tectonic-java25-off-a.jfr
byepregen-worldgen-radius1000-pre-lazy-cse-tectonic-java25-on-a.jfr
byepregen-worldgen-radius1000-pre-lazy-cse-tectonic-java25-on-b.jfr
byepregen-worldgen-radius1000-pre-lazy-cse-tectonic-java25-off-b.jfr
```

修订前 entry-eager baseline 在修复 AST identity 和最高 invariant 选择后，Tectonic
class dump 从
`evalColumn_15_final_final_density` 做可达图遍历，共触达 117 个生成方法：15 个
`Column_`、97 个 `ColumnPoint_`、4 个 `ColumnSpline_` 和入口自身。entry 的
`setInvariant` 从修复前的 54 次降为 4 次；四个 invariant delegate 的完整子图都不读取
其它 invariant。可达图中没有普通 `method_*`/`evalMulti`，也没有 Cache2D、CacheOnce、
CellCache 或通用 `int[] x/y/z` multi descriptor。
同一个 `DfcCompiled_0` 中仍存在其它 NoiseRouter root 的普通方法，这些不可达方法不表示
column 路径发生了回退。

## 11. 当前实现状态

实验 worktree 已按第 4、5、7 节完成 Cache normalization、canonical CSE 和统一 lazy
materialization：

1. `cache_2d` 只把重写后的 child 记录为 forced 2D，随后删除 wrapper。它本身不生成
   方法或 slot；`cache_once`、`cache_all_in_cell` 同样删除 wrapper。
2. FlatCache 和 Interpolated 被转换为带 object identity 的 `ColumnCacheNode` source，
   不参与结构合并；普通 Delegate、未知 CacheLike 和 Root 也不参与 canonicalization。
3. 纯 AST 先递归重建 child，再以严格 `equals/hashCode` 做 hash-consing。已有 identity DAG
   保持共享，结构相同但 identity 不同的纯子树也会合并为同一个 canonical node。
4. canonical DAG 上分别统计 direct parent/refCount，并传播 Y dependency。Y-independent
   node 在跨入 Y-dependent parent/root，或 `refCount > 1` 时成为 `MEMOIZED_2D`；只有被
   Cache2D 强制标记时，Constant/Coordinate 才允许单独占 slot。
5. 每个 canonical `MEMOIZED_2D` node 只分配一个 slot。两个 Cache2D child 被同一个更高
   2D parent 覆盖时，只 materialize parent；同一 2D 子树在条件和非条件路径复用时，
   两条路径引用同一个 wrapper 和 slot。
6. `ColumnConstantNode` 和 entry eager invariant 序列已经删除。column entry 只调用
   `prepareMemoizedCount`，把有效 slot 范围初始化为
   `Double.longBitsToDouble(0x7ffddb972d486a4fL)`，随后进入 Column root。
7. array emitter 遇到 `MEMOIZED_2D` 时先在 loop 外解析一次再 `Arrays.fill`；原本就在
   Mul/MinShort/MaxShort/RangeChoice/Spline 等条件性 ColumnPoint 路径中的节点，则在第一次
   实际命中时解析。两者使用相同的 lazy helper。
8. lazy helper 使用 `Double.doubleToRawLongBits` 与完整 sentinel bits 比较；miss 才计算
   delegate 并写回。`Double.isNaN`、`Double.doubleToLongBits` 和普通 Cache2D API 均不参与。

相关实现集中在：

```text
ColumnAstRewriter.java
ColumnCse.java
ColumnAstSpecializer.java
ColumnMemoized2DNode.java
ColumnEvaluationContext.java
ColumnBytecodeGen.java
ColumnPointBytecodeGen.java
DfcObjectCache.java
```

`SplineAstNode.mapChildren` 用于在不丢失 spline child AST identity/语义的前提下重建
specialized graph。`ColumnSupport.prepare` 仍在 specialization 和 ASM 写入前执行 exact-class
preflight；未知节点不会产生半个 Column 图，而是受控禁用该 root 的专用 column method。

### 2026-08-08 Lazy-CSE 验收

DFC 单元测试共 16 项通过：`ColumnAstSpecializerTest` 7 项、
`ColumnEvaluationContextTest` 3 项、`ColumnSupportTest` 6 项。覆盖已有 DAG identity、
严格结构 CSE、最高 2D parent、条件 lazy slot、条件/非条件共享、Cache2D parent 吸收、
等价 Cache2D delegate 合并、sentinel reset、非 sentinel NaN payload 和 object-cache bridge。

Java 25.0.3 和 Java 21.0.3 + Tectonic 3.0.25 的 radius-16 raw-bit smoke 均已通过，
各完成 9 chunks；日志确认 JarJar 实际选择当前 `0.4.0-alpha.0.116-dirty` 实验 DFC jar，
没有 boundary mismatch。

最新 `DfcCompiled_0.class` 从 `evalColumn_15_final_final_density` 做完整可达图遍历，结果为：

```text
73 methods total
15 Column array helpers
53 ColumnPoint helpers
4 ColumnSpline helpers
1 evalColumn entry
3 MEMOIZED_2D lazy helpers / slots
```

所有可达生成方法均带 Column 标记；没有普通 `method_*`、`evalMulti`、Cache2D、CacheOnce、
CellCache、`setInvariant` 或通用 `double[]/int[] x/y/z` multi descriptor。entry 只执行
`prepareMemoizedCount(3)` 后调用 Column root。三个 lazy helper 均先调用
`c2me$getColumnMemoized`，做 raw-bit sentinel 比较，并仅在 miss 分支调用 delegate 和
`c2me$setColumnMemoized`。

这里的 forced 2D 属性是 Cache2D contract 对 child 的语义声明，而不是从 child 类型猜测。
因此 canonicalization 后，相同纯子树的所有引用会共享该 2D 属性。这要求产生 Cache2D 的
上游遵守其 delegate 与 Y 无关的契约；若第三方错误地用 Cache2D 包装真实 Y-dependent
函数，本方案会像 Cache2D 本身一样依赖一个无效 hint，不额外为这种非法图保留 wrapper。

### 2026-08-08 Lazy-CSE Tectonic Java 25 结果

固定条件为 Eclipse Adoptium Java 25.0.3、radius 1000、overworld、16129 chunks、
Tectonic 3.0.25、相同 seed/mods、YA Light 关闭、raw-bit verify 关闭和同一个实验 DFC jar。
运行顺序为 `off-a -> on-a -> on-b -> off-b`：

| 运行 | column off | column on |
| --- | ---: | ---: |
| A | 41 s | 37 s |
| B（反向顺序） | 43 s | 38 s |
| 均值 | 42.0 s | 37.5 s |

以 Chunky `Task finished ... Total time` 为准，平均时间减少 10.7%；等价平均吞吐从约
384.0 chunks/s 提升到约 430.1 chunks/s，提升 12.0%。本轮机器绝对时间慢于第 10 节
pre-lazy 的 36.5/32.0 s，不能直接用绝对秒数判断 lazy CSE 的增减；可比的相对收益从
12.3% 变为 10.7%，差异为 1.6 个百分点，当前四次运行不足以证明这是稳定回退。

四份 JFR：

```text
byepregen-worldgen-radius1000-lazy-cse-tectonic-java25-off-a.jfr
byepregen-worldgen-radius1000-lazy-cse-tectonic-java25-on-a.jfr
byepregen-worldgen-radius1000-lazy-cse-tectonic-java25-on-b.jfr
byepregen-worldgen-radius1000-lazy-cse-tectonic-java25-off-b.jfr
```

`c2me-worker-*` CPU 样本显示：

- `final_final_density` 从 off 的 18.0%/18.7% 降到 on 的 2.9%/3.3%；
- 全部 DFC 栈从 33.9%/33.7% 降到 19.9%/20.7%；
- Aquifer 从 22.7%/22.5% 上升到 28.2%/28.8%，仍是优化后主要热点；
- Beardifier 从 7.6%/7.5% 降到 0.9%/1.2%；
- 含 `ColumnMemoized2DNode` 的样本占全部 worker 样本 0.88%/0.74%，其中 127/128 个
  可归因样本位于首次 miss 的实际 noise delegate 计算，helper 自身只出现 1 个 leaf
  样本；没有证据表明 sentinel hit check 成为主要热点。

四次 worker sampled allocation 总量为 94.0-94.6 GB，彼此接近；专用 FinalColumn 栈只占
0.00-0.01%。GC pause 总量为 805-818 ms，GC count 为 141-154，没有观察到 lazy slot
带来的分配或 GC 回退。这些是 JFR sampled allocation 和整次进程记录，只用于排除明显
回退，不能解释为精确分配字节数。

### 2026-08-08 Aquifer Surface Envelope

Java 25.0.3、Tectonic 3.0.25、radius 1000、16129 chunks、YA Light 关闭，使用同一个实验
DFC jar 的相邻 off/on：

```text
off: 40 s
on:  36 s
```

按 Chunky 最终时间计算端到端改善约 10%。`c2me-worker-*` JFR 中：

- `refreshDistPosIdx` 从 2489/15065 samples（16.52%）降到 365/11932（3.06%），绝对样本
  减少约 85%；
- Aquifer 总栈从 3798 samples 降到 846，绝对样本减少约 78%；
- preliminary-surface 相关样本从 220 增到 275，envelope 新增成本远小于省掉的候选搜索；
- Noise generation inclusive samples 从 6713 降到 3630；完整 workload 的 worker sample
  总数和运行时也下降，因此百分比变化需结合绝对样本读取。

正确性验证使用 `byepregen.verifyAquiferSurfaceShortcut=true`：原版 radius 16、Tectonic
radius 32 和 Tectonic radius 128 均通过；最大一次覆盖 289 chunks，逐个 shortcut 命中点
均由原始 Aquifer 确认为同一个 AIR BlockState。

## 12. 后续验证与范围

1. 执行 Java 21 radius-1000 性能 counter-run；Java 21 raw-bit 正确不代表性能无回退。
2. 增加可重复运行的自动化 class 可达图检查，以及运行期条件分支零次/一次 delegate
   调用断言，减少依赖人工 `javap` 审计。
3. 用汇编或 C2 IR 证明具体算术 loop 是否生成 AVX；JFR 热点下降不能单独证明 SIMD。
4. 对其它第三方数据包出现的新 AST 类型增加显式专用 emitter 或受控拒绝路径。
5. 在 surface-envelope 后重新分析剩余 Aquifer 热点；只有地下候选搜索仍足够热时，才设计
   完整 Aquifer column evaluator。Aquifer 内部 noise 不混入 `final_final_density` codegen。
