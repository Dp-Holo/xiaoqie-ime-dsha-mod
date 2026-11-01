# patches

本目录是 1.0.0 相对上游基础包的全部改动，用于可复现构建（LGPL-2.1 要求提供可修改的形式）。

## 内容

| 文件 | 说明 |
|---|---|
| `smali-changes.patch` | 反编译后的 smali 改动（`diff -ruN` 格式，`-p1` 应用） |
| `mod-src/` | 自建 dex 的 Java 源码（`ModExtras.java`、`ModRestore.java`） |
| `rebuild.py` | 重打包脚本：替换 `classes.dex`、追加模块 dex、保留条目压缩方式与 4 字节对齐 |

## 应用步骤

```bash
# 1. 反编译基础包（基础包来源见 docs/BUILD.md）
apktool d --no-res -f base.apk -o decoded

# 2. 应用 smali 补丁
cd decoded && patch -p1 < ../patches/smali-changes.patch && cd ..

# 3. 重新汇编（得到新的 classes.dex）
apktool b decoded -o built.apk
unzip -o built.apk classes.dex -d out

# 4. 编译模块源码为 dex（需要 android.jar，JDK 17）
javac -source 8 -target 8 -cp android.jar -d classes patches/mod-src/*.java
d8 --release --min-api 23 --lib android.jar --output moddex classes/**/*.class

# 5. 重打包 + 签名
python3 patches/rebuild.py base.apk out/classes.dex moddex/classes.dex out/unsigned.apk
apksigner sign --ks your.keystore --out release.apk out/unsigned.apk
```

> 重建产物与 Releases 中的 APK **不会字节相同**（签名、压缩实现、时间戳都不同），
> 这是正常现象；本项目未提供逐字节可复现构建。
