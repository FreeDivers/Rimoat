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
- **自定义可点击容器：** 遥控器卡片、品牌行、设置行、房间与编辑预览共用 `MutableInteractionSource`，使用 `rememberExpressiveShape` 在当前形状与按压形状之间过渡。只改变轮廓和视觉反馈，不改变布局占位、焦点顺序或点击区域。不可点击的容器保持静态。

| 场景 | 首选组件与使用规则 |
|---|---|
| 分类、模式、风速、挡位、定时预设与单选列表 | `ToggleButton`；关联短选项可放入 `ButtonGroup` 获得分组按压扩张。单选容器声明 `selectableGroup()`，选项保留 `selected` 与 `Role.RadioButton`；实际选择状态由页面状态驱动，重复点选不得产生第二份状态。 |
| 同一任务的并列动作 | `ButtonGroup`；按钮按真实可用状态启用。较长标签采用换行、自适应列或官方溢出菜单，不能挤压到无法辨认。溢出项保留原名称、回调和禁用状态。 |
| 页面底部主操作与相邻辅助操作 | `HorizontalFloatingToolbar`；通过实际译文宽度与可用空间决定布局。窄屏或大字时切换为纵向容器，完整保留按钮文字和热区，不强制单行。 |
| 多个相关悬浮快捷操作 | 评估 `FloatingActionButtonMenu`；只有单个“添加”动作时保留普通 FAB，不额外制造菜单层级。 |
| 一个主动作及其同一任务的替代操作 | 评估 `SplitButtonLayout`；前提是主次关系明确。测试发射与保存、恢复与永久删除等含义不同的操作继续独立呈现。 |
| 不确定耗时的加载、恢复或发送等待 | `LoadingIndicator`；需要独立容器强调时使用 `ContainedLoadingIndicator`。仅在真实忙碌状态显示，结束即移除。 |
| 下载进度、已学习数量或外设配置加载 | `LinearWavyProgressIndicator`；已知进度绑定真实数值，未知进度使用不确定模式。静态完成比例关闭波浪平移，不模拟任务前进。 |
| 红外接收等待 | `ContainedLoadingIndicator` 配合真实剩余秒数；可以使用确定进度形变表示剩余比例，不能把它描述为电器自身倒计时。 |

