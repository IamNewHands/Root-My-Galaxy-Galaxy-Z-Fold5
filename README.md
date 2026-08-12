# Root-My-Galaxy F731U (Galaxy Z Flip 5 美版) 定制 APK

Root-My-Galaxy 移植版，内置支持 **SM-F731U/DS**（Z Flip 5 美版 T-Mobile 运营商锁），
内核 **5.15.189-android13-8-33404244-abF731USQS8GZF1**（固件 F731USQS8GZF1）。

> ✅ **2026-08-12 真机验证成功**：v0.2.34 首次尝试即 `temporary-root-ready`（bootstrap root + KernelSU 启用）。
> 注意：临时 root，重启后失效，需重跑 App。

## 📦 Release（保留成功版本）

| 版本 | 状态 | 说明 |
|---|---|---|
| v0.2.36 | ✅ 最新 | 修复第二次运行报 "bundled exploit missing"（chmod 0444 只读导致重写失败） |
| v0.2.35 | ✅ | 修复更新检查误报（AppUpdater 指向本仓库） |
| v0.2.34 | ✅ 首次成功 | Shizuku 分支补 P0 env → temporary-root-ready 零重启 |

下载：https://github.com/youyoudezhuzhu/rmg-f731u/releases

## 📁 仓库结构

- `app-src/` — 改造后的 Root-My-Galaxy app 源码（含全部 assets）
  - `app-src/app/src/main/assets/cve-2026-43499-app.so` — **最终版 exploit payload（131,072B，md5 `3c82d4f678bd58846facf3e4ad356a33`）**，APK 实际打包用
  - `app-src/app/src/main/assets/ksud-f731u-kdp` — KernelSU ksud（6.7MB，内嵌 .ko）
  - `app-src/app/src/main/assets/targets-v3.json` — 设备支持清单
  - `app-src/app/src/main/jniLibs/arm64-v8a/libcve43499root.so` — root helper（23KB）
- `artifacts/f731u-F731USQS8GZF1/` — 同款 payload（与 assets 同步，md5 一致）——**同机型直接引用此文件**
- `support/` — targets-v3.json + payload（与 artifacts 同步）
- `kernelsu/` — KernelSU 模块与 ksud
- `.github/workflows/build-apk.yml` — GitHub Actions 云端编译

## 🎯 同机型（SM-F731U/DS, 固件 F731USQS8GZF1）直接引用方法

```
# exploit payload（CVE-2026-43499, F731U BTF 偏移已打）
curl -LO https://raw.githubusercontent.com/youyoudezhuzhu/rmg-f731u/main/artifacts/f731u-F731USQS8GZF1/cve-2026-43499-app.so

# 设备支持清单
curl -LO https://raw.githubusercontent.com/youyoudezhuzhu/rmg-f731u/main/support/targets-v3.json
```

其他机型：参照 `app-src/app/src/main/assets/targets-v3.json` 格式新增 profile，
payload 需按目标机型 BTF 重新计算偏移（见 `app-src/app/src/main/cpp/native_probe.c`）。

## 🔧 构建 APK

1. **自动**：push 到 main 分支自动触发 GitHub Actions
2. **手动**：仓库 → Actions → Build F731U APK → Run workflow

产物在 Actions 的 `rmg-f731u-apk` artifact 下载（保留 14 天）。

## 📄 产物来源

- exploit: Root-My-Galaxy-Payloads 移植，F731U 自身 BTF 偏移（CVE-2026-43499）
- kernelsu.ko: KernelSU v3.2.5 + 三星 KDP/RKP/DEFEX patch，DDK android13-5.15 编译
- ksud: KernelSU v3.2.5 userspace Rust 交叉编译（NDK r27c）
- root helper: F731U 编译版（已替换 app 内默认 helper）

## ⚠️ 注意

- 仅适用于 **SM-F731U/DS + F731USQS8GZF1 固件**（其他 F731 型号/固件需重新适配）
- 临时 root，**重启后失效**，需要 root 时重新运行 App（10 秒）
- 使用 Shizuku 时需 **root 模式启动**（否则 createDexMirror 类系统调用 UID 校验失败）
- 风险自负
