#!/usr/bin/env bash
# =============================================================================
# extract-f9460.sh — 在 WSL(Linux) 上从 Samsung AP 固件提取 F9460 内核符号
#
# 用途：为升级 KernelSU v3.3.0 提供目标内核符号(vmlinux.elf)与 kernel Image。
# 在 WSL 里运行一次，输出到当前目录的 ./f9460-out/ 文件夹。
#
# 用法：
#   bash extract-f9460.sh /path/to/AP_F9460ZCS9GZF1_*.tar.md5
# =============================================================================
set -euo pipefail

AP="$1"
OUT="f9460-out"
mkdir -p "$OUT"

echo "==> [1/6] 检查并安装工具 (lz4 / vmlinux-to-elf / llvm) ..."
sudo apt-get update -y -qq >/dev/null 2>&1 || true
sudo apt-get install -y -qq python3 python3-pip lz4 llvm binutils >/dev/null 2>&1 || true
pip3 install --quiet --break-system-packages lz4 vmlinux-to-elf 2>/dev/null || \
  pip3 install --quiet lz4 vmlinux-to-elf || true

echo "==> [2/6] 解 AP 包，取出 boot.img.lz4 ..."
# AP 是 tar.md5；用 tar 直接解（tar 会忽略 .md5 尾部校验）
rm -f "$OUT/boot.img.lz4"
# 方法1：标准顶层路径
tar -xf "$AP" -C "$OUT" boot.img.lz4 2>/dev/null || true
# 方法2：若顶层没有，从整个 tar 里搜 boot.img.lz4 的确切路径
if [ ! -f "$OUT/boot.img.lz4" ]; then
  echo "  (顶层无 boot.img.lz4，正在从 tar 中搜索 ...)"
  fname=$(tar -tf "$AP" 2>/dev/null | grep -iE '(^|/)boot\.img\.lz4$' | head -1)
  if [ -n "$fname" ]; then
    tar -xf "$AP" -C "$OUT" "$fname"
    # 保持输出文件名为 boot.img.lz4
    mv -f "$OUT/$(basename "$fname")" "$OUT/boot.img.lz4" 2>/dev/null || true
  fi
fi
if [ ! -f "$OUT/boot.img.lz4" ]; then
  echo "错误：未能从 AP 包中找到 boot.img.lz4" >&2
  exit 1
fi
ls -la "$OUT/boot.img.lz4"

echo "==> [3/6] 解压 boot.img.lz4 -> boot.img，并切出 kernel Image ..."
python3 - "$OUT" <<'PY'
import sys, lz4.frame, struct
from pathlib import Path
out = Path(sys.argv[1])
compressed = (out / "boot.img.lz4").read_bytes()
boot = lz4.frame.decompress(compressed)
(out / "boot.img").write_bytes(boot)
kernel_size = struct.unpack_from("<I", boot, 0x08)[0]
kernel = boot[0x1000:0x1000 + kernel_size]
(out / "kernel").write_bytes(kernel)
import hashlib
print("boot.img  size:", len(boot))
print("boot.img  sha256:", hashlib.sha256(boot).hexdigest())
print("kernel    size:", len(kernel))
print("kernel    sha256:", hashlib.sha256(kernel).hexdigest())
PY

echo "==> [4/6] 用 vmlinux-to-elf 恢复符号 -> vmlinux.elf ..."
vmlinux-to-elf "$OUT/kernel" "$OUT/vmlinux.elf"
echo "  vmlinux.elf 生成完成:"
ls -la "$OUT/vmlinux.elf"

echo "==> [5/6] 用 llvm-nm 列符号 -> vmlinux.nm ..."
llvm-nm --numeric-sort "$OUT/vmlinux.elf" > "$OUT/vmlinux.nm" 2>/dev/null || \
  nm --numeric-sort "$OUT/vmlinux.elf" > "$OUT/vmlinux.nm"
wc -l "$OUT/vmlinux.nm"
echo "  (抽样符号)"
grep -m5 -E " (init_task|prepare_kernel_cred|commit_creds|ashmem_fops|kmalloc_caches)$" "$OUT/vmlinux.nm" || true

echo "==> [6/6] 汇总固件身份信息 ..."
cat > "$OUT/firmware-info.txt" <<EOF
Firmware: AP_F9460ZCS9GZF1 (SM-F9460 国行)
Kernel  : 5.15.189-android13-8
Target  : F9460ZCS9GZF1
Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
# 尝试从 vmlinux 提取版本信息
strings -a "$OUT/kernel" 2>/dev/null | grep -m3 -E "Linux version 5\.15|5\.15\.189-android" >> "$OUT/firmware-info.txt" || true
echo "----------------------------------------"
echo "  完成! 提取结果在: $(pwd)/$OUT/"
echo "  需要上传给编译环境的关键文件:"
echo "    - $OUT/kernel         (原始内核 Image, 约 30-40MB)"
echo "    - $OUT/vmlinux.elf    (恢复符号后的 ELF)"
echo "    - $OUT/vmlinux.nm     (符号列表)"
echo "    - $OUT/firmware-info.txt (固件身份)"
echo ""
echo "  [可选] 保留 vmlinux.elf + vmlinux.nm 即可, kernel 也可保留做校验"
