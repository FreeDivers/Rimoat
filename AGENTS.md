# 纯本地 Android 红外遥控器（Rimoat）

## 0. 默认协作与分级验证（最高优先级）

> **优先级**：本节优先于本文件其他章节和 `docs/` 中的构建、测试、Lint 与模拟器规则；用户当前明确指令优先。

- 始终使用通俗的简体中文沟通。修改后说明改了什么、实际验证结果、测试耗时、截图位置及能否运行预览；未运行的检查不得宣称通过。

### Android UI 与功能验证规范（分级双轨制）

1. **第一顺位（日常 90% 场景，默认强制使用）：Robolectric + Roborazzi**
   - 凡是修改 Jetpack Compose 界面布局、样式、文案、主题适配、表单输入、按钮点击、页面状态流转以及常规 Bug 修复，一律在 `src/test/` 下编写或更新与改动相关的 JVM 本地测试，不需要用户每次另行要求。
   - 使用 `./gradlew :app:recordRoborazziDebug --tests '<相关测试类>' --console=plain -q`（Windows 使用 `.\gradlew.bat`，其他模块替换 `:app:`），运行相关交互，通过 `captureRoboImage()` 导出交互前后的**无损 WebP**截图，并直接打开图片做视觉验收，确认真实界面、文字和操作结果正常，不是白屏或黑屏。WebP 是用户确认的长期格式，不改回 PNG。
   - 保持 `isIncludeAndroidResources = true` 和 `GraphicsMode.NATIVE`；复用真实页面和资源，使用有意义的交互、状态断言。已有参考图先检查差异，确认变化符合预期再更新，不能靠重新录图掩盖回归；需要比对时使用对应的 `verifyRoborazziDebug` 任务。
   - MD3E 组件与动效修改须覆盖实际点击、选择、忙碌禁用和操作结果，并同步校验截图。弹簧动画按状态收敛等待，不依赖旧的固定补间时长。Dialog 等独立宿主的大字号测试在创建内容前用 `RuntimeEnvironment.setFontScale()` 设置真实资源字号，核对实际 `fontScale` 和按钮滚动可达性，不能只在外层覆盖 `LocalDensity`。
   - **严禁**在上述常规开发中启动 Android CLI 无头模拟器（QEMU）或调用 ADB；不能因本地测试尚未编写、依赖下载或编译失败就改走模拟器。

2. **第二顺位（特殊 10% 场景，仅作为备用方案）：Android CLI + 无头 QEMU + ADB**
   - 仅当本次改动涉及以下 JVM 无法真实模拟的能力，或进行大功能最终验收时，才允许启用：WebView 网页渲染、视频播放、地图组件、相机预览；系统原生权限弹窗、真实软键盘弹出遮挡、通知栏或跨应用跳转；整个大功能彻底完工后的最终全链路冒烟验收。
   - **启用前必须说明原因**：启动 QEMU 前，先用一句话向用户说明“为什么 Roborazzi 测不了这项功能，需要临时启用备用模拟器”，指出本次具体能力或最终全链路验收范围。
   - **用完即焚（强制内存回收）**：本次启动的每个无头 QEMU 模拟器，无论测试成功、失败、异常或中断，都必须在结束前关闭。使用 `try/finally` 或等价清理流程，执行 `adb -s <本次模拟器序列号> emu kill`；ADB 不可用时，仅停止已核实属于本次启动的 QEMU 进程。核验目标已退出，绝不留在后台常驻，也不得误关用户已有的其他实例。
   - 现有 Android CLI / 无头 QEMU / ADB 测试脚本和配置完整保留作为备用，不删除、覆盖或破坏。

### 执行范围与失败上限

- 纯文档、AI 规则文件修改只做静态检查，不运行 Gradle、测试或模拟器；应用里的文案修改属于第一顺位，不能当作文档例外。
- 默认只运行本次改动相关的测试，用 `--tests` 限定范围。不自动扩展为全量单元测试、Lint、`check`、`build`、release/optimized 构建或全部 Instrumentation；第二顺位确需的最小构建和设备测试按需执行，其他重型检查由用户明确要求。
- 每条 Gradle 命令都必须带 `--console=plain -q`。失败时只检查关键错误，不输出大段堆栈。
- **最多修复重编译 2 次**：首次编译失败后，最多修复并重试两次；仍失败就立即停止，用通俗中文说明阻碍，不继续循环或自动改走 QEMU。

