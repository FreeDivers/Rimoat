# Rimoat Material 3 Expressive 设计与交互规范

> 应用页面采用 Material 3 Expressive（MD3E）。界面令牌与动效实现统一对应 `ui/theme/`（`Color.kt`、`Shape.kt`、`Spacing.kt`、`Theme.kt`、`Type.kt`）与 `ui/motion/AppMotion.kt`。

## 1. 视觉主题与氛围

**清晰、可靠、便于连续遥控。** MD3E 通过容器层次、按压形变和弹簧反馈表达操作关系；主读数、当前选项和首要操作应一眼可辨，不添加无意义装饰或遮挡按键的动效。

- **统一主题入口：** `IrRemoteTheme` 使用 `MaterialExpressiveTheme`，注入项目 `colorScheme`、`Typography`、`Shapes` 和 `MotionScheme.expressive()`。页面与公共组件读取 `MaterialTheme` 令牌；需要实验性 API 时在最小适用范围声明 `ExperimentalMaterial3ExpressiveApi`。
- **动态取色：** 保留 Android 12+ 的 Material You 颜色方案与用户偏好；关闭动态取色时使用第 2 节的默认配色。MD3E 组件继承当前主题，不另建固定色板。
- **AMOLED 纯黑：** 仅在深色且 `pureBlackBackground` 开启时，将 `background`、`surface`、`surfaceDim`、`surfaceContainerLowest` 四个基础表面设为 `#000000`。此处理在动态或默认颜色方案选定后执行；其余容器、语义色及前景色保持原方案，确保卡片与操作组仍有层次。
- **声音与触觉：** 应用关闭全部 Compose 系统点击音；保留已有触觉分级与硬件反馈。新窗口的声音设置及验证要求见第 6、7 节。

## 2. 色彩系统

### 浅色方案（`LightColorScheme`）
- **Primary：** `#3e6700`（主操作、激活指示与主按钮的自然绿）
- **On-Primary：** `#ffffff`（绿色主表面上的纯白文字与图标）
- **Primary Container：** `#e1eecf`（选中项与激活状态的柔和浅绿容器填充）
- **On-Primary Container：** `#284508`（主色容器内的深林绿文字与图标）
- **Secondary & Tertiary：** `#555d50`（`secondaryContainer = #e8ebe5`）、`#4d635b`（`tertiaryContainer = #dfebe4`）
- **Background & Surface：** `#ffffff`（基础表面）、`#f6f8f3`（`surfaceContainerLow`）、`#eef2e8`（`surfaceContainer`，全屏浅绿画布底色）、`#e4e9dd`（`surfaceContainerHigh`）、`#dbe1d3`（`surfaceContainerHighest`）；`surfaceContainerLowest`（`#ffffff`）作为浅色画布上的纯净卡片表面
- **Border / Outline：** `#747873`（`outline`）、`#d5dbd0`（`outlineVariant`）
- **Text (Primary)：** `#1f1f1f`（`onBackground` / `onSurface`）
- **Text (Muted)：** `#444746`（`onSurfaceVariant`，用于次级标签与当前设置项取值）

### 深色方案（`DarkColorScheme`）
- **Primary：** `#aed67b`（深色表面上的柔和亮绿）
- **On-Primary：** `#203600`
- **Primary Container：** `#314a1a`（`onPrimaryContainer = #d3edb5`）
- **Secondary & Tertiary：** `#c2c8ba`（`secondaryContainer = #3c4337`）、`#b3cfc0`（`tertiaryContainer = #354c40`）
- **Background & Surface：** `#121412`（深色基础画布，AMOLED 纯黑模式为 `#000000`）、`#0e100d`（`surfaceContainerLowest`）、`#1b1f19`（`surfaceContainerLow`，深色画布卡片表面）、`#21261f`（`surfaceContainer`）、`#2b3128`（`surfaceContainerHigh`）、`#353b31`（`surfaceContainerHighest`）
- **Border / Outline：** `#8d9387`（`outline`）、`#363b33`（`outlineVariant`）
- **Text (Primary)：** `#e4e6e1`（`onBackground` / `onSurface`）
- **Text (Muted)：** `#c0c5bb`（`onSurfaceVariant`）

