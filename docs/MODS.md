# 改动清单（实现细节）

本文件对应 README 中的「使用体验改了什么」，给出每条的动机、实现位置与关联设置。

> 说明：本修改版基于 `fcitx5-android-bibi-keyboard 0.1.3-2`。其中「长按空格语音」来自该基础版本，
> 本仓库未修改其逻辑；其余各条为本仓库的 smali 注入。

## 1. 空格键长按 → 外部语音（AIDL）

- **行为**：长按空格进入语音会话，说完自动上屏；再次点按结束/取消。
- **依赖**：需自行安装语音应用（BiBi / 说点啥）并在其中开启「允许外部输入法联动（AIDL）」。
- **实现**：来自基础版本（`link/AsrkbSpeechClient`、`input/keyboard/CommonKeyActionListener`），本仓库未改动。

## 2. 第四行颜文字键（`:-)`）

- **行为**：第四行原「表情」键位改为 `:-)` 文本键，点击直接打开**颜文字（Emoticon）**面板。
- **动机**：常用颜文字少两次点击（原路径：表情面板 → 切 Tab）。
- **实现**：`input/keyboard/TextKeyboard.smali`（`<clinit>` 中重建第四行键序列）、
  新增 `mod/KeyFactory.smali`（构造 `:-)` 键的 `KeyDef` 与 `PickerSwitchAction(Emoticon)`）。
- **关联**：按键外观参数（字号/权重/变体）沿用键盘主题，不改主题默认值。

## 3. `?123` 单击 / 长按·上划

- **行为**：单击 → 数字键盘；长按或上划 → 符号面板。
- **动机**：数字输入一键直达；符号仍随手可达。原实现单击进符号面板且会记忆上次面板，
  导致「单击 `?123` 却停在符号面板」。
- **实现**：`mod/KeyFactory.smali` 的 `numberKey()`：`Press(LayoutSwitchAction("Number"))`、
  `LongPress/Swipe(LayoutSwitchAction("Symbol"))`。
- **关联**：上划是否生效受「虚拟键盘 → 划动输入标点符号及数字」设置影响。

## 4. `，。` 合一键

- **行为**：点 = `，`；上划 = `。`；长按 = `。`；键面角标显示 `。`。
- **动机**：右手拇指少跑一趟，中文标点更快。
- **实现**：`input/keyboard/TextKeyboard.smali`（构造合并键：`AltText(",", 3, ".")` +
  `Press(",")`/`Swipe(".")`/`LongPress(".")` + 预览）、
  `KeyDef$Behavior$Swipe.smali` 与 `BaseKeyboard.smali`（把 Swipe 的载荷类型泛化，
  使非 `FcitxKey` 语义也能挂在上划）。
- **关联**：角标位置遵循「主题 → 标点位置」设置。

## 5. 候选栏双击清空预输入

- **行为**：双击候选栏空白处 = 清空当前拼音预输入（不再逐个退格）。
- **实现**：`input/bar/KawaiiBarComponent$$ExternalSyntheticLambda3.smali`
  给候选 UI 设置双击监听，调用 `mod/ModExtras.clearPreeditOnDoubleTap()`（内部经 `FcitxAPI.reset`）。

## 6. 语言键长按 → 系统输入法选择器

- **行为**：长按语言键弹出系统输入法选择器。
- **实现**：`input/picker/PickerWindow$$ExternalSyntheticLambda0.smali` 中把原动作替换为
  `ModExtras.showSystemImPicker(context)`（`InputMethodManager.showInputMethodPicker()`）。

## 7. 卸载重装后自动恢复配置与词库

- **行为**：首次启动时，如果检测到外部备份比本地新，则自动恢复配置/主题/词库。
- **实现**：`FcitxApplication.smali` 注入 `ModRestore.restoreIfNeeded(context)`；
  `mod-src/ModRestore.java` 负责比较时间戳、复制文件、仅在不完整时不写恢复标记。
- **恢复来源**（按顺序）：
  `/sdcard/Android/media/<包名>/backup` → `/sdcard/Download/fcitx-backup`
- **注意**：卸载会清空应用私有数据；`Download/fcitx-backup` 不受影响。

## 8. 计算器候选（1.1.0 新增）

- **行为**：输入形如 `2+4` 的算式（结尾），候选栏顶部居中显示 `=6`；点选上屏，**长按结果**切换"带 =/纯数字"并持久保存
- **实现**：`mod/ModCalc`（表达式解析 + 候选页切换 + 长按切换 + SharedPreferences）
  - `InputView` 的 `InputPanelEvent` 分支记录 `preedit`
  - `HorizontalCandidateComponent` 设置候选数组时调用 `ModCalc.decorate`，可追加一条真实候选
  - `FcitxInputMethodService.commitText` 触发刷新；候选栏 `ViewAnimator` 切到候选页显示结果
- **注意**：拼音预输入存在时不显示（避免与真实候选抢位置）

## 9. 长按退格加速（1.1.0 新增）

- **行为**：按住退格连续删除，间隔从 50ms 递减到 15ms
- **实现**：`PageFetcher$flow$1` 里的重复间隔常量替换为 `mod/ModRepeat.nextDelay()`
- **注意**：该间隔对所有使用 `Repeat` 行为的按键生效

## 10. 空闲看门狗（1.1.0 新增，内部机制）

- `mod/ModWatchdog`：每 5 分钟用 `Process.getElapsedCpuTime()` 采样一次；键盘不可见且 CPU ≥30% 连续 2 次时做软重置，再持续 2 次才重启进程
- 正常运行几乎零开销；日志只在异常时写入 `files/mod-watchdog.log`

## 杂项

- `PickerLayout$Keyboard.smali`、`CapsKey.smali`：面板底部按键的排列/配色微调。
- `InputView.smali`：`v33` 常量改为 `3`（早期 `:-)` 表情/文字切换键的变体参数），未在上文单列。
- `link/AsrkbSpeechClient*.smali`：回调错误提示的一处静默处理。