### Git 提交与一键发版

- 每次完成一个小功能并经用户确认没问题后，主动执行一次规范的 `git commit`，无需用户另行催促。提交信息使用英语，采用 `type(scope): summary` 格式（scope 可省略），准确描述本次改动；仅提交该功能相关且已确认的内容。
- 严禁随意删除、覆盖或破坏现有 APK 签名配置、签名密钥及 GitHub Secrets 设置，保持构建脚本与工作流中的签名引用有效；不得为绕过构建失败而移除签名或更换密钥。
- 用户说“发版”或“发布新版本”时，自动按以下固定顺序执行，无需用户逐步指挥：

1. **检查本地改动**：检查本地代码是否有未保存、未提交的改动，用 `git status` 和差异检查核对暂存、未暂存及未跟踪文件，明确本次发布范围；保留已有工作，不擅自丢弃改动或混入无关文件。Git 状态只能反映磁盘文件，不能据此断言编辑器没有未保存内容。
2. **编写更新说明**：以上一个发布版本的 git tag 为基准，审阅该 tag 到当前待发布内容的全部代码改动，包括拟纳入发布的本地改动。用面向普通用户、通俗易懂的简体中文和英文概括实际变化，覆盖保存到项目根目录 `RELEASE_NOTES.md`，不追加历史版本或开发流水账。固定模板如下：

   ```markdown
   ### 简体中文

   本次版本的中文更新说明。
   ### English

   English release notes for this version.
   ```

3. **建议版本并等待确认**：根据改动大小建议新版本号：小修补递增最后一位（如 `v1.0.1`），新增功能递增中间一位并将最后一位归零（如 `v1.1.0`）。将建议的版本号和写好的完整中英文更新说明一起发给用户确认；如需同步应用版本字段，一并说明。此时等待用户回复“确认”或“可以”，尚未确认不得执行发布提交、打 tag 或推送。
4. **确认后自动发布**：收到用户对上述版本号和更新说明的确认后，按现有版本管理方式同步必要的版本字段，以英语提交信息将 `RELEASE_NOTES.md`、必要的版本变更及已确认纳入发布的代码提交到 Git，创建对应版本号的 tag，并将发布分支代码和该 tag 一起推送到 GitHub，触发现有自动打包工作流，无需再次逐项确认。核对推送结果和工作流触发状态；失败时如实说明，不擅自覆盖已有 tag 或强制推送。

本文件保留核心约束与现行工具索引：

| 任务 | 参考文档与工具 |
|---|---|
| 项目概览、构建与测试命令 | [README.md](README.md) |
| UI、交互与桌面小组件设计规范 | [DESIGN.md](DESIGN.md) |
| USB 红外外设与串口协议规格 | [USB 红外协议说明](docs/USB_IR_PROTOCOLS.md) |
| 公开仓库隐私、路径与缓存产物扫描 | [tools/audit_public_files.py](tools/audit_public_files.py) |
| 国际化资源一致性与码库标签审计 | [tools/audit_i18n.py](tools/audit_i18n.py) |
| 品牌图标与矢量资源校验 | [tools/verify_brand_assets.py](tools/verify_brand_assets.py) |

## 1. 技术栈与运行环境
- **语言**：Kotlin 2.2+（当前 `2.2.10`，Coroutines、StateFlow、SharedFlow）；使用 AGP 内置 Kotlin，Compose 与 Serialization 插件保持兼容版本。
- **工具链**：AGP `9.5.2`、Gradle `9.6.0`、JDK 21；`compileSdk` 为 `37.1`（`release(37) { minorApiLevel = 1 }`）。本机 JDK 路径仅在当前进程配置，不写入受跟踪文件。
- **UI 框架**：Jetpack Compose BOM `2026.11.01`、Compose `1.13.0`、Material 3 `1.5.0`，采用 Material 3 Expressive（MD3E）与单 Activity 架构；依赖基线以 Gradle 配置为准。
- **本地 UI 验证**：Robolectric + Roborazzi `1.79.0`，原生图形模式与无损 WebP，验证范围遵循第 0 节。
- **桌面小组件**：Jetpack Glance `1.2.0`（`glance-appwidget`、`glance-material3` 同版本），用于单键、遥控面板和电器状态小组件；以 Glance 实现静态 MD3E 视觉，依赖不受 Compose BOM 管理，工程与验证边界见第 8 节。
- **序列化与存储**：`kotlinx.serialization-json` + `context.filesDir` 下的本地 JSON 文件（纯离线，无云端依赖）
- **硬件接口**：
  - 内置红外：Android 原生 `android.hardware.ConsumerIrManager`
  - USB 红外：Android 原生 `android.hardware.usb.UsbManager`（支持 USB 转串口 `com.github.mik3y:usb-serial-for-android` 或原生 BulkTransfer / ControlTransfer）

