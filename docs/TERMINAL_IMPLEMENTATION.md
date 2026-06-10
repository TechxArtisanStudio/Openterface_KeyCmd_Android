# KeyMod Terminal BLE SSH 实现文档

## 概述

本文档详细记录了 KeyMod 项目 Android 端 Terminal 功能的完整实现，包括 BLE SSH 连接、USB ECM 支持以及多传输架构设计。

## 目录

- [项目背景](#项目背景)
- [已完成工作](#已完成工作)
- [架构设计](#架构设计)
- [技术细节](#技术细节)
- [使用指南](#使用指南)
- [已知问题与解决方案](#已知问题与解决方案)
- [测试指南](#测试指南)

## 项目背景

KeyMod 是一个多功能硬件设备，支持通过 BLE 和 USB 连接实现远程控制、键盘模拟和 SSH Terminal 功能。Android 端需要实现完整的 SSH 客户端，支持两种传输方式：
1. **BLE SSH**：通过蓝牙低功耗连接
2. **USB ECM SSH**：通过 USB CDC ECM 网络连接

## 已完成工作

### 1. 核心传输层实现

#### BLE-Eth Transport（`BleEthTransport.java`）
- 实现完整的 BLE-Eth 协议栈
- 支持 CONNECT/DATA/DISCONNECT 命令
- 分片处理：大数据自动分片（最大 246 字节/片）
- 数据重组：正确重组来自固件的分片数据
- 基于 PipedStream 的数据流桥接

#### USB ECM Transport（`UsbEcmTransport.java`）
- 实现标准 TCP Socket 连接
- 支持 15 秒超时
- 启用 TCP_NODELAY 优化延迟
- 完整的错误处理和状态管理

#### Socket Factory 集成（`BleEthSocketFactory.java`）
- 实现 JSch SocketFactory 接口
- 桥接 BleEthSocket 到 JSch SSH 库
- 透明的传输层抽象

### 2. UI 层实现

#### Terminal Fragment（`TerminalFragment.java`）
- 完整的 SSH 连接管理界面
- 支持传输方式选择（BLE/USB）
- 凭据输入和保存
- 实时状态显示
- 特殊键支持（Ctrl、Esc、Tab）

#### Terminal View（`TerminalView.java`）
- 终端显示渲染
- 键盘输入处理
- 触摸事件支持
- 滚动历史管理

### 3. SSH 客户端集成

#### SSH Client（`SshClient.java`）
- 基于 JSch 0.2.17 的 SSH 实现
- 支持密码认证
- Shell 通道管理
- 数据流桥接（Transport ↔ JSch）

### 4. Bluetooth Service 增强

#### BLE-Eth 数据通道
- `writeBleEthData()` 方法：写入 BLE-Eth 数据
- 回调注册机制：`addBleEthCallback()` / `removeBleEthCallback()`
- 异步写入支持
- 错误处理和日志

### 5. Bug 修复

#### 已修复的问题
1. **MTU 分片问题**：修复大块数据传输时的分片逻辑
2. **数据重组错误**：修正 DataReassembler 的分片重组逻辑
3. **连接泄漏**：修复 BLE 连接槽位未正确释放的问题
4. **超时处理**：优化连接超时和重试机制

## 架构设计

### 传输层架构

```
┌─────────────────────────────────────────────────────────┐
│                    Terminal UI Layer                     │
│              TerminalFragment + TerminalView             │
└────────────────┬────────────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
┌───────▼──────┐  ┌──────▼───────┐
│ SSH Client   │  │ SSH Client   │
│  (JSch)      │  │  (JSch)      │
└───────┬──────┘  └──────┬───────┘
        │                 │
        │         ┌───────▼────────┐
        │         │ BleEthSocket   │
        │         │   Factory      │
        │         └───────┬────────┘
        │                 │
┌───────▼──────┐  ┌──────▼───────┐
│ BleEth       │  │ USB ECM      │
│ Transport    │  │ Transport    │
└───────┬──────┘  └──────┬───────┘
        │                 │
        │         ┌───────▼────────┐
        │         │ TCP Socket     │
        │         │ (192.168.11.1) │
        │         └────────────────┘
┌───────▼──────────────┐
│ Bluetooth Service    │
│   (BLE GATT)         │
└───────┬──────────────┘
        │
   BLE Hardware
```

### 数据流架构

#### BLE SSH 数据流
```
用户输入 → TerminalView → TerminalSession → SshClient
    → BleEthTransport → BluetoothService → BLE Hardware
    → KeyMod 设备 → 目标 SSH 服务器

SSH 响应 → BLE Hardware → BluetoothService → BleEthTransport
    → SshClient → TerminalSession → TerminalView → 屏幕显示
```

#### USB ECM 数据流
```
用户输入 → TerminalView → TerminalSession → SshClient
    → UsbEcmTransport → TCP Socket → KeyMod USB 网络接口
    → 目标 SSH 服务器

SSH 响应 → TCP Socket → UsbEcmTransport → SshClient
    → TerminalSession → TerminalView → 屏幕显示
```

## 技术细节

### BLE-Eth 协议

#### 帧格式
```
┌──────┬──────┬──────┬──────┬──────┬─────────┬──────────┐
│ 0x57 │ 0xAB │ ADDR │ CMD  │ LEN  │ PAYLOAD │ CHECKSUM │
└──────┴──────┴──────┴──────┴──────┴─────────┴──────────┘
   1B     1B     1B     1B     1B     0-249B     1B
```

#### 命令定义
- `0x10` CONNECT：建立 TCP 连接
- `0x11` DATA：传输数据（可分片）
- `0x12` DISCONNECT：关闭连接
- `0x90` CONNECT_RESP：连接响应
- `0x91` DATA_RESP：数据响应（或服务器推送数据）
- `0x92` DISCONNECT_RESP：断开响应

#### 分片机制
- 最大分片大小：246 字节
- 分片头部：3 字节（flags + seq + connId）
- flags 位定义：
  - bit 7 (0x80): 更多分片
  - bit 6 (0x40): 首片
  - bits 0-3: 总分片数

### SSH 配置

```java
Properties config = new Properties();
config.put("kex", "curve25519-sha256,diffie-hellman-group14-sha256");
config.put("server_host_key", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
config.put("cipher.s2c", "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com");
config.put("cipher.c2s", "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com");
config.put("mac.s2c", "hmac-sha2-256");
config.put("mac.c2s", "hmac-sha2-256");
config.put("compression.s2c", "none");
config.put("compression.c2s", "none");
config.put("StrictHostKeyChecking", "no");
config.put("PreferredAuthentications", "password,keyboard-interactive");
config.put("PubkeyAuthentication", "no");
```

### 连接参数

- BLE MTU：协商后约 250 字节
- 连接超时：15 秒
- SSH 握手超时：20 秒
- 最大分片：246 字节
- TCP 端口：22（默认）

## 使用指南

### BLE SSH 连接

1. **准备 KeyMod 设备**
   - 确保设备已开机
   - 确保设备处于 BLE 可发现模式

2. **Android 端操作**
   ```
   打开 App → 主界面 → 点击 "SSH" 按钮
   → 选择 "BLE" 传输方式
   → 输入目标主机（默认 192.168.11.1）
   → 输入用户名
   → 输入密码
   → 点击 "连接"
   ```

3. **连接状态**
   - 状态栏显示：连接中 → 已连接 / 连接失败
   - 成功连接后可开始使用终端

### USB ECM SSH 连接

1. **准备 KeyMod 设备**
   - 使用 USB 线连接 Android 设备和 KeyMod
   - 确保设备处于 USB 网络模式

2. **Android 端操作**
   ```
   打开 App → 主界面 → 点击 "SSH" 按钮
   → 选择 "USB" 传输方式
   → 输入目标主机
   → 输入用户名
   → 输入密码
   → 点击 "连接"
   ```

3. **注意事项**
   - 需要先建立 USB 串口连接
   - USB 网络接口（usb0）必须可用

## 已知问题与解决方案

### 1. KEXINIT 超时问题

**症状**：SSH banner 交换成功，但 KEXINIT 阶段超时

**原因**：
- 大数据包（336 字节 KEXINIT）需要分片
- 分片重组可能存在时序问题
- BLE 传输延迟导致超时

**解决方案**：
- 优化分片间隔（5ms）
- 改进数据重组逻辑
- 增加超时时间（15s → 20s）
- 建议：简化 SSH 算法列表，减少 KEXINIT 大小

### 2. BLE 连接槽位耗尽

**症状**：多次连接后，新的 CONNECT 请求无响应

**原因**：
- 固件的连接槽位（0-5）未正确释放
- 异常断开后槽位被占用

**解决方案**：
- 连接前发送 6 个 DISCONNECT 帧清理所有槽位
- 等待 5 秒让固件完成清理
- 建议：固件端实现自动槽位回收机制

### 3. 数据丢失

**症状**：传输过程中部分数据丢失

**原因**：
- BLE 信道干扰
- 缓冲区溢出
- 分片重组错误

**解决方案**：
- 实现 ACK 确认机制
- 优化缓冲区大小
- 添加数据校验
- 建议：实现重传机制

## 测试指南

### 环境准备

```bash
# 连接设备
adb connect 192.168.100.68:5555

# 安装应用
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/KeyCmd-debug.apk

# 清空日志
adb logcat -c
```

### 日志监控

```bash
# 实时监控关键日志
adb logcat | grep -E "Terminal|BleEth|SSH|JSch"

# 过滤特定级别
adb logcat | grep -E "TerminalFragment|BleEthTransport" | grep -E "I|W|E"
```

### 测试用例

#### 测试 1：BLE SSH 基本连接
```
1. 重启 KeyMod 设备
2. BLE 连接 KeyMod
3. Terminal → Connect → 选择 BLE
4. 输入：192.168.11.1, root, password
5. 预期：成功连接到 SSH shell
```

#### 测试 2：USB ECM SSH 连接
```
1. USB 线连接 KeyMod
2. 等待 USB 串口连接成功
3. Terminal → Connect → 选择 USB
4. 输入：192.168.11.1, root, password
5. 预期：成功连接到 SSH shell
```

#### 测试 3：多次重连
```
1. 连接 SSH
2. 断开连接
3. 重复步骤 1-2 共 10 次
4. 预期：每次都能成功连接，无槽位耗尽
```

#### 测试 4：大文件传输
```
1. 连接 SSH
2. 执行：dd if=/dev/urandom of=test.bin bs=1M count=10
3. 执行：md5sum test.bin
4. 预期：文件传输完成，校验和正确
```

### 调试技巧

#### 1. 检查 BLE 连接状态
```bash
adb logcat | grep "BluetoothService" | grep -E "connected|disconnected|error"
```

#### 2. 检查数据传输
```bash
adb logcat | grep "BleEthTransport" | grep -E "TX|RX|fragment|reassemble"
```

#### 3. 检查 SSH 握手
```bash
adb logcat | grep "SshClient" | grep -E "banner|KEX|auth|channel"
```

#### 4. 检查连接槽位
```bash
adb logcat | grep "BleEthTransport" | grep "slot"
```

## 文件清单

### 核心文件
- `TerminalFragment.java` - UI 控制器
- `TerminalView.java` - 终端视图
- `TerminalSession.java` - 会话管理
- `SshClient.java` - SSH 客户端
- `BleEthTransport.java` - BLE 传输层
- `UsbEcmTransport.java` - USB 传输层
- `BleEthSocketFactory.java` - Socket 工厂
- `BleEthSocket.java` - BLE Socket 实现
- `TransportAdapter.java` - 传输适配器接口

### 支持文件
- `TerminalPrefs.java` - 偏好设置
- `FrameParser.java` - 帧解析器
- `DataReassembler.java` - 数据重组器
- `ConnectionManager.java` - 连接管理（已修改）
- `BluetoothService.java` - BLE 服务（已修改）

## 参考资料

- [JSch 文档](http://www.jcraft.com/jsch/)
- [BLE GATT 规范](https://www.bluetooth.com/specifications/gatt/)
- [SSH 协议 RFC 4253](https://tools.ietf.org/html/rfc4253)
- [USB CDC ECM 规范](https://www.usb.org/sites/default/files/CDC1.2_WMC1.1_012011.zip)

## 版本历史

### v1.0.0 (2024)
- 初始版本
- 实现 BLE SSH 连接
- 实现 USB ECM SSH 连接
- 支持密码认证
- 完整的终端 UI

## 许可证

本项目遵循项目主许可证。

## 联系方式

如有问题或建议，请通过项目 Issue Tracker 提交。