引入或修改组件前，查阅 [Material 3 Components](https://m3.material.io/components) 与 [Android Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)，对照工程实际依赖核实 API、默认形状和无障碍行为。Glance 小组件使用第 9 节的独立组件规则。

## 5. 间距与布局

- **基准单位：** `4dp`（`2dp` 仅限组件内部微对齐使用）
- **间距阶梯：** `[4, 8, 12, 16, 20, 24dp]`
  - `4–8dp`：图标与文字间距、相邻可交互目标的最小间距（`>= 8dp`）
  - `12–16dp`：标准卡片与容器内边距、表单参数间距（`12dp`）、数字键盘横向间距（`16dp`）
  - `20–24dp`：分组间距与对话框内容内边距
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
- **白底（`#ffffff`）配主文字（`#1f1f1f`）：** 对比度 ≈ **16.6:1**（远超 WCAG AAA `7:1`）
- **主色绿（`#3e6700`）配纯白文字（`#ffffff`）：** 对比度 ≈ **6.7:1**（超过交互控件 WCAG AA `4.5:1`）
- **白底（`#ffffff`）配次级文字（`#444746`）：** 对比度 ≈ **9.3:1**（超过 WCAG AAA `7:1`）
- **中性表面（`#f2f4f0`）配主文字（`#1f1f1f`）：** 对比度 ≈ **15.1:1**（超过 WCAG AAA）
- **深色背景（`#121412`）配深色主绿（`#aed67b`）：** 对比度 ≈ **10.8:1**（超过 WCAG AAA）

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

- 顶栏标题为 `Rimoat`（`app_name`），右侧为发射通道状态图标与设置齿轮。保留 `SearchBar`；竖屏分类采用可横向滚动的 `ButtonGroup` 与 `ToggleButton`，选中分类滚入可见区域。横屏侧栏按空间换行排列 `ToggleButton`，两种布局共用筛选状态。
- 房间使用锚点 `DropdownMenu`，保留选中项通栏底色、对准触发位置且无右侧对勾的样式；房间数量与名称可变，不强行改成固定宽度的内联组。排序图标保留当前值与无障碍说明。
- 空库展示空状态与主添加按钮；单一添加入口保留普通 FAB。加载只在实际读取期间展示波浪进度。
- 遥控卡片最小高度 `72dp`、间距 `8dp`，左侧 `24dp` 电器图标，中部名称允许完整换行并保留 `16dp` 收藏星标，右侧管理按钮热区为 `48dp`。卡片按压形变与弹簧列表重排不得改变点击、收藏或删除的触发方式。
- 保留右滑收藏、左滑移入回收站：底部距侧边 `28dp` 处露出 `48dp` 正圆图标，`80dp` 完全显现，`104dp` 触发阈值，最大拖动 `144dp`。滑动阈值不随弹簧视觉进度改变。

### 8.3 遥控面板、定时与布局编辑

- 顶栏提供编辑、学习模式开关和发射源反馈。静止发射按键为正圆：电源 `72dp`、常规 `64dp`、数字键 `56dp`，数字键横向间距 `16dp`；按压允许第 6 节规定的小幅形变。方向盘保留 `216dp` 圆角方盘与 `64dp` 中央确认键，五区独立焦点按“上 → 左 → 确认 → 右 → 下”遍历，物理左右不随 RTL 镜像。
- 空调主卡片包含 `displayLarge` 温度、加减键和电源图标切换按钮；空间不足时加减键移至读数下方，温度边界由既有状态约束禁用。模式、风速、风向等保留圆键单击循环与圆外 `48dp` 直选入口；直选菜单使用 `ToggleButton` 列，保留当前选中项，提交后立即关闭并禁用退出过程中的操作。
- 定时预设使用 `ToggleButton`；滑块保留离散取值、限值、触觉和状态绑定。底部使用 `HorizontalFloatingToolbar`，大字号或窄屏下以纵向布局完整展示“关闭定时”和确认动作，不压缩核心时间信息；是否显示关闭动作仍由当前定时状态决定。
- 扩展功能 `<= 6` 个且不超过可用高度 `30%`（或 `240dp`）时同页合并，否则使用“常规功能 / 更多功能”页签与 `HorizontalPager`。横竖屏共用选择、数字键展开与学习状态。
- 布局编辑预览复用按键图形与按压轮廓，拖动采用轻微抬升和弹簧重排；隐藏、排序、恢复默认及保存继续执行原有回调。形变不得更改拖动命中范围或持久化内容。
- 不可用按键灰显，`5 秒` 内连续点按 `3 次` 弹出 `Indefinite` 诊断 `Snackbar`，含原因详情与关闭；详情区分缺信号、模式限制、编码限制与硬件不可用。这些诊断条件保持业务含义。

### 8.4 添加、品牌、对码与红外学习

- **添加对码：** 保留“选择类型 → 选择品牌 → 按键对码 → 确认保存”，每次进入从类型选择开始。类型卡、热门品牌卡和品牌行采用按压形变，加载采用 `LoadingIndicator`。品牌页保留两字母大写短标（无拉丁或拼音字母时回退 `IR`）及右侧 `56dp` 通道、`48dp` 宽 A–Z/`#` 索引；索引以准确滑动定位为主，不增加移动命中目标的弹跳。
- **对码操作：** 方案名称仍按列表顺序生成；“无响应 / 有响应”使用 `ButtonGroup`，保持触觉、启用条件和单次反馈提交。长译文可进入官方溢出菜单，两个结果必须均可访问。“使用此方案”放入 `HorizontalFloatingToolbar`，忙碌时真实禁用。
- **学习配置：** 电器类型和学习按键队列采用 `ToggleButton`，保持单选语义。学习网格按内容调整为 `1–3` 列；已学键用 `primaryContainer` 底色和勾选图标，当前键保留 `2dp` 主色边框，状态不能仅靠颜色区分。
- **学习状态：** 顶部接收器状态与 `headlineLarge` 当前按键名保持清晰；等待接收时使用 `ContainedLoadingIndicator` 与真实剩余秒数，捕获后展示真实波形、脉冲数、时长和频率。已学习比例用确定的 `LinearWavyProgressIndicator`，关闭波浪平移，恢复会话的忙碌状态使用容器加载指示器。
- **学习动作：** 测试发射和确认/保存是独立动作，宽度足够时进入浮动工具栏；窄屏、长译文或 `200%` 字号改为纵向布局。单键学习对话框使用同一规则，不强制单行。测试、保存、接收期间的禁用条件以及取消、重录、跳过和继续学习回调保持不变。

### 8.5 设置、发射通道与管理页面

- 设置页按“外观、遥控器、备份与导入、应用”分组，每行最小 `64dp`，间隔 `8dp`；可点击行与开关所在行使用轮廓形变。取值右对齐并保留锚点菜单；语言与配置选择继续等待弹层退出后应用，避免新宿主重建期间重复交互。
- 动态取色、深浅色与纯黑偏好由统一主题解析；纯黑只覆盖第 1 节的四个基础表面。开关仍为 `Switch`，多项备份导入选择仍为 `Checkbox`，不将不同语义统一改成单选。
- 发射通道和串口模块类型使用 `ToggleButton` 单选列表；可用、待授权、未连接与演示状态分别展示。串口配置读取使用波浪加载指示器；授权与刷新只调用现有逻辑，不能因选择动效自动重放发送。
- 房间分配动作使用浮动工具栏，可点击房间卡与移入房间行采用轮廓形变；恢复与永久删除保持独立操作及危险色语义。回收站每项两个操作保留 `48dp` 热区，大字或窄屏可移至右下；恢复加载使用 `ContainedLoadingIndicator`。
- 关于、仓库链接及离线许可列表共用主题、按压轮廓和标准形变按钮；许可加载使用 `LoadingIndicator`。备份导入和更新对话框保留真实进度与错误状态，下载使用确定的波浪进度，不改变取消或安装条件。

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
