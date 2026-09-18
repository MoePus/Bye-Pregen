# 26.3 世界生成迁移工作边界

用一个doc记录工作边界，但是仅简单记录取舍，不记录过去舍弃的设计。仅记录迁移过程中重大的bug，不记录小bug。记录工作边界。哪些完成那些未完成，不详细记录，而是依赖仓库代码作为真值。把这个表述直接写到doc里面

## 范围

目标版本为 Minecraft 26.3。工作范围包括 Arena 的方块存储、terrain 批量填充、必要高度图和保存加载，DFC 的 float 密度编译，Features 的放置调度、placement、方块谓词、Disk 和树木集合优化，以及 Climate R-tree 查询、表面 biome 缓存、材质规则编译和生成后处理。

YA 光照、GC-free 区块保存与原始 I/O 已迁移完成，Fabric 与 NeoForge 两侧都有运行验证。专用 biome 列填充不迁移：26.3 的 biome 步骤在 `ChunkGenerator.doCreateBiomes` 里用 `RandomState.createClimateSampler` + `BiomeSource.createResolverForChunk` 完成（后者本身就是按块的六条气候根预采样），全程不再创建 `NoiseChunk`，`LevelChunkSection.fillBiomesFromNoise` 也已覆盖旧实现手写的调色板填充；对应 26.2 文件保留在盘上但未进编译集。第三方模组兼容（Tectonic/FastNoise/Voxy/TerraBlender）与 surface 测试/基准 harness 不属于当前范围。未迁移模块通过构建和 mixin 注册边界停用，恢复位置标注 TODO。

已经回到 26.3 的 26.2 能力（此前被停用或删掉）：palette 锁移除（`worldgen.misc.palette-lock` 再次生效，Lithium 存在时由其接管）、世界生成期的树叶邻居更新跳过（新开关 `worldgen.misc.leaf-worldgen-tick`）、后处理排序+去重、`MatchingBlocksPredicate` 的单/双方块展开、C2ME 的 chunk-status 预归一化钩子、YA 光的手写 halo/任务队列容器、DFC 的列分析、优化 pass 与 spline 专用字节码生成以及 surface 条件内联（`worldgen/material/ConditionPlan` + `MaterialBindings`，7 类条件进生成代码、bandlands 直连 `context.getBand`、无可达 CEILING 检查时用 `@ModifyConstant` 缩短列扫描）。

生物群系对拍已补齐：arena harness 的 chunk 快照把每个 section 的 4×4×4 biome palette 一并哈希，`testArenaRuntime` 增比 `arenaDfc`（密度编译开启）与 vanilla 基线，三个维度的方块、高度图、后处理队列与 biome 调色板都必须逐字节相同；快照里只出现单一 biome 会直接判失败，避免这项对拍退化为空比较。

## 取舍