### 全屏画布与分段容器层次（`ExpressiveSurface`）
- **显式画布注入：** 全屏页面由 `ProvideExpressiveCanvas` 与 `Scaffold(containerColor = ExpressiveSurface.canvas)`、`MotionContent` 共同维护连贯画布——浅色使用鼠尾草绿调 `surfaceContainer`（`#eef2e8`）衬托纯白 `surfaceContainerLowest`（`#ffffff`）卡片；深色使用 `#121412`（或 AMOLED `#000000`）衬托 `surfaceContainerLow`（`#1b1f19`）卡片，依靠色调对比区分层级，不在分组外再套硬边 `1dp` 边框。
- **卡片内部徽章与独立宿主回退：** 白色卡片内部的功能图标底徽使用 `surfaceContainer` 保持可见对比；脱离 `ProvideExpressiveCanvas` 的独立组件或测试容器自动回退为 `surfaceContainerLow` 卡片底色，防止白底上出现白卡。

### 功能与语义强调色
- **电源红（`AccentPower`）：** `#d32f2f`（电源按键专用强调色）
- **制冷蓝（`AccentCool`）：** `#1976d2`
- **制热橙（`AccentHeat`）：** `#f57c00`
- **成功绿（`AccentSuccess`）：** `#2e7d32`
- **发射反馈：** 使用当前 `colorScheme.tertiary`，随深浅色与动态取色变化，不单独固定波纹颜色。
- **错误（Error）：** `#ba1a1a`（浅色模式 `errorContainer = #ffdad6`）/ `#ffb4ab`（深色模式 `errorContainer = #761b1b`）

## 3. 字体与排版

- **字体族：** 系统无衬线字体（Android `Roboto`），搭配中日韩系统原生字体回退（`Typography()` 默认角色）。
- **角色映射与层级：**
  - `displayLarge`：空调目标温度主读数
  - `headlineLarge`：红外学习页当前目标按键标题
  - `titleLarge` / `titleMedium`：页面顶栏、分组标题与遥控器卡片标题
  - `bodyLarge` / `bodyMedium`：主界面标签、设置行与状态说明；字号、字重与行高沿用 `Typography()` 对应角色
  - `labelLarge` / `labelMedium`：按钮、分类与选项组标签及辅助说明
- **字号缩放与单位：** 布局尺寸使用 `dp`，字号使用 `sp`。所有页面必须通过文本自然换行或滚动适配最高 `200%` 系统字体缩放与 `320dp` 窄屏视口，严禁缩小触控热区或截断核心数值与单位。

## 4. MD3E 组件与形状规范

- **官方组件优先：** 使用当前工程 Material 3 版本的 Expressive API，不手绘标准按钮、单选控件或进度条。`Card`、`ListItem`、`SearchBar`、`Slider`、`Switch`、`Checkbox`、`DropdownMenu`、`ModalBottomSheet` 和页签等继续按其任务语义使用，不为统一外形改变交互含义。
- **形状令牌：** 保留 `Shapes()` 的组件默认角色：`extraSmall`（4dp）、`small`（8dp）、`medium`（12dp）、`large`（16dp）、`extraLarge`（28dp）。这些是静止状态的通用令牌，不能据此覆盖所有官方组件为同一圆角。按钮组、切换按钮和浮动工具栏使用各自的官方形状与按压行为。
- **标准按钮形变：** `Button`、`FilledTonalButton`、`OutlinedButton`、`TextButton` 等使用 `ButtonDefaults.shapes()`；图标按钮使用 `IconButtonDefaults.shapes()`，图标切换按钮使用 `toggleableShapes()`。新增代码优先选择 `shapes` 重载，并用命名参数传入 `modifier`，避免仍落入静态 `shape` 重载。
- **自定义可点击容器与分段列表（`ExpressiveContainers`）：** 遥控器卡片、品牌行、设置行、房间、回收站、关于链接与开源库列表通过 `ExpressiveSegmentedSurface` 与 `rememberSegmentedExpressiveShape` 统一管理圆角（组首尾外侧 `20dp`、相邻内侧 `4dp`、条目间距 `2dp`）与按压形变；形变只改变轮廓和视觉反馈，不改变布局占位、焦点顺序或点击区域。不可点击容器保持静态。
- **顶栏与双页切换角色：** 浏览与管理类页面（首页、分类、设置、房间管理、回收站、关于、开源许可列表）在竖屏使用可折叠 `MediumFlexibleTopAppBar`（`ExpressivePageTopBar`），横屏双栏窗口自动切换为紧凑 `TopAppBar` 以节省纵向空间；遥控面板、布局编辑、品牌筛选、对码与学习工作区使用单行紧凑 `ExpressiveCompactTopBar`。双页内容切换采用居中动画胶囊轨道 `ExpressivePageSwitcher`（高 `52dp`）。