## 2. 包结构规范
保持清晰的模块化包结构（位于 `app/src/main/java/org/qinyan/rimoat/`）：
- `hardware/`：`IrTransmitter`、`IrSignal`、`IrTransmitterManager`、`BuiltInIrTransmitter`、`IrProtocolEncoder` 及 USB 权限会话控制；下设 `usb/`（USB 红外外设）、`serial/`（USB 转串口模块）、`audio/`（音频口红外）与 `learning/`（红外信号学习与清洗）。
- `data/`：`@Serializable` 数据模型（`Models.kt`、`AcSettings.kt` 等）、`RemoteRepository`（本地 JSON 读写、备份与迁移）、`NativeAcEncoder`、`RemoteCommandExecutor` 及 `db/`（离线码库 SQLite 访问）。
- `i18n/`：应用语言切换、本地化资源解析封装与内置码库/按键标签映射。
- `ui/`：Compose 页面（`screens/`、`learning/`）、通用组件（`components/`）、ViewModel（`viewmodel/`）、主题令牌（`theme/`）与动效配置（`motion/`）。
- `widget/`：Jetpack Glance `RemoteAppWidget`、`RemoteAppWidgetReceiver`、配置 Activity（`WidgetConfigurationActivity`）、尺寸桶与后台发射运行时。

## 3. 核心硬件抽象层（统一接口）
UI 层与小组件层不得感知红外信号由内置红外、USB 外设、串口模块还是音频口发送，所有发射必须统一经过 `IrTransmitterManager`（或封装它的 `RemoteCommandExecutor`）路由：
- **接口与信号定义**：以 `hardware/IrSignal.kt`（`IrSignal.Raw` 微秒脉冲数组、`IrSignal.Nec` 标准地址与命令码）和 `hardware/IrTransmitter.kt`（`IrTransmitter` 接口、`TransmitterType` 通道枚举）为准。
- **协议编码转换**：当 `IrSignal.Nec` 传入仅支持脉冲发射的通道（如 `BuiltInIrTransmitter`）时，先通过 `IrProtocolEncoder` 转换为 `IrSignal.Raw`（38000Hz + 微秒脉冲数组），再调用底层硬件接口。
- **通道路由策略**：若已连接并授权外置 USB / 串口红外设备，优先路由至对应外置发射器（除非用户手动指定发射通道）；否则回退至 `BuiltInIrTransmitter` 或模拟模式。

## 4. 本地数据模型与持久化
- 所有遥控器与空调状态配置使用 `kotlinx.serialization` 以 JSON 保存在本地（模型定义以 `data/Models.kt` 的 `RemoteProfile`、`RemoteButton`、`DeviceCategory`、`SignalType` 及 `data/AcSettings.kt` 为准）。
- 演进序列化数据类时，新增字段必须提供默认值并保持向后兼容，严禁破坏已有本地遥控器、房间、回收站及备份 JSON 的反序列化。

