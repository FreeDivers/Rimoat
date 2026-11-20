# Rimoat 设计与交互规范

> 界面令牌与动效实现统一对应 `ui/theme/`（`Color.kt`、`Shape.kt`、`Spacing.kt`、`Theme.kt`、`Type.kt`）与 `ui/motion/AppMotion.kt`。

## 1. 视觉主题与氛围

**极简、可靠、以工具属性为核心。** 界面摒弃拟物化与冗余装饰，采用扁平、模块化的 Material 3 容器表面区分信息层级。浅色模式通过高对比度文字与柔和中性容器色（`#f8f9f7`、`#f2f4f0`）自然区分卡片与控件组；深色模式采用中性深炭灰表面（`#121412`、`#20231e`）搭配柔和浅绿高亮（`#aed67b`），避免暗绿色泥泞感。同时支持 Android 12+ 动态取色（Material You）与可选的深色纯黑背景模式（`#000000`），在满足个性化设置的同时保持容器对比度。

## 2. 色彩系统

### 浅色方案（`LightColorScheme`）
- **Primary：** `#3e6700`（主操作、激活指示与主按钮的自然绿）
- **On-Primary：** `#ffffff`（绿色主表面上的纯白文字与图标）
- **Primary Container：** `#e1eecf`（选中项与激活状态的柔和浅绿容器填充）
- **On-Primary Container：** `#284508`（主色容器内的深林绿文字与图标）
- **Secondary & Tertiary：** `#555d50`（`secondaryContainer = #e8ebe5`）、`#4d635b`（`tertiaryContainer = #dfebe4`）
- **Background & Surface：** `#ffffff`（基础背景）、`#f8f9f7`（`surfaceContainerLow`）、`#f2f4f0`（`surfaceContainer`）、`#ecefe9`（`surfaceContainerHigh`）、`#e6e9e3`（`surfaceContainerHighest`）
- **Border / Outline：** `#747873`（`outline`）、`#dee1db`（`outlineVariant`）
- **Text (Primary)：** `#1f1f1f`（`onBackground` / `onSurface`）
- **Text (Muted)：** `#444746`（`onSurfaceVariant`，用于次级标签与当前设置项取值）

### 深色方案（`DarkColorScheme`）
- **Primary：** `#aed67b`（深色表面上的柔和亮绿）
- **On-Primary：** `#203600`
- **Primary Container：** `#314a1a`（`onPrimaryContainer = #d3edb5`）
- **Secondary & Tertiary：** `#c2c8ba`（`secondaryContainer = #3c4337`）、`#b3cfc0`（`tertiaryContainer = #354c40`）
- **Background & Surface：** `#121412`（深色基础画布）、`#1a1d18`（`surfaceContainerLow`）、`#20231e`（`surfaceContainer`）、`#292d26`（`surfaceContainerHigh`）、`#343830`（`surfaceContainerHighest`）
- **Border / Outline：** `#8d9387`（`outline`）、`#363b33`（`outlineVariant`）
- **Text (Primary)：** `#e4e6e1`（`onBackground` / `onSurface`）
- **Text (Muted)：** `#c0c5bb`（`onSurfaceVariant`）

### 功能与语义强调色
- **电源红（`AccentPower`）：** `#d32f2f`（电源按键专用强调色）
- **制冷蓝（`AccentCool`）：** `#1976d2`
- **制热橙（`AccentHeat`）：** `#f57c00`
- **成功绿（`AccentSuccess`）：** `#2e7d32`
- **发射波纹蓝：** `#0b57d0`（浅色模式）/ `#a8c7fa`（深色模式）
- **错误（Error）：** `#ba1a1a`（浅色模式 `errorContainer = #ffdad6`）/ `#ffb4ab`（深色模式 `errorContainer = #761b1b`）

## 3. 字体与排版