| 场景 | 首选组件与使用规则 |
|---|---|
| 短筛选、定时预设 | `ToggleButton`（首页分类、定时预设使用统一胶囊形状与 `selected` 语义）；选中状态由页面状态驱动，重复点选不产生第二份状态。 |
| 全宽单选列表（通道、串口模块等） | `ExpressiveSingleChoiceRow`（分段卡片、圆形图标底徽与右侧选中标志）；保留 `Role.RadioButton` 与 `selected` 合并语义，避免全宽条目按胶囊翻方导致比例失调。 |
| 同一任务的并列确认动作 | 在容器内依据实测译文宽度切换横向并排 `Row` 或纵向堆叠 `Column`（如对码确认“无响应 / 有响应”通过 `BoxWithConstraints` 自适应），不使用溢出菜单折叠隐藏。 |
| 页面底部单一或简单动作栏 | 在锚定底部表面、卡片或弹窗底部内直接放置 `Button`、`FilledTonalButton` 或 `OutlinedButton`，不为单个主按钮或窄屏操作额外嵌套外层 `HorizontalFloatingToolbar`。 |
| 多个相关悬浮快捷操作 | 评估 `FloatingActionButtonMenu`；首页单一“添加”入口保留单一胶囊 `ExtendedFloatingActionButton`（窄屏按空间自适应），不额外增加菜单层级。 |
| 一个主动作及其同一任务的替代操作 | 评估 `SplitButtonLayout`；前提是主次关系明确。测试发射与保存、恢复与永久删除等含义不同的操作继续独立呈现。 |
| 不确定耗时的加载、恢复或发送等待 | `LoadingIndicator`；需要独立容器强调时使用 `ContainedLoadingIndicator`。仅在真实忙碌状态显示，结束即移除。 |
| 下载进度、已学习数量或外设配置加载 | `LinearWavyProgressIndicator`；已知进度绑定真实数值，未知进度使用不确定模式。静态完成比例关闭波浪平移，不模拟任务前进。 |
| 红外接收等待 | `ContainedLoadingIndicator` 配合真实剩余秒数；可以使用确定进度形变表示剩余比例，不能把它描述为电器自身倒计时。 |