## 5. 严格编码规范
1. **禁止传统 XML 界面布局**：应用页面使用 Jetpack Compose，小组件主体使用 Jetpack Glance。保留 `AndroidManifest.xml`、USB filter 和 Widget provider 元数据；允许必要的字符串、颜色、尺寸、主题、drawable 等 Android XML 资源。`res/layout` 仅允许系统小组件预览、初始加载和组合失败回退所需的最小布局（优先复用 Glance 内置资源），不得借此新增 XML 应用页面或另做一套 XML 小组件主界面。详见第 8 节。
2. **触觉与无声点击**：Compose 中每次遥控按键触发都必须调用 `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` 或 `VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)`，模拟实体按键触感。所有应用点击音保持关闭：主题统一使用 `SoundEffectOnInteraction(enabled = false)`，Dialog、Popup 等独立宿主也须保持无声，不局部开启 Compose `clickable` 默认音效或添加自定义点击音；保留现有触觉反馈。
3. **非阻塞硬件 I/O**：将 `ConsumerIrManager.transmit()` 与 USB/串口传输封装在 `withContext(Dispatchers.IO)` 中执行，确保高频连按不会阻塞主线程与 UI 帧率。
4. **防御性硬件检查**：妥善处理缺失 `FEATURE_CONSUMER_IR` 及 USB 动态插拔事件，严禁崩溃；在无红外硬件的设备或模拟器上提供模拟/日志模式。
5. **复用成熟协议实现（避免重复造轮子）**：当遇到红外发射失败、硬件通信、协议编码或码库异常时，优先对照 [USB 红外协议说明](docs/USB_IR_PROTOCOLS.md) 与成熟的开源红外协议实现（Android 原生 `ConsumerIrManager`、各类 USB 红外外设帧格式、NEC/Raw/空调状态码编码逻辑）进行排查。


### 5.1 Android 国际化与多语言规范（i18n）

1. **用户可见文案资源化**：Kotlin、Compose、Glance 与允许保留的 XML 中不得硬编码应用提供的用户可见文案，包括标题、按钮、提示、错误、状态和无障碍名称。先在 `res/values/` 定义资源，再通过 `stringResource`、`getString` 或统一资源访问封装读取；XML 使用 `@string/...`。允许按模块拆分为 `strings.xml`、`screen_strings.xml`、`widget_strings.xml` 等，翻译放在对应语言目录，资源键保持一致。纯 JVM 测试所需回退限于统一封装或测试夹具，不得成为另一套独立维护的翻译来源。
2. **命名约定**：新增资源键使用小写 snake_case，按 `<screen_or_module>_<element>_<description>` 命名，如 `settings_title_theme`、`remote_btn_power`；跨页面复用文案可用 `common_` 前缀。键名表达用途，不含语言标识，不因译文变化而改名。
3. **完整句子与位置占位符**：动态文案使用完整句子资源及 `%1$s`、`%2$d` 等位置占位符，不使用无编号的 `%s`、`%d`，不通过拼接已翻译片段构造句子。各语言必须保持参数索引和类型一致，允许按语言调整词序。
4. **Android XML 转义**：字符串内容中的 ASCII 单引号、双引号分别使用 `\'`、`\"` 转义；同时遵守 XML 对 `&`、`<` 等字符的转义要求。翻译编辑先静态检查 XML 转义，再按第 0 节第一顺位运行相关本地测试与截图验收。
5. **复数与不可翻译资源**：数量影响措辞或语法时使用 `<plurals>`，通过 `pluralStringResource` 或 `getQuantityString` 选择数量形式，并按需传入格式参数。固定品牌标识、URL、协议标识及仅供开发者使用的调试文本若放入字符串资源，应标记 `translatable="false"`；已有官方本地化名称的品牌不一概禁译，用户可见的错误提示也不能以“调试文本”为由跳过翻译。
6. **布局适应性**：文字按钮与文本容器优先使用内容自适应、最小尺寸、权重、换行或滚动，避免固定宽高造成译文截断。项目规定的正圆遥控按钮、图标和触控目标可保留固定尺寸，长标签在控件外换行并提供完整无障碍名称，不缩小触控区域迁就译文。布局间距与对齐优先使用 `start` / `end`；物理方向按键及具有明确方向语义的图标不能机械镜像。视觉细节遵循第 7 节与现有主题实现。
7. **展示与持久化分离**：语言切换不得改写用户填写的遥控器名称、房间名、自定义按键名或已保存信号。内置按键、枚举和码库名称在展示边界按稳定 ID / 来源映射本地化；业务判断、协议编码和序列化不得依赖翻译后的文字，也不得批量替换持久化中文标签。
8. **语言切换与验收**：保持默认资源与已支持语言键和占位符一致，不得静态永久缓存译文；日常多语言界面使用第一顺位配置语言、字号和主题进行本地验收，涉及系统能力或最终全链路验收时才按第二顺位检查设备，未运行的检查不得记为通过。
9. **语言资源目录**：当前使用完整英语默认资源 `values/`，并维护简体中文 `values-zh/`、繁体中文 `values-b+zh+Hant/`、英语 `values-en/`、日语 `values-ja/`；默认与显式英语资源保持一致。需要地区限定时使用 `values-en-rUS`、`values-zh-rTW` 等格式，地区前的小写 `r` 不可省略；BCP 47 格式如 `values-b+en+US`、`values-b+zh+Hant` 也可使用，不得写成 `values-en-US` / `values-zh-TW`。语言标签（如 `zh-CN`）与资源目录限定符不是同一语法；语言列表、Manifest 的 `localeConfig` 与 `res/xml/locales_config.xml` 应保持一致。新增地区变体仍须保留完整默认资源，仅在构建时校验。
10. **ViewModel 文案状态**：不得仅为读取字符串向 ViewModel 注入或保存 Activity、View 或其 Context。新增或重构的提示/错误状态使用 `UiText` 密封类型或等价结构，携带 `@StringRes` 资源 ID、格式参数，复数另携带 `@PluralsRes` ID 和数量，在 Compose/Glance 展示边界按当前应用语言解析；`DynamicString` 仅用于用户输入或确实动态的外部文本，不能绕过应用文案资源化。底层返回稳定错误类型，由展示层映射资源，不根据中文或译文的前缀判断错误类型。Application Context 不等同于 Activity 泄漏，但已有 `AppStrings` 全局读取不能成为在 ViewModel 中缓存已翻译文案的理由；相关本地测试按第 0 节第一顺位编写和运行，回退仍遵循第 1 条。
11. **应用语言 API 与迁移边界**：跨版本手动语言切换优先统一到 AndroidX `AppCompatDelegate.setApplicationLocales(LocaleListCompat)` 与 `locales_config.xml`，同步处理 `MainActivity`、`WidgetConfigurationActivity` 的 `AppCompatActivity` 宿主与主题兼容、Android 12 及以下持久化与旧偏好迁移；Android 13+ `LocaleManager` 与应用内选择须共享同一语言来源。当前 `AppLanguage` 自管的 `attachBaseContext` / `createConfigurationContext` 路径属于迁移范围，不再复制扩散，也不得用 `Resources.updateConfiguration()` 或仅改 `Locale.setDefault()` 替代。Widget 与后台的非 Activity Context 需单独适配，迁移完成后须覆盖新旧 API、跟随系统及小组件刷新。
12. **日期、时长与数字**：回收站时间、定时预计开关机时间和数字按当前应用资源 Locale 格式化，不假定 `Locale.getDefault()` 等于应用内选择。日期/时间采用本地化格式器并遵循系统 12/24 小时制与明确时区；定时时长按持续时间处理（搭配本地化单位与复数）。最低 API 为 24，使用 `java.time.DateTimeFormatter` 前需核对核心库脱糖支持，否则用兼容 API。数字使用 `NumberFormat`；协议数值、JSON、稳定 ID 与机器日志保持机器格式，不写回本地化展示文本。