- **字体族：** 系统无衬线字体（Android `Roboto`），搭配中日韩系统原生字体回退（`Typography()` 默认角色）。
- **角色映射与层级：**
  - `displayLarge`：空调目标温度主读数
  - `headlineLarge`：红外学习页当前目标按键标题
  - `titleLarge` / `titleMedium`：页面顶栏、分组标题与遥控器卡片标题
  - `bodyLarge` / `bodyMedium`：主界面标签、设置行与状态说明（`14sp–16sp`，字重 `400`，行高 `1.5`）
  - `labelLarge` / `labelMedium`：按钮标签、筛选标签（FilterChip）与辅助说明
- **字号缩放与单位：** 布局尺寸使用 `dp`，字号使用 `sp`。所有页面必须通过文本自然换行或滚动适配最高 `200%` 系统字体缩放与 `320dp` 窄屏视口，严禁缩小触控热区或截断核心数值与单位。

## 4. 组件与形状规范

- **Material 3 官方组件与形状令牌（`Shapes()`）：**
  - `extraSmall = 4dp`：房间筛选下拉触发器与紧凑辅助容器
  - `small = 8dp`：`DropdownMenu`（SimpleMenu 风格弹窗）、紧凑标签与辅助徽标
  - `medium = 12dp`：设置分组卡片、方向盘外层圆角方盘（最小 `216dp`）及方向盘中央 `OK` 键（`64dp`）
  - `large = 16dp`：标准卡片与弹窗容器
  - `extraLarge = 28dp`：主容器与底部弹窗（Bottom Sheet）
  - `CircleShape`：专用遥控发射按键（电源 `72dp`、常规功能 `64dp`、数字键 `56dp`）、圆形状态图标、单选圆点、字母索引圆点及发射波纹
