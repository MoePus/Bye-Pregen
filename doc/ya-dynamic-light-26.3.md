# YA 动态发光兼容与回归测试

`969d056` 的初始多加载器移植把 NeoForge 的世界/位置发光查询替换成了原版无参查询，
并删除 `hasDynamicLightEmission` 分类条件。该提交没有说明这一兼容性变化。
普通火把、海晶灯及切换 LIT 状态的红石灯不能区分这两种查询，因此原有测试没有发现差异。

现在通过已有的 PlatformBridge 隔离加载器 API：NeoForge 保留动态标记与带世界/位置的查询，
Fabric 继续使用原版状态值。动态发光状态进入 SLOW 分类；普通非发光方块保留快路径。
光源扫描、逐点检查、队列光源查询、减光后的光源重新传播及客户端亮度合成都使用同一平台契约。

## 回归场景

测试块在测试模组注册阶段注册，静态发光为零、形状固定且透明，只有 NeoForge 动态标记
能使它进入 SLOW 分类。两个位置存储同一个 BlockState，世界数据分别给出 14 和 7 的亮度；
改变世界数据而不替换状态，验证 14 → 7 → 0 及恢复发光。

- 服务端覆盖 palette/map 两类来源、负坐标、chunk/section 边界、世界高度边界、初始扫描、
  sourceLight、checkBlock、邻近强光源减光后的弱光源重新传播；预期值按曼哈顿距离独立计算。
- 客户端在实际加载 YA mixin 后调用 LightCoordsUtil 两个入口，覆盖无天空引擎的虚拟视图、
  自定义天空亮度以及比方块自身更亮的外部方块光，不只是检查 render sink 通知。

新增 `:neoforge:testYADynamicLight` 自动启用 YA 光并运行服务端和客户端探针，已接入
`:neoforge:ciCheck`。也可以继续通过光照重启矩阵运行服务端探针：

```powershell
.\gradlew.bat :neoforge:testYADynamicLight --offline -PbyepregenLightRestartRoot=F:\.codex-tmp\ya-dynamic-light-regression -PbyepregenStartupRoot=F:\.codex-tmp\ya-dynamic-light-startup
.\gradlew.bat :neoforge:testYALightC2MERestart --offline -PbyepregenLightRestartRoot=F:\.codex-tmp\ya-dynamic-light-regression
```

新增探针已在未修复实现上实际失败，分别捕获错误分类、忽略世界数据的光源查询及客户端查询。
另外临时恢复减光分支的无参查询时，服务端光场断言失败；恢复修复后再验证，防止只覆盖初始化。

验证通过：282 项单元测试、Fabric/NeoForge 构建、实际客户端动态光探针、YA 专用服务器启动，
以及标准/raw 两套各两次重启和磁盘验证。日志位于 `F:\.codex-tmp\ya-dynamic-light-*.log`。