## 6. Android 开发与验证

运行第 0 节对应轨道的 Gradle 或设备验证时，遵守以下基础约束：
- 使用项目 `.\gradlew.bat` 并在当前进程配置兼容 JDK，禁止在 `gradle.properties` 中固化本机 Java 路径；所有 Gradle 命令必须带 `--console=plain -q`。
- 第二顺位设备命令明确指定 `--device=<serial>` 或 `-s <serial>`；同一设备的 Instrumentation 串行运行并检查独立 PASS，不与 Android CLI layout 同时占用 UiAutomation；测试包、主包与 ABI 必须匹配。
- 保护用户遥控器、房间、回收站和设置；修改模拟器字体、主题、尺寸、密度或旋转前记录原值并恢复；所有设备截图转存为 ≤ 1 MB 的 WebP；未实际运行的项目不得记为通过。

码库与协议的当前约束：
- 离线码库源或 `ac_states.db` 更新后，使用本地打包脚本重建运行时码库 `catalog_v1.db` 及 `ac_index.json`；数据格式版本与 `CatalogUpgrade.VERSION` 分别维护。
- 协议 670 已启用小时恢复，`NativeAcEncoder` / `AcTimerOptions` 开放 0–1440 分钟。

## 7. 设计规范

- 界面视觉、配色、字体、间距、组件样式、动效与交互细节以 [DESIGN.md](DESIGN.md) 与 `ui/theme/`（`Color.kt`、`Shape.kt`、`Spacing.kt`、`Theme.kt`、`Type.kt`）为准；修改 UI 前须先对照 [DESIGN.md](DESIGN.md)。
- 应用采用 MD3E：主题使用 `MaterialExpressiveTheme` 与 `MotionScheme.expressive()`，动效从 `MaterialTheme.motionScheme`（或 `AppMotion.current`）读取弹簧参数，不另建固定时长体系；密集遥控区局部使用 `MotionScheme.standard()` 降低弹跳，保持触控稳定。
- 全屏页面通过 `ProvideExpressiveCanvas` 与 `ExpressiveSurface` 维持浅绿画布/纯白卡片（深色为深灰画布/低容器卡片）层级，不套硬边 `1dp` 描边。顶栏按页面角色选用 `ExpressivePageTopBar`（竖屏 `MediumFlexibleTopAppBar` / 横屏紧凑 `TopAppBar`）或 `ExpressiveCompactTopBar`；双页切换使用 `ExpressivePageSwitcher`。
- 短筛选与定时预设使用 `ToggleButton`，全宽单选列表使用 `ExpressiveSingleChoiceRow` 并保留 `Role.RadioButton` / `selected` 语义；单功能或窄屏底部直接放置胶囊按钮，不额外套用外层 `HorizontalFloatingToolbar`；多动作入口按场景评估 `FloatingActionButtonMenu` / `SplitButtonLayout`。加载使用 `LoadingIndicator` / `ContainedLoadingIndicator` / `LinearWavyProgressIndicator`，真倒计时与确定进度不使用装饰动画。窄屏、大字号与长译文通过换行、滚动或纵向布局适配。
- 涉及 MD3E 组件、Compose / Glance 或系统 API 时，优先查阅 Android Developers 官方文档（`https://developer.android.com`）与 Material Design 3 规范（`https://m3.material.io/components`），并结合当前工程依赖版本核实默认令牌与行为。
- 完整保留 Material You 动态取色与 `pureBlackBackground`（AMOLED 纯黑仅覆盖既定基础表面，小组件边界见第 8 节）。UI 调整不得改动 ViewModel 业务逻辑、`hardware/`、`cpp/`，普通 App UI 任务不顺带修改 `widget/`。

