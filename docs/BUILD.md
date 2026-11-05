# 构建说明

## 1. 基础包

| 项 | 值 |
|---|---|
| 基础 APK | `org.fcitx.fcitx5.android-0.1.3-2-0-gdf8e0324-arm64-v8a-release.apk` |
| 来源 | [fcitx5-android-bibi-keyboard](https://github.com/BryceWG/fcitx5-android-bibi-keyboard) Release `0.1.3-2` |
| 大小 | 45,986,672 字节 |
| 对应上游 | fcitx5-android `0.1.3-11-ge6199a28`（build 339，commit `e6199a28`） |

## 2. 工具链

- JDK 17（`javac` 目标 8）
- apktool 2.12.0（反编译 / 汇编）
- d8（R8 8.13.17，生成模块 dex）
- apksigner（Android build-tools 36.0.0，v1+v2+v3 签名）
- Python 3（`patches/rebuild.py`）

构建过程不需要 NDK：所有改动都在 Java/smali 层。

## 3. 步骤

```bash
# 1) 反编译（只反 smali，保留原始 resources.arsc）
apktool d --no-res -f base.apk -o decoded

# 2) 应用补丁
cd decoded && patch -p1 < ../patches/smali-changes.patch && cd ..

# 3) 汇编得到新的 classes.dex
apktool b decoded -o built.apk
unzip -o built.apk classes.dex -d out

# 4) 编译模块源码（自建 dex）
javac -source 8 -target 8 -cp "$ANDROID_JAR" -d classes patches/mod-src/*.java
d8 --release --min-api 23 --lib "$ANDROID_JAR" --classpath kotlin-stdlib.jar \
   --output moddex classes/**/*.class

# 5) 重打包（替换 classes.dex、追加 classesN.dex、保留对齐）
python3 patches/rebuild.py base.apk out/classes.dex moddex/classes.dex unsigned.apk

# 6) 签名（自签名，与官方不同）
apksigner sign --ks mod.keystore --ks-pass pass:*** --key-pass pass:*** \
  --ks-key-alias mod --v1-signing-enabled true --v2-signing-enabled true \
  --v3-signing-enabled true --out release.apk unsigned.apk

# 7) 校验
apksigner verify release.apk && sha256sum release.apk
```

## 4. 关于改名版（已取消）

早期曾发布过包名为 `com.dsha.xiaoqie.ime` 的独立版，因 provider authority 未彻底改名、可与官方版冲突，**1.1.0 起取消**，只发布沿用官方包名的单一版本。

## 5. 发布物校验

每次发布在 Releases 提供 `SHA256SUMS.txt`：

```bash
sha256sum -c SHA256SUMS.txt
```
