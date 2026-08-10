# Root-My-Galaxy F731U (Galaxy Z Flip 5 美版) 定制 APK

Root-My-Galaxy 移植版，内置支持 **SM-F731U/DS**（Z Flip 5 美版 T-Mobile），
内核 **5.15.189-android13-8-33404244-abF731USQS8GZF1**（固件 F731USQS8GZF1）。

## 仓库结构
- `support/targets-v3.json` — 设备支持清单（F731U profile，schema v3）
- `artifacts/f731u-F731USQS8GZF1/cve-2026-43499-app.so` — exploit payload（104,128B，ELF ARM64）
- `kernelsu/android13-5.15.189_kernelsu.ko` — KernelSU 模块（vermagic 精确匹配 F731U，393KB stripped）
- `kernelsu/ksud-f731u-kdp` — ksud late-load 二进制（内嵌 .ko，4.9MB）
- `app-src/` — 改造后的 Root-My-Galaxy app 源码（PayloadRepository 指向本仓库）
- `.github/workflows/build-apk.yml` — GitHub Actions 云端编译 APK

## 构建 APK
两种方式：
1. **自动**：push 到 main 分支自动触发
2. **手动**：仓库 → Actions → Build F731U APK → Run workflow

产物在 Actions 的 `rmg-f731u-apk` artifact 中下载（保留 14 天）。

## 产物来源
- exploit: Root-My-Galaxy-Payloads 移植，F731U 自身 BTF 偏移（CVE-2026-43499）
- kernelsu.ko: KernelSU v3.2.5 + 三星 KDP/RKP/DEFEX patch，DDK android13-5.15 编译
- ksud: KernelSU v3.2.5 userspace Rust 交叉编译（NDK r27c）
- root helper: F731U 编译版（已替换 app 内默认 helper）

## 注意
- 仅适用于 SM-F731U/DS + F731USQS8GZF1 固件
- 未真机验证，风险自负
- app 运行时从本仓库 raw 下载 payload（需联网）