## 8. 桌面小组件工程边界

桌面小组件的视觉、官方依据表、响应式断点、系统圆角及按钮/文案禁令统一以 [DESIGN.md](DESIGN.md) 第 9 节为准；工程实现、配置与生命周期须遵守以下边界：

- 主体使用 Glance `1.2.0`，通过静态形状、主次容器、配色和字重表达 MD3E，与应用 Compose 界面独立维护；不嵌入 `MaterialExpressiveTheme`、Compose 控件、按压形变或 `MotionScheme` 弹簧。配置页使用 Compose，允许满足系统结果契约的最小配置 Activity。新小组件先说明场景、信息、首要操作与至少三种空间布局，再确定依赖、SizeMode、状态及验收方案。核对当前 Gradle 兼容性，不假定 Compose BOM 管理 Glance，不为套用示例自动升级工具链。
- Glance 独立组合没有 Compose UI `LocalOwner`；应用启动与 Robolectric 共用 `ComposeCompatibility.initialize()`，仅关闭 `ComposeUiFlags.isVectorDrawCacheSharingEnabled`，R8 保持相同值。保留其他 Compose 优化与独立矢量缓存，不使用仅测试生效的绕过。升级依赖时核对该兼容开关，并通过真实小组件预览测试验证后再调整。
- 发射统一经过共用仓库、命令提交管线与 `IrTransmitterManager`，支持冷进程初始化和共享互斥。按 AppWidget id 持久化绑定，Action 使用稳定 ID 并重新读取、校验最新数据；模拟状态不得覆盖真实已保存状态，模拟通道沿用 App 设置，不提供独立演示模式选项。
- 快速点击采用明确的有界策略，不能静默合并加减或积累无界队列；不把物理发射交给延迟重试任务，不在授权、重连、恢复或刷新时自动重放。刷新只读取本地数据；默认 `updatePeriodMillis=0`。
- 以实际 Host 空间布局，共享 `WidgetLayoutSizes` 与预览尺寸桶，不能把 options 或 LocalSize 当作精确测量。主操作保持突出；副操作可按布局使用 `CircleIconButton`、`SquareIconButton` 或静态胶囊，热区不小于 48dp、间距不小于 8dp，关键文字使用适当字重。尺寸变化调整信息和列数，不等比缩小内容；具体形状、颜色与文字规则见 `DESIGN.md` 第 9 节。
- 修改小组件视觉后同步校验真实 Glance 内容、配置预览和 Launcher 静态预览，覆盖四种语言、日夜、窄高尺寸与 200% 字号。配置预览允许滚动但禁止执行发射、跳转或播放音效；滚动新增的行同样受此限制。选择器图片复用 `WidgetPickerPreviewRoborazziTest` 生成并审阅后同步到对应 drawable，不另画一套布局。
- 日夜、动态色和 App 颜色模式变化须同步更新。`WidgetVisualTheme` 从主 App 默认或动态方案映射颜色，`LocalWidgetSurfaces` 区分背景与内部容器；主键/电源使用 `SquareIconButton` 和主色，副键使用 `CircleIconButton` 及容器色。该桥接不等于在 RemoteViews 嵌入 Compose 主题；当前小组件未读取 `pureBlackBackground`，不得宣称已继承 AMOLED 覆盖。
- 数据持久化后更新所有受影响实例，并处理活跃 Glance 会话、加载/错误回退、删除、恢复、ID 映射及失效绑定。配置先设 `RESULT_CANCELED`，保存更新后用相同 ID 返回 `RESULT_OK`；取消重配保留旧配置，删除 Widget 只删除实例设置。
- 保留各具体 `GlanceAppWidget` 的独立类身份及 `ActionCallback` 反射入口；R8 映射检查、optimized 包及真实 Launcher 验证的启动边界按第 0 节执行，未验证项不得记为通过。