- 保留原版 terrain 的任务生命周期、section 锁、Aquifer、表面、雕刻和状态提交，只接管密度消费与 Arena 填充。
- DFC 保留原版编译入口与 cache ID，用 26.2 的 AST、Core/Spline/Core 优化流水线、Y 依赖分析及列生成器适配 float。适用于 terrain、气候和 Aquifer 的 sampler，沿用独立于 Arena 存储的 `worldgen.arena.density-column-compiler` 开关。
- DFC 使用 float 密度与原版批量噪声入口，底层噪声坐标保留 double 精度。接受代数等价导致的有界舍入和临界方块差异，不无条件消除奇点。
- 插值在 DFC 请求内部准备粗网格并按列展开，复用已有 Z/X 系数和增量 Y 算法；cell 只定义插值区间，不恢复旧 interpolator 状态机或向 Arena 共享插值状态。非线性运算留在原有插值边界一侧。
- 每次请求独立持有粗网格、memo 和 scratch，保证嵌套调用以及列消费期间的 Aquifer 查询不会覆盖它们。未知函数和不支持的采样几何按函数节点保留原版批量路径，已知父节点和兄弟节点继续编译。
- 预采样来源及顺序在编译期确定，请求期只执行列表；原版纯函数包装器沿子图传播纯度和轴依赖，未知或依赖上下文的来源保留原版求值顺序。
- `IntervalSelect` 直接编译为列分支：选择器每列求值一次，按同一分支的连续 Y 段执行子图，共享父请求的 memo 与 scratch；未知来源仍保留原版批量求值顺序。
- 样条沿用 26.2 的参数化简和专用方法生成，参数绑定在编译实例中。已知纯函数保留列范围剪枝与懒求值；不透明 delegate 保持必要的原版请求范围和调用语义。
- Arena 对支持列接口的 sampler 打开一次 session，按原版 Z→X 顺序取得最终密度列并立即消费，省去最终整块密度缓冲。普通 sampler 保留原版 volume 接口；不恢复 NoiseChunk 的 token/插值器绑定。
- Arena page 是存储布局，不限制插值 cell 尺寸。支持范围外的采样走原版批量路径。
- Arena 快速填充仅接管全新空气区块中的连续整幅 XZ 体积，支持裁剪的 Y 范围；已有内容或其他体积形状走原版。按原版 z→x→y 降序调用 Aquifer，在写入时累计四种 section 计数与两张初始高度图，保留表面和雕刻的后续增量更新。
- Terrain 预绑定 section 和 4 层 page，缓存常用方块的 raw ID、元数据和页 palette 索引；每个位置只写一次。palette 溢出沿用整节 dense 回退，所有 page 随即使用同一 dense 存储。缓存限于本次填充，不跨数据包标签或区块生命周期复用。
- Features 保留 RC2 的 modifier 批量收集顺序、随机数消耗、嵌套放置和 nullable provider 行为；未知 modifier 使用原版回调接口。
- 树木保留开放寻址集合优化，允许存活叶子 1..6 的 distance 分配、相关写入和叶子距离更新事件与原版不同；树形和原版外观不变，运行对照仍校验 distance=7 的衰败分界、方块位置和其他属性。带装饰器的树木保留放置集合的原版顺序，叶子传播集合仍优化。不接管树叶更新或衰败逻辑。
- DFC 验证覆盖原版数值、列/volume 对照、独立单点、缓存命中及替换后的单点查询、二维复用、局部回退、特殊 float 运算和完整区块生成。相同密度输入的 Arena 方块写入、元数据与序列化往返必须一致。性能检查使用预热后的 JFR，不要求运行改造前性能基准。
- Climate 使用原版树构建和分支数，保持查询顺序、严格距离比较及线程内上次命中的等距选择；仅复用目标数组和搜索距离。气候批量采样由 RC2 的 resolver 与 DFC 承担，不为了按列复用距离改变 biome 查询顺序。
- 表面 biome 缓存只包装已确认属于当前 WorldGenRegion 和中心区块的原版 manager；区块内读取已填充的 biome palette，均匀单元使用八角证书，边缘和不满足包装条件的来源保持原版查询。
- 材质规则在 `MaterialSystem.buildSurface/topMaterial` 接入，编译有序分支、序列和最终方块返回；**surface 编译沿用 26.2 的标量 ASM 管线**（`worldgen/surface/**`：分析 → 绑定期布局 → 区域切分 → 条件/规则发射 → hidden class），条件内联进生成代码（石深、水、Y、垂直梯度、噪声阈值、初步地表之上、洞），bandlands 直连 `context.getBand`，陡坡/温度/biome/未知条件仍绑原版 evaluator，模板按规则源身份缓存，编译失败或 preflight 不通过一律回退 `source.compile(context)`。三处按 26.3 收窄并在源码标注：噪声改内联 `context.getNoiseSampler(key, is3d)` 且不再有列 epoch 噪点 bank；石深列扫描只在"该规则树内完全没有 CEILING 检查"时缩短（26.2 还能对固定小上限的 CEILING 检查现场重算，26.3 的 `MaterialRuleContext` 没有 chunk）；`YAbove` 在绑定期解析锚点。生成字节码通过 `MaterialRuleContextAccessMixin` 注入的 accessor 读上下文（`byepregen$blockX` 等，与编译器同一个 `SURFACE_RULE_COMPILER` 门），因此编译产物用的是本 mod 自己的成员名、在重映射的生产命名空间下依然有效；公开 getter 名只作为纯 JVM 单测的回退。
- 石深列扫描只在"该规则树内不存在任何 CEILING 检查、也没有未知规则"时缩短为 `WAY_BELOW_MIN_Y`（`@ModifyConstant` + `@Share`）；26.2 还能对"固定小上限的 CEILING 检查"现场用 `Context.chunk` 重算，26.3 的 `MaterialRuleContext` 没有 chunk/column 访问途径，这类规则树一律不缩短扫描。
- 材质规则保持 RC2 的 float 密度与 double 阈值 ABI，不套用 DFC 的代数重排许可。已有 possibleBiomes 剪枝可消除对应的不可达分支。
- 后处理先按 (bucket, paletted index) 排序并去重，再交给原版执行：排序与去重改变同一 section 内的执行顺序与重复次数（这是换取更少无效调用的取舍），流体 tick、方块写入和清空生命周期保持原版；执行期只减少已核对的无效邻面调用，特殊方块和未知实现保留原版更新；藤蔓转换保持 DOWN→UP 顺序和随机数消耗。
- 第三方密度函数按采样边界处理：其子节点仍进入编译区域，包装器保留自身算法；`rewriteChildren` 探测失败的类退回原版路径（`-Dbyepregen.dfc.trustThirdParty=false` 可整体关闭）。
- 访问规则只有一对生效文件：`META-INF/arena-core.cfg` 与 `byepregen.arena.accesswidener`，两者必须同步（`AccessRulesParityTest` 会校验）；旧的归档 mono mixin 配置、旧 access widener 与旧 access transformer 已删除，其登记内容记录在 `doc/restore-list-26.3.md`。

## 重大问题

只记录迁移中实际发现、会改变地形结果或损坏区块数据等重大问题及其处理约束；不维护细粒度进度表。

- RC2 方块状态 NBT 使用 `id/properties`，默认状态允许字符串形式。旧 Arena 的 `Name/Properties` 编码会被原版拒绝；读写必须匹配新 codec，并用原版解码器和原版生成的 palette 做双向验证。
- RC2 modifier 在执行下一层前完成当前层输出。边收集边递归放置会交错随机数和世界写入，改变生成结果；执行器必须显式保存这条边界。
- 原版 PreparedCache 包住纯原生叶节点时若丢失轴依赖元数据，会使已知 Y-independent 节点退化为 opaque 并重复采样；缓存边界必须保留可信 source 的 axes/purity，未知或 context-bound source 仍按函数级回退。
- RC2 规则 provider 命中后返回空值时仍继续后续规则；提前返回会漏放方块。
- 后处理中返回相同 BlockState 仍可能安排 tick；树叶、掉落方块和含水方块的更新不能据此视为无操作。原版流体与方块 tick 及其顺序必须保留，避免改变衰败、下落和流动行为。
