# Root-My-Galaxy Galaxy Z Fold 5

Root-My-Galaxy 移植版（适配 Galaxy Z Fold 5），内置支持 **SM-F9460**（Z Fold 5 国行，q5q），
内核 **5.15.189**（固件 **F9460ZCS9GZF1**），提供**临时 root**（bootstrap root + KernelSU）。

> ✅ **已真机验证**：SM-F9460 + F9460ZCS9GZF1 固件临时 root 成功。
> 临时 root，重启后失效，需重跑 App。已知问题与解决方法见 [docs/KNOWN-ISSUES.md](docs/KNOWN-ISSUES.md)。

## 📦 支持范围

| 项目 | 值 |
|---|---|
| 设备 | Samsung SM-F9460（Galaxy Z Fold 5 国行，q5q） |
| 固件 | F9460ZCS9GZF1 |
| 内核 | 5.15.189-android13-8 |
| 系统 | Android 16 (API 36) |
| ABI | arm64-v8a |

## 📁 仓库结构

- `app-src/` — Root-My-Galaxy app 源码（含全部 assets）
  - `app-src/app/src/main/assets/cve-2026-43499-app.so` — exploit payload（131,072B，md5 `3c82d4f678bd58846facf3e4ad356a33`，F731U 基准 + 运行时 patch 为 F9460ZCS9GZF1 偏移）
  - `app-src/app/src/main/assets/ksud-f731u-kdp` — KernelSU ksud（6.5MB，内嵌 .ko）
  - `app-src/app/src/main/assets/targets-v3.json` — 设备支持清单（仅 F9460）
  - `app-src/app/src/main/jniLibs/arm64-v8a/libcve43499root.so` — root helper
- `kernelsu/android13-5.15.189_kernelsu.ko` — KernelSU 内核模块（5.15.189，KDP/RKP/DEFEX patch）
- `support/targets-v3.json` — 设备支持清单（与 assets 同步）
- `tools/` — payload 补丁工具
- `.github/workflows/build-apk.yml` — GitHub Actions 云端编译

## 🔧 构建 APK

1. **自动**：push 到 main 分支自动触发 GitHub Actions
2. **手动**：仓库 → Actions → Build Fold5 F9460ZCS9GZF1 APK → Run workflow

产物在 Actions 的 `RootMyGalaxy-F9460ZCS9GZF1` artifact 下载（保留 14 天）。

## 🎯 同机型（SM-F9460, 固件 F9460ZCS9GZF1）直接引用方法

```
# exploit payload
curl -LO https://raw.githubusercontent.com/IamNewHands/rmg-f731u/main/app-src/app/src/main/assets/cve-2026-43499-app.so

# KernelSU daemon
curl -LO https://raw.githubusercontent.com/IamNewHands/rmg-f731u/main/app-src/app/src/main/assets/ksud-f731u-kdp

# 设备支持清单
curl -LO https://raw.githubusercontent.com/IamNewHands/rmg-f731u/main/support/targets-v3.json
```

## 🔬 Payload 适配说明

- payload 基准来自 `q5q-F9460TBS9GZF1`（s9180-root-kit 多机型包），经 `q5q → F731U` 补丁得到 F731U 基准（md5 `3c82d4...`），
  App 运行时再通过 `PayloadRepository.patchF9460Zcs9Gzf1()` 把 F731U 基准 patch 为 **F9460ZCS9GZF1** 的符号偏移。
- 详细方法论见 [docs/PORTING.md](docs/PORTING.md)。

## ⚠️ 注意

- 仅适用于 **SM-F9460 + F9460ZCS9GZF1 固件**（其他 F946 型号/固件需重新适配）
- **临时 root**，重启后失效，需要 root 时重新运行 App（约 10 秒）
- 需启用 Shizuku 才能 root 成功（exploit 引擎依赖 shell 权限）；App 会在安装前主动引导
- 风险自负
