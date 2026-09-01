# KNOWN-ISSUES.md — 已知问题与解决方法

> 本文档记录 `Root-My-Galaxy Galaxy Z Fold 5` 在 SM-F9460（国行 F9460ZCS9GZF1）上
> 使用时的已知问题与对应解决方法。真机验证：**已通过**（临时 root 成功）。

## 问题 1：必须启用系统设置里的"使用 Shizuku"才能 root 成功

### 现象

- 启用 **设置 → 使用 Shizuku** 后，临时 root 成功。
- **不启用** Shizuku 时，root 失败（exploit 中断）。

### 原因

本仓库的 exploit 引擎（`libcve43499root.so` + `cve-2026-43499-app.so`）依赖 **shell 级
UID 权限**才能完成关键操作（如 `createDexMirror`、LD_PRELOAD 注入等）。

- **启用 Shizuku**：exploit 通过 Shizuku 以 `shell` 权限（UID 2000）执行 → UID 校验通过 → root 成功。
- **不启用 Shizuku**：exploit 以 App 自身权限（`untrusted_app`）执行 → UID 校验失败 → root 中断。

这是 exploit 引擎的**硬性设计依赖**，不是 App 的 bug。要在不依赖 Shizuku 的情况下 root，
本质需要另一个能提供 shell 权限的通道（如 adb），Android 普通 App 无法自行获取 shell 权限。

### 解决方法

1. **安装 Shizuku**（`https://github.com/RikkaApps/Shizuku/releases/`）。
2. **启动 Shizuku**：用 `adb shell sh /sdcard/Android/data/moe.shizuku.manager/start.sh`
   或 root 方式启动。
3. **在 Shizuku 中授权**：授予 `Root-My-Galaxy Galaxy Z Fold 5` 权限。
4. 回到 App，**开启"使用 Shizuku"**，然后正常安装。

### App 内的引导优化

从 v0.2.35 起，App 在**点击"确认安装"时会主动检查 Shizuku 就绪状态**：

- 若 Shizuku 已启用但未运行/未授权 → 弹窗引导用户去打开 Shizuku。
- 若未启用 Shizuku → 提示"建议启用"，但仍允许用户选择"继续尝试"。

> **提示**：因该设备上不启用 Shizuku 通常必失败，强烈建议启用。

---

## 问题 2：KernelSU Manager 提示"管理器版本与驱动版本不匹配"

### 现象

安装 App 内置的 KernelSU 后，打开 KernelSU Manager，提示版本不匹配（"管理器版本与驱动版本不匹配"），
通常发生在 Manager 被更新到更高版本之后。

### 原因

App 内置的 KernelSU **内核驱动**（`ksud` + `kernelsu.ko`）版本为 **v3.2.5 (build 32525)**。
KernelSU 要求 **Manager APK 与内核驱动版本匹配**（Manager 通过 ioctl 与内核驱动通信，协议不兼容时即报错）。
你在商店更新了 Manager 到更高版本 → 与内置的 v3.2.5 驱动不匹配 → 报错。

### 解决方法（按推荐顺序）

#### 方法 1（最快）：装回 v3.2.5 Manager 并关闭自动更新

1. 卸载当前的 KernelSU Manager。
2. 从 App 内置链接重新安装 **`KernelSU_v3.2.5_32525-release.apk`**
   （`https://github.com/tiann/KernelSU/releases/download/v3.2.5/KernelSU_v3.2.5_32525-release.apk`）。
3. 安装后在应用商店（或 Manager 内）**关闭自动更新**，避免再次被升级。

#### 方法 2（治本）：升级内置驱动到新版

重新编译与新版 Manager 匹配的 `ksud` + `kernelsu.ko`，替换 App 内置组件。
详见下文"重新编译新版 ksud + kernelsu.ko"部分，以及
[`PORTING.md`](PORTING.md) 第 7 节的 KernelSU 模块构建说明。

#### 方法 3（临时）：忽略提示

临时 root 场景下，若仅需一次性使用，可尝试忽略版本提示。**不保证可用**，且可能不稳定，
不建议作为长期方案。

---

## 附：重新编译新版 ksud + kernelsu.ko（自编译路线）

> 若决定升级 KernelSU 到新版，或在长期安全需求下走自编译，参考以下流程。

### 关键背景：vermagic 差异（务必先理解）

- 内置 `android13-5.15.189_kernelsu.ko` 的 vermagic 为
  `5.15.189-android13-8-33404244-abF731USQS8GZF1`（**F731U** 构建号 `33404244`）。
- 目标设备 F9460ZCS9GZF1 的内核为
  `5.15.189-android13-8-3248304-abF9460ZCS9GZF1`（构建号 `3248304`）。
- 两者 vermagic **不同**。但真机 root 已成功，说明 KernelSU 走的是
  **kallsyms 感知的手动加载**（不依赖严格 vermagic 匹配）。
- 重新编译新版时，**建议以目标内核的 vermagic 为准**（`CONFIG_LOCALVERSION` 对齐），
  或沿用 kallsyms 手动加载路径（不校验严格 vermagic）。

### 需要提供的材料

1. **目标固件 AP 文件**：`AP_...tar.md5`（SM-F9460 国行 F9460ZCS9GZF1），
   用于提取内核符号/偏移、确认 vermagic、生成匹配的 `kernelsu.ko`。
2. **KernelSU 源码 + 三星 KDP/RKP/DEFEX patch**：官方 `tiann/KernelSU`（推荐 v3.2.5
   或目标新版）叠加三星 patch。参考 `Root-My-Galaxy-Payloads/kernelsu/patches/`：
   - `KernelSU-v3.2.5-samsung-kdp-rkp-defex.patch`
   - `KernelSU-v3.2.5-dm2q-fzg1.patch`（5.15 系参考）
   - `KernelSU-v3.2.5-dm1q-android13-5.15-build-fix.patch`
3. **Android NDK / 交叉编译工具链**（Linux 环境，推荐 Ubuntu）。

### 主要步骤

1. 从 AP 固件解出 `boot.img` → `kernel` Image → `vmlinux-to-elf` 恢复符号。
2. 按 `Root-My-Galaxy-Payloads` 的 `src/targets/<机型>/target.h` 结构生成 `target.h` + `p0_fingerprint.h`。
3. `make TARGET=... ANDROID_NDK_HOME=... release` 编译 `cve-2026-43499-app.so` + root helper。
4. 编译 KernelSU 内核模块：叠加三星 patch，`check_symbol` 校验符号、确认 vermagic 与目标内核匹配。
5. 替换 App 内置的 `ksud` / `kernelsu.ko`。

> **注意**：整套工具链（`vmlinux-to-elf`、`bpftool`、`llvm`）为 **Linux 专用**，Windows 上无法
> 直接运行。推荐通过 **GitHub Actions（Ubuntu）** 或 WSL 完成编译。

### 升级到新版是否更好？

- **优点**：Manager 与驱动版本匹配，不再有"版本不匹配"提示；修复旧版可能的 bug/安全补丁。
- **缺点**：需要重新编译内核模块（工作量大）；三星 KDP patch 需要与新版本 KernelSU 对齐；
  新版本可能改变 ioctl 协议或 late-load 流程，需重新验证 exploit 兼容性。
- **建议**：若只是临时 root 用途，**方法 1（装回 v3.2.5）已足够**；若希望长期稳定 + 获得新版
  修复，再考虑升级内核驱动。

---

*整理：2026-09-01 · 面向 SM-F9460 + F9460ZCS9GZF1（真机已验证临时 root）*
