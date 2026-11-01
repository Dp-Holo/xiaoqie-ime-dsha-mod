# 第三方组件与许可

本仓库以 LGPL-2.1 发布（见 [LICENSE](LICENSE)），并基于以下项目：

| 项目 | 用途 | 许可 | 说明 |
|---|---|---|---|
| [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) | 上游输入法（build 339 / `e6199a28`） | LGPL-2.1-or-later | 本修改版的基础 |
| [fcitx5-android-bibi-keyboard](https://github.com/BryceWG/fcitx5-android-bibi-keyboard) | 语音联动基础（`0.1.3-2` / `df8e0324`） | LGPL-2.1 | 长按空格语音（AIDL）来自该分支 |
| [Fcitx5](https://github.com/fcitx/fcitx5) | 输入法核心（随上游打包） | LGPL-2.1-or-later | 二进制随上游提供 |
| AndroidX / Kotlin / kotlinx | 上游依赖（随上游打包） | Apache-2.0 | — |
| [BiBi / 说点啥](https://github.com/BryceWG/AsrKb) | 外部语音识别应用 | 见其仓库 | **本仓库不打包、不分发**，仅通过 AIDL 调用 |

## 备注

- 本仓库只包含**补丁、脚本与文档**，不重新分发上游源码；构建方法与基础包来源见 `docs/BUILD.md`。
- 上游作者与本项目无关，请勿向其后报本修改版的问题。
