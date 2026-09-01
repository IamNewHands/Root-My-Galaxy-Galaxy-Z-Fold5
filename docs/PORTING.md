# PORTING.md — F9460 payload 适配说明（闭源引擎 + 偏移补丁）

> 本文档解释 `rmg-f731u` 仓库中 F9460（Z Fold 5 国行）payload 的适配现状，
> 以及闭源引擎的通用适配方法论。

## 1. 背景：闭源引擎是什么、从哪来

本仓库的 `cve-2026-43499-app.so`（131072B）是 **s9180-root-kit 多机型工具包**中的
官方闭源 CVE-2026-43499 (GhostLock) 新架构引擎。该引擎特性：

- **不用 KernelSnitch / futex 时序侧信道**（老架构，三星上不可靠）
- **`/proc/slabinfo` 直接定位 mm_struct**（解析 `mm_struct %lu %lu...` 行）
- **`/proc/sys/kernel/random/boot_id` 泄 KASLR**
- **configfs/ashmem 写原语 + LD_PRELOAD 注入 + KernelSU late-load**
- 阶段标记：`preparing-kernel-access` → `locating-kernel` → `kernel-location-ready` →
  `verifying-kernel-access` → `starting-temporary-root` → `temporary-root-ready`

**引擎是通用的**：同一引擎换偏移表即可适配任意机型。证据：F731U 闭源 payload 与 S9180 闭源 payload
只差 36 字节（0.03%），全部是偏移常量 + 少量指令补丁。

## 2. 仓库文件说明

```
app-src/app/src/main/assets/cve-2026-43499-app.so   # App 实际加载的 payload（F731U 基准 + 运行时 patch 为 F9460 偏移）
app-src/app/src/main/assets/ksud-f731u-kdp           # KernelSU daemon（KDP 排除版，内嵌 .ko）
app-src/app/src/main/jniLibs/arm64-v8a/libcve43499root.so  # root helper（闭源 root 组件）
kernelsu/android13-5.15.189_kernelsu.ko              # KernelSU 内核模块（5.15.189）
support/targets-v3.json                              # 设备支持清单（与 assets 同步）
tools/patch_payload.py                               # 通用 payload 补丁工具
```

## 3. F9460 payload 适配现状

**基准链路**：

1. `q5q-F9460TBS9GZF1__payload`（Z Fold 5 国行，131072B，md5 `31fab32a...`）——s9180-root-kit 包中的原始引擎
2. 经 `q5q → F731U` 补丁（符号偏移 +0x40，详见旧版 spec）得到 **F731U 基准**（md5 `3c82d4f6...`）
3. App 运行时 `PayloadRepository.patchF9460Zcs9Gzf1()` 把 F731U 基准 patch 为 **F9460ZCS9GZF1** 的符号偏移（15 处字节补丁）

**当前状态**：✅ **F9460ZCS9GZF1 的 patch 偏移已真机验证**（临时 root 成功）。
闭源引擎自带 `verifying-kernel-access` 自校验阶段，偏移错误时会**安全拦截**（不 panic），
真机成功即证明偏移与 F9460ZCS9GZF1 的实际 BTF 布局匹配。

> 已知问题（如必须启用 Shizuku、KernelSU Manager 版本不匹配）见
> [`KNOWN-ISSUES.md`](KNOWN-ISSUES.md)。

### 3.1 补丁后验证

1. **ELF 完整性**：`readelf -h` / `file` 确认仍是合法 ELF
2. **符号偏移自校验**：引擎内部有 `verifying-kernel-access` 阶段，偏移不对会安全拦截（不 panic）
3. **真机验证**：`temporary-root-ready` 日志出现 = 成功

## 4. 适配新机型通用流程

### 4.1 准备
- 目标机型固件（AP 文件）→ boot.img → 内核 Image → vmlinux-to-elf 恢复符号
- 参考 `Root-My-Galaxy-Payloads` 仓库 `src/targets/<机型>/target.h`（开源符号偏移，可交叉验证）

### 4.2 找基准引擎
- 从 s9180-root-kit 包 `payloads/` 选**内核版本最接近**的机型 payload（5.15 系列选 q5q/dm3q，6.1 选 e3q 等）

### 4.3 计算偏移差
- 目标机型符号偏移 vs 基准机型符号偏移 → 差值
- 如果差值 ≤ 0xFFFF（单 MOV 立即数范围）：只需改 MOV 指令立即数
- 如果差值 > 0xFFFF：需要改 `movk` 高 16 位（`lsl #16`）指令

### 4.4 打补丁
1. 用 capstone 反汇编 .text，找所有 `mov wN/xN, #imm` 引用目标符号的位置
2. 计算新立即数（基准值 ± 偏移差）
3. 用 keystone 重新汇编对应指令，写回 .so
4. 处理机型检查（找到检查点，改成 nop / 恒 true）

### 4.5 配套组件
- **ksud**：不同内核需要不同 KDP 版（5.15 版已在本仓库，6.1/6.6 在 s9180-root-kit tools/ 有）
- **KernelSU 内核模块**：需与目标内核匹配（编译或用包内现成的）

### 4.6 测试
- CLI 直接跑 payload（`SLIDE_P0_OFFSET` 可强制偏移）→ 看阶段日志
- App 模式：替换 assets 里的 .so + targets-v3.json

## 5. 常见问题

- **`kernel page prepare mode=` 后重启**：KASLR slide 未匹配 / p0 偏移错误（KDP/RKP 拦截）
- **`[grku] verification failed E...`**：授权校验没绕干净
- **偏移不匹配安全拦截**：引擎自校验，不会 panic，但也不会成功——说明还有偏移没改对
- **`ro.arch=exynos9810` 之类检测**：部分引擎有硬件检测，需绕过

## 6. 与开源老架构的区别

| | 开源老架构（KernelSnitch） | 闭源新架构（本仓库） |
|---|---|---|
| mm_struct 定位 | futex 时序侧信道盲扫（概率性） | /proc/slabinfo 直读（确定性） |
| KASLR 泄漏 | pipe oracle 碰撞 | boot_id + tracefs |
| 写原语 | pipe/configfs 碰撞 | configfs/ashmem 直接写 |
| 成功率 | 三星上低（1/24 甚至更低） | 高（attempt 1 即成功） |
| 稳定性 | 误判写坏内核对象 → 重启 | 自校验，偏移错安全拦截 |

**建议**：所有三星机型适配都优先用闭源新架构（本仓库的方法），不要走 KernelSnitch 老路径。

---
*整理：2026-09-01 · 面向 SM-F9460 + F9460ZCS9GZF1 适配*