## 9. 公开仓库合规与洁净规范（强制执行）

本项目作为通用开源软件发布，所有受 Git 跟踪的文件（源码、注释、字符串资源、测试、配置、脚本、Markdown 文档与 Git 提交信息）必须严格保持干净、通用、专业，严禁引入以下 5 类内容：

1. **严禁版权与私有协议来源敏感词**：
   - 严禁在任何受跟踪文件或注释中提及闭源安装包拆解、二进制协议分析、通信流量截取或第三方闭源软件名称。
   - 协议与数据结构注释一律只写客观、中立的技术规格（如字节偏移、帧结构、脉冲时序），不得引用未入库的外部或本地参考目录。
2. **严禁个人设备痕迹与隐私信息**：
   - 严禁写入个人电脑用户主目录绝对路径、开发者个人真机品牌/具体型号、真实设备或模拟器序列号（ADB serial）、MAC 地址、内网 IP 或真实密钥/口令。
   - 测试夹具与文档示例统一使用中性的通用占位符；提交前通过 `python -B tools/audit_public_files.py` 校验。
3. **严禁开发流水账**：
   - 严禁在代码注释、测试命名、资源或文档中记录过程性废话（如修改轮次、阶段编号、日期戳、AI 调试过程或修复流水账）。
   - 代码注释只解释当前设计“是什么 / 为什么”，不记录“以前是什么 / 哪天改了什么”。
4. **严禁 AI 防御性套话**：
   - 严禁在代码注释、工具脚本、应用文案或仓库文档中写入生硬的 AI 免责声明或防御性过渡套话。
   - 对话汇报时直接用事实说明“实际运行并通过的测试项”与“未运行的检查项”，不写八股免责套话。
5. **严禁撰写过程验证与日志归档文档**：
   - **彻底禁止**在仓库内创建或追加任何过程性验证报告、工程维护日记、单独的设计流水文档、命令行日志归档、证据 JSON 或手工排查记录。
   - 所有验证结论、测试耗时、截图验收结果**仅在当前对话回复中直接向用户说明**。
   - 仓库内允许保留的截图与测试产物仅限：`app/src/test/screenshots/*.webp`（Roborazzi 自动化基准图）和 `docs/images/*.webp`（`README.md` 展示图）。验证过程中产生的临时文件或构建日志必须在任务结束前清理干净。
6. **规则文件体积预算与静态检查**：
   - 根目录 `AGENTS.md` 保持 UTF-8 文件小于 32,768 字节（建议控制在 24 KiB 内）；修改 `AGENTS.md` 后做静态检查，核对 `(Get-Item -LiteralPath AGENTS.md).Length`、本地链接有效性及 `python -B tools/audit_public_files.py`，不运行 Gradle 或模拟器。