引入或修改组件前，查阅 [Material 3 Components](https://m3.material.io/components) 与 [Android Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)，对照工程实际依赖核实 API、默认形状和无障碍行为。Glance 小组件使用第 9 节的独立组件规则。

## 5. 间距与布局

- **基准单位与分段间距：** 以 `4dp` 为基准单位；分段列表项之间统一使用 `2dp`（`ExpressiveShapeTokens.SegmentedGap`）制造清晰的组内微切分感。
- **间距阶梯：** `[2, 4, 8, 12, 16, 20, 24dp]`
  - `2dp`：分段容器组内相邻卡片间隔（`SegmentedGap`）
  - `4–8dp`：图标与文字间距、相邻可交互圆键的最小间距（`>= 8dp`）
  - `12–16dp`：卡片内容内边距、表单参数间距（`12dp`）、数字键盘横向间距（`16dp`）
  - `20–24dp`：分组段落间距、卡片外圆角令牌与弹窗内边距
- **响应式适配（视口断点 `320dp`、`480dp`、`569dp`）：** 网格与操作行通过测量实际本地化文本宽度（`TextMeasurer` + `BoxWithConstraints`），在窄屏或 `200%` 大字号下自动在 `1`、`2`、`3` 列之间切换，避免短标签逐字折行。
- **横屏与宽窗口布局（`width >= 480dp && width > height * 1.2`）：** 使用扣除系统安全区域、不受输入法遮挡影响的窗口尺寸判断。按任务组织双栏：主控与扩展、筛选与结果、列表与详情、预览与编辑分别并排，两栏独立滚动。`480–599dp` 外边距与栏间距使用 `8dp`，更宽窗口使用 `16dp`，列表侧栏按内容分配并限制可读宽度。保持既有遥控按键尺寸与 `48dp` 触控区域，窄栏和大字号通过减少列数、换行与局部滚动适配。搜索、选择、编辑草稿、页签及数字键展开状态由横竖布局共用。

## 6. 弹簧动效与交互（`AppMotion`）

- **统一来源：** `AppMotion.current` 读取 `MaterialTheme.motionScheme`。位置、尺寸、轮廓、缩放及列表重排使用 `fastSpatialSpec()`、`defaultSpatialSpec()` 或 `slowSpatialSpec()`；透明度、颜色与进度值使用对应的 `EffectsSpec`。在组合中读取方案，再供手势或协程回调使用，不在回调内调用 Composable。
- **弹簧连续性：** 按下、释放、取消和反向切换应从当前视觉状态衔接。页面导航、预测性返回、展开收起和自管菜单退出使用主题弹簧，不另立 Compose 固定毫秒时长或贝塞尔缓动表。退出期间保留必要的显示内容，但立即禁用已关闭页面或弹层的操作。
- **窗口动画边界：** `MotionAlertDialog` 与 `MotionBasicAlertDialog` 保留 Android 窗口动画 `RimoatDialogAnimation`，由 `res/anim/dialog_enter.xml` 与 `dialog_exit.xml` 定义。它们仍是系统窗口的透明度/缩放动画，不属于 `MotionScheme` 弹簧；关闭时由系统完成窗口退场，Compose 内容可立即释放。对话框内部控件继续使用主题形变与弹簧。
- **高频遥控：** 密集遥控键、方向盘和温度步进局部采用 `MaterialTheme(motionScheme = MotionScheme.standard())`；按压仅作小幅缩放和圆角形变。正圆按键保持既定静止尺寸，形变不能推动邻键、缩小热区或产生大幅回弹。方向键的物理方位及中心确认区域始终稳定。
- **状态反馈：** 卡片按压改变轮廓，编辑拖动只作轻微抬升与缩放；发射波纹只由实际发送结果事件触发，演示通道明确标为模拟发送。学习、加载等连续动画必须对应实际进行中的任务，不能常驻作装饰。
- **系统动效设置：** Compose 动画响应系统动画时长缩放，`animator duration scale 0x` 时直接到达目标视觉状态；上述 Android 窗口动画遵循系统窗口动画缩放设置。不能通过自建计时循环绕过缩放设置；关闭动画也不能阻止按钮操作、菜单关闭或状态更新。
- **硬件时序独立：** 长按识别、连发间隔、接收超时、反馈保持窗口和触觉限频继续由现有逻辑控制，不能换成弹簧结束回调或随系统动效缩放改变。末次发送后的 `600ms` 反馈保持窗口与其后的弹簧淡出属于不同职责。
- **关闭点击音：** 主题内容统一使用 `SoundEffectOnInteraction(enabled = false)`。`Dialog`、`Popup`、`DropdownMenu`、`ModalBottomSheet` 等新宿主必须在其内容内部再次关闭；只包在调用外部不能保证新窗口继承。`ButtonGroup` 的溢出项使用可控制内容的 `customItem`，在 `menuContent` 内关闭点击音并保留选项启用状态及提交后关闭。关闭系统点击音不移除 `HapticHelper` 或其他现有触觉，不新增应用自定义点击音。

## 7. 无障碍设计（Accessibility）

### 对比度标准
- **白底（`#ffffff`）配主文字（`#1f1f1f`）：** 对比度 ≈ **16.5:1**（超过 WCAG AAA `7:1`）
- **主色绿（`#3e6700`）配纯白文字（`#ffffff`）：** 对比度 ≈ **6.7:1**（超过交互控件 WCAG AA `4.5:1`）
- **白底（`#ffffff`）配次级文字（`#444746`）：** 对比度 ≈ **9.3:1**（超过 WCAG AAA `7:1`）
- **浅色画布（`#eef2e8`）配主文字（`#1f1f1f`）：** 对比度 ≈ **14.5:1**（超过 WCAG AAA `7:1`）
- **深色画布（`#121412`）配深色主绿（`#aed67b`）：** 对比度 ≈ **11.2:1**（超过 WCAG AAA `7:1`）

### 基础无障碍要求
- **最小触控热区：** 应用页面与桌面小组件的所有可点击目标不小于 `48dp × 48dp`，相邻可点击目标间距不少于 `8dp`。
- **语义播报：** 分组标题声明 `heading()`；动态状态更新（空调温度/模式摘要、对码反馈、学习倒计时、搜索无结果）声明 `liveRegion = Polite`。
- **触觉反馈分级：** 遥控按键按下（`ACTION_DOWN`）保留清脆的 `EFFECT_CLICK`；滑块步进与长按连发保留 `EFFECT_TICK`（限频 `60ms`）；物理红外发送完成保留轻柔完成反馈。每次反馈由单一振动后端触发，模拟模式不伪造物理完成振动；MD3E 控件形变与系统点击音设置不得改变这些规则。
- **选择与忙碌语义：** 单选按钮组提供分组与选中语义，切换开关保留开关语义，多选复选框保持多选含义。进度与倒计时播报来自实际状态，装饰图形不重复播报；忙碌时按钮应真实禁用，不能仅变灰。

---

## 8. 应用页面与交互细则

### 8.1 文案与状态真实性

- 界面只保留名称、必要参数、实际状态、输入错误和操作结果；不显示常驻功能解释小字、重复副标题或教程式卡片；全应用与小组件禁止使用 `U+00B7`（`·`）分隔符。
- 严格区分真实红外发射、演示/模拟模式（“演示模式 / 模拟发送 / 模拟设定”）与未连接/未授权。单向红外发射不伪装成电器实时开机、室温或真实硬件倒计时；所有 MD3E 进度组件遵守同一规则。

### 8.2 首页、筛选与遥控器卡片（`RemoteListScreen`）

- **顶栏与筛选：** 顶栏使用 `ExpressivePageTopBar`（竖屏为可折叠 `MediumFlexibleTopAppBar`，展示应用名与遥控器数量副标题；横屏双栏为紧凑 `TopAppBar`），右侧保留发射通道图标与设置入口。搜索栏使用纯白圆角 `SearchBar`；竖屏分类使用可横向滚动的 `ToggleButton` 胶囊组（选中滚入可见区，并在弹簧过冲下保持单行），横屏侧栏按可用宽度换行排列。
- **房间与排序：** 房间选择（锚点 `ExpressiveDropdownMenu`）与排序按钮使用色调胶囊样式；下拉菜单统一采用 MD3E 分段菜单容器（`DropdownMenuPopup` + `DropdownMenuGroup` + `SelectableDropdownMenuItem` / `DropdownMenuItem`），呈现分组圆角、`tertiaryContainer` 选中高亮与勾选图标，适配任意房间长度；排序按钮保留当前状态与无障碍说明。
- **列表、空状态与滑动：** 空库居中展示带 `88dp` 主色容器图标的空状态卡片；单一添加入口保留胶囊 `ExtendedFloatingActionButton`；仅在实际读取期间展示波浪进度。遥控卡片使用 `ExpressiveSegmentedSurface`（最小高 `72dp`、组内间隔 `2dp`），左侧为 `44dp` 圆角方形 `surfaceContainer` 图标底徽，中部展示名称与房间/品牌副信息（禁用 `U+00B7`）及收藏星标，右侧菜单热区 `>= 48dp`。右滑收藏、左滑移入回收站保持既有阈值：距侧边 `28dp` 处露出 `48dp` 正圆图标，`80dp` 完全显现，`104dp` 触发阈值，最大拖动 `144dp`。

### 8.3 遥控面板、定时与布局编辑

- **基础按键与方向盘：** 顶栏使用 `ExpressiveCompactTopBar` 提供编辑、学习开关与发射源入口。静止发射按键为正圆：电源 `72dp`、常规 `64dp`、数字键 `56dp`（横向间距 `16dp`），按压保留第 6 节的标准形变。方向盘采用 `CircleShape` 一体化圆形转盘（`>= 196dp`，默认 `208dp`），内嵌同心圆盘与 `64dp` 中央确认键，焦点按“上 → 左 → 确认 → 右 → 下”遍历，物理左右不随 RTL 镜像。
- **电视与图标映射：** 电视竖屏将“音量+/音量-”与“频道+/频道-”分别封装在 `36dp` 圆角竖向双联摇杆胶囊中；功能组置于 `24dp` 圆角卡片内。`RemoteButtonView` 依据 `buttonId`、`builtinLabel` 及常见中文键名补全语义图标（返回、主页、菜单、静音、信源、退出等），`0–9` 与 `OK` 保持纯文本键。
- **空调卡片与直选：** 空调主卡（`28dp` 圆角）整合状态标签胶囊、`displayLarge` 温度、加减键、电源切换键及底部分隔的模式/风速/定时状态条（状态条屏蔽重复播报）。电源开机为 `primary` 主色底，关机为中性 `surfaceContainerHigh`。模式、风速、风向等在 `48dp` 热区内使用轻量胶囊下拉角标实现等高对齐；直选菜单采用 MD3E `ExpressiveDropdownMenu`（`SelectableDropdownMenuItem`）。
- **定时、分页与编辑：** 定时底部弹窗采用 `ExpressiveSurface.canvas` 背景、`ExpressiveSurface.card` 读数卡片、圆形胶囊 `ToggleButton` 预设及底部直排动作按钮（窄屏/大字自动纵向全宽排列）。扩展功能 `<= 6` 个且不超可用高度 `30%`（或 `240dp`）时同页合并，否则通过 `ExpressivePageSwitcher` 切换“常规功能 / 更多功能”。布局编辑采用 `18dp`/`24dp` 预览卡片与统一语义图标，拖动仅作轻微抬升与弹簧重排。不可用按键灰显，`5 秒` 内连按 `3 次` 弹出 `Indefinite` 诊断 `Snackbar` 说明原因。

### 8.4 添加、品牌、对码与红外学习

- **添加对码：** 保持“选择类型 → 选择品牌 → 按键对码 → 确认保存”。电器类型采用 `20dp` 圆角图标徽章卡片，确认表单置于 `24dp` 卡片内。品牌页展示 `38dp` `primaryContainer` 首字母方徽、字母分组下的分段白卡行、胶囊搜索框及右侧 A–Z/`#` 快速索引。
- **对码确认：** 代码切换器使用 `24dp` 胶囊，按键分组置于 `24dp` 卡片内；下方 `28dp` 确认区依据实测译文宽度直接并排或堆叠显示“无响应 / 有响应”，不使用溢出菜单隐藏；底部直接放置胶囊主按钮“使用此方案”，忙碌时真实禁用。
- **学习配置与工作区：** 学习向导的电器类型卡片与添加页一致（选中态呈现 `primaryContainer` 与勾选徽章），队列按钮使用 `16dp` 圆角切片；工作区顶部状态条与 `24dp` 主卡展示 `headlineLarge` 当前按键名，等待接收使用 `ContainedLoadingIndicator` 与真实剩余秒数，捕获后展示真实波形、脉冲数、时长与频率，已学进度使用确定的 `LinearWavyProgressIndicator`。
- **学习动作：** 测试发射与确认/保存直接在底部容器内按可用宽度并排或纵向排列，单键学习对话框与主体保持统一透明/高容器层级，禁用条件及取消、重录、跳过、继续学习回调不变。

### 8.5 设置、发射通道与管理页面

- **设置页：** 通过 `SettingsSectionScope` 按可见项自动生成 `ExpressiveSegmentedSurface` 分段卡片，不使用外层硬边描边；自动旋转子选项内嵌于同一展开卡片内并以细分割线区隔，每行最小高 `64dp`。取值右对齐并保留锚点菜单；开关使用 `Switch`，多项备份导入使用圆角 `Checkbox`。
- **发射通道与串口：** 通道与串口模块类型使用 `ExpressiveSingleChoiceRow` 分段单选列表，分别呈现可用、待授权、未连接（置灰并保持对比）与演示状态；串口配置读取使用波浪加载指示器。
- **房间管理与回收站：** 统一采用 `ExpressivePageTopBar`、`ProvideExpressiveCanvas` 与 `ExpressiveSegmentedSurface`（卡片内徽章使用 `surfaceContainer` 保持浅/深色可辨）；房间管理底部在锚定表面内直接承载 `52dp` 胶囊主按钮；回收站横屏详情置于 `24dp` 卡片。
- **关于、开源许可与弹窗：** 关于、开源库列表及详情均采用 `ProvideExpressiveCanvas`、`ExpressivePageTopBar`、`ExpressiveSegmentedSurface` 与 `20–28dp` 内容卡片（关于页在横屏大字号下自适应缩紧图标与版本徽章圆角以防裁剪）；备份导入与更新弹窗保留真实进度与错误状态。

### 8.6 UI 验证与预览

- 修改布局、主题、选项状态、反馈或点击音时，同步更新相关 Robolectric/Roborazzi 交互测试。使用真实页面与资源、`GraphicsMode.NATIVE` 和无损 WebP；覆盖操作前后、单次回调、选中/禁用语义以及受影响的弹层。
- 截图覆盖相应的浅深色、动态色或纯黑分支，并检查 `320dp` 与 `200%` 字号下的关键操作可达性。先审阅已有截图差异，再记录符合预期的基准；保存后直接打开确认文字、形变与操作结果，不用重新录图掩盖回归。
- 系统点击音用真实触摸验证，覆盖主页面及新建 Dialog/Popup 宿主；触觉回调保持原有测试。系统动效缩放的验证不应改变连发与学习超时。
- Compose 预览统一使用 `IrRemoteTheme`；截图基准仅放在 `app/src/test/screenshots/*.webp`。测试范围、命令与备用设备验证条件遵循 `AGENTS.md` 第 0 节。

---

## 9. Android 桌面小组件设计规范（Glance 静态 MD3E）

桌面小组件（单键遥控、遥控面板、电器状态）采用 Jetpack Glance `1.2.0`（`glance-appwidget` 与 `glance-material3` 同版本），以静态形状、容器层次、语义配色和文字字重表达 MD3E。Glance 依赖单独维护，不受 Compose BOM 管理；不将 `MaterialExpressiveTheme`、Compose `ButtonGroup`、浮动工具栏、加载指示器或 `MotionScheme` 按压动画嵌入 Glance 主体；小组件外框、交互与刷新能力以实际 Host 和 Glance API 为准。配置 Activity 的 Compose 页面使用应用主题，主体仍使用 `GlanceTheme`，双方共享数据与适用的颜色语义。设计严格遵守 **Google Widget Quality Tier 1** 规范。在设计与实现前须对照以下 Android Developers 官方文档，并核实当前工程依赖与 SDK 版本是否支持对应 API：

| 官方文档依据 | 本项目采用的核心准则 |
|---|---|
| [Android Widget Design Guidelines](https://developer.android.com/design/ui/mobile/guides/widgets) | 单一核心场景、一眼可扫读的状态摘要与一步直达的发射动作。 |
| [Widget Quality Guidelines](https://developer.android.com/docs/quality-guidelines/widget-quality) | Tier 1 分级标准、响应式尺寸覆盖、四边网格对齐、Header 规范、配色与内容同步。 |
| [Canonical Widget Layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts) | Text、Toolbar、List/Grid 的信息组织方式；这是布局用途，不表示可嵌入 Compose 浮动工具栏。 |
| [Widget Style](https://developer.android.com/design/ui/mobile/guides/widgets/style) / [Configuration](https://developer.android.com/design/ui/mobile/guides/widgets/configuration) | 系统 OEM 外框圆角、Material 语义配色、字体层级与系统配置/重配入口。 |
| [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) / [Jetpack Compose](https://developer.android.com/develop/ui/compose) | 小组件主体使用 Glance，App 与配置页使用 Compose；二者共享设计令牌与数据仓库，不混用不兼容的 UI 组件。 |

1. **响应式尺寸与信息层级**：
   - 使用 `SizeMode.Responsive` 与 `WidgetLayoutSizes` 共享尺寸桶；配置页预览通过 `canonicalPreviewSize()` 选择同一套布局。`LocalSize` 只用于选择布局，不能当作 Host 最终精确尺寸。禁止等比缩放文字或缩小触控区域。
   - **紧凑型（`128dp × 64dp`、`280dp × 64dp`）**：优先保留电器识别、关键读数与首要操作；窄宽度只显示必要信息，宽条形增加有效识别或操作，不拉伸空白。
   - **中等型（`152dp × 168dp`、`224dp × 144dp`、`280dp × 144dp` 等）**：展示名称、设定状态和常用按键；少量按键采用识别信息与按钮并排的操作行，网格列数不超过实际配置数量。
   - **展开型（高 `240dp`，宽组件另有 `280dp × 320dp`）**：增加状态摘要、按组操作或可滚动列表；布局应利用新增空间，不能仅放大原有按钮。长名称、大字号与 RTL 场景保留必要信息及操作可达性。
   - 上述尺寸是当前内容桶，不等同于 Launcher 网格格数。外部预览和真实 Host 分别验证紧凑、中等、展开，以及窄高和宽矮布局；核心读数、主操作与副操作的层级在各尺寸保持一致。

2. **网格填充与系统圆角规范**：
   - 最外层容器必须使用 `GlanceModifier.fillMaxSize().appWidgetBackground()`，**严禁添加外边距（outer margin）**，确保填满桌面网格四条边。
   - 外层背景圆角使用系统资源 `android.R.dimen.system_app_widget_background_radius`，内部卡片圆角使用 `android.R.dimen.system_app_widget_inner_radius`（低版本提供兼容回退）。内容内边距放在独立内层：当前紧凑桶横向 `8dp`、纵向 `4dp`，常规桶四周 `12dp`，以保留必要的触控空间；不将应用内 Compose 的形状令牌套用到小组件外框。

3. **静态形状、配色与无障碍**：
   - 外层包裹 `GlanceTheme`，由 `WidgetVisualTheme` 视觉桥接读取既有 `dynamicColor` 与 `colorMode`。默认色取自主 App 的 `LightColorScheme` / `DarkColorScheme`；Android 12+ 动态色取自对应的动态方案，分别处理跟随系统、强制浅色和强制深色，不能另造一套相近但不同的绿色主题。
   - `LocalWidgetSurfaces` 将主 App 的 `surfaceContainerLow` 映射为小组件背景，将 `surfaceContainerHigh` 映射为内部读数/内容容器。主键与电源使用 `primary` / `onPrimary`；活动副键使用 `primaryContainer` / `onPrimaryContainer`；普通副键使用容器色 / `onSurface`；错误保留 `errorContainer` / `onErrorContainer`，不把每个电源操作都画成失败警示。
   - 小组件背景和前景以颜色 provider 适配系统日夜及应用颜色模式，容器必须有可辨层次。当前小组件颜色输入不含 `pureBlackBackground`；应用 AMOLED 逻辑保持不变，不声称小组件外框已应用纯黑覆盖。
   - 电器识别标题使用 `FontWeight.Bold`，紧凑布局 `14sp`、宽裕布局 `18sp`；面板网格名称 `13sp`。温度使用 Bold 与主题 `primary`，常规宽裕布局 `36sp`、紧凑布局 `20sp`；字体缩放达到 `150%` 时分别使用 `24sp`、`16sp` 并继续遵循系统缩放。次级房间和辅助信息使用 `onSurfaceVariant`，不添加装饰性彩色标题或额外解释行。
   - 宽裕单键组件在大字号时为名称与房间保留 `80dp` 高的识别区；其中高度小于 `208dp` 的空调布局隐藏次级房间行并改用 `56dp` 名称区，完整无障碍名称仍包含房间。状态组件的宽矮布局使用 `56dp` 读数区，大字号名称区为 `56dp` 并收起房间；宽高布局大字号名称区为 `80dp`。窄高布局在至多两排副键时使用 `48dp` 名称区、`48dp` 紧凑读数区与 `8dp` 间隔，确保 `152dp × 240dp` 和 `200%` 字号下四个常用操作全部可见。面板列表识别区保留 `8dp` 起始内边距。其余高空调状态布局在正常字号下使用 `88dp` 读数区容纳辅助图标，大字号读数区为 `56dp`。
   - 所有可点击按钮的最小触控热区不少于 `48.dp × 48.dp`；当前首要操作与电源使用 `SquareIconButton`，副操作使用 `CircleIconButton`；允许按任务使用圆形、圆角方形或静态胶囊，形状表达主次关系且同类操作一致。单键组件空间充足时首要操作可为 `56dp`，其他布局维持至少 `48dp`；不按动画帧变化形状。图标按钮**仅展示应用内已有图标**并配完整无障碍名称，不显示按钮外操作名、不显示“上次发送/上次设定”或任何发射状态文字，禁止使用 `U+00B7` 分隔号；无房间时直接收起房间行，不留空行或占位文字。
   - 文本必须显式设置 `maxLines`（超出省略），防止小尺寸或大字号下文字被异常裁切；发送失败仅在实际失败的按键上显示警示图标与无障碍说明，点按重试该动作。

4. **状态闭环与 XML 配置**：
   - 包含 `Loading`（加载中）、`Empty` / 未配置 / 绑定失效（空状态引导点击添加或重新选择遥控器）和 `Content`（正常数据展示与直接发射）三种完整 UI 状态切换。
   - 配套完整的 `res/xml/` 小组件元数据（包含 Android 12+ 的 `targetCellWidth`、`targetCellHeight`、`minWidth`、`minHeight`、`resizeMode="horizontal|vertical"`、`updatePeriodMillis="0"`、`previewLayout` / `previewImage`、`configure` Activity）以及 `AndroidManifest.xml` 中的 `Receiver` 注册代码。
   - Launcher 选择器的静态预览由 `WidgetPickerPreviewRoborazziTest` 复用真实 Glance 内容生成，覆盖英语、日语、简体中文、繁体中文及日夜主题；视觉修改须同步 `widget_picker_*` 与 `widget_preview_*` 的无损 WebP 资源。录图并视觉验收后运行 `python -B tools/sync_widget_previews.py --sync` 同步资源，省略 `--sync` 时只校验尺寸、透明格式与文件一致性。配置页的实时预览保留列表滚动，所有点击、长按和音效仅在预览 Host 内屏蔽，不能发射信号或跳转页面；缓存键包含字号和密度。
