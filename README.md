<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner_dark.png?v=20261123">
  <img src="docs/assets/banner.png?v=20261123" width="880" alt="LittleStar — 汉字积木，给海外华裔孩子的识字 App" />
</picture>

**汉字积木 · 给海外华裔孩子的识字 App**

<sub>A Chinese-literacy app for heritage kids growing up in English-speaking homes.</sub>

<p>
  <a href="https://github.com/Green-hats/LittleStar/releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-APK-8957e5?style=flat-square" /></a>
  <img alt="CI" src="https://github.com/Green-hats/LittleStar/actions/workflows/ci.yml/badge.svg?style=flat-square" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Offline" src="https://img.shields.io/badge/Offline-no%20INTERNET-27A567?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-1F6FD0?style=flat-square" />
</p>

<hr />

<img src="docs/screenshots/readme/hero.png?v=20261123" width="880" alt="浅色 Material 3 首页、识字卡和学习完成页" />

<sub>首页、识字卡和学习完成页。应用使用固定浅色的 Material 3 界面。</sub>

</div>

## 它是什么

一个给三到六岁、在英语环境里长大的孩子学汉字的 Android App。

界面使用英文，教学内容使用中文。孩子可以通过图标和声音浏览汉字，也可以听儿歌、故事和古诗。家长通过 PIN 进入学习计划，设置每日识字目标和提醒时间。

## 两个内容模块

<table>
<tr>
<td align="center" width="50%"><img src="docs/screenshots/readme/feature_quiz.png?v=20261123" width="230" alt="听音测验中的答题反馈" /><br/>
<b>字</b><br/><sub>识字卡 → 四选一测验 → 学习完成。答错时会显示正确答案。</sub></td>
<td align="center" width="50%"><img src="docs/screenshots/readme/feature_player.png?v=20261123" width="230" alt="《两只老虎》的播放页，显示圆角封面和播放控件" /><br/>
<b>文</b><br/><sub>儿歌、故事、古诗。先看简介与学习重点，再收听；支持后台播放、暂停、切歌和拖动进度。</sub></td>
</tr>
</table>

## 三人团队结构

项目分为识字学习与测评、文化内容学习、学习计划与反馈三个子系统，共用一个首页。文化流程依次经过分类、列表、Discover 详情和播放器。
具体职责、五个用例与数据交接见 [团队分工](docs/TEAM_PLAN.md)。

## 家长设置

从首页点 **Learning plan**，通过 PIN 验证后可设置每日识字目标、提醒时间与星期。首次引导包含目标和提醒设置，第一次打开学习计划时创建 PIN。设置菜单提供帮助、关于和测试通知。学习记录保存在本机。

## 功能验收

### 自适应词汇浏览

从首页进入 **Browse words**，再选择一个主题。可用宽度小于 600 dp 时先显示主题列表，选中后切到词表，返回可回到主题列表。可用宽度达到 600 dp 时，主题和词表同屏，点击左侧主题会更新右侧词表。

主题列表和词表分别由 `ThemesFragment` 和 `WordListFragment` 实现。同一个 `ThemesActivity` 通过 `FragmentManager` 管理切换，并在 Activity 重建后恢复所选主题。手机和平板的示例见 [PR #16](https://github.com/Green-hats/LittleStar/pull/16)。

### 可操作学习提醒

1. 授予 LittleStar 系统通知权限。在 **Learning plan** 勾选当天，设置稍后的提醒时间并保存，保留至少一个待学汉字。
2. 等待系统送达并展开学习提醒。提醒会显示当天剩余字数，学完后当天的提醒不再发送。调度使用 Android 的非精确闹钟，窗口为 10 分钟。
3. 点击 **Learn now** 直接进入当天的识字卡，或点 **Dismiss** 关闭本次提醒。后续提醒仍按学习计划发送。通知示例见 [PR #17](https://github.com/Green-hats/LittleStar/pull/17)。

## 架构

<p align="center"><img src="docs/screenshots/readme/architecture.png?v=20261123" width="880" alt="LittleStar 架构，包含 AndroidX Fragment 与每日提醒" /></p>

<p align="center">
  <sub><b>Java 17</b> · XML View + ViewBinding · AndroidX Fragment · Material 3 · MVVM + LiveData · Media3 ExoPlayer · Gson + AtomicFile<br/>
  零 Kotlin · 无数据库 · JVM 单测与设备回归</sub>
</p>

<details>
<summary><b>🧩 几个实现细节</b> &nbsp;<sub>数据库 · 磁盘 · 卡片 · 字体</sub></summary>

<br/>

- **不用数据库** — 一年数据只有几百 KB。`progress.json` 原子写 + 2 秒防抖 + 每日备份 + 损坏回退，查询就是普通 Java 循环。
- **磁盘单线程** — 所有读写投递到同一个线程，状态只在那里改，天然免锁；一次 `update()` 就是一次事务。
- **卡片样式** — `BlockLayout` 使用 Material 形状、圆角和描边，可点击卡片使用涟漪反馈。
- **汉字子集字体** — 从 Noto Sans SC 提取内置教学内容和预览文本所需字形，保证「月」「骨」的写法不随厂商变。

</details>

## 快速开始

**装到手机** —— 在 [GitHub Releases](https://github.com/Green-hats/LittleStar/releases/latest) 下载 APK，适用于 Android 11 或更高版本。

```bash
adb install -r path/to/LittleStar.apk
```

**从源码构建** —— 安装 Android SDK 37，并通过 Android Studio 配置项目的 SDK 路径，或设置 `ANDROID_HOME`。可用 JDK 21 启动 Gradle，构建守护进程依仓库配置使用 JDK 25，应用源码目标为 Java 17。

```bash
git clone https://github.com/Green-hats/LittleStar.git && cd LittleStar
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 文档

| 文档 | 内容 |
|---|---|
| [**团队分工**](docs/TEAM_PLAN.md) | 三个子系统、团队用例及模块交接 |
| [**设计书**](docs/DESIGN.md) | 用户流程、文化详情与家长菜单 |
| [**工程技术文档**](docs/ENGINEERING.md) | 数据模型、业务逻辑、组件规格、逐屏实现要点 |
| [**验收清单**](docs/ACCEPTANCE.md) | 20 项结果与证据：对比度、权限、进程恢复、APK 体积 |
| [**决策记录**](docs/DECISIONS.md) | 设计与实现取舍 |
| [**协作指南**](CONTRIBUTING.md) | 环境、提交前自检、代码约定 |
| [**截图归档**](docs/screenshots) | 各阶段的历史界面截图 |

<sub>MIT © 2026 Green-hats · 字体 Noto Sans SC / Poppins（SIL OFL 1.1）</sub>
