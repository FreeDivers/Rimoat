# USB 红外协议与模块说明

Rimoat 支持直接通过 Android USB Host 接口或 USB 转串口驱动连接外置红外发射与学习硬件。所有 USB I/O 均在 `Dispatchers.IO` 执行，并由 `IrTransmitterManager` 统一调度与加锁。

## 支持的硬件与协议概览

| 设备 / 模块 | 连接与协议机制 | 信号格式与限制 |
|---|---|---|
| **Tiqiaa / Tview (`10c4:8468`)** | USB Bulk IN/OUT；`0x02` 外层分包（每包最多 56 字节有效载荷）；16µs 脉冲量化与载波表索引 | 33–45 kHz RAW；NEC 自动编码为 RAW；单帧 ≤ 1024 字节、总时长 < 2 秒；以序列号匹配的 `O 09` 应答确认发送完成 |
| **ElkSmart D552** | 连接探测发送 `FC FC FC FC`，校验回复前缀并匹配型号 `70 01` | RAW；NEC 自动编码为 RAW；按完整写入与发送耗时等待 |
| **ElkSmart D226** | 连接探测识别型号 `02 AA`；采用脉冲字典与频率表 Huffman 压缩编码、62 字节分块写入 | RAW；NEC 自动编码为 RAW |
| **YS-IRTM（USB-UART 模块）** | 基于 `usb-serial-for-android` 驱动（8N1、无流控）；线帧为 `[模块地址, F1, 地址字节0, 地址字节1, 命令]` | 支持 4800 / 9600 / 19200 / 57600 bps；标准及扩展 NEC（命令反码由模块生成）；等待模块 `F1` 应答 |
| **USB IR Toy v2（CDC ACM）** | 115200 / 8N1；校验 `V2xx`（固件版本 ≥ 22）并进入 `S01` 采样模式；每 tick 为 `64/3` 微秒，大端 16 位计时并以 `FFFF` 结束，按 32 字节分块握手 | 20–100 kHz RAW；NEC 自动编码为 RAW；校验缓冲区握手、`t` 字节计数及 `C`/`c` 完成应答 |

## 串口模块与底层驱动说明

- 应用通过 `usb-serial-for-android:3.9.0` 处理 CH34x、CP21xx、FTDI、PL2303 与 CDC ACM 等芯片的控制传输、波特率配置及完整写入。
- 接收缓冲为 4096 字节并保留未消费数据，以兼容 FTDI 状态头及单包合并多条协议应答的情况。
- 读取及写入异常、超时或协议错误均中止当前发送并释放端口锁；IR Toy 在出错后会尝试发送结束标记并复位采样状态。

### Tiqiaa / Tview 分帧细节

- 支持 30 项标准载波频率表（33–45 kHz），编码时自动匹配最接近目标频率的索引。
- 发送前自动规范化正负脉冲并拒绝零脉冲或超长帧；接收端按 envelope 声明的长度拼帧，忽略 USB 63 字节包尾部的残留字节并在发送前清理历史输入缓冲。

### ElkSmart D226 压缩格式

- 压缩载荷依次包含：2 字节大端符号数、按符号升序排列的 `[符号, 次数高字节, 次数低字节]` 表、1 字节补零位数与高位优先 Huffman 编码流。

## 应用内串口配置流程

1. 打开主页顶部或遥控页面的发射通道设置面板，进入“串口模块配置”。
2. 选择对应的模块类型（如 YS-IRTM 或 USB IR Toy）并填写串口参数（波特率、模块地址等）。
3. 插入 USB 设备后选择目标设备及端口序号（从 0 开始），授予 USB 权限并保存。
4. 在发射通道面板中选择自动路由或指定 USB 串口，即可使用遥控面板测试发送。

配置通过 `data/SerialIrSettingsStore.kt` 原子写入应用私有目录 `filesDir/serial_ir.json`，优先按 USB 序列号匹配设备；无序列号时仅在 VID/PID 唯一匹配或当前会话明确选中时启用。

## 公开开源参考资料

- [micropython-ys-irtm](https://github.com/mcauser/micropython-ys-irtm)：YS-IRTM 5 字节帧结构、模块地址、波特率及 `F1` 应答格式。
- [USB IR Toy 固件 (`IRs.c`)](https://github.com/DangerousPrototypes/USB_IR_Toy/blob/master/Firmware-main/IRs.c) 与 [`prj_usb_config.h`](https://github.com/DangerousPrototypes/USB_IR_Toy/blob/master/Firmware-main/prj_usb_config.h)：采样计时换算、分包握手及 `04D8:FD08` 设备标识。
- [PyIrToy](https://github.com/crleblanc/PyIrToy/blob/master/irtoy.py)：固件版本查询、32 字节分包与采样模式恢复顺序（CC BY-SA 3.0）。
- [usb-serial-for-android v3.9.0](https://github.com/mik3y/usb-serial-for-android/tree/v3.9.0)：Android USB 串口芯片驱动（MIT）。