- **组件使用纪律：** 统一使用官方 `androidx.compose.material3` 组件（`Button`、`FilledTonalButton`、`OutlinedButton`、`IconButton`、`Card`、`OutlinedCard`、`SearchBar`、`ListItem`、`FilterChip`、`Slider`、`Switch`、`Checkbox`、`RadioButton`、`ModalBottomSheet`、`DropdownMenu`、`Tabs`）。禁止将全局圆角覆盖为单一数值，也禁止用自定义 `Surface` 边框手绘标准控件。
- **官方参考规范：** 引入或修改 UI 组件前，须查阅 [Material 3 Components](https://m3.material.io/components) 与 [Android Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)，并对照工程实际 `androidx.compose.material3` 依赖版本核实令牌与 API 行为。

## 5. 间距与布局

- **基准单位：** `4dp`（`2dp` 仅限组件内部微对齐使用）
- **间距阶梯：** `[4, 8, 12, 16, 20, 24dp]`
  - `4–8dp`：图标与文字间距、相邻可交互目标的最小间距（`>= 8dp`）
  - `12–16dp`：标准卡片与容器内边距、表单参数间距（`12dp`）、数字键盘横向间距（`16dp`）
  - `20–24dp`：分组间距与对话框内容内边距
- **响应式适配（视口断点 `320dp`、`480dp`、`569dp`）：** 网格与操作行通过测量实际本地化文本宽度（`TextMeasurer` + `BoxWithConstraints`），在窄屏或 `200%` 大字号下自动在 `1`、`2`、`3` 列之间切换，避免短标签逐字折行。
- **横屏与宽窗口布局（`width >= 480dp && width > height * 1.2`）：** 遥控按键尺寸与 `48dp` 最小触控热区保持不变，采用双栏或可读宽度居中排布——遥控面板（TV / 风扇 / 通用）左右分栏、空调温度卡片与功能键并排、“更多功能”页与阅读/列表类页面（设置、回收站、房间管理、对码、学习向导配置、关于）限宽 `600dp` 居中（类型与品牌选择限宽 `720dp`）；首页在宽窗口下采用左侧 `288dp` 筛选栏（`SearchBar` + 折行 `FilterChip` + 房间与排序）配右侧 `260dp` 自适应多列网格；红外学习工作区左右并排展示当前键状态与按键网格。

## 6. 动效与交互（`AppMotion`）

- **时长规范：**
  - 按压 / 释放触觉动画：按压 `90ms`，释放 `140ms`
  - 局部淡入淡出与对话框过渡：淡入 `180ms`（对话框缩放 `96% → 100%`），淡出 `100ms`（对话框缩放 `100% → 98%`）
  - 列表位移与展开/收起：展开 `220ms`，收起 `180ms`
  - 页面导航切换：`240ms`（支持预测性返回手势）
  - 发射波纹衰减：末次发射后保持 `600ms`，随后执行 `360ms` 非线性淡出
- **缓动曲线：**
  - 标准曲线：`cubic-bezier(0.2, 0.0, 0.0, 1.0)`
  - 进入曲线：`cubic-bezier(0.0, 0.0, 0.2, 1.0)`
  - 退出曲线：`cubic-bezier(0.4, 0.0, 1.0, 1.0)`
- **动效原则：** 动效仅用于引导空间方向与硬件反馈，严禁循环装饰动画。所有动画必须遵循系统动画时长缩放及无障碍减弱动画（`animator duration scale 0x`）设置。

## 7. 无障碍设计（Accessibility）

### 对比度标准
- **白底（`#ffffff`）配主文字（`#1f1f1f`）：** 对比度 ≈ **16.6:1**（远超 WCAG AAA `7:1`）
- **主色绿（`#3e6700`）配纯白文字（`#ffffff`）：** 对比度 ≈ **6.7:1**（超过交互控件 WCAG AA `4.5:1`）
- **白底（`#ffffff`）配次级文字（`#444746`）：** 对比度 ≈ **9.3:1**（超过 WCAG AAA `7:1`）
- **中性表面（`#f2f4f0`）配主文字（`#1f1f1f`）：** 对比度 ≈ **15.1:1**（超过 WCAG AAA）
- **深色背景（`#121412`）配深色主绿（`#aed67b`）：** 对比度 ≈ **10.8:1**（超过 WCAG AAA）

### 基础无障碍要求
- **最小触控热区：** 应用页面与桌面小组件的所有可点击目标不小于 `48dp × 48dp`，相邻可点击目标间距不少于 `8dp`。
- **语义播报：** 分组标题声明 `heading()`；动态状态更新（空调温度/模式摘要、对码反馈、学习倒计时、搜索无结果）声明 `liveRegion = Polite`。
- **触觉反馈分级：** 每次交互由单一振动后端触发——按下（`ACTION_DOWN`）触发清脆的 `EFFECT_CLICK`；滑块步进与长按连发触发 `EFFECT_TICK`（限频 `60ms`）；物理红外发送完成触发轻柔完成反馈（模拟模式绝不伪造物理硬件完成振动）。

---

## 8. 应用页面与交互细则

### 8.1 文案收敛与状态真实性
- 界面只保留名称、必要参数、实际状态、输入错误和操作结果；不显示常驻功能解释小字、重复副标题或教程式卡片；全应用与小组件禁止使用 `U+00B7`（`·`）分隔符。
- 严格区分**真实红外发射**、**演示/模拟模式**（“演示模式 / 模拟发送 / 模拟设定”）与**硬件未连接/未授权**；单向红外发射不伪装成电器实时开机、室温或真实硬件倒计时。

### 8.2 首页与遥控器卡片（`RemoteListScreen`）
- 顶栏标题为 `Rimoat`（`app_name`），右侧为发射通道状态图标与设置齿轮。
- 筛选区包含 `SearchBar`、横向滚动分类主标签栏、左下方房间下拉选择（弹窗采用 `small` 8dp 圆角、选中项通栏浅底色高亮且对准触发位置、无右侧对勾）及右下方排序图标按钮（默认排序 / 最近使用）。空库时仅展示空状态与主添加按钮。
- 遥控卡片最小高度 `72dp`、间距 `8dp`、左侧 `24dp` 电器图标、中部遥控器名称（允许完整换行）与 `16dp` 收藏星标、右侧 `48dp` 管理按钮。支持右滑收藏、左滑移入回收站（底部距侧边 `28dp` 处露出 `48dp` 正圆图标，`80dp` 完全显现，`104dp` 触发阈值，最大拖动 `144dp`）。

### 8.3 遥控面板、温度步进与诊断（`RemoteControlScreen`）
- 顶栏右侧提供编辑按钮（`Edit`）、学习模式开关（`School` / `Done`）与发射源反馈图标。
- 独立按键采用正圆（电源 `72dp` 语义红、常规 `64dp`、数字键 `56dp` 居中横向间距 `16dp`）；方向盘采用 `216dp`、`medium` 圆角方盘，中央 `OK` 为 `64dp` `medium` 按钮，五区独立焦点按“上 → 左 → 确认 → 右 → 下”遍历且物理左右不随 RTL 镜像。
- 空调主卡片集成 `displayLarge` 温度读数、加减键（空间不足时移至读数下方，`16–30°C` 边界禁用）与电源 `IconToggleButton`。具备完整状态集的模式/风速/风向/温度显示键支持圆键单击循环、长按或圆外 `48dp` 下拉标签直选。
- 扩展功能 `<= 6` 个且不超过可用高度 `30%`（或 `240dp`）时同页合并，否则使用“常规功能 / 更多功能” `Tabs` + `HorizontalPager`。
- 不可用按键灰显，`5 秒` 内连续点按 `3 次` 弹出 `Indefinite` 诊断 `Snackbar`（含“原因”详情与关闭），详情区分方案缺信号、模式限制、编码限制与硬件不可用。

### 8.4 对码、品牌索引、学习与设置页
- **添加对码四步流程**：固定为“选择类型 → 选择品牌 → 按键对码 → 确认保存”，每次进入从类别选择开始。品牌页全屏展示两字母大写短标（无拉丁/拼音字母时回退 `IR`）与名称，右侧预留 `56dp` 通道与 `48dp` 宽 A–Z/`#` 浮动索引。对码页按列表顺序命名为 `index + 1`（“方案1 / Code set 1 / コードセット 1”），底部固定“有响应吗”确认区与“使用此方案”主按钮。
- **红外学习页**：顶部显示接收器状态与 `headlineLarge` 当前按键名，中部展示倒计时环或真实捕获波形（脉冲数、时长、频率），底部单行紧凑排列 `56dp` 圆形测试发射按钮与主确认按钮，下方为 `1–3` 列自适应按键网格（已学键 `primaryContainer` 底色带勾，当前键 `2dp` 主色边框）。
- **设置、房间、回收站与关于**：设置页按“外观、遥控器、备份与导入、应用”四组 `medium` 容器卡片排列，每行 `>= 64dp` 高、间隔 `8dp`，当前值右对齐并支持锚点 `DropdownMenu`（`small` 8dp 圆角、选中项通栏底色高亮并覆盖对齐触发行、无右侧对勾）；开启“深色模式使用纯黑背景”时仅将深色 `background`、`surface`、`surfaceDim`、`surfaceContainerLowest` 设为 `#000000`。回收站每项提供恢复与彻底删除两个 `48dp` 图标按钮（窄屏/大字体移至右下）。关于页展示应用信息、仓库地址与离线可搜索的“开放源代码许可”列表。

---

## 9. Android 桌面小组件设计规范（Jetpack Glance & Google Tier 1 强约束）

桌面小组件（单键遥控、遥控面板、电器状态）使用 Jetpack Glance 与 Material 3 构建，严格遵守 **Google Widget Quality Tier 1** 规范。在设计与实现前须对照以下 Android Developers 官方文档，并核实当前工程依赖与 SDK 版本是否支持对应 API：

| 官方文档依据 | 本项目采用的核心准则 |
|---|---|
| [Android Widget Design Guidelines](https://developer.android.com/design/ui/mobile/guides/widgets) | 单一核心场景、一眼可扫读的状态摘要与一步直达的发射动作。 |
| [Widget Quality Guidelines](https://developer.android.com/docs/quality-guidelines/widget-quality) | Tier 1 分级标准、响应式尺寸覆盖、四边网格对齐、Header 规范、配色与内容同步。 |
| [Canonical Widget Layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts) | Text、Toolbar、List/Grid 的信息组织方式；按遥控器内容选择，不机械照搬与业务无关的模板。 |
| [Widget Style](https://developer.android.com/design/ui/mobile/guides/widgets/style) / [Configuration](https://developer.android.com/design/ui/mobile/guides/widgets/configuration) | 系统 OEM 外框圆角、Material 语义配色、字体层级与系统配置/重配入口。 |
| [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) / [Jetpack Compose](https://developer.android.com/develop/ui/compose) | 小组件主体使用 Glance，App 与配置页使用 Compose；二者共享设计令牌与数据仓库，不混用不兼容的 UI 组件。 |

1. **响应式断点（Responsive Sizing）**：
   - 设置 `sizeMode = SizeMode.Responsive`，定义三个核心断点层级并通过 `LocalSize.current` 判断当前宽高切换对应 Composable 布局，**严禁等比例缩放文字或缩小 48dp 按钮**：
     - **紧凑型（Small，约 `110.dp × 110.dp` / 2×1 或紧凑 2×2）**：仅展示核心识别/状态数据和 `1` 个主正圆发射按钮（窄宽度下温度读数与加减按钮分行排列）。
     - **中等型（Medium，约 `250.dp × 110.dp` / 标准 2×2 或 4×2）**：展示核心识别/设定状态与完整常用按键行（列数不超过已配置按钮数，少量按键不留空列；1–2 个快捷键采用“名称 + 正圆按钮”完整操作行）。
     - **展开型（Large，约 `250.dp × 250.dp` / 4×2 扩展或 4×4）**：包含顶栏识别区（图标 + 标题 + 上下文入口）及按组排列的更多常用操作，或完整可滚动的 `LazyColumn` 列表，扩大尺寸必须增加有效信息或操作项。

2. **网格填充与系统圆角规范**：
   - 最外层容器必须使用 `GlanceModifier.fillMaxSize().appWidgetBackground()`，**严禁添加外边距（outer margin）**，确保填满桌面网格四条边。
   - 外层背景圆角使用系统资源 `android.R.dimen.system_app_widget_background_radius`，内部卡片圆角使用 `android.R.dimen.system_app_widget_inner_radius`（低版本提供兼容回退），内容内边距统一设为独立内层的 `12.dp` 或 `16.dp`，不将应用内 Compose 的形状令牌套用到小组件外框。

3. **视觉配色、正圆按钮与无障碍**：
   - 外层包裹 `GlanceTheme`，所有颜色使用 `GlanceTheme.colors.*`（如 `widgetBackground`、`primaryContainer`、`onSurface`）以自动适配默认绿色主题、系统动态取色和深浅色模式。
   - 所有可点击按钮的最小触控热区不少于 `48.dp × 48.dp`；发射按钮统一为 `>= 48.dp` 的正圆，**仅展示应用内已有图标**并配完整无障碍名称，不显示圆外操作名、不显示“上次发送/上次设定”或任何发射状态文字，禁止使用 `U+00B7` 分隔号；无房间时直接收起房间行，不留空行或占位文字。
   - 文本必须显式设置 `maxLines`（超出省略），防止小尺寸或大字号下文字被异常裁切；发送失败仅在实际失败的按键上显示警示图标与无障碍说明，点按重试该动作。

4. **状态闭环与 XML 配置**：
   - 包含 `Loading`（加载中）、`Empty` / 未配置 / 绑定失效（空状态引导点击添加或重新选择遥控器）和 `Content`（正常数据展示与直接发射）三种完整 UI 状态切换。
   - 配套完整的 `res/xml/` 小组件元数据（包含 Android 12+ 的 `targetCellWidth`、`targetCellHeight`、`minWidth`、`minHeight`、`resizeMode="horizontal|vertical"`、`updatePeriodMillis="0"`、`previewLayout` / `previewImage`、`configure` Activity）以及 `AndroidManifest.xml` 中的 `Receiver` 注册代码。
